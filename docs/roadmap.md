# Delivery roadmap

The roadmap favors an early usable build while preserving clean boundaries around the restriction engine.

## Increment 0 — Foundation

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

### Goal

Collect trustworthy-enough local usage data and make reliability visible.

### Deliverables

- onboarding and permission health screen;
- monitored application configuration;
- UsageStats integration;
- accessibility monitoring experiment behind an interface;
- normalized usage events;
- persisted daily usage;
- minimal dashboard;
- collection-gap diagnostics;
- process and device restart restoration.

### Exit criteria

- monitored applications are detected on the reference device;
- daily totals can be compared with Android system data;
- missing permissions and gaps are shown honestly;
- no usage data leaves the device.

## Increment 2 — Shared sessions and cooldowns

### Goal

Deliver the first version that actively interrupts doomscrolling.

### Deliverables

- shared cross-application sessions;
- configurable session limit;
- configurable global cooldown;
- rule-based restriction engine;
- blocking interface;
- persisted cooldown state;
- recorded blocked-opening attempts;
- restart recovery;
- critical unit and integration tests.

### Exit criteria

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
