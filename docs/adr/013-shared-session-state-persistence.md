# ADR-013: Persist and restore shared-session runtime state

- Status: Accepted
- Date: 2026-08-15
- Decision owners: Project maintainers

## Context

ADR-010 introduced a process-local shared-session runtime, ADR-011 added bounded UsageStats reconciliation and ADR-012 added replaceable monotonic session deadlines. This runtime is reliable while the process remains alive, but its session state and scheduled deadline are lost after a real process death. A device reboot also resets `SystemClock.elapsedRealtime()`, so persisted monotonic timestamps cannot be reused across boots.

Enforcement must not be built on state that exists only in memory. At the same time, persisting complete runtime snapshots or restriction decisions would make transient evaluation results the durable source of truth and would couple persistence to implementation details.

## Decision

Persist only the active shared-session facts required to rebuild runtime state.

The domain exposes `SharedSessionCheckpoint` and `SharedSessionStateRepository`. The data module stores a single checkpoint in Room and migrates the database from schema version 3 to 4. The checkpoint records:

- restriction profile identifier and version;
- device boot identifier;
- session start wall-clock and monotonic anchors;
- accumulated monitored foreground duration;
- current foreground package or inactivity anchor;
- last observed wall-clock and monotonic timestamps.

`SharedSessionRuntime` restores the checkpoint before subscribing to live session event sources. It then recalculates the current restriction decision and one-shot deadline from the restored state rather than persisting either result.

The Android integration reads `Settings.Global.BOOT_COUNT` through a small `DeviceBootIdentifierProvider`. `SystemClock.elapsedRealtime()` remains the duration clock.

For restoration within the same boot, the original monotonic anchors are retained. This preserves elapsed foreground duration across process death without periodic database writes.

For restoration after a reboot, monotonic anchors are re-created on the current boot. A session that was already paused keeps its accumulated foreground duration. A session that was still foreground at the last checkpoint is restored as paused with at least the configured maximum session duration consumed. This conservative rule prevents a reboot from granting a fresh session allowance when the exact shutdown interval cannot be reconstructed safely.

A checkpoint whose profile identifier or version does not match the active profile is discarded rather than silently reinterpreted.

The production profile remains `observation`, so this change does not activate user-visible restrictions.

## Alternatives considered

### Persist the complete runtime snapshot

`SharedSessionRuntimeSnapshot` contains the latest transition and restriction decision in addition to durable session facts. Those values are derived and may become stale after time, profile or rule changes. Persisting the whole snapshot is rejected.

### Persist the one-shot deadline

The deadline is an elapsed-realtime implementation detail derived from session state and policy. It is invalid across a reboot and can be recalculated cheaply. Persisting it is rejected.

### Use wall-clock time for duration continuation

Wall-clock time can change independently of actual elapsed device time. Using it for session duration would allow manual clock changes to affect restriction state. It is rejected for duration arithmetic.

### Use DataStore for runtime state

The project already uses Room as the durable structured-data store, with explicit schemas and migration tests. Shared-session state is structured and expected to evolve alongside cooldown and quota persistence. Adding another persistence technology provides no current benefit and is rejected.

### Introduce cooldown persistence before cooldown exists

No cooldown state exists in the current engine. Adding speculative persistence for it would create unused abstractions and unclear invariants. Cooldown persistence will be introduced together with the actual cooldown state transition.

## Consequences

### Positive

- process death no longer resets an active shared session;
- same-boot restoration keeps exact monotonic duration accounting;
- reboot cannot provide a fresh session allowance for a session that was previously foreground;
- restriction decisions and deadlines remain derived values;
- the accessibility service and foreground lifetime service remain free of business state;
- Room remains the single durable store for structured runtime data;
- the production observation profile still applies no restriction.

### Negative

- a rebooted foreground session is restored conservatively because its exact shutdown interval is unknowable from the in-memory runtime alone;
- paused-session inactivity is re-anchored after reboot and may therefore remain in the same session longer than it would without a reboot;
- an asynchronous application bootstrap is now required before live session event collection begins;
- cooldown, quota and scheduled-block persistence remain future work because those states do not exist yet.

## Migration and rollback

Database schema version 4 adds the singleton `shared_session_state` table. Migration `3 -> 4` creates the table without modifying existing observation data. Rolling back to a build expecting schema version 3 is not supported after migration unless the application data is cleared or a dedicated reverse migration is implemented.

## Validation

- pure unit tests for same-boot restoration;
- pure unit tests for conservative cross-reboot restoration;
- pure unit tests for profile-version mismatch rejection;
- runtime tests for persisted-state bootstrap and deadline rearming;
- runtime tests proving profile changes clear persisted session state;
- Room migration test proving `3 -> 4` preserves existing observation data;
- Android CI for formatting, JVM tests, lint and APK/device-test assembly;
- physical-device validation for process recreation and reboot before enforcement is enabled.
