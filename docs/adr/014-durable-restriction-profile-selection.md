# ADR-014: Persist the active restriction profile selection

## Status

Accepted

## Context

The shared-session runtime can now persist and restore active restriction state, but the active restriction profile was still process-local. A process restart therefore recreated the profile source with the observation profile even if a future restrictive profile had previously been selected.

That mismatch would make durable restriction state ineffective: the restored checkpoint is intentionally tied to its profile, so restarting with a different profile causes the checkpoint to be rejected.

## Decision

Persist the selected restriction profile identifier in Room as a singleton record.

The complete profile remains application configuration. Persistence stores only the identifier, while the application-level profile catalog resolves that identifier to the current profile definition.

The profile source must initialize from durable storage before shared-session restoration begins. The shared-session runtime remains in `STARTING` until profile initialization and session restoration are both complete.

Profile activation uses persistence-first ordering:

1. resolve the requested identifier through the profile catalog;
2. persist the identifier;
3. publish the resolved profile to the runtime.

If persistence fails, the in-memory profile does not change.

If a persisted identifier cannot be resolved by the current catalog, initialization fails instead of silently falling back to the observation profile. This deliberately avoids a fail-open downgrade path.

## Storage choice

Room remains the persistence mechanism.

The selection is small enough that DataStore would also be technically suitable, but introducing a second persistence technology for one value would add dependencies and operational paths without a clear benefit. Room already owns durable restriction state and has explicit schema migration coverage.

## Consequences

- process recreation no longer resets a selected profile to observation;
- a future restrictive profile can survive process death and reboot without contradicting shared-session restoration;
- profile policy values are still defined in application configuration, not in the database;
- profile identifiers become durable compatibility keys and should therefore remain stable;
- schema version 5 adds `restriction_profile_selection`;
- a fresh or migrated installation with no selection persists the catalog default on first profile-source initialization;
- unknown persisted identifiers leave the restriction runtime in startup rather than silently disabling restrictions.

## Validation

Validation must cover:

- default selection persistence on first initialization;
- restoration of a persisted selection;
- persistence-before-publication when activating a profile;
- rejection of unknown persisted identifiers;
- Room migration from schema 4 to 5 without loss of existing data;
- shared-session bootstrap continuing only after profile initialization.
