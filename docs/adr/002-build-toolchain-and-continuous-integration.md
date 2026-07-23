# ADR-002: Build toolchain and continuous integration

- Status: Accepted
- Date: 2026-07-23
- Decision owners: Project maintainers

## Context

Anti-Scroll depends on Android platform integration and requires a reproducible build before restriction-engine development begins. The generated project uses Android Gradle Plugin 9.3.1 and Gradle 9.5.0. Android Gradle Plugin 9.3 requires at least JDK 17, while the generated project also commits Gradle daemon JVM criteria requiring Java 21.

The project also needs early feedback for compilation failures, unit-test regressions and Android lint findings. At the same time, adding several third-party quality plugins immediately would increase build complexity before their compatibility with Android Gradle Plugin 9.3, Gradle 9.5 and Kotlin 2.2 has been validated.

## Decision

The project adopts the following build and validation baseline:

- Java 21 is used for the Gradle daemon and Android build execution.
- `gradle/gradle-daemon-jvm.properties` is committed and defines the required daemon JVM version.
- GitHub Actions installs JDK 21 so the launcher and daemon use the same Java major version.
- The committed Gradle Wrapper is the only supported Gradle entry point.
- GitHub Actions validates pushes to all branches and pull requests targeting `main` or `develop`.
- The validation pipeline executes unit tests, Android lint and debug APK assembly.
- `gradle/actions/setup-gradle` validates the Gradle Wrapper and provides dependency caching.
- The open-source basic cache provider is used.
- Verification reports are uploaded when the workflow fails.
- Dependency update pull requests target `develop`.
- Repository-wide formatting and line-ending rules are defined with `.editorconfig` and `.gitattributes`.

Kotlin 2.2.10 is the Android application plugin version. The Kotlin version displayed by `gradlew --version` is Gradle's embedded Kotlin used for Kotlin DSL execution and is not the application Kotlin plugin version.

Detekt, ktlint Gradle integration or equivalent third-party analysis plugins are deferred until compatibility has been tested in isolation. Android lint, tests, compilation and code review remain mandatory in the meantime.

## Alternatives considered

### No continuous integration during early development

This would reduce initial setup work but allow build failures and environment-specific problems to accumulate. It would also make later modularization riskier.

### Add all planned quality plugins immediately

This would provide more automated checks but would couple the foundation to plugins whose compatibility with the current Android and Kotlin toolchain has not yet been demonstrated.

### Keep JDK 17 in CI while requiring Java 21 for the Gradle daemon

This configuration works because Gradle can resolve a separate daemon JVM matching the committed criteria, but it introduces an unnecessary launcher/daemon mismatch and may trigger additional toolchain provisioning. Aligning CI on Java 21 is simpler and more reproducible.

## Consequences

### Positive

- Build failures are detected before merge.
- Local and CI Gradle daemons use the same Java major version.
- The required daemon JVM is explicit and versioned.
- The same Gradle Wrapper is used locally and in CI.
- Android lint and unit tests become merge gates from the beginning.
- The initial pipeline remains understandable and maintainable.
- Additional quality tools can be evaluated without blocking project initialization.

### Negative

- Contributors need access to Java 21 or must allow Gradle toolchain provisioning.
- Formatting is configured but not yet enforced by a dedicated Gradle task.
- Kotlin-specific static analysis is initially limited to compiler diagnostics, Android lint and review.
- CI depends on GitHub-hosted runner availability.

## Migration and rollback

The workflow and repository configuration are additive. They can be updated or removed without migrating application data. If Java 21 becomes incompatible with a future Android toolchain version, the daemon JVM criteria, CI runtime and documentation must be changed together in one pull request.

## Validation

The decision is validated when:

- the workflow succeeds with JDK 21;
- the Gradle Wrapper checksum is accepted;
- `gradlew --version` reports a Java 21 daemon locally;
- `test`, `lint` and `assembleDebug` succeed on GitHub Actions;
- the same commands succeed on the local Windows development environment;
- a deliberately failing test or lint violation causes the workflow to fail during a future pipeline verification exercise.
