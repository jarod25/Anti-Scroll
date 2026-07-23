# Anti-Scroll documentation

This directory contains the technical documentation used as the development reference for Anti-Scroll.

## Documents

- [`product.md`](product.md): product vision, scope and core behavioral requirements.
- [`architecture.md`](architecture.md): software architecture, module boundaries and dependency rules.
- [`restriction-engine.md`](restriction-engine.md): rule-based decision engine and restriction model.
- [`android-integration.md`](android-integration.md): Android APIs, permissions, monitoring strategy and platform limits.
- [`roadmap.md`](roadmap.md): incremental delivery plan and acceptance targets.
- [`adr/`](adr/): Architecture Decision Records.

## Documentation rules

- The product requirements document remains the high-level source for product intent.
- Repository documentation is versioned with the code and defines how requirements are interpreted technically.
- Major architectural decisions require an ADR before implementation.
- Documentation must be updated in the same pull request as any change that invalidates it.
- Android limitations must be documented explicitly; unsupported guarantees must never be implied.
