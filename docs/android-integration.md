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

`UsageStatsManager.queryEvents` is the historical and recovery source for increment 1. Android retains event history for only a limited period, and Android 11 or later may return no data while the user is locked. Collection therefore persists normalized observations regularly and classifies locked or unavailable periods explicitly.

On API 35 and later, `UsageEventsQuery` should restrict queries to relevant package names and event types. API 29 to 34 uses the timestamp-range query and filters the returned events locally.

UsageStats data may be delayed, incomplete or ordered differently depending on Android version and device behavior. It is not treated as a perfect real-time event stream or presented as exact to the second.

## Accessibility service

An accessibility service may be used to detect foreground application changes quickly and later trigger the block experience when a restricted application is opened.

During increment 1 it remains an optional experiment behind a domain-facing monitoring contract. UsageStats collection and daily reconstruction must continue to work when the service is disabled or disconnected.

Its scope must remain minimal:

- observe only the event types required for application identification;
- restrict package filtering when the platform configuration permits it;
- set `canRetrieveWindowContent` to `false` for the observation experiment;
- avoid reading accessibility nodes, visible text or user content;
- perform no remote transmission;
- document the purpose clearly in the service description and onboarding;
- remain removable from the domain through an interface;
- report enabled, connected and disconnected states separately.

Store-distribution requirements must be reviewed again before release. Eligibility must not be assumed from current development behavior.

## Monitoring composition

The monitoring implementation combines sources with distinct roles:

```text
UsageStats history ──────┐
Accessibility events ────┼─> observation normalizer -> normalized domain events
System signals ──────────┘
```

UsageStats owns historical reconstruction and comparison with Android system data. Accessibility provides optional low-latency signals for evaluation. System signals report boot, user unlock, time, time-zone and package changes that affect recovery or interpretation.

Source implementations do not write directly to Room. They emit source observations that are normalized through `domain` contracts. The `app` module composes monitoring and persistence implementations while `monitoring` and `data` remain independent sibling modules.

Events must be ordered, deduplicated and normalized before they affect durable projections. Android does not provide one universal event identifier, so deduplication is deterministic but must not be described as infallible.

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

Android 11 and later filter package information returned to applications. The initial monitored-application picker uses a versioned catalog of supported package names and targeted `<queries>` entries when labels, icons or installation state must be resolved.

Increment 1 does not request `QUERY_ALL_PACKAGES`.

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

Blocking is not implemented during increment 1.

## Foreground execution

Increment 1 does not introduce a permanent foreground service or aggressive polling loop.

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

Later increments also reconstruct:

- active profile;
- cooldown expiry;
- quota consumption;
- latest session state.

WorkManager is the default owner of persistent deferrable reconciliation. A boot receiver may be added only if real-device validation demonstrates a concrete missing trigger. A receiver performs no heavy work and only enqueues or refreshes durable work within Android background-execution limits.

## Time and time-zone changes

The integration layer forwards wall-clock and time-zone changes to the domain. Elapsed durations use monotonic time while the process is alive. Persisted expiry data must include enough information to avoid shortening restrictions through simple clock changes.

Observation events preserve source wall-clock timestamps. Daily projections use an explicit local-day interpretation so time-zone changes can be handled deterministically and raw observations can be replayed if aggregation rules change.

The final restriction-time strategy will be specified and tested before cooldown implementation because Android monotonic clocks reset on reboot.

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
- repeated overlapping reconciliation;
- rapid switching between monitored applications;
- screen lock and unlock;
- time and time-zone changes;
- package visibility behavior;
- battery-optimization behavior;
- supported Android-version boundaries.

The observation architecture and its trade-offs are recorded in ADR-007.
