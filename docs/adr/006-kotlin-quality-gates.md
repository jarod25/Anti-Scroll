# ADR-006: Kotlin formatting and static-analysis policy

- Status: Accepted
- Date: 2026-07-25
- Decision owners: Project maintainers

## Context

Anti-Scroll requires deterministic code formatting and useful static checks before feature development accelerates. The active build uses Android Gradle Plugin 9.3.1, Gradle 9.5.0, Kotlin 2.2.10 and Java 21.

Android lint is already executed in continuous integration. It analyzes Android, Compose, resource, API compatibility, correctness, performance, accessibility and structural problems. A separate Kotlin formatter is still required because `.editorconfig` communicates conventions but does not fail the build when source files violate them.

Detekt was evaluated as an additional Kotlin code-smell analyzer. Its stable 1.x line is built against older Kotlin and Android Gradle Plugin versions, while its modern 2.x line remains in alpha. Adopting it now would add build-toolchain risk before the project contains enough business code to justify the additional rule set.

Spotless 8.8.0 supports Gradle configuration cache and can run a pinned ktlint engine. ktlint 1.8.0 supports current Kotlin syntax and provides deterministic formatting for Kotlin source and Kotlin Gradle scripts.

## Decision

The project adopts the following Kotlin quality policy:

- Spotless 8.8.0 is the repository formatting integration;
- ktlint 1.8.0 is the pinned Kotlin formatting engine;
- one root Spotless configuration covers every committed `*.kt` and `*.gradle.kts` file;
- generated and build directories are excluded;
- formatting conventions are stored in `.editorconfig`;
- `spotlessCheck` is a mandatory CI gate;
- `spotlessApply` is the supported automatic formatting command;
- no formatting baseline, ratchet or existing-violation suppression is introduced;
- Android lint remains the primary static-analysis tool;
- Detekt is deferred until a stable release supports the active toolchain and the codebase has enough complexity to benefit from it.

The supported local commands are:

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat spotlessApply
```

On Linux and macOS:

```bash
./gradlew spotlessCheck
./gradlew spotlessApply
```

The complete local verification command includes `spotlessCheck` before tests, Android lint and assembly.

## Alternatives considered

### Editor formatting only

Relying on Android Studio formatting would avoid a plugin, but formatting would depend on each contributor's IDE settings and would not be enforceable in CI.

### Standalone ktlint Gradle integration

A dedicated ktlint Gradle plugin would provide similar checks. Spotless was selected because it provides one stable formatting entry point, supports configuration cache and can later cover other repository formats without changing the quality workflow.

### Detekt 1.x

The stable Detekt line is built against older Kotlin, Gradle and Android Gradle Plugin versions. It may work through backward compatibility, but it does not match the active toolchain closely enough for a foundation quality gate.

### Detekt 2.x alpha

The modern Detekt line targets current Gradle and Android tooling, but it remains pre-release software. Requiring it in every build would conflict with the project's reliability-first priority.

### Spotless ratchet or formatting baseline

A ratchet would check only newly changed files and leave existing violations accepted. The current codebase is small, so all committed Kotlin files must satisfy the formatter immediately.

## Consequences

### Positive

- formatting is identical on Windows, Linux and macOS;
- formatting violations fail before merge;
- most formatting problems can be corrected automatically;
- the configuration remains centralized and versioned;
- no duplicate code-smell analyzer is added before it provides clear value;
- Android-specific analysis remains owned by Android lint.

### Negative

- local and CI builds resolve an additional Gradle plugin and formatting engine;
- contributors must run `spotlessApply` when formatting checks fail;
- Kotlin-specific complexity and maintainability smells beyond Android lint are not yet enforced automatically;
- ktlint upgrades can produce repository-wide formatting changes and therefore require review.

## Revisit triggers

This decision must be reconsidered when any of the following occurs:

- Detekt 2.x reaches a stable release compatible with the active toolchain;
- the restriction engine or data layer develops complexity that Android lint does not measure well;
- recurring review findings show that a specific static rule would prevent real defects;
- Spotless or ktlint becomes incompatible with the supported Gradle configuration.

## Validation

The decision is validated when:

- `spotlessCheck` passes on Windows and GitHub-hosted Linux runners;
- `spotlessApply` produces a clean subsequent `spotlessCheck`;
- formatting violations cause CI to fail;
- existing Kotlin and Kotlin Gradle files pass without a baseline;
- Android lint, unit tests and application assembly continue to pass.
