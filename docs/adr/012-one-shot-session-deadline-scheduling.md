# ADR-012: Schedule shared-session limits with replaceable one-shot deadlines

- Status: Accepted
- Date: 2026-08-05
- Decision owners: Project maintainers

## Context

ADR-010 connects foreground events to the shared-session runtime, and ADR-011 adds explicit UsageStats background corrections. The runtime can therefore maintain monitored foreground duration while platform events continue to arrive.

A session limit still cannot be enforced reliably if the user remains inside one stable monitored activity. Android may emit no additional accessibility or UsageStats event between session entry and the configured maximum duration. Evaluating only when another platform event arrives can therefore leave an already-expired session temporarily allowed.

A fixed heartbeat could reevaluate the engine every second, but it would wake the process continuously, spend battery while no deadline is relevant and mix technical polling frequency with business rules.

## Decision

Add an exact, replaceable, process-local deadline for the next shared-session limit evaluation.

The implementation is split into two responsibilities:

- `SharedSessionDeadlinePlanner` in `:engine` calculates the monotonic elapsed-realtime deadline from immutable shared-session state and the active profile policy;
- `SharedSessionDeadlineScheduler` in `:app` schedules one coroutine callback after a supplied non-negative delay.

The runtime:

- schedules a deadline only while a shared session has a foreground monitored application;
- includes previously accumulated foreground duration when calculating the remaining time;
- keeps at most one scheduled deadline;
- cancels and replaces the deadline after relevant profile or session changes;
- uses elapsed realtime rather than wall-clock time for duration arithmetic;
- validates each callback with a generation number so an obsolete callback cannot overwrite newer state;
- reevaluates the restriction engine at the callback time without creating a synthetic Android event;
- leaves session state and the latest transition unchanged during deadline-only evaluation;
- rearms a new one-shot deadline if the callback fires early and the decision is still allowed;
- stops scheduling after a blocking decision;
- remains dormant while the production profile has no shared-session policy.

The deadline scheduler is a runtime mechanism, not a restriction rule. Session duration remains defined exclusively by the active versioned profile.

## Alternatives considered

### Fixed one-second heartbeat

A heartbeat is straightforward but wakes continuously even when no active session exists. It also creates unnecessary battery cost and couples precision to an arbitrary polling interval. It is rejected.

### Depend on repeated accessibility events

A stable application screen may not generate another event before the session limit. Platform event frequency is not a clock and cannot guarantee timely reevaluation. It is rejected.

### End the session directly when the deadline fires

The restriction engine produces a decision; it must not silently mutate session state or create a cooldown. Deadline evaluation therefore updates only the latest decision. Explicit transitions remain the responsibility of later orchestration. Direct mutation is rejected.

### Use WorkManager or AlarmManager for every active session deadline

WorkManager is deferrable and cannot provide minute-level exactness. AlarmManager introduces broader system scheduling complexity and exact-alarm policy concerns that are unnecessary while the runtime remains process-local. Both are rejected for the current increment.

## Consequences

### Positive

- a stable monitored activity reaches the configured session limit without requiring another Android event;
- no permanent heartbeat or foreground service is introduced;
- application switching preserves one shared deadline rather than resetting per package;
- pauses and profile changes cancel unnecessary work immediately;
- deadline calculation remains pure and unit-testable;
- scheduler timing and concurrency can be tested deterministically through a fake implementation;
- obsolete callbacks are harmless even if cancellation races with execution.

### Negative

- the scheduler is process-local and is lost if Android kills the process;
- coroutine delay timing is subject to normal process scheduling latency;
- a device reboot resets elapsed realtime and requires durable restoration before exact continuation is possible;
- the deadline decision is not yet enforced through a blocking interface;
- no cooldown is created when the session limit is reached.

## Migration and rollback

No database migration is introduced. Removing the planner, scheduler binding and runtime integration restores event-only evaluation without changing persisted observation data or domain event contracts.

## Validation

- unit tests for inactive and paused states producing no deadline;
- unit tests for accumulated foreground duration and already-reached limits;
- runtime tests for exact deadline blocking without another platform event;
- runtime tests for early callback rearming;
- runtime tests for cross-application remaining duration;
- runtime tests for pause and profile-change cancellation;
- runtime tests proving obsolete callbacks cannot alter newer state;
- Android CI for formatting, compilation, Hilt graph generation, JVM tests, lint and APK assembly.
