# Software architecture

## Goals

The architecture must prioritize reliability, maintainability and testability. The restriction domain must be independently testable without an Android device.

## Dependency direction

Dependencies point toward the domain:

```text
Android framework / UI / persistence
              ↓
      application use cases
              ↓
       pure domain model
```

The domain must not import Android classes, Room annotations, Compose types or implementation-specific framework APIs.

## Initial Gradle modules

The initial module set is deliberately small:

```text
app
domain
engine
data
monitoring
```

A generic `core` module is intentionally deferred. Shared modules are created only after a concrete responsibility and dependency boundary exists. When required, narrowly scoped modules such as `core:time` or `core:testing` are preferred over a miscellaneous utility container.

### `app`

Android application composition, dependency injection setup, navigation, Compose screens and process entry points.

Dependencies:

```text
domain
engine
data
monitoring
```

### `domain`

Immutable business models, repository contracts, clocks, restriction rule contracts and pure use cases.

This is a pure Kotlin/JVM module and has no project dependencies.

### `engine`

Restriction orchestration, session state transitions, quota evaluation and deterministic rule execution.

This is a pure Kotlin/JVM module and depends only on `domain`.

### `data`

Room database, DataStore-backed settings where appropriate, repository implementations, entity/domain mapping and migrations.

This is an Android library module and depends only on `domain`.

### `monitoring`

Android-specific observation: UsageStats integration, accessibility events, boot/time-change signals and conversion to normalized domain events.

This is an Android library module and depends only on `domain`.

Additional modules are created only when they provide a real dependency, ownership or compilation boundary.

## Module dependency graph

```text
                  app
         ┌─────────┼─────────┐
         ↓         ↓         ↓
    monitoring    data     engine
         │         │         │
         └─────────┴─────────┘
                   ↓
                 domain
```

The initial allowed project dependencies are:

```text
app        → domain, engine, data, monitoring
domain     → none
engine     → domain
data       → domain
monitoring → domain
```

`data`, `engine` and `monitoring` must not depend on one another. Coordination happens in `app` through contracts and models defined by `domain`.

## Main components

```text
SystemUsageMonitor
        ↓
UsageEventNormalizer
        ↓
UsageEventRepository
        ↓
SessionStateReducer
        ↓
RestrictionEngine
        ↓
RestrictionDecision
        ↓
AndroidBlockCoordinator
```

Historical aggregation and dashboard queries consume persisted domain events and state but do not own restriction decisions.

## State model

Critical runtime state must be reconstructable from durable data. In-memory state is a cache, not the sole source of truth.

Critical state includes:

- active profile and profile version;
- current or most recent session;
- active cooldown and expiry information;
- daily quota consumption;
- monitored application configuration;
- permission and monitoring health;
- block decisions and opening attempts.

## Time handling

Duration measurement uses an injected monotonic time source whenever possible. Calendar boundaries and user-facing timestamps use an injected wall clock and time zone.

Domain code must never directly call system clocks. Tests use controlled clocks.

## Naming rules

- `Rule`: evaluates a condition and returns a decision contribution.
- `Engine`: orchestrates rules.
- `Repository`: reads or persists domain data.
- `Monitor`: observes an external system.
- `Service`: implements a technical capability.
- `Manager`: reserved for components that genuinely manage a complex lifecycle or mutable state.

Generic `Utils`, `Helper` and vague `Manager` classes are avoided.

## Data access

Room stores structured history and restriction state. DataStore may hold small preferences that do not duplicate Room-owned data.

Repositories expose domain types and flows. UI code does not query DAOs directly.

## Error handling

Failures must be explicit and classifiable. Permission loss, incomplete monitoring, persistence failure and unsupported platform behavior are distinct states, not generic exceptions hidden from the domain.

## Change policy

A major change to module boundaries, state ownership, restriction evaluation or Android monitoring requires an Architecture Decision Record describing benefits, drawbacks, risks and migration impact.
