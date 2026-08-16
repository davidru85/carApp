# ADR-0011 / D-10 - Use Firebase Analytics Behind AnalyticsTracker

## Status

Accepted

## Context

The MVP needs product metrics to understand onboarding, fuel entry creation, account conversion, and sync health at an aggregate level. The project already uses Firebase for authentication and remote data replication, so Firebase Analytics is operationally aligned with the selected backend stack.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Firebase Analytics | Integrates with Firebase ecosystem, no additional provider, good mobile support. | Must avoid leaking personal vehicle/fuel data into events. |
| PostHog | Product analytics flexibility and provider independence. | Additional service and integration complexity. |
| No analytics | Simplest and most private. | Harder to validate onboarding and MVP usage. |

## Decision

Use Firebase Analytics behind a common `AnalyticsTracker` abstraction.

## Consequences

### Positive

- Product metrics are available during MVP validation.
- Analytics provider can be replaced through `AnalyticsTracker`.

### Negative

- Requires strict event and payload guardrails.
- Privacy policy and store privacy labels must cover analytics.

### Constraints Introduced

- No analytics calls from domain logic or data persistence logic.
- No exact odometer, fuel volume, price, cost, notes, entity IDs, UIDs, tokens, or raw sync payloads in events.
- Firebase Analytics implementation lives in `:integration:firebase-analytics`.

## Verification

- Architecture checks prevent Firebase Analytics outside integration/wiring modules.
- Analytics event contract tests validate allowed event names and parameter allowlist.
- Privacy review before release.
