# Delivery roadmap

The roadmap favors an early usable build while preserving clean boundaries around the restriction engine.

## Increment 0 — Foundation

**Status: Complete**

### Goal

Create a buildable Android project with enforceable quality checks and documented architecture.

### Deliverables

- Gradle project and version catalog;
- initial modules and dependency rules;
- Kotlin and Compose setup;
- dependency injection;
- Room skeleton and migration testing setup;
- lint, static analysis and formatting;
- unit-test infrastructure;
- GitHub Actions CI;
- first ADRs;
- installable empty application.

### Exit criteria

- debug APK builds locally and in CI;
- module dependency direction is enforced;
- unit tests and static checks run in CI;
- application launches on a physical device or emulator.

## Increment 1 — Observation foundation

**Status: Feature complete — validation debt remaining**

### Goal

Collect trustworthy-enough local usage data and make reliability visible.

### Architecture baseline

- UsageStats is the required historical and recovery source;
- normalized usage observations form the durable journal;
- daily usage values are rebuildable persisted projections;
- checkpoints and collection gaps make recovery and uncertainty explicit;
- WorkManager provides persistent deferrable reconciliation without being treated as a real-time guarantee;
- foreground duration and intentional-opening estimation remain separate projections;
- increment 1 introduces no permanent foreground service, broad package-visibility permission or blocking behavior.

The detailed observation decision is recorded in ADR-007.

### Delivered

- onboarding and real Usage Access health;
- monitored application configuration from a versioned package catalog;
- UsageStats integration with API-appropriate query filtering;
- immutable normalized usage events;
- persisted normalized event journal;
- persisted daily per-application usage projections;
- collection checkpoints and explicit gap diagnostics;
- incremental overlapping reconciliation and deterministic deduplication;
- process and device restart restoration mechanisms;
- package-level session reconstruction;
- intentional-opening estimation across brief sharing interruptions;
- minimal observation dashboard;
- deterministic initial baseline from reliable completed days;
- local-only data handling.

### Remaining validation debt

- compare Anti-Scroll totals with Android system statistics over a representative full day;
- validate observation restoration after a complete physical-device reboot and user unlock;
- validate package-replacement recovery independently from instrumentation-test installation;
- exercise manufacturer battery restrictions and application hibernation on the reference device.

This debt does not block restriction-engine development, but it must be closed before observation reliability is presented as fully validated for sustained daily use.

### Exit criteria

- monitored applications are detected on the reference device;
- daily totals can be compared with Android system data;
- repeated overlapping reconciliation does not double-count known observations;
- process recreation and device restart preserve or reconstruct daily totals;
- missing permission, locked-user state and collection gaps are shown honestly;
- no unknown period is presented as observed usage;
- no usage data leaves the device.

## Increment 2 — Shared sessions and cooldowns

**Status: In progress**

### Goal

Deliver the first version that actively interrupts doomscrolling.

### Deliverables

- minimal accessibility service for low-latency foreground signals;
- shared cross-application sessions;
- configurable session limit;
- configurable global cooldown;
- rule-based restriction engine;
- blocking interface;
- persisted cooldown state;
- recorded blocked-opening attempts;
- restart recovery;
- critical unit and integration tests.

### Delivered so far

- accessibility service limited to transient package-level foreground signals;
- explicit shared-session domain state and transitions;
- configurable shared-session maximum duration and inactivity timeout in a versioned profile;
- pure deterministic shared-session reducer;
- pure ordered restriction engine with explicit priorities and secondary reasons;
- `SessionLimitRule` with a stable block reason and boundary behavior;
- unit coverage for cross-application continuity, inactivity, duplicate and stale events, limit boundaries and rule priorities.

The current shared-session foundation is not connected to Android enforcement, does not persist restriction state and does not start a cooldown yet.

### Exit criteria

- the accessibility service observes only package-level foreground signals for selected applications and cannot retrieve window content;
- switching monitored applications does not reset the session;
- reaching the limit starts one global cooldown;
- all monitored applications stay blocked during the cooldown;
- closing apps or restarting the device does not clear it;
- block reason and remaining time are visible.

## Increment 3 — Quotas and schedules

### Goal

Add daily control beyond individual sessions.

### Deliverables

- per-application quotas;
- shared global quota;
- configurable daily boundary;
- recurring scheduled blocks;
- rule priority handling;
- daily history;
- quota and block information on dashboard.

### Exit criteria

- quota calculations remain correct across application switches;
- scheduled periods crossing midnight behave correctly;
- clock changes do not create simple duplicate resets;
- simultaneous block reasons resolve deterministically.

## Increment 4 — Full observation and progressive reduction

### Goal

Generate realistic limits from actual behavior.

### Deliverables

- configurable 14-day observation workflow;
- data-quality validation;
- robust baseline calculation;
- generated initial profile;
- deterministic reduction stages;
- stage hold rules and minimum limits;
- profile transition history.

### Exit criteria

- incomplete data does not produce arbitrary quotas;
- generated targets are reproducible from the same dataset;
- progression can be fully unit-tested with controlled clocks;
- changes remain understandable to the user.

## Increment 5 — Statistics and test distribution

### Goal

Make progress visible and prepare sustained personal use.

### Deliverables

- 7-day and 30-day views;
- application breakdown;
- session and opening trends;
- hourly heatmap;
- accessibility refinements;
- battery profiling;
- migration hardening;
- signed internal test build and release notes.

## Backlog after the first release

- export CSV;
- encrypted local backup;
- optional encrypted synchronization;
- widgets;
- additional profiles;
- optional Device Owner deployment;
- local VPN web blocking;
- reliable content-level Reel/Short detection if technically viable.

## Definition of done

A feature is complete only when:

- behavior and acceptance criteria are met;
- relevant tests pass;
- error and degraded states are handled;
- no undocumented architectural shortcut is introduced;
- affected documentation is updated;
- CI is green;
- real-device behavior is checked when Android integration is involved.
