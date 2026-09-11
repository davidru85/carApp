# Agent Handoff — E1-14

## Story

`E1-14 - FuelEntryStateHolderTest Kotlin/Native Timeout Flake`

## Ready Check

- Backlog story: `docs/BACKLOG.md`, E1-14, size S; Ready.
- Acceptance criteria reviewed: bounded explicit graph-backed state expectations; reusable helper;
  forced-starvation regression fixture; more than 17 repeated Apple-silicon runs; audit every shared
  graph-mounting test; test-only implementation with no production, schema, contract, architecture
  or decision change.
- Dependencies checked: E1-12 and its handoff (ordered collector teardown), E2-03 and its handoff
  (original timeout evidence), and the Android recurrence recorded in the E1-14 backlog.
- Decisions checked: accepted D-56 (test factory), D-89 (database ownership), D-105 (continuity) and
  D-106 (bootstrap). Pending D-149/D-150 concern unrelated backend stories and do not block E1-14.
- Normative sections reviewed: AGENTS.md in full; SPECIFICATION §11 TDD; CONTRACTS §14 presentation,
  §18 checks and §20.10 holder lifecycle; DECISION_BOARD rows above; CONTRIBUTING conventions.
- Expected verification: focused helper RED on `:shared:testAndroidHostTest`; shared tests on Android
  host and iosSimulatorArm64; at least 30 forced repetitions on each target on this Apple-silicon
  host; shared lint; complete non-instrumented command from AGENTS.md; ten required CI jobs.
- Human review gates identified before work: no gated implementation path/topic; updating AGENTS.md
  repository status would require owner review. PR creation is authorised; merge is not requested.
- Rule 0 acknowledged: all owner conversation uses Spanish (es-ES); repository artifacts use
  technical English.

## In-Progress Checkpoint

- Date: 2026-09-11.
- Branch and base: `story/E1-14-bounded-state-expectations`, origin/main `eb52daf`.
- Current phase and latest commit: bounded-expectation cycle RED `e8cb797`, GREEN `9bcec1c`,
  REFACTOR `001ab63`. The second cycle is GREEN, after RED `f93c37e`; this checkpoint records GREEN.
- Push and pull-request status: not pushed; no PR.
- Completed since the previous checkpoint: extracted the deadline constant, documented collection
  context and cancellation, formatted all migrated calls. Investigated two diagnostic failures.
- Verification evidence and known failures: the initial bound-only batch passed 24 complete
  iterations and then failed on Android at `saveFullEntry`, with a loaded but empty list. An
  uncommitted UnconfinedTestDispatcher experiment passed 28 Android and 27 Native iterations,
  then Native `litersAndPriceDeriveTotalCostWhileTyping` reported `litersScaled=null`,
  `pricePerLiterScaled=1789`, `totalCostMinor=null`. This is lost input, not slow SQLite work.
  UnconfinedTestDispatcher still allows undispatched continuation delivery and does not serialize
  fixture main work. That experiment was removed; its evidence is preserved in this handoff.
  The completed helper behavior is green, but the fuel fixture needs its own deterministic
  regression and scheduler refactoring before E1-14 can be submitted.
- Open decisions or blockers: no owner decision. Production uses a confined main dispatcher;
  the test-only unconfined fixture violates that premise. No production change is planned.
- Second RED evidence: `graphFixtureWorkWaitsForTheCallerTestScheduler` compiled and failed:
  expected no work before advancing the scheduler, observed `[main, io, default]`.
  The fixture factory extraction is behavior-preserving; the test constructs dependencies without
  opening a database, so its failure is deterministic and independent of SQLite timing.
- Second GREEN evidence: StandardTestDispatcher(testScheduler) now queues all fixture dispatchers.
  `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test :shared:ktlintCheck
  :shared:detekt` passed: Android host 157 tests, Native 165 tests, no failures/skips.
- Exact next step: commit the second GREEN, finish fixture documentation and execute 30 forced
  repetitions of the final code before the complete repository verification and REFACTOR commit.

## Scope Completed

- Added `Flow<T>.awaitState(expectation, timeout, predicate)` in shared commonTest, with a default
  five-second real-time deadline, a mandatory description and last-emission diagnostics.
- Kept collection on the caller context and cancellation structured; no production dispatcher changed.
- Migrated all 37 raw predicate flow waits in seven files, including direct database settings and
  auth-flow waits adjacent to graph-backed holder tests.
- Added seven focused helper tests, including two independently bounded starvation fixtures.
- Audited all 11 graph-mounting test files plus the directly constructed adoption-failure holder.

## Acceptance Evidence

- Both forced-starvation fixtures compiled and failed against the raw `first(predicate)` RED helper.
  GREEN uses a 20 ms expectation deadline and produces its diagnostic assertion before the independent two-second
  outer bound. One fixture also proves the collector has stopped before the assertion is returned.
- Helper tests cover first matching value, matching null, a virtual one-day delayed emission,
  cancellation propagation/collector completion, and preservation of the original upstream cause.
- Shared GREEN reports: Android host 156 tests; iOS simulator 164 tests; 0 failures and 0 skipped.
- Audit (paths below are relative to `shared/src/commonTest/kotlin/com/ruizurraca/carapp/`):

| Audited file | Finding and treatment |
|---|---|
| `FuelEntryStateHolderTest.kt` | 14 waits migrated, including graph bootstrap and shared save helpers; harness teardown retained. |
| `VehicleFormStateHolderTest.kt` | 6 save-completion state waits migrated. |
| `VehicleListStateHolderTest.kt` | 4 save/list/recovery waits migrated. |
| `SwiftAppGraphLifecycleTest.kt` | 2 saved-vehicle waits migrated; Swift-owned lifecycle retained. |
| `LocalOwnerAdoptionTest.kt` | 6 list/save/session/auth waits migrated. |
| `LocalOwnerAdoptionTriggerTest.kt` | 2 auth waits migrated, including the graph-backed fuel-write case. |
| `AppGraphTestHarnessTest.kt` | No state predicate wait; deliberate awaitCancellation collector is cancelled and joined by the harness. |
| `AppGraphCloseTest.kt` | No state predicate wait; lifecycle/bootstrap tests use explicit cancellation and immediate assertions. |
| `AppGraphContractTest.kt` | No wait; checks the returned holder types. |
| `BuildAppGraphTest.kt` | No wait; checks graph dependency mapping. |
| `SessionStateHolderTest.kt` | Graph-backed session state is inspected after scheduler advancement; no predicate wait. |
| `LocalOwnerAdoptionFailureTest.kt` | Additional audit of a database-backed holder constructed without an AppGraph: 3 error/retry/auth waits migrated. |

The remaining shared test files construct coordinators or holders with controlled doubles rather
than mounting a graph, or cover static contracts/platform adapters. None imports `flow.first`.
The only remaining predicate `first` in shared test sources is encapsulated in `FlowExpectation.kt`.
Finite `flowOf(...).toList()` in the odometer-suggestion test is not an unbounded state expectation.
The pre-existing non-flow adoption polling loops remain outside this state-emission fix.

## Out of Scope / Not Done

- Production timing, holders, graph, database and schema changes; E1-17 iOS host flakes.

## Files Changed

- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/FlowExpectation.kt` and `FlowExpectationTest.kt`.
- The seven migrated files listed in the audit above.
- `docs/handoff-E1-14.md`; repository status and historical-follow-up pointers will be updated after
  stability verification.

## Decisions Made

- The owner's explicit one-push-after-REFACTOR instruction supersedes the default phase-by-phase
  push cadence for this story. Separate RED, GREEN and REFACTOR commits are retained.
- No technical decision introduced: E1-14 already requires a reusable bounded expectation in tests.
  No new library, product behavior or normative policy is needed.

## Verification Run

- REFACTOR lint: `./gradlew :shared:ktlintCheck :shared:detekt` passed after formatting.
- GREEN: `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test` passed.
- RED: `./gradlew :shared:testAndroidHostTest --tests 'com.ruizurraca.carapp.FlowExpectationTest'`
  failed as expected (7 tests, 2 assertion failures caused by missing expectation timeout).
- `./gradlew --version`: Gradle 9.7.1, JDK 21.0.11, macOS aarch64.

## Contract Impact

- No contract changes.

## Decision Board Impact

- No decision changes.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [ ] Entry appended

## Risks or Follow-ups

- Repetition establishes observed stability, not a proof that arbitrary host starvation is impossible.

## Human Review Gate

- Applies: `AGENTS.md` is a gated path. The change only updates repository status; the owner
  reviews and merges the PR. No product contract or runtime path is changed.
