# ADR-0252 / D-251 - Gate Cold Start on a Returning User's Populated Vehicle List

## Status

Accepted

The owner selected option A on 2026-10-24, with the returning-user state produced by option R-a.

## Context

`docs/SPECIFICATION.md §11` requires a cold start under 2 s "to first content", and
`docs/versions-matrix.md` measures "to the first frame that shows real content, not the splash". No
document said which screen that is. A returning user sees the vehicle list; a first-run user sees the
welcome screen; every launch first draws a waiting indicator while the session resolves.

A `LOCAL_OWNER` session is not restored by a cold start: `SessionPhase.LOCAL` exists only in the
published state of `SessionStateHolder`, and a device without a Firebase user starts at
`AuthState.SignedOut`, which both hosts route to the welcome screen. Data written under the sentinel
therefore never produces a populated list at cold start.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Gate on a returning user's populated vehicle list; record the first-run welcome screen as a second figure | Measures what a returning user waits for; the second figure covers first run | Needs a persisted session and a populated database on the measurement device |
| B. Gate on the first non-spinner frame, whichever screen it is | No session setup | A returning user's list may arrive later than that frame, so the gate can pass while the user still waits |

Ways to produce the returning-user state on a benchmark build:

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| R-a. Seed under `LOCAL_OWNER` offline, open the app once online so `D-124` acquires an anonymous identity and adoption pushes the rows to the development project, then measure offline | The measured state is the common production state: a persisted anonymous session, adopted rows and an empty outbox | About 1,001 document writes reach the development project once; the benchmark build must be registered with the development project |
| R-b. Stay offline throughout | Nothing reaches Firebase | The cold start of a sentinel device shows the welcome screen, so option A cannot be measured |
| R-c. Sign in anonymously on an empty database, then seed offline under that identity | Nothing reaches Firestore | Seeding enqueues 1,001 outbox rows and every measured launch carries them |

## Decision

The selected options are **A** and **R-a**.

- The cold-start gate ends at the first frame of the **populated vehicle list** of a returning user.
  The second figure ends at the first frame of the **welcome screen** of a signed-out device with an
  empty database.
- Both hosts mark that moment with a platform marker that ships in production builds: Android calls
  `ReportDrawn()` (`androidx.activity.compose`) where the populated list and the welcome screen first
  compose, so Macrobenchmark's `timeToFullDisplayMs` ends there; iOS starts one MetricKit extended
  launch task in `carAppApp.init()` and finishes it when the first vehicle row, the empty list, the
  unreadable list or the welcome screen first appears, so
  `XCTApplicationLaunchMetric(waitUntilResponsive: false)` ends there.
- The returning-user state is produced by `D-254`'s seeding followed by one online launch that adopts
  the seeded rows into an anonymous identity of the development project. Measurements then run with
  the device offline. The protocol ends by deleting that anonymous account in-app (`D-23`).

## Consequences

### Positive

- The gate measures the wait a returning user actually has.
- The production markers also feed Play vitals and MetricKit with the same definition.

### Negative

- One adoption push writes about 1,001 documents to the development project per protocol run.
- The markers are production code on both hosts.

### Constraints Introduced

- The benchmark builds use the development registration (`D-252`, `D-253`) and never the production
  project.
- A first-content marker MUST NOT move off the populated list, the welcome screen, the empty list or
  the unreadable list without a new decision.

## Verification

- `PerformanceHarnessContractTest.firstContentIsReportedOnBothHosts` pins both markers.
- `docs/handoffs/E4-03.md` records the two figures per platform, with device, OS and date.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-251`)
- `docs/SPECIFICATION.md §11`; `docs/versions-matrix.md` performance measurement baselines
- `docs/CONTRACTS.md §11.2`-`§11.4` (`D-124` local owner adoption)
- `docs/runbooks/performance-measurement.md`
