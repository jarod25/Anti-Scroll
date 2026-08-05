# Restriction engine

## Purpose

`RestrictionEngine` is the single domain entry point for deciding whether a monitored application may be used at a given moment.

It does not embed the detailed logic of every restriction. It evaluates an ordered collection of independent `RestrictionRule` implementations against an immutable context.

## Evaluation context

The context should contain only the information required for a deterministic decision:

- target package;
- evaluation instant;
- active profile and profile version;
- current session state;
- active cooldown state;
- global and per-application quota consumption;
- scheduled block configuration;
- monitoring and permission health;
- relevant persisted transitions.

The first implemented context contains the target package, wall-clock evaluation instant, elapsed realtime, active versioned profile and shared-session state. Later increments extend it only when another rule requires additional immutable data.

## Rule result

Each rule returns an explicit result containing at least:

- outcome: allow, block or not-applicable;
- stable reason code;
- priority;
- optional end instant;
- optional metadata for UI and local diagnostics.

Human-readable strings do not belong in the rule result. The UI maps stable reason codes to localized messages.

A blocking rule must provide a stable reason code. The engine selects the highest-priority blocking result as the primary reason and retains other active blocking results as secondary diagnostics. Equal priorities preserve rule registration order.

## Initial rules

- `PermissionHealthRule`
- `ObservationRule`
- `ScheduledBlockRule`
- `CooldownRule`
- `GlobalQuotaRule`
- `AppQuotaRule`
- `SessionLimitRule`

`SessionLimitRule` is the first implemented rule. It is not applicable when the active profile has no shared-session policy or when no session is active. It allows usage below the configured maximum and blocks with `session_limit_reached` when foreground duration is greater than or equal to the maximum.

The rule reports the measured session duration and configured limit as technical metadata. It does not mutate the session, start a cooldown, persist state or invoke Android behavior.

## Priority

Initial priority order:

1. technical state preventing reliable operation;
2. scheduled block;
3. active cooldown;
4. exhausted global quota;
5. exhausted application quota;
6. reached session limit;
7. observation or normal allowance.

A blocking result wins over an allowing result. The engine exposes the highest-priority reason and retains secondary active reasons for diagnostics.

Priorities are represented explicitly rather than inferred from list position. Rule registration order is used only to break ties between results with the same priority.

## Determinism

For the same context, profile and controlled clocks, evaluation must return the same result. Rules must not perform database access, Android API calls or hidden time reads during evaluation.

Data required by rules is collected before engine invocation. Rule and profile collections are copied at construction boundaries so later external mutation cannot alter an existing evaluation unexpectedly.

## Shared session state

The shared session covers every monitored application. It is not an observation session per package and does not reset when the user switches between monitored applications.

`SharedSessionReducer` consumes explicit events:

- `ApplicationForegrounded` starts, resumes or switches the shared session;
- `ApplicationBackgrounded` pauses foreground-duration accumulation;
- `EndRequested` closes the session with an explicit stable reason.

The reducer never infers inactivity from silence or missing accessibility events. Time spent outside monitored applications is excluded only after an explicit background transition.

The active state stores accumulated monitored foreground duration and the current foreground segment. A direct switch from one monitored package to another closes the previous segment and continues the same session. A repeated signal for the same package is idempotent and does not restart the segment.

When a session is paused, returning before the configured inactivity timeout resumes the same session without adding the gap. Returning at or after the timeout starts a new session. All thresholds are supplied by the active versioned profile; no duration is embedded in the reducer.

Elapsed realtime is used for active duration arithmetic because it is monotonic during one device boot. Wall-clock instants remain attached for audit, history and future persistence. Reboot restoration requires a separate durable strategy because elapsed realtime resets on boot.

The detailed decision is recorded in ADR-009.

## Process runtime

`SharedSessionRuntime` is the app-layer coordinator connecting monitoring adapters to the pure reducer and rule engine.

It consumes:

- a `RestrictionProfileSource` exposing the current versioned profile;
- one or more `SharedSessionEventSource` flows;
- `SharedSessionReducer` for explicit state transitions;
- `RestrictionEngine` for foreground-entry decisions;
- controlled wall-clock and elapsed-realtime readings for runtime-owned transitions.

Profile observations and events are serialized before state mutation. A foreground event is reduced first, then evaluated against the resulting immutable state. Stale foreground events remain unevaluated because their timestamp precedes the state already accepted by the reducer.

The runtime publishes `SharedSessionRuntimeSnapshot` values containing the active profile, current state, latest transition and latest decision. It does not enforce the decision, persist state or start a cooldown.

The production runtime initially uses an observation profile without a shared-session policy. This means monitoring can be wired and tested without silently activating an example restriction value. A later durable profile source will activate configured policies explicitly.

Changing the profile ends an active shared session with `PROFILE_CHANGED` before the new profile becomes current. Future background, reconciliation and restoration adapters can implement `SharedSessionEventSource` without obtaining direct access to mutable runtime state.

The detailed composition decision is recorded in ADR-010.

## State transitions

Evaluation and state mutation are separate concerns:

1. Build an immutable evaluation context.
2. Evaluate rules.
3. Produce a `RestrictionDecision`.
4. Apply explicit domain transitions through a use case or reducer.
5. Persist critical transitions atomically where possible.
6. Notify Android integration and UI layers.

This separation prevents a rule from silently changing state while being evaluated.

The shared-session foundation currently provides the reducer and explicit end reasons for session limit, inactivity, priority restriction, day boundary and profile change. Persistence, cooldown creation and Android enforcement are intentionally deferred to later increments.

## Extensibility

Adding a new restriction normally requires:

1. a new rule implementation;
2. tests for its behavior and priority interactions;
3. profile configuration support;
4. UI mapping for its reason code;
5. an ADR only when the common rule contract or engine behavior changes.

Existing rules should not require modification.

## Required tests

- each rule in isolation;
- all priority combinations;
- multiple simultaneous blocking reasons;
- expiry boundaries;
- shared-session application switches;
- duplicate, stale and irrelevant monitoring events;
- inactivity timeout boundaries;
- runtime event-source adaptation and serialization;
- runtime startup and profile transitions;
- midnight and scheduled periods crossing days;
- controlled time-zone changes;
- profile transitions;
- incomplete monitoring state;
- persisted restoration after process or device restart.
