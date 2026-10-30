# ADR-0244 / D-243 - Record Non-Fatals at the Shared Message Chokepoints

## Status

Accepted

The owner selected option A on 2026-10-24.

## Context

`docs/CONTRACTS.md §20.3.1` requires `recordNonFatal` for every `UnexpectedError` and every poisoned
sync transition, forbids it for the expected `AuthError` leaves, connectivity-only `RemoteError` codes
and validation warnings, and requires a fixture that asserts the call sites. Before `E4-04` two
production sites existed: the graph binds the tombstone purge's `onFailure` and the sync engine's
`onPoisoned` to the reporter. Every production `UnexpectedError` is created in `:core:sync` today —
the sync cycle, the stale-`SYNCING` reset and the tombstone purge — and already reaches the reporter
there; the database and integration boundaries map failures to typed errors.

`docs/TECHNICAL_PLAN.md §4` lets neither feature `presentation` nor feature `data` depend on
`:core:crash`. `:shared` may.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. At the `:shared` chokepoints where an `AppError` becomes a `UiMessage`, plus the two existing sync and purge sites | Few sites and one fixture; every future `UnexpectedError` that reaches the UI is recorded without each producer remembering to | An `UnexpectedError` that never reaches a message and is not created in `:core:sync` is not recorded |
| B. Inside each repository or use case | Records at the producer | Many sites; feature `data` cannot reach `:core:crash` without a `§4` change; recording at every layer risks counting one failure twice |

## Decision

The selected option is **A**, realised as follows:

- The existing `:core:sync` bindings stay; they record `UnexpectedError` and poisoned transitions at
  the moment they are created.
- `NonFatalReporting`, internal to `:shared`, is the single policy: it records only `UnexpectedError`,
  with the single field `code`.
- The session chokepoint is the one conversion `toSessionUiMessage(nonFatals)` used by
  `SessionStateHolder` and `AccountDepartureFlow`.
- For the holders `§4` keeps away from `:core:crash` — the vehicle and fuel-entry holders — and for
  `SettingsStateHolder`, the chokepoint is the graph boundary: the graph places
  `NonFatalReportingVehicleRepository`, `NonFatalReportingFuelEntryRepository` and
  `NonFatalReportingSettingsRepository` outermost on the repositories it hands them, so every error
  those holders can convert passes the policy once.
- `SyncStateHolder` and the vehicle-list refresh are not chokepoints: they show a `SyncController`
  result whose `UnexpectedError` was recorded where it was created.
- Crash fields pass the closed `CrashFieldAllowlist` of `:core:crash` (`code`, `cycleId`,
  `entityType`, `syncState`, `syncTrigger`, `consumptionInvalidReason`, `confirmation`). The Firebase
  reporter sends the recorded error's code under its own key `errorCode`, sets every allowlisted key on
  every report (blank when absent), never sets a user identifier and records a throwable whose message
  is the code alone.

## Consequences

### Positive

- Every `UnexpectedError` that reaches the UI is recorded exactly once, and the negative rules live in
  one function.
- No change to `docs/TECHNICAL_PLAN.md §4` and no change to the Swift-facing surface.

### Negative

- A background failure that is neither shown nor created in `:core:sync` is not recorded; a future
  producer of such an `UnexpectedError` must call the reporter itself.
- Two decorator layers join the vehicle, fuel-entry and settings repository chains.
- Every non-fatal report carries the same exception type, so Crashlytics groups them by stack; the
  `errorCode` key distinguishes them.
- Provider custom keys are process-global: a later fatal crash carries the allowlisted keys of the
  last non-fatal report, and two non-fatals recorded concurrently can interleave their keys.

### Constraints Introduced

- A new `AppError`-to-`UiMessage` conversion in `:shared` uses `toSessionUiMessage(nonFatals)` or
  receives a decorated repository.
- A new repository the graph hands to a holder is decorated outermost.
- A new crash field key requires a `§20.3.1` amendment.

## Verification

- `NonFatalReportingTest`, `SessionNonFatalReportingTest`, `NonFatalReportingRepositoriesTest`,
  `NonFatalReportingAppGraphTest`.
- `CrashFieldAllowlistTest` and `FirebaseCrashReporterTest`.
- Existing: `DefaultSyncControllerTest` and `TombstonePurgeAppGraphTest` for the `:core:sync`
  bindings.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-243`)
- `docs/CONTRACTS.md §6`, `§17`, `§20.3.1`
- `docs/TECHNICAL_PLAN.md §4`
- `docs/adr/0023-firebase-crashlytics.md` (`D-21`)
