# ADR-005: Room persistence foundation

- Status: Accepted
- Date: 2026-07-24
- Decision owners: Project maintainers

## Context

Anti-Scroll stores all user data locally in its first version. Restriction state, monitored applications, usage history, cooldowns and quota consumption must remain reconstructable after process death and device restart.

The persistence layer therefore needs a versioned relational schema, compile-time query validation and a migration policy before product data begins to accumulate. The project already isolates persistence in the Android `data` module and uses Hilt with KSP.

## Decision

The project uses Room as the structured local database in the `data` module.

The initial baseline is:

- Room 2.8.4;
- KSP for Room code generation;
- the Room Gradle plugin with schema export to `data/schemas`;
- schema snapshots committed to version control;
- one process-wide `AntiScrollDatabase` instance provided by Hilt in `SingletonComponent`;
- database version 1 containing the `monitored_applications` table;
- DAO instrumentation tests using an in-memory database;
- migration-test infrastructure based on `MigrationTestHelper`.

The following persistence rules apply:

- destructive migration fallback is forbidden for production data;
- every schema version change requires an explicit migration or a reviewed Room auto-migration;
- every migration must validate the resulting schema and any transformed data;
- CI must fail when generated Room schema snapshots differ from the committed files;
- Room entities and DAOs remain implementation details of `data`;
- repository implementations map persistence types to domain types before exposing them outside `data`;
- UI code never queries DAOs directly;
- database timestamps are stored as explicit numeric values with their semantic meaning documented by the owning model;
- DataStore may be introduced only for small preference-like values that do not duplicate Room-owned state.

The first table stores monitored application configuration by Android package name. Package names are stable identifiers; localized application labels and icons are resolved from Android when needed rather than persisted as authoritative identity data.

## Alternatives considered

### DataStore for all local state

DataStore is appropriate for small preferences but is not designed for relational history, indexed queries, migrations across multiple tables or transactional restriction state. Using it as the primary store would make later usage analytics and state reconstruction harder.

### Raw SQLite APIs

Raw SQLite would provide full control but would require manual schema validation, mapping and query safety. Room supplies compile-time query verification and standardized migration testing without preventing direct SQL where a migration requires it.

### Delay persistence until usage monitoring is implemented

Deferring the database would reduce immediate setup work, but the first observation increment would then mix Android monitoring, data modeling, migrations and persistence wiring in one larger change. Establishing the schema policy now reduces that risk.

### Destructive migrations during development

Destructive fallback simplifies early schema changes but can hide missing migration paths. Anti-Scroll depends on durable behavior history and restriction state, so silent data loss is unacceptable even during early internal use.

## Consequences

### Positive

- SQL queries and entity mappings are validated at build time;
- schema history becomes reviewable in Git;
- migration tests can be added from the first schema change;
- the database lifecycle is explicit and process-scoped;
- persistence remains isolated from the pure domain and engine modules;
- the first observation increment can focus on repository behavior and monitoring rather than database bootstrap.

### Negative

- Room and KSP add code generation to the `data` module;
- every schema change requires a committed schema snapshot and migration review;
- instrumentation tests are required for DAO and migration behavior;
- the initial schema creates a compatibility commitment that must evolve deliberately.

## Migration and rollback

Before user data exists, the Room setup can be removed by deleting the database classes, Hilt module, schema snapshots and dependencies.

After a released build persists data, rollback must preserve a valid migration path. Database version numbers must never be reused, and a previously published schema must not be rewritten in place.

## Validation

The decision is validated when:

- Room code generation succeeds locally and in CI;
- version 1 schema is exported and committed;
- `AntiScrollDatabase` is created through Hilt without destructive fallback;
- DAO instrumentation tests verify insert, update, observation and deletion behavior;
- `MigrationTestHelper` can create the exported version 1 schema;
- CI detects missing or stale schema snapshots;
- `domain` and `engine` remain free of Room and Android persistence types.
