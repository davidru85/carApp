# ADR-0242 / D-241 - Tie Crash Collection to the Analytics Opt-In

## Status

Accepted

The owner selected option B on 2026-10-24.

## Context

`E4-04` binds Firebase Crashlytics behind `CrashReporter` (`D-21`, ADR-0023). `docs/SPECIFICATION.md
§3.1` lists one analytics opt-in and no crash-reporting row. `§11` Privacy requires crash-reporting
redaction but does not say whether crash collection is on or off by default.

Both Crashlytics SDKs initialise at process start and collect by default. They persist a runtime
collection command across launches and apply a disable only from the next launch (the GitLive 2.6.0
and Android documentation: "When set to false, the new value does not apply until the next run of the
app"; firebase-ios-sdk 11.8.0 `FIRCrashlytics.h`: "The value does not apply until the next run of the
app"). While collection is off, the SDKs keep crash reports on the device until they are sent or
deleted.

This record is not legal advice; the owner decided the consent posture.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. On by default in release builds, off in debug, disclosed in the privacy policy and store labels | Crash visibility from the first launch | Crash data, including the provider's installation identifier, is collected before any consent; the highest EU consent exposure; behaviour differs per build type |
| B. Tied to the analytics opt-in, off by default | One switch and no new Settings row; the lowest consent exposure; the same posture as `§16.1` | Crash visibility only for opted-in users; amends `§16.1` and `§20.3.1`; the Settings copy must say the switch covers crash data |
| C. A separate Settings toggle | Independent consent | Amends gated `§3.1` scope and reopens `E4-01`'s surface |

## Decision

The selected option is **B**:

- The `:shared` opt-in gate (`AnalyticsEmissions`) commands `CrashReporter.setEnabled` with the same
  value as `AnalyticsTracker.setEnabled`, at startup and on every change.
- Both hosts disable Crashlytics collection in platform configuration: the
  `firebase_crashlytics_collection_enabled` manifest `meta-data` and the
  `FirebaseCrashlyticsCollectionEnabled` `Info.plist` key, both `false`.
- The Firebase crash reporter starts disabled, records and buffers nothing while disabled, and on
  `setEnabled(false)` commands the provider off and deletes the reports the provider stored while
  collection was off.
- `docs/SPECIFICATION.md §3.1`, `§8.1` and `§11` Privacy, `docs/CONTRACTS.md §16.1` and `§20.3.1`,
  `§18` assertion {N3}, `docs/SECURITY.md` and the Settings copy in English and Spanish on both hosts
  say so.

## Consequences

### Positive

- A fresh install reports no crash data, and nothing captured before an opt-in is uploaded after it.
- No new Settings row and no change to `E4-01`'s surface.

### Negative

- Crash visibility covers opted-in users only.
- After an opt-out, the provider's fatal-crash handler stays active until the app restarts, so a fatal
  crash in that session may still be uploaded on Android. Non-fatal reports stop at once. Recorded in
  `docs/SECURITY.md`.
- After an opt-in on iOS, fatal-crash capture starts at the next launch.
- A non-fatal raised at startup before the gate applies the stored opt-in is dropped (diagnostic loss
  only).

### Constraints Introduced

- `CrashReporter.setEnabled` is called only by the `§16.1` opt-in gate.
- The two host keys MUST stay `false`; `§18` assertion {N3} enforces it.
- A separate crash-reporting toggle needs a superseding decision and a `§3.1` amendment.

## Verification

- `SharedCrashConsentTest` (fresh install off, opt-in on, opt-out off) and `CrashConsentAppGraphTest`.
- `FirebaseCrashReporterTest`: disabled by default, enable and disable commands, stored-report
  deletion, no replay.
- `§18` assertion {N3} and its failing fixtures in `CrashlyticsIntegrationContractTest`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-241`)
- `docs/adr/0023-firebase-crashlytics.md` (`D-21`)
- `docs/adr/0204-collect-product-analytics-without-advertising-identification.md` (`D-203`)
- `docs/CONTRACTS.md §16.1`, `§20.3.1`, `§18`
- `docs/SPECIFICATION.md §3.1`, `§11`
