# ADR-010: Compose shared-session runtime from explicit event sources

- Status: Accepted
- Date: 2026-08-05
- Decision owners: Project maintainers

## Context

ADR-008 introduced a minimal accessibility service that emits low-latency package-level foreground signals for selected monitored applications. ADR-009 introduced explicit shared-session transitions and rejected inference from missing signals.

The restriction engine and reducer are pure Kotlin, but no application-lifetime component currently connects Android monitoring signals to those contracts. A direct implementation inside the accessibility service would mix Android lifecycle handling, mutable session state and restriction evaluation. It would also make future recovery or correction sources difficult to add.

Accessibility receives only the selected monitored packages. It cannot prove that a monitored application moved to the background merely because no further event arrived. UsageStats exposes resumed and paused activity history, but the current integration queries that history for reconciliation and does not provide a guaranteed immediate push stream.

The first runtime integration must therefore process trustworthy foreground signals without inventing background time, remain extensible to explicit correction sources and avoid activating restrictions before an active versioned profile exists.

## Decision

Introduce domain contracts for:

- the active restriction profile as a `StateFlow`;
- explicit shared-session events as `Flow` sources;
- the current runtime snapshot as a `StateFlow` containing the profile, session state, last transition and last restriction decision.

The app module owns a singleton `SharedSessionRuntime` started from `AntiScrollApplication.onCreate`.

The runtime:

- collects every registered `SharedSessionEventSource`;
- serializes profile changes and events through one mutex before changing state;
- applies `SharedSessionReducer` to explicit events only;
- evaluates `RestrictionEngine` after non-stale foreground transitions;
- exposes immutable snapshots through `SharedSessionRuntimeStateSource`;
- ends an active session with `PROFILE_CHANGED` when the active profile changes;
- remains process-local and performs no persistence or Android intervention.

`ForegroundSignalSharedSessionEventSource` adapts each accessibility `ForegroundApplicationSignal` to `ApplicationForegrounded` while preserving both wall-clock and elapsed-realtime timestamps.

The initial production profile is the versioned `observation` profile with no shared-session policy. Foreground signals are therefore ignored by the restriction runtime until a later profile source activates an explicit configured policy. Tests use controlled profiles rather than relying on production defaults.

Additional sources may later provide explicit background transitions, UsageStats reconciliation, process-restoration transitions, day boundaries or profile transitions. They must implement the same event-source contract and must not mutate runtime state directly.

## Alternatives considered

### Keep session state inside the accessibility service

This would couple domain state to Android callbacks, complicate testing and make process restoration or additional event sources dependent on one service implementation. It is rejected.

### Infer background transitions from missing accessibility signals

Silence does not prove that the monitored application left the foreground. A timer-based inference could overcount or undercount depending on manufacturer behavior and event delivery. It is rejected.

### Listen to every package through accessibility

Removing the package filter could reveal unmonitored foreground transitions, but it would broaden a sensitive permission beyond the current documented purpose and increase privacy and distribution risk. It is rejected.

### Poll UsageStats for real-time exits

UsageStats is retained as the durable history and recovery source. Aggressive polling would consume resources and still would not provide a guaranteed immediate event stream. It is rejected.

### Activate an example session duration in production

Example durations in the product specification are not implicit defaults. The runtime starts with an observation profile and waits for a later explicit versioned profile. It is rejected to embed arbitrary restriction values in this integration.

## Consequences

### Positive

- Android callbacks remain adapters rather than owners of business state;
- foreground signals reach the reducer and restriction engine through explicit contracts;
- event processing is serialized and deterministic at the runtime boundary;
- switching monitored applications continues one shared session once a policy is active;
- stale signals remain safely ignored by the reducer;
- profile changes close active sessions explicitly;
- future background and recovery sources can be added without rewriting the accessibility service;
- no sensitive accessibility scope is broadened.

### Negative

- the initial observation profile means production signals do not yet create an active restriction session;
- the runtime cannot exclude unmonitored foreground time until a trustworthy explicit background or correction source is added;
- runtime state is lost with the process because persistence is intentionally deferred;
- a restriction decision is observed but not yet enforced or converted into a cooldown;
- source failures are not yet represented in the runtime snapshot.

## Migration and rollback

No database schema changes are introduced. The runtime can be removed without migrating durable data. Replacing the event-source or profile-source contracts later would require adapting app composition and consumers but would not affect existing observation persistence.

## Validation

- unit tests for runtime startup idempotence;
- unit tests proving the observation profile does not activate restrictions;
- unit tests for profile activation, cross-application continuity and exact-limit decisions;
- unit tests for explicit background transitions and paused-time exclusion;
- unit tests for profile-change session termination;
- unit tests proving accessibility timestamps are preserved during event adaptation;
- instrumentation assembly verifying Hilt application bootstrap starts the runtime;
- later real-device validation when background correction and enforcement are implemented.
