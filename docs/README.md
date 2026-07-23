# Anti-Scroll documentation

This directory contains the technical documentation used as the development reference for Anti-Scroll.

## Documents

- [`product.md`](product.md): product vision, scope and core behavioral requirements.
- [`architecture.md`](architecture.md): software architecture, module boundaries and dependency rules.
- [`restriction-engine.md`](restriction-engine.md): rule-based decision engine and restriction model.
- [`android-integration.md`](android-integration.md): Android APIs, permissions, monitoring strategy and platform limits.
- [`development-workflow.md`](development-workflow.md): Git workflow, toolchain baseline, quality gates and local validation.
- [`roadmap.md`](roadmap.md): incremental delivery plan and acceptance targets.
- [`adr/`](adr/): Architecture Decision Records.

## Architecture decisions

- [`ADR-001`](adr/001-rule-based-restriction-engine.md): adopt a rule-based restriction engine.
- [`ADR-002`](adr/002-build-toolchain-and-continuous-integration.md): establish the build toolchain and continuous integration baseline.
- [`ADR-003`](adr/003-initial-module-boundaries.md): define the initial Gradle modules and dependency direction.
- [`ADR-004`](adr/004-hilt-dependency-injection.md): adopt Hilt and KSP for Android dependency injection.
- [`ADR-005`](adr/005-room-persistence-foundation.md): establish Room, schema versioning and migration policy.

## Documentation rules

- The product requirements document remains the high-level source for product intent.
- Repository documentation is versioned with the code and defines how requirements are interpreted technically.
- Major architectural decisions require an ADR before implementation.
- Documentation must be updated in the same pull request as any change that invalidates it.
- Android limitations must be documented explicitly; unsupported guarantees must never be implied.
