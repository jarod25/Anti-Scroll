# ADR-007: Observation monitoring and recovery architecture

- Status: Accepted
- Date: 2026-07-25
- Decision owners: Project maintainers

## Context

Increment 1 must collect local application-usage data that is reliable enough to support later sessions, cooldowns and quotas. Android does not expose one unrestricted, perfectly real-time and permanent foreground-application stream to ordinary applications.

`UsageStatsManager` provides system-maintained usage history after the user grants usage access. Its event history is retained for only a limited period, may be delayed or incomplete, and returns no data while the user is locked on Android 11 and later. It is suitable for historical reconstruction and comparison with Android system totals, but it must not be presented as an exact real-time feed.

An accessibility service can receive application-transition signals more quickly, but it is a sensitive user-enabled capability whose lifecycle is controlled by Android. It must not become the only source of durable usage history, and the observation increment must remain functional when it is disabled.

The existing module boundaries prevent `monitoring`, `data` and `engine` from depending directly on one another. Android sources must therefore communicate through framework-free contracts and models owned by `domain`, while `app` remains the composition root.

Process death, device reboot, delayed work and manufacturer battery restrictions require recovery from persisted state. In-memory cursors or totals cannot be the sole source of truth, and missing periods must be exposed instead of filled with invented usage.

## Decision

### Monitoring source roles

The observation pipeline separates source-specific Android signals from normalized domain events.

- UsageStats is the required historical and recovery source for increment 1.
- Accessibility is an optional low-latency experiment behind the same domain-facing source boundary.
- System signals report lifecycle-relevant conditions such as boot, unlock, time-zone and package changes.
- No Android source owns restriction rules or writes directly to Room.

The initial pipeline is:

```text
UsageStats source ───────┐
Accessibility source ────┼─> observation normalizer -> usage event repository
System signal source ────┘                                  |
                                                             -> daily usage projection
                                                             -> collection health
```

The `app` module composes source implementations, repositories and orchestration through contracts defined in `domain`. `data`, `monitoring` and `engine` remain independent sibling modules.

### Normalized observations

Source-specific records are converted into immutable normalized events before persistence. A normalized event contains only information required by the product, including:

- package name;
- stable event type;
- wall-clock timestamp;
- source identifier;
- collection or reliability metadata required for diagnostics.

The normalizer does not collect visible text, accessibility node content, viewed pages, messages, media or other screen content. Display names and icons remain Android-resolved presentation data rather than event identity.

Source ordering and duplication are handled before an event affects persisted aggregates. Because Android does not expose a universal event identifier, deduplication uses a deterministic canonical identity derived from the available source fields and must be validated against real-device traces. The implementation must not claim perfect event uniqueness when the platform does not provide it.

### Durable event journal and projections

Normalized usage events form the durable observation journal. Daily per-application totals are persisted projections that can be rebuilt from that journal.

Persistence also records:

- the last successfully reconciled range or checkpoint per source;
- explicit collection gaps and their reason;
- collection health and last successful reconciliation;
- enough metadata to replay overlapping query windows without double-counting known observations.

In-memory state is a cache only. Daily totals, checkpoints and health must survive process recreation and device restart.

### Reconciliation and recovery

Collection is incremental and idempotent.

- Application start and relevant foreground resumes request reconciliation.
- WorkManager owns persistent deferrable reconciliation that must survive process and device restarts.
- Work is enqueued uniquely so repeated lifecycle signals do not create uncontrolled parallel collection.
- Historical queries replay a bounded overlap before the persisted checkpoint to avoid missing events that share a boundary or arrive late.
- Replayed observations are normalized and deduplicated before projection updates.
- When usage history is unavailable because the user is locked, permission is missing or Android returns incomplete data, the period is classified explicitly and retried when appropriate.
- No duration is fabricated for a period that cannot be observed.

A boot receiver may be introduced only when a concrete restoration trigger is still needed beyond WorkManager rescheduling. It must perform no heavy collection itself and may only enqueue or refresh durable work.

### Execution policy

Increment 1 does not introduce a permanently running foreground service or aggressive polling loop. WorkManager is used for reliable deferrable reconciliation, not as a real-time guarantee. Accessibility remains the experiment for faster foreground-transition signals.

A foreground service can be reconsidered only when an implemented feature demonstrates a continuous user-visible requirement that cannot be met within normal background-execution limits. Such adoption requires a separate architecture review covering service type, notification, battery cost and target-SDK restrictions.

### Permission and capability health

Usage access is required for historical collection and is verified from the actual system state whenever the application resumes or before a query. Opening the system settings screen is never treated as proof that access was granted.

Accessibility is reported separately as optional, disabled, enabled, disconnected or unsupported. Missing accessibility access must not prevent UsageStats reconciliation.

Permission loss, unavailable history, user-locked state, source disconnection and persistence failure remain distinct health conditions.

### Package visibility

The initial monitored-application picker uses a versioned catalog of supported package names and targeted `<queries>` declarations where Android package metadata must be resolved. The application does not request `QUERY_ALL_PACKAGES` for increment 1.

Package names remain the stable identity. Labels and icons are resolved from Android when visible and available; failure to resolve presentation metadata does not change stored usage identity.

### Privacy

All observation events, projections, checkpoints and diagnostics remain on the device. No analytics SDK, remote telemetry or network synchronization is introduced by this increment.

## Alternatives considered

### Accessibility as the primary and only source

This would improve transition latency but would make observation dependent on a sensitive optional service, provide weak historical reconstruction after downtime and create a larger distribution-policy risk. Accessibility remains optional and replaceable.

### Persist daily aggregates only

This would reduce storage and schema complexity, but late events, deduplication fixes and changed aggregation rules could not be replayed safely. A durable normalized journal provides the auditability and reconstruction required by later restrictions.

### Continuous polling or a permanent foreground service

This could appear simpler than reconciliation, but it would increase battery cost, require a persistent notification and still would not guarantee uninterrupted execution across Android versions and manufacturers. Increment 1 uses bounded queries and persistent deferrable work instead.

### Direct dependencies between monitoring and data

Allowing `monitoring` to write directly to Room would reduce initial wiring but violate the accepted module graph, couple Android acquisition to persistence and make source behavior harder to test. Coordination remains in `app` through `domain` contracts.

### Request `QUERY_ALL_PACKAGES`

This would simplify listing installed applications but expands access to sensitive package inventory and creates unnecessary store-policy exposure. A targeted catalog is sufficient for the initial product scope.

## Consequences

### Positive

- Usage history can be reconstructed after normal process interruption;
- optional accessibility monitoring can be evaluated without becoming a mandatory dependency;
- collection reliability is visible and classifiable;
- daily projections can be rebuilt when aggregation logic changes;
- module boundaries remain unchanged;
- missing data is represented honestly;
- no permanent background service or broad package visibility permission is introduced prematurely.

### Negative

- storing normalized observations requires more schema and migration work than aggregate-only storage;
- deduplication is necessarily heuristic because UsageStats exposes no universal stable event identifier;
- WorkManager execution time is controlled by Android and cannot provide real-time guarantees;
- real-device validation is required across Android versions and manufacturer behavior;
- the supported package catalog must be maintained as monitored applications evolve.

## Revisit triggers

This decision must be reconsidered when any of the following occurs:

- real-device testing shows that UsageStats history cannot support acceptable daily reconstruction;
- the accessibility experiment demonstrates a materially different source model that cannot fit the shared contract;
- a blocking feature requires continuous user-visible execution;
- storage growth from the normalized journal becomes significant in sustained use;
- Google Play or Android platform policy changes affect accessibility or package visibility;
- a new official Android API provides a more reliable supported foreground-application signal.

## Validation

The decision is validated when:

- UsageStats observations are collected for configured packages on the reference device;
- repeated overlapping reconciliation does not double-count known observations;
- process recreation and device reboot preserve or reconstruct daily totals;
- locked, missing-permission and incomplete-history periods are shown as degraded or missing data;
- accessibility can be disabled without breaking historical collection;
- accessibility collection reads no window content;
- `data`, `monitoring` and `engine` retain their accepted dependency boundaries;
- no observation data leaves the device.

## References

- Android `UsageStatsManager`: https://developer.android.com/reference/android/app/usage/UsageStatsManager
- Android `UsageEventsQuery`: https://developer.android.com/reference/android/app/usage/UsageEventsQuery
- Android accessibility services: https://developer.android.com/guide/topics/ui/accessibility/service
- Android persistent work: https://developer.android.com/develop/background-work/background-tasks/persistent
- Android broadcast exceptions: https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions
- Android package visibility: https://developer.android.com/training/package-visibility
