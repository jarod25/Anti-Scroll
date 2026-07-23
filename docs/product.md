# Product specification

## Vision

Anti-Scroll is a native Android application designed to reduce doomscrolling through system-level friction. It measures usage, groups monitored applications into shared scrolling sessions, applies mandatory cooldowns and quotas, and provides factual local analytics.

The product must not rely solely on willpower at the moment a user is already scrolling compulsively.

## Product principles

1. **Progressive reduction** — limits should evolve from observed usage instead of starting at unrealistically low values.
2. **Shared restriction state** — switching from one monitored application to another must not reset sessions, cooldowns or global quotas.
3. **Predictability** — every block must have an explicit reason and, when possible, an end time.
4. **Minimal frustration** — restrictions should be firm but not arbitrary or humiliating.
5. **Privacy by default** — initial releases store usage data locally and include no account, advertising tracker or remote analytics SDK.
6. **Honest platform behavior** — the application must communicate when Android permissions or manufacturer restrictions reduce reliability.

## Initial monitored applications

Applications are identified by Android package name and loaded from configuration. Initial candidates include TikTok, Instagram, Threads, X, Reddit, Facebook and Snapchat. Pinterest and Twitch are optional.

Communication, banking, navigation, authentication, password-management and email applications are excluded by default.

## Functional scope

### Observation

The initial observation period is configurable, with 14 days as the recommended default. No behavioral restrictions are applied during this period. Usage, openings, sessions and collection interruptions are recorded.

A baseline must not be generated from clearly insufficient data. Observation may be extended when data quality is too low.

### Shared sessions

A scrolling session spans all monitored applications. Moving between TikTok, Instagram and Threads remains one session. Session duration and inactivity thresholds are configuration values.

### Cooldowns

Reaching a configured session limit triggers a global cooldown. All monitored applications remain blocked until it expires. Closing applications, switching applications or restarting the phone must not reset the persisted cooldown.

### Daily quotas

The product supports both per-application quotas and one shared global quota. Exhausting an application quota blocks that application. Exhausting the global quota blocks all monitored applications until the next configured daily boundary.

### Scheduled blocks

Profiles can define recurring blocked periods, especially at night. Scheduled blocks take priority over sessions, cooldowns and quotas.

### Progressive targets

After observation, the product generates an initial profile and lowers targets through deterministic, configurable stages. It may hold a stage when repeated failures indicate that further reduction would be counterproductive.

### Dashboard and statistics

The first dashboard prioritizes today's total, remaining quota, current session, cooldown state, most-used applications, active profile and permission health.

Later statistics include 7-day and 30-day views, hourly distribution, application breakdown, opening counts, block attempts and trend indicators.

## Product boundaries

The first version does not include cloud synchronization, accounts, web blocking, local VPN, content-level Reel/Short detection, artificial intelligence, Wear OS or medical recommendations.

## Success criteria

Success is measured over weeks by lower average scrolling time, fewer long sessions, fewer simple bypass attempts and continued use of Anti-Scroll. The number of blocks alone is not a success metric.
