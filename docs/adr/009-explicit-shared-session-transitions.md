# ADR-009: Model shared sessions with explicit transitions

- Status: Accepted
- Date: 2026-08-05
- Decision owners: Project maintainers

## Context

Anti-Scroll must enforce one continuous scrolling session across every monitored application. Switching from TikTok to Instagram must not reset the session, while time spent in an unmonitored application must not increase scrolling duration. A sufficiently long interruption must start a new session.

The accessibility integration introduced in ADR-008 emits low-latency foreground signals for selected monitored packages only. The absence of such a signal does not prove that the current monitored application left the foreground. Inferring a background transition from silence would therefore overcount usage and make restriction timing device-dependent.

Accessibility window-state events can also repeat for internal application transitions, arrive out of order or report a new monitored application without a matching background event for the previous one.

## Decision

Represent shared-session evolution as a pure reducer over an immutable state and explicit domain events:

- `ApplicationForegrounded` starts, resumes or switches the shared session;
- `ApplicationBackgrounded` pauses foreground-duration accumulation;
- `EndRequested` closes the session for an explicit stable reason.

The Android integration layer is responsible for producing these events from the best available platform evidence. The reducer never interprets missing events or elapsed silence as a background transition.

The active state stores:

- wall-clock instants for audit and future persistence;
- elapsed realtime values for in-process duration calculation;
- accumulated monitored foreground duration;
- the current foreground package and segment start;
- the start of an explicit inactive period when paused.

Repeated foreground signals for the same package are idempotent and do not restart the current segment. A direct transition to another monitored package closes the previous segment and continues the same shared session. A paused session resumes when inactivity is shorter than the configured timeout and restarts when inactivity is greater than or equal to that timeout.

Session mutation remains separate from restriction evaluation. `SessionLimitRule` reads the immutable state and profile, then returns an allow, block or not-applicable result. Reaching the exact configured duration blocks the target application. Starting a cooldown or persisting state belongs to later explicit transitions and is not performed by the rule.

## Alternatives considered

### Infer background from missing accessibility signals

This would require a timer or polling assumption and could count unmonitored time as scrolling when Android simply emitted no useful event. It is rejected because the source contract cannot support that guarantee.

### Use wall-clock time for active durations

Wall-clock changes could shorten or extend an active session. Elapsed realtime provides monotonic in-process duration measurement and is therefore used while the device remains booted.

### Let rules mutate session state

This would hide side effects inside evaluation, weaken determinism and conflict with ADR-001. Explicit reducer transitions keep state changes reconstructable and independently testable.

### Maintain one session per application

This would allow switching applications to reset the limit and directly violate the product requirement for a global shared session.

## Consequences

### Positive

- switching monitored applications cannot bypass the session limit;
- unmonitored time is excluded from foreground duration;
- duplicate and stale signals are handled deterministically;
- all durations and inactivity thresholds come from a versioned profile;
- the reducer and rule engine remain pure Kotlin without Android, Room or hidden clock reads;
- future persistence can store explicit transitions and restore state without replaying undocumented behavior.

### Negative

- Android integration must obtain or derive a trustworthy explicit background transition before live enforcement is complete;
- elapsed realtime resets after reboot, so persisted restoration requires an additional durable time strategy;
- a direct package switch is treated as continuous even when the platform omitted an intermediate unmonitored application event;
- the first engine increment does not yet block applications or start cooldowns.

## Migration and rollback

No persistence schema exists for restriction state yet, so no migration is required. Replacing the explicit-transition model would affect core domain contracts and require a superseding ADR.

## Validation

- unit tests for application switches, duplicate signals and stale events;
- unit tests proving that paused time is excluded;
- boundary tests for the configured inactivity timeout;
- boundary tests for the configured session limit;
- deterministic rule-priority tests;
- later Android integration tests proving that real platform events produce the required explicit transitions.
