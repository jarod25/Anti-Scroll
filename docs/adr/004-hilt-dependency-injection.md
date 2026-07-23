# ADR-004: Hilt dependency injection

- Status: Accepted
- Date: 2026-07-24
- Decision owners: Project maintainers

## Context

Anti-Scroll is a multi-module Android application whose `app` module composes the restriction engine, persistence and Android monitoring implementations. Future increments will introduce Room, Android services, broadcast receivers, ViewModels and lifecycle-bound coordinators.

Manual dependency injection would remain manageable for the current empty application, but the amount of container and lifecycle wiring would grow quickly once Android entry points and long-lived services are introduced. The project also requires dependency errors to fail during compilation rather than becoming hidden runtime lookups.

Android Gradle Plugin 9 uses built-in Kotlin support. Annotation processing therefore needs to remain compatible with that build model.

## Decision

The project uses Hilt as the Android dependency-injection framework and KSP for code generation.

The initial baseline is:

- Hilt 2.60.1;
- KSP 2.3.9;
- the Hilt Gradle plugin applied to `app`;
- the Hilt aggregating task enabled;
- `AntiScrollApplication` annotated with `@HiltAndroidApp`;
- Android framework entry points annotated with `@AndroidEntryPoint` only when injection is required.

The following dependency rules apply:

- `domain` and `engine` must not depend on Hilt, Dagger, KSP or Android dependency-injection annotations;
- constructor injection is preferred for classes owned by the project;
- `@Binds` is preferred for interface-to-implementation bindings;
- `@Provides` is reserved for dependencies that cannot use constructor injection, such as framework or third-party objects;
- scopes are introduced only when the intended lifecycle and ownership are explicit;
- Android library modules add Hilt dependencies only when they actually own injectable classes or bindings;
- `app` remains the composition root and must have transitive access to all installed Hilt modules.

No placeholder service or artificial binding is added solely to demonstrate injection. The generated application component and Android entry-point transformation provide the initial compilation proof.

## Alternatives considered

### Manual dependency injection

Manual construction would avoid code generation and external framework annotations. It is attractive for a very small application, but it would require custom containers and lifecycle wiring for activities, services, receivers, ViewModels and process-wide objects. That boilerplate would become a maintenance risk in the Android integration layer.

### Dagger without Hilt

Direct Dagger usage offers maximum control, but requires the project to own component hierarchies and Android lifecycle integration that Hilt already standardizes. The additional flexibility is not currently justified.

### Service locator

A global service locator is simple to access but hides dependencies, moves failures to runtime and makes tests dependent on mutable global state. It is incompatible with the project's explicit dependency and testability goals.

### kapt instead of KSP

kapt is in maintenance mode and is not the preferred annotation-processing path for projects using modern Android Gradle Plugin built-in Kotlin support. KSP is supported by Hilt and is the chosen processing mechanism.

## Consequences

### Positive

- dependency graphs are validated at compile time;
- Android lifecycle integration is standardized;
- future Room repositories, services and ViewModels can be composed consistently;
- `domain` and `engine` remain framework-independent;
- constructor dependencies stay explicit and testable;
- KSP avoids introducing legacy kapt configuration.

### Negative

- the application build now includes code generation and Hilt bytecode transformation;
- contributors must understand Hilt component lifecycles and scoping rules;
- careless use of scopes or modules could hide ownership mistakes;
- Hilt version compatibility becomes part of build-toolchain maintenance.

## Migration and rollback

The initial change only bootstraps the application component and main activity entry point. No business implementation depends on Hilt yet, so rollback would consist of removing the annotations, application class registration, plugins and dependencies.

Once production bindings exist, replacing Hilt would require moving construction logic to another composition mechanism while preserving the contracts owned by `domain`.

## Validation

The decision is validated when:

- Gradle configuration resolves Hilt and KSP successfully;
- Hilt code generation completes in CI and locally;
- `test`, `lint` and `assembleDebug` succeed;
- the application launches with `AntiScrollApplication` registered;
- `MainActivity` launches as a Hilt Android entry point;
- `domain` and `engine` remain free of Hilt and Android dependencies.
