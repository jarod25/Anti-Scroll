# ADR-003: Initial module boundaries

- Status: Accepted
- Date: 2026-07-24
- Decision owners: Project maintainers

## Context

Anti-Scroll requires a restriction engine that remains independently testable from Android framework behavior. The project also needs clear ownership boundaries for persistence and usage monitoring, which will depend on Android APIs and permissions.

The initial architecture document proposed `app`, `core`, `domain`, `data`, `engine` and `monitoring`. At this stage, however, no stable shared technical responsibility exists for a generic `core` module. Creating it preemptively would encourage unrelated utilities and constants to accumulate behind a vague name.

## Decision

The project starts with five Gradle modules:

```text
:app
:domain
:engine
:data
:monitoring
```

Their dependency rules are:

```text
:app        → :domain, :engine, :data, :monitoring
:domain     → none
:engine     → :domain
:data       → :domain
:monitoring → :domain
```

`:domain` and `:engine` are pure Kotlin/JVM modules. They must not expose or depend on Android framework classes.

`:data` and `:monitoring` are Android library modules. They remain independent from one another and from `:engine`.

`:app` is the composition root. It wires implementations to domain contracts and owns Android application entry points, navigation and UI.

A generic `:core` module is not created. Future shared modules require a concrete responsibility and should use narrow names such as `:core:time` or `:core:testing`.

## Alternatives considered

### Keep all code in `:app`

This minimizes initial Gradle configuration but makes it easy for Android classes, persistence details and UI concerns to leak into the restriction engine. It also weakens JVM-only testing.

### Create the original six-module structure including `:core`

This follows the earlier architecture draft but introduces a module with no current owner or stable purpose. Empty architectural placeholders add maintenance cost and often become utility dumps.

### Create feature and infrastructure submodules immediately

A larger structure such as `:feature:dashboard`, `:core:database` and `:core:designsystem` could become useful later, but current code and team size do not justify those compilation boundaries yet.

## Consequences

### Positive

- Domain and restriction logic compile and test without Android.
- Android monitoring and persistence are isolated behind explicit boundaries.
- The application module remains the single composition root.
- The module graph is small enough to understand and maintain.
- Future modules must be justified by concrete ownership or dependency needs.

### Negative

- Shared build configuration is initially duplicated across a small number of modules.
- `:app` depends directly on all implementation modules.
- Moving code between modules later may require package and build-file changes.
- Boundary rules are currently expressed by Gradle dependencies and review rather than a dedicated architecture-test plugin.

## Migration and rollback

The modules are initially empty except for build configuration and isolation tests, so migration risk is low. If the boundaries prove incorrect, modules can be merged before substantial product code is added. Any later major boundary change requires a new ADR.

## Validation

The decision is validated when:

- all five modules are registered in Gradle;
- `:domain` and `:engine` execute JVM tests without an Android classpath;
- `:data` and `:monitoring` build as Android libraries;
- the application assembles while depending on all required modules;
- root `test`, `lint` and `assembleDebug` tasks succeed locally and in CI.
