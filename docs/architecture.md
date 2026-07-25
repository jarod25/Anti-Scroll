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

This is an Android library module and depends only on `domain`. It owns the Room database schema, DAO implementations, schema snapshots and persistence-specific Hilt bindings. Room entities and DAOs do not cross the module boundary as domain-facing contracts.

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

## Dependency injection

Hilt is the Android dependency-injection framework. KSP performs Hilt code generation.

The injection boundary follows these rules:

- `app` owns the process-level Hilt application component and remains the composition root;
- `domain` and `engine` never import Hilt, Dagger or Android injection annotations;
- constructor injection is preferred for project-owned implementations;
- interface bindings use `@Binds` when possible;
- `@Provides` is reserved for framework or third-party objects that cannot use constructor injection;
- scopes are added only when lifecycle and ownership are explicit;
- `data` and `monitoring` add Hilt dependencies only when they own real injectable implementations or binding modules;
- artificial bindings are not created merely to demonstrate that Hilt compiles.

The application component is initialized by `AntiScrollApplication`. Android framework classes become entry points only when they require injected dependencies. `AntiScrollDatabase` is a process-wide singleton provided from `data` because Room owns its construction lifecycle.

## Observation pipeline

Increment 1 separates Android acquisition, normalization, persistence and projection:

```text
UsageStats source ───────┐
Accessibility source ────┼─> UsageObservationNormalizer -> UsageEventRepository
System signal source ────┘                                  |
                                                             -> DailyUsageProjection
                                                             -> MonitoringHealth
```

UsageStats is the required historical and recovery source. Accessibility remains an optional low-latency experiment behind a domain-facing source contract. System signals report conditions that affect recovery or interpretation, such as boot, user unlock, time-zone changes and package changes.

Source implementations live in `monitoring`. Repository implementations and Room entities live in `data`. The `app` module composes them through contracts and immutable models owned by `domain`; sibling implementation modules never call one another directly.

Normalized usage events are the durable observation journal. Daily usage values are persisted projections that can be rebuilt from the journal. Collection checkpoints and explicit gaps make delayed or missing data visible instead of allowing the application to invent durations.

Reconciliation is incremental and idempotent. Application lifecycle events and persistent deferrable work request bounded historical queries with an overlap before the last checkpoint. Normalization and deterministic deduplication happen before projections are updated. WorkManager supports recovery but is not treated as a real-time execution guarantee.

The detailed source, persistence and recovery decision is recorded in ADR-007.

## Main restriction components

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
- normalized usage observations and daily usage projections;
- collection checkpoints, gaps and monitoring health;
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

The Room schema is versioned from the first table. Generated schema snapshots are committed under `data/schemas` and verified by CI. Every schema version change requires an explicit migration or reviewed auto-migration, plus instrumentation tests that validate both schema and transformed data. Production database construction never enables destructive migration fallback.

The initial `monitored_applications` table uses Android package names as stable identifiers. Display labels and icons are resolved from Android rather than treated as persisted identity data.

Observation persistence stores normalized usage events as the durable journal, daily per-application usage as rebuildable projections, and collection checkpoints or gaps as explicit diagnostic state. Event insertion, checkpoint advancement and affected projection updates must be transactional when they form one logical reconciliation step.

Repositories expose domain types and flows. UI code does not query DAOs directly. Persistence entities remain separate from domain models even when their fields initially look similar.

## Error handling

Failures must be explicit and classifiable. Permission loss, incomplete monitoring, persistence failure and unsupported platform behavior are distinct states, not generic exceptions hidden from the domain.

## Change policy

A major change to module boundaries, state ownership, restriction evaluation or Android monitoring requires an Architecture Decision Record describing benefits, drawbacks, risks and migration impact.
