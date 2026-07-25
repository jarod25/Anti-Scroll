# Development workflow

## Branch model

Anti-Scroll uses a lightweight Git Flow model:

- `main` contains stable, validated releases.
- `develop` is the integration branch for ongoing development.
- `feature/*` contains new capabilities created from `develop`.
- `fix/*` contains non-urgent corrections created from `develop`.
- `release/*` prepares a version for delivery.
- `hotfix/*` contains urgent corrections created from `main`.

Working branches must be short-lived, focused and named in English. Examples:

```text
feature/usage-monitoring
feature/restriction-engine
fix/cooldown-restoration
docs/architecture
```

## Commit conventions

Commit messages are written in English and describe the change precisely. Conventional prefixes are preferred when they improve readability:

```text
feat: add usage event normalization
fix: restore active cooldown after reboot
ci: add Android validation workflow
build: update Android Gradle plugin
docs: document monitoring architecture
test: cover session boundary conditions
chore: normalize repository line endings
```

Commits should remain small enough to review and should not combine unrelated changes.

## Pull request flow

1. Create a working branch from an up-to-date `develop` branch.
2. Implement one focused change.
3. Run the local verification commands.
4. Push the branch and open a pull request targeting `develop`.
5. Review the diff and resolve all CI failures.
6. Validate Android-specific behavior on an emulator or physical device when required.
7. Merge only after the branch satisfies the definition of done.

Release branches target `main` only when a version is ready for delivery. Release changes must also be reintegrated into `develop`.

## Local verification

Run the same core tasks used by continuous integration:

```powershell
.\gradlew.bat spotlessCheck test lint assembleDebug assembleDebugAndroidTest
```

On Linux or macOS:

```bash
./gradlew spotlessCheck test lint assembleDebug assembleDebugAndroidTest
```

The build must use the committed Gradle Wrapper. A locally installed Gradle version is not part of the supported workflow.

## Formatting

Spotless checks every committed Kotlin source file and Kotlin Gradle script with the pinned ktlint engine.

Check formatting without modifying files:

```powershell
.\gradlew.bat spotlessCheck
```

Apply the formatter automatically:

```powershell
.\gradlew.bat spotlessApply
```

On Linux or macOS, replace `.\gradlew.bat` with `./gradlew`.

Formatting rules are stored in `.editorconfig`. No formatting baseline or changed-files-only ratchet is used: the entire committed Kotlin codebase must remain clean.

## Toolchain baseline

The initial project baseline is:

- Android Gradle Plugin 9.3.1
- Gradle 9.5.0 through the Gradle Wrapper
- JDK 21 for the Gradle daemon and Android build execution
- Kotlin 2.2.10 for the Android application
- Gradle embedded Kotlin 2.3.20 for Kotlin DSL execution
- Hilt 2.60.1
- KSP 2.3.9
- Room 2.8.4
- Kotlin coroutines 1.11.0
- Spotless 8.8.0 with ktlint 1.8.0
- Jetpack Compose with the Compose BOM
- minimum SDK 29
- target SDK 36
- compile SDK 36.1

The required Gradle daemon JVM is versioned in `gradle/gradle-daemon-jvm.properties`. The local launcher JVM may differ, but Gradle must resolve and run a daemon matching the committed Java 21 criteria.

Dependency versions are centralized in `gradle/libs.versions.toml` whenever the dependency model supports it.

## Quality gates

Every pull request must pass:

- Kotlin formatting checks;
- unit tests;
- Android lint;
- debug application APK assembly;
- debug instrumentation-test APK assembly;
- Room schema snapshot synchronization when persistence definitions change;
- Gradle Wrapper checksum validation performed by the Gradle setup action;
- review of the affected documentation and architecture decisions.

Room schema files under `data/schemas` are version-controlled artifacts. A database change is incomplete when generated schemas differ from the committed snapshots.

Android lint remains the primary static-analysis tool. Detekt is intentionally deferred until a stable release supports the active toolchain and recurring code-review findings demonstrate that an additional Kotlin rule set would prevent meaningful defects.

## Definition of done

A change is complete when:

- the implementation matches the documented requirement;
- the code has a clear responsibility and respects dependency boundaries;
- committed Kotlin files pass `spotlessCheck`;
- relevant tests exist and pass;
- Android lint passes without newly introduced critical findings;
- the application builds successfully;
- emulator or physical-device validation is completed when system behavior is involved;
- persistence migrations and schema snapshots are updated when database definitions change;
- documentation and ADRs are updated when the change affects architecture or workflow;
- no local configuration, generated build output, credentials or sensitive data are committed.

## Dependency updates

Dependabot opens dependency update pull requests against `develop`. Updates must be reviewed and validated by CI before merge. Major upgrades that change architecture, build behavior, permissions or Android compatibility require a dedicated ADR or an update to an existing decision.
