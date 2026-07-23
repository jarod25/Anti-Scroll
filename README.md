# Anti-Scroll

Native Android application designed to reduce doomscrolling through progressive restrictions, shared scrolling sessions, mandatory cooldowns, daily quotas and privacy-first local analytics.

Anti-Scroll is not intended to be another screen-time dashboard. Its core purpose is to apply predictable restrictions when the user is no longer in the best position to make a rational decision about stopping.

## Project status

The project is currently in its foundation phase. Product requirements and architecture are being defined before implementation of the first Android increment.

## Core principles

- Progressive reduction rather than abrupt deprivation.
- Shared sessions across all monitored scrolling applications.
- Restrictions that cannot be bypassed simply by switching applications.
- Local-only data storage for the initial version.
- Explicit, deterministic and testable restriction rules.
- Honest handling of Android platform limitations.
- Reliability and maintainability before premature optimization.

## Documentation

Start with [`docs/README.md`](docs/README.md).

The complete product specification is maintained in the project's requirements document. Repository documentation contains the technical, version-controlled interpretation used during development.

## Planned stack

- Kotlin
- Jetpack Compose
- Coroutines and Flow
- Room
- DataStore where appropriate
- Hilt
- JUnit
- Android Lint and Detekt
- GitHub Actions

Exact versions and Android SDK levels will be recorded through Architecture Decision Records before implementation.

## License

Licensed under the Apache License 2.0.
