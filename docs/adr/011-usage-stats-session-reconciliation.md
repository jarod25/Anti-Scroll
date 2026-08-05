# ADR-011: Reconcile shared-session exits with bounded UsageStats queries

- Status: Accepted
- Date: 2026-08-05
- Decision owners: Project maintainers

## Context

ADR-008 deliberately limits the accessibility service to package-level foreground events from enabled monitored applications. ADR-010 connects those events to the shared-session runtime but does not infer a background transition from silence.

That privacy boundary creates an explicit gap: when the user leaves a monitored application for the launcher or an unmonitored application, the filtered accessibility service normally receives no event from the destination package. The runtime therefore cannot prove that the monitored application left the foreground.

Android UsageStats already provides activity resumed and paused history for applications when Usage Access is granted. The API is historical rather than a guaranteed immediate callback stream, so it cannot replace accessibility as the low-latency entry signal. It can, however, provide explicit correction events without retrieving window content or observing every foreground package through accessibility.

## Decision

Add a second `SharedSessionEventSource` that performs bounded UsageStats reconciliation when, and only when, the active restriction profile contains a shared-session policy.

The source:

- remains dormant under the production `observation` profile;
- queries only enabled monitored package names;
- derives its first lookback window from the configured maximum session duration, inactivity timeout, technical overlap and settlement delay;
- repeats queries using a technical two-second polling interval and five-second overlap;
- delays processing the newest second of UsageStats events so closely spaced Activity transitions can settle before package-level interpretation;
- does not advance its successful cursor when UsageStats is unavailable, the device is locked or Usage Access is missing;
- deduplicates events across overlapping windows;
- clusters nearby activity transitions per package before evaluating foreground state;
- tracks resumed activity classes per package so pausing one activity does not falsely background a package that still has another resumed activity;
- accepts transient accessibility foreground hints so a later pause can correct a session even when the matching resume occurred before the initial UsageStats window;
- emits only explicit `ApplicationForegrounded` and `ApplicationBackgrounded` domain events;
- converts wall-clock UsageStats timestamps to elapsed realtime from a same-query clock snapshot;
- rejects future events and events that would map before the current device boot.

The polling interval, overlap and settlement delay are Android integration parameters, not restriction business values. They are provided by application composition and can be adjusted after reference-device battery and latency measurements without changing the restriction engine.

## Alternatives considered

### Remove the accessibility package filter

Listening to all foreground package names would make exits more immediate, but it would broaden a sensitive permission beyond the documented purpose, expose unrelated application usage to the service and increase privacy and distribution risk. It is rejected for the current architecture.

### Infer a background transition after accessibility silence

Silence does not prove that the monitored application left the foreground. Manufacturer event batching or an application with a stable window could produce the same absence of events. It remains rejected.

### Poll UsageStats continuously regardless of profile

The production application is still in observation mode and does not need low-latency restriction reconciliation. Continuous polling would consume resources without providing user-visible enforcement. It is rejected.

### Treat every paused activity as a package exit

Applications can replace or overlay activities while remaining foreground. A package-level session must remain active until its last known resumed activity pauses. Naive event mapping is rejected.

### Process the newest UsageStats event immediately

A pause and the following resume for an internal Activity replacement can be published a few milliseconds apart. Processing the pause before the resume appears would create a false package exit. The source therefore retains a short settlement delay before interpreting recent history.

## Consequences

### Positive

- monitored application exits can be represented explicitly without all-package accessibility visibility;
- the existing Usage Access permission is reused;
- activity replacement inside one package does not create an immediate false session pause;
- closely spaced Activity transitions are interpreted together after a bounded delay;
- overlapping queries tolerate delayed UsageStats publication without duplicate runtime events;
- process startup can reconstruct recent monitored activity from a policy-derived window;
- the pure reducer and restriction engine remain unchanged;
- the source automatically stops when the profile or monitored package set no longer requires it.

### Negative

- background correction can arrive after the one-second settlement delay, polling interval and UsageStats publication delay;
- clustering nearby transitions is a bounded platform heuristic that requires reference-device validation;
- a wall-clock change during the short reconciliation window can make a record unmappable and therefore ignored;
- the source remains process-local and does not persist its deduplication or activity tracker state;
- source-unavailable states are retried but are not yet exposed in the runtime snapshot;
- the current observation profile means this integration is assembled but dormant in production.

## Migration and rollback

No database migration is introduced. The source is registered through the existing event-source multibinding and can be removed without changing persisted observation data or pure engine contracts.

## Validation

- unit tests for accessibility-hint background correction;
- unit tests for overlapping-window deduplication;
- unit tests for multi-activity package aggregation;
- unit tests for settlement-window Activity replacement;
- unit tests proving unsettled recent events are replayed later;
- unit tests for future and pre-boot timestamp rejection;
- flow tests proving observation mode performs no UsageStats queries;
- flow tests proving an active profile maps UsageStats history to explicit session events;
- Android CI for formatting, compilation, Hilt graph generation, JVM tests, lint and APK assembly;
- later physical-device latency and battery validation when a non-observation profile is activated.
