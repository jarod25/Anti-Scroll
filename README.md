# Anti-Scroll

[![Android CI](https://github.com/jarod25/Anti-Scroll/actions/workflows/android-ci.yml/badge.svg?branch=develop)](https://github.com/jarod25/Anti-Scroll/actions/workflows/android-ci.yml)

Native Android application designed to reduce doomscrolling through progressive restrictions, shared scrolling sessions, mandatory cooldowns, daily quotas and privacy-first local analytics.

Anti-Scroll is not intended to be another screen-time dashboard. Its core purpose is to apply predictable restrictions when the user is no longer in the best position to make a rational decision about stopping.

## Project status

The project is currently in increment 0: establishing a reproducible Android build, continuous integration, repository conventions and the technical documentation required before product features are implemented.

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

Development conventions and local validation commands are documented in [`docs/development-workflow.md`](docs/development-workflow.md). Architecture decisions are stored in [`docs/adr`](docs/adr).

The complete product specification is maintained in the project's requirements document. Repository documentation contains the technical, version-controlled interpretation used during development.

## Current toolchain

- Kotlin 2.2.10
- Jetpack Compose
- Android Gradle Plugin 9.3.1
- Gradle 9.5.0 through the Gradle Wrapper
- JDK 17 for build execution
- minimum SDK 29
- target SDK 36
- compile SDK 36.1
- JUnit and Android lint
- GitHub Actions

Additional libraries such as Room, DataStore and Hilt will be introduced only when required by the relevant increment.

## Build locally

On Windows:

```powershell
.\gradlew.bat test lint assembleDebug
```

On Linux or macOS:

```bash
./gradlew test lint assembleDebug
```

## License

Licensed under the Apache License 2.0.
