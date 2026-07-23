# Android integration

## Objective

The Android integration layer observes application usage, restores state after interruptions and enforces domain decisions as reliably as the platform allows.

It must not contain business rules that belong in the domain or restriction engine.

## Usage access

Usage access is required to query Android-provided usage history and reconstruct periods of application activity.

The application must:

- explain why access is needed;
- guide the user to the relevant system screen;
- verify the real permission state on every relevant resume;
- detect revocation;
- record collection gaps;
- never fabricate usage during missing periods.

UsageStats data may be delayed or incomplete depending on Android version and device behavior. It is therefore not treated as a perfect real-time event stream.

## Accessibility service

An accessibility service may be used to detect foreground application changes quickly and trigger the block experience when a restricted application is opened.

Its scope must remain minimal:

- observe only the event types required for application identification;
- avoid collecting visible text or user content;
- perform no remote transmission;
- document the purpose clearly in the service description and onboarding;
- remain removable from the domain through an interface.

Store-distribution requirements must be reviewed again before release. Eligibility must not be assumed from current development behavior.

## Monitoring composition

The monitoring implementation may combine multiple sources:

```text
Accessibility events ─┐
UsageStats history ───┼─> UsageEventNormalizer -> domain events
System broadcasts ───┘
```

Accessibility provides responsiveness. UsageStats assists reconstruction and validation. System broadcasts report boot, time, time-zone and package changes.

Events must be deduplicated and normalized before reaching the domain.

## Blocking coordination

The domain returns a restriction decision. Android-specific code then performs the best supported intervention, such as presenting a dedicated blocking activity or directing the user away from the restricted application.

The block UI must show:

- restricted application;
- stable reason translated for the user;
- remaining duration or expected end time when available;
- safe navigation away from the application;
- no instant bypass control.

The exact enforcement mechanism requires real-device validation across supported Android versions.

## Foreground execution

A foreground service is used only when continuous execution is necessary and compliant with the targeted Android version.

When active, it must:

- display a clear persistent notification;
- perform minimal work;
- avoid aggressive polling;
- stop when no longer required;
- use the correct foreground-service type and permissions for the final target SDK.

## Restart and process death

The application must not assume that its process remains alive.

After process recreation or device boot it reconstructs:

- active profile;
- cooldown expiry;
- quota consumption;
- latest session state;
- permission health;
- monitoring schedule.

Boot receivers and scheduled work are used only within Android background-execution limits.

## Time and time-zone changes

The integration layer forwards wall-clock and time-zone changes to the domain. Elapsed durations use monotonic time while the process is alive. Persisted expiry data must include enough information to avoid shortening restrictions through simple clock changes.

The final strategy will be specified and tested before cooldown implementation because Android monotonic clocks reset on reboot.

## Manufacturer restrictions

Some manufacturers aggressively stop background services. The application should detect degraded monitoring where possible and provide device-specific guidance without claiming universal reliability.

## Device Owner

Device Owner capabilities are not required for the standard first release because provisioning is incompatible with a normal consumer installation path.

A stronger Device Owner mode may be explored later as a separate deployment profile. Standard architecture must not depend on it.

## Validation matrix

Before the first useful release, testing must include:

- at least one physical reference device;
- process kill and recreation;
- device restart;
- permission revocation and restoration;
- rapid switching between monitored applications;
- screen lock and unlock;
- time and time-zone changes;
- battery-optimization behavior;
- supported Android-version boundaries.
