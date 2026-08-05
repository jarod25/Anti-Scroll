# ADR-008 — Use accessibility only as a transient foreground signal

## Status

Accepted.

## Context

UsageStats provides the durable Android history required to reconstruct usage after process death, delayed work and device restart. It is not a reliable low-latency source for reacting immediately when a monitored application becomes active.

The first restriction increment therefore needs a faster Android signal without allowing the monitoring adapter to own session, cooldown or blocking rules. The service must also minimize the sensitive accessibility surface and avoid collecting screen content.

## Decision

Anti-Scroll introduces an `AccessibilityService` with the following boundaries:

- it requests only `TYPE_WINDOW_STATE_CHANGED` events;
- `canRetrieveWindowContent` remains `false`;
- it never traverses accessibility nodes, reads visible text or performs gestures;
- its package filter follows the applications currently enabled in the local monitored-application repository;
- when no application is enabled, the Android filter is restricted to Anti-Scroll itself rather than left open to every package;
- every accepted event becomes a transient `ForegroundApplicationSignal` containing the package name, wall-clock observation instant and Android elapsed realtime;
- signals are exposed through a domain contract but are not persisted by the accessibility adapter;
- UsageStats remains the durable journal and recovery source;
- the service does not calculate sessions, start cooldowns, show a blocker or make restriction decisions.

The permission reader distinguishes four operational states: disabled, enabled and connected, enabled but disconnected, and unavailable or erroneous system state.

## Consequences

### Positive

- future restriction evaluation can react without an aggressive polling loop;
- session and cooldown logic remains pure Kotlin and independent from Android callbacks;
- historical reconstruction remains possible when the service was disconnected;
- privacy exposure is reduced because window content capability is not requested;
- monitored-package changes are applied without restarting the service.

### Negative

- accessibility is a sensitive permission that requires explicit user activation and clear disclosure;
- Android and manufacturer behavior still require physical-device validation;
- package-level window-state events can repeat for internal activity transitions, so future restriction evaluation must be idempotent;
- transient signals can be unavailable during service or process interruption and must never replace durable UsageStats recovery.

## Alternatives rejected

### Faster UsageStats polling

Rejected because it would add battery cost without providing a real-time guarantee.

### Persist accessibility events as a second journal

Rejected because it would create competing sources of truth and more complex deduplication. UsageStats remains authoritative for durable reconstruction.

### Retrieve window content

Rejected because package-level foreground detection does not require accessibility nodes, visible text or interactive-window access.

### Add blocking behavior in the same change

Rejected to keep Android signal acquisition independently testable before session and cooldown rules consume it.
