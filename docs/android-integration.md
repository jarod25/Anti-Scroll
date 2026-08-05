# Android integration

## Objective

The Android integration layer observes application usage, restores state after interruptions and enforces domain decisions as reliably as the platform allows.

It must not contain business rules that belong in the domain or restriction engine.

## Usage access

Usage access is required to query Android-provided usage history and reconstruct periods of application activity.

The application must:

- declare its intent to use `PACKAGE_USAGE_STATS`;
- explain why access is needed;
- guide the user to the relevant system screen;
- verify the real system state on every relevant resume and before collection;
- detect revocation;
- record collection gaps;
- never fabricate usage during missing periods.

Opening the usage-access settings screen is not proof that access was granted. The application reads the actual app-op state and handles a missing settings activity without crashing.

`UsageStatsManager.queryEvents` is the historical and recovery source. Android retains event history for only a limited period, and Android 11 or later may return no data while the user is locked. Collection therefore persists normalized observations regularly and classifies locked or unavailable periods explicitly.

On API 35 and later, `UsageEventsQuery` restricts queries to relevant package names and event types. API 29 to 34 uses the timestamp-range query and filters the returned events locally.

UsageStats data may be delayed, incomplete or ordered differently depending on Android version and device behavior. It is not treated as a perfect real-time event stream or presented as exact to the second.

## Accessibility service

The accessibility service provides a low-latency package-level signal when a selected scrolling application reports a window-state transition. It does not replace UsageStats and does not own durable observation history.

Its scope is deliberately narrow:

- receive only `TYPE_WINDOW_STATE_CHANGED` events;
- dynamically restrict Android package filtering to applications enabled in the monitored-application repository;
- use Anti-Scroll's own package as a safe empty filter when no scrolling application is enabled;
- set `canRetrieveWindowContent` to `false`;
- never inspect accessibility nodes, visible text, messages or typed content;
- never perform gestures or automate another application;
- emit transient `ForegroundApplicationSignal` values through a domain contract;
- attach both a wall-clock instant and Android elapsed realtime to each signal;
- persist no accessibility event journal;
- perform no remote transmission.

Package-level window-state events can repeat for internal Activity transitions. The source therefore reports signals rather than claiming that every event is a unique application opening. Restriction evaluation remains idempotent and derives session behavior from its own state.

The application reports accessibility state separately from Usage Access:

- disabled when Android has not enabled the service;
- connected when Android has enabled and bound the service;
- disconnected when Android reports it enabled but the current process has no active service connection;
- unavailable or error when the platform state cannot be read reliably.

UsageStats collection and daily reconstruction continue to work when the service is disabled or disconnected. Store-distribution requirements must be reviewed again before release; eligibility must not be assumed from development-device behavior.

The detailed decision is recorded in ADR-008.

## Monitoring composition

The monitoring implementation combines sources with distinct roles:

```text
UsageStats history ──────> normalized durable observations -> Room journal
Accessibility signals ───> explicit foreground events ─────> shared-session runtime
Additional event sources ─> explicit background/recovery ──> shared-session runtime
System signals ──────────> recovery and interpretation triggers
```

UsageStats owns historical reconstruction and comparison with Android system data. Accessibility provides low-latency foreground signals only. System signals report boot, user unlock, time, time-zone and package changes that affect recovery or interpretation.

`ForegroundSignalSharedSessionEventSource` preserves the accessibility signal's package, wall-clock instant and elapsed realtime while adapting it to `ApplicationForegrounded`. It does not infer a matching background transition.

`SharedSessionRuntime` starts with the application process, serializes profile and event inputs, applies the pure reducer and evaluates the restriction engine after accepted foreground events. Its state and latest decision are exposed through a read-only `StateFlow`.

The production runtime begins with the versioned observation profile, which has no shared-session policy. This wires monitoring without activating arbitrary example durations. A later durable profile source must activate configured restrictions explicitly.

UsageStats source implementations do not write directly to Room. They emit source observations that are normalized through domain contracts. The app module composes monitoring and persistence implementations while monitoring and data remain independent sibling modules.

Historical events must be ordered, deduplicated and normalized before they affect durable projections. Android does not provide one universal event identifier, so deduplication is deterministic but must not be described as infallible.

The runtime composition and its limitations are recorded in ADR-010.

## Collection reconciliation

Observation collection is incremental and idempotent.

- Application start and relevant foreground resumes request a bounded reconciliation.
- WorkManager schedules unique persistent deferrable reconciliation.
- Each historical query includes a bounded overlap before the last persisted checkpoint.
- Replayed observations are normalized and deduplicated before daily projections change.
- Successful processing advances the source checkpoint transactionally with affected persistence changes.
- Unavailable history, missing permission, locked-user state and source failure produce distinct health or gap records.
- Unknown periods remain unknown; the application never fills them with estimated usage unless a future documented feature explicitly introduces an estimate.

WorkManager persists and reschedules required work across process and device restarts, but Android controls the exact execution time. It is not used as a real-time foreground-application detector.

## Package visibility

Android 11 and later filter package information returned to applications. The monitored-application picker uses a versioned catalog of supported package names and targeted `<queries>` entries when labels, icons or installation state must be resolved.

Anti-Scroll does not request `QUERY_ALL_PACKAGES`.

Package names remain the persisted identity. Labels and icons are presentation metadata resolved from Android when visible and available. A failure to resolve them must not alter previously stored usage identity.

## Blocking coordination

The domain returns a restriction decision. Android-specific code then performs the best supported intervention, such as presenting a dedicated blocking activity or directing the user away from the restricted application.

The block UI must show:

- restricted application;
- stable reason translated for the user;
- remaining duration or expected end time when available;
- safe navigation away from the application;
- no instant bypass control.

The exact enforcement mechanism requires real-device validation across supported Android versions.

The shared-session runtime currently observes decisions only. It does not start cooldowns, persist restriction state, record blocked attempts or launch a blocking interface.

## Foreground execution

The observation foundation, accessibility foreground-signal service and process runtime do not introduce a permanent foreground service or aggressive polling loop.

A foreground service is used only when continuous execution is necessary for a concrete user-visible feature and compliant with the targeted Android version.

When active, it must:

- display a clear persistent notification;
- perform minimal work;
- avoid aggressive polling;
- stop when no longer required;
- use the correct foreground-service type and permissions for the final target SDK.

Adopting one requires a separate architecture review covering lifecycle ownership, battery cost, notification behavior, service type and background-start restrictions.

## Restart and process death

The application must not assume that its process remains alive.

During observation, durable recovery reconstructs:

- monitored application configuration;
- normalized usage observations;
- daily usage projections;
- collection checkpoints and gaps;
- permission and monitoring health;
- persistent reconciliation work.

Accessibility signals and the current shared-session runtime snapshot are intentionally process-local. After a process or service interruption, UsageStats reconstructs durable observation history while later increments must restore persisted restriction session and cooldown state.

Later increments also reconstruct:

- active profile;
- cooldown expiry;
- quota consumption;
- latest session state.

WorkManager is the default owner of persistent deferrable reconciliation. A boot receiver performs no heavy work and only enqueues or refreshes durable work within Android background-execution limits.

## Time and time-zone changes

The integration layer forwards wall-clock and time-zone changes to the domain. Elapsed durations use monotonic time while the process is alive. Persisted expiry data must include enough information to avoid shortening restrictions through simple clock changes.

Accessibility foreground signals contain both the current wall-clock instant and Android elapsed realtime. The monotonic value is transient and resets on reboot; it must not be treated as a durable timestamp.

The runtime uses Android elapsed realtime only for runtime-owned transitions such as ending an active session after a profile change. Foreground monitoring events retain their source timestamps unchanged.

Observation events preserve source wall-clock timestamps. Daily projections use an explicit local-day interpretation so time-zone changes can be handled deterministically and raw observations can be replayed if aggregation rules change.

The final restriction-time strategy will be specified and tested before cooldown persistence because Android monotonic clocks reset on reboot.

## Manufacturer restrictions

Some manufacturers aggressively stop background services or defer scheduled work. The application should detect degraded monitoring where possible and provide device-specific guidance without claiming universal reliability.

## Device Owner

Device Owner capabilities are not required for the standard first release because provisioning is incompatible with a normal consumer installation path.

A stronger Device Owner mode may be explored later as a separate deployment profile. Standard architecture must not depend on it.

## Validation matrix

Before the first useful release, testing must include:

- at least one physical reference device;
- UsageStats comparison with Android system totals;
- process kill and recreation;
- device restart and user unlock;
- permission revocation and restoration;
- accessibility enable, disable and disconnect behavior;
- proof that window content and accessibility nodes are not requested;
- repeated overlapping reconciliation;
- rapid switching between monitored applications;
- explicit background and recovery event correction;
- screen lock and unlock;
- time and time-zone changes;
- package visibility behavior;
- battery-optimization behavior;
- supported Android-version boundaries.

The observation architecture and its trade-offs are recorded in ADR-007. Accessibility foreground monitoring is recorded in ADR-008. Shared-session runtime composition is recorded in ADR-010.
