# ADR-0016 / D-15 - Use Kermit Behind the `Logger` Abstraction

## Status

Accepted

Accepted by the owner on 2026-08-17.

## Context

`Logger` is a member of `AppGraphDependencies` and is therefore needed from Phase 0, but no implementation had been selected. Leaving it undecided blocked `E0-03` while the Definition of Ready forbids starting a story that depends on an unresolved decision.

Logging also carries privacy obligations: release builds must never contain the Firebase UID, notes, exact odometer values or costs.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Kermit behind `Logger` | Mature KMP logging, platform sinks for Logcat and OSLog, small API. | One more dependency. |
| Napier | Similar feature set. | Smaller ecosystem. |
| Custom sinks only | No dependency. | Reimplements platform log routing for no product value. |

## Decision

Use Kermit as the implementation behind the common `Logger` interface declared in `docs/CONTRACTS.md §20.3`.

Kermit is the logging implementation only. It does not replace `AnalyticsTracker`, Firebase Analytics, `CrashReporter` or Firebase Crashlytics. A Kermit sink may forward allowed diagnostic logs or breadcrumbs to the Crashlytics integration, but analytics events and crash reports still go through their own contracts.

## Consequences

### Positive

- Logging works on both platforms with no product code change.
- The abstraction keeps the implementation swappable.

### Negative

- One additional dependency in the sink modules.

### Constraints Introduced

- Kermit types MUST NOT appear outside the sink implementation; product code sees only `Logger`.
- Redaction is driven by the injected `isDebugBuild` flag, per `docs/CONTRACTS.md §17`.
- Every sync log line carries the `cycleId` field.

## Verification

- Architecture check forbids Kermit imports outside the logger sink module.
- `E4-04` verifies release logs contain no UID, notes, odometer or cost values.

## References

- `docs/DECISION_BOARD.md` (`D-15`)
- `docs/CONTRACTS.md` §17, §20.3
