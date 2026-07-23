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

## Rule result

Each rule returns an explicit result containing at least:

- outcome: allow, block or not-applicable;
- stable reason code;
- priority;
- optional end instant;
- optional metadata for UI and local diagnostics.

Human-readable strings do not belong in the rule result. The UI maps stable reason codes to localized messages.

## Initial rules

- `PermissionHealthRule`
- `ObservationRule`
- `ScheduledBlockRule`
- `CooldownRule`
- `GlobalQuotaRule`
- `AppQuotaRule`
- `SessionLimitRule`

## Priority

Initial priority order:

1. technical state preventing reliable operation;
2. scheduled block;
3. active cooldown;
4. exhausted global quota;
5. exhausted application quota;
6. reached session limit;
7. observation or normal allowance.

A blocking result wins over an allowing result. The engine exposes the highest-priority reason and may retain secondary active reasons for diagnostics.

## Determinism

For the same context, profile and controlled clocks, evaluation must return the same result. Rules must not perform database access, Android API calls or hidden time reads during evaluation.

Data required by rules is collected before engine invocation.

## State transitions

Evaluation and state mutation are separate concerns:

1. Build an immutable evaluation context.
2. Evaluate rules.
3. Produce a `RestrictionDecision`.
4. Apply explicit domain transitions through a use case or reducer.
5. Persist critical transitions atomically where possible.
6. Notify Android integration and UI layers.

This separation prevents a rule from silently changing state while being evaluated.

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
- midnight and scheduled periods crossing days;
- controlled time-zone changes;
- profile transitions;
- incomplete monitoring state;
- persisted restoration after process or device restart.
