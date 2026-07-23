# ADR-001: Use a rule-based restriction engine

- Status: Accepted
- Date: 2026-07-23
- Decision owners: Project maintainers

## Context

Anti-Scroll must support several independent restriction mechanisms: observation, scheduled blocks, cooldowns, global quotas, application quotas and session limits. Additional rules are expected later.

Embedding all conditions in one service would create a large mutable component, make rule priority difficult to test and require central modifications for every new restriction.

## Decision

Use a pure, deterministic `RestrictionEngine` that evaluates an ordered collection of independent `RestrictionRule` implementations against an immutable evaluation context.

Rules return explicit results containing an outcome, stable reason code, priority, optional end instant and diagnostic metadata.

Rules do not access Android APIs, databases or clocks directly. Required data is assembled before evaluation. Evaluation itself does not mutate state.

## Alternatives considered

### One central restriction manager

Simpler during the first few days of implementation, but likely to become a God Object. Rule interactions, testing and extension would become increasingly fragile.

### Event-driven rules that mutate their own state

Potentially flexible, but introduces hidden side effects and makes reconstruction, ordering and deterministic tests harder.

### External generic rules framework

Adds dependency and abstraction cost without a demonstrated need. The Anti-Scroll rule contract is small and domain-specific.

## Consequences

### Positive

- Rules can be tested independently.
- Priority behavior is explicit.
- New restrictions usually require a new implementation rather than central rewrites.
- The domain remains independent from Android.
- User-facing block reasons are stable and explainable.

### Negative

- An evaluation context must be assembled before every decision.
- Rule interactions require dedicated combination tests.
- Poorly designed priorities could still produce confusing results.
- The architecture is slightly more elaborate than a single conditional service.

## Migration and rollback

This decision is made before implementation, so no migration is required. Replacing it later would affect the central domain contract and require a new ADR.

## Validation

- Unit tests for every rule.
- Pairwise and multi-rule priority tests.
- Determinism tests using controlled clocks and fixed contexts.
- Prototype integration in the sessions-and-cooldowns increment.
