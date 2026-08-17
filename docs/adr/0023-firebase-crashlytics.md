# ADR-0023 / D-21 - Firebase Crashlytics Behind CrashReporter

## Status

Accepted

Accepted by the owner on 2026-08-17.

## Context

The MVP needs crash visibility before release, but crash reporting is not required for the foundation, local persistence, authentication or synchronization stories. The project already uses Firebase services, and logging is handled separately by Kermit behind `Logger`.

Crash reporting must not become a second analytics channel and must not leak vehicle, fuel, sync payload, UID, token, note, odometer or cost data.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Firebase Crashlytics behind `CrashReporter` | Fits the Firebase stack; strong mobile support; no additional provider. | Requires strict privacy redaction and store disclosure. |
| Sentry | Strong diagnostics and release tools. | Adds another external provider and SDK stack. |
| No crash reporting in MVP | Simplest and most private. | Release hardening lacks crash visibility. |

## Decision

Use Firebase Crashlytics behind a common `CrashReporter` abstraction in Phase 4. `CrashReporter` is a contract boundary; Firebase Crashlytics types stay inside `:integration:firebase-crashlytics` and `:wiring:firebase`.

Kermit remains the logging implementation. A Kermit sink may forward allowed diagnostic breadcrumbs to the Crashlytics integration, but logging events are not analytics events or crash reports.

## Consequences

### Positive

- Release hardening has crash visibility without adding a second provider family.
- Crash reporting remains replaceable through `CrashReporter`.

### Negative

- Privacy policy and store privacy labels must cover crash diagnostics.
- The release build needs explicit redaction verification.

### Constraints Introduced

- No UID, tokens, notes, exact odometer values, exact costs, raw Firestore payloads or free-text user content may be attached to crash reports.
- `CrashReporter` is introduced as an abstraction; provider SDK types do not cross the integration boundary.
- Crash reporting is implemented in Phase 4, not during Phase 0.

## Verification

- `E4-04` verifies release logging and crash-reporting redaction.
- Architecture checks prevent Crashlytics imports outside integration and wiring modules.
- Privacy review before release covers crash diagnostics.

## References

- `docs/DECISION_BOARD.md` (`D-21`)
- `docs/CONTRACTS.md` §17
- `docs/BACKLOG.md` `E4-04`
