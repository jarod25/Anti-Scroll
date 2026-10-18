# ADR-015 — Restore foreground monitoring after device reboot

## Status

Accepted.

## Context

Anti-Scroll has two different Android monitoring mechanisms. Android manages the lifecycle of the user-enabled AccessibilityService, which provides low-latency foreground signals. The application also has an optional, notification-bearing foreground service that keeps the shared-session runtime process active when protection requires continuous execution.

Before this decision, the foreground service was started only by `MainActivity.onStart()`. Removing the application's task did not request a stop, but a reboot or package replacement had no explicit foreground-service restart path. The boot receiver already restored periodic and immediate WorkManager reconciliation.

An AccessibilityService cannot be enabled programmatically or reliably relaunched by the app itself. Its lifecycle must remain system-managed.

## Decision

- Keep the AccessibilityService as a system-bound component independent of the settings Activity.
- Explicitly declare `stopWithTask=false` for the AccessibilityService; this documents Android's existing default and is not itself a functional fix.
- Keep the existing `MonitoringForegroundService` and its `START_STICKY` behavior; do not introduce polling, hidden Activity launches or watchdog loops.
- Centralize foreground-service startup in `MonitoringForegroundServiceStarter`.
- Start the foreground service only if Usage Access is granted and Android reports the AccessibilityService enabled (including enabled-but-not-yet-connected).
- Request the same permission-aware startup from the visible Activity and from the existing `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` receiver.
- Catch and log platform-denied startup rather than crashing the Activity or boot receiver.
- Keep the existing WorkManager recovery path separate from foreground-service startup. Never use WorkManager for immediate app blocking.

## Consequences

### Positive

- The ongoing monitoring service no longer relies exclusively on reopening the settings Activity after a reboot or package replacement.
- The app avoids starting its foreground service when required monitoring permissions are missing.
- The AccessibilityService and session engine remain separate; domain and persistence modules do not acquire Android lifecycle dependencies.
- Existing notification, session-state restoration and historical reconciliation behavior remains unchanged.

### Negative and limitations

- An Android foreground service is not immortal: users, the OS and device manufacturers can stop it.
- Android 12+ restricts background foreground-service starts, with defined exceptions for boot and package replacement; Android 14+ imposes service-type requirements. The existing `specialUse` declaration still requires a valid use case and store review.
- A user force-stop, revoked permission, manufacturer autostart restriction or Safe Mode cannot be overridden by this approach.
- Eligibility checks immediately after boot depend on what the Android permission APIs report at that moment. A later user launch can retry service startup.
- Physical-device validation of reboot, package replacement, task removal, lock/unlock and power management remains necessary before claiming continuous monitoring.

## Alternatives considered

### Start the foreground service unconditionally from boot

Rejected because a notification-bearing monitoring service should not run without the required permissions.

### Start a foreground service from the AccessibilityService callback

Rejected because accessibility lifecycle is managed by Android and callbacks may arrive while foreground-service background-start restrictions apply.

### Use a periodic restart watchdog

Rejected because WorkManager scheduling is inexact, battery cost would increase and neither Android nor manufacturers guarantee continuous restarts.
