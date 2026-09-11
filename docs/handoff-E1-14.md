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
- Branch and base: `story/E1-14-bounded-state-expectations`, origin/main `eb52daf` (merged PR #65).
- Current phase and latest commit: implementation and local verification complete. First cycle:
  RED `e8cb797`, GREEN `9bcec1c`, REFACTOR `001ab63`. Second cycle: RED `f93c37e`, GREEN `8430c91`;
  second REFACTOR `52d58f1`. The latest commit is this documentation-only PR continuity checkpoint.
- Push and pull-request status: all six TDD phase commits were pushed together, then PR #66 was
  created: https://github.com/davidru85/carApp/pull/66. This documentation-only checkpoint is also
  pushed; the PR stays open and is not merged.
- Completed since the previous checkpoint: final graph fixture uses StandardTestDispatcher tied
  to runTest; all 30 final repetitions passed; the full repository command passed; audit, acceptance
  evidence, historical failed attempts and project log are recorded. PR #66 has been created and
  its URL is now linked from the backlog and repository status.
- Verification evidence and known failures: final code passed 30/30 complete forced runs per target
  on Apple Silicon (157 Android-host tests and 165 Native-simulator tests each, no failures/skips).
  Full non-instrumented verification passed: 636 tasks, 41 executed, 595 up-to-date. Contract check
  reports all assertions PASS, 168 aligned decisions/ADRs, two unrelated pending decisions tracked
  by their own stories, and no PENDING executable assertions. Earlier failed stability experiments
  are retained under Acceptance Evidence; neither is counted as final-code evidence.
- Open decisions or blockers: no technical decision or local verification blocker. Owner review
  and the PR's ten required CI checks remain before merge. E1-14 is implemented, not yet merged.
- CI checkpoint: initial run https://github.com/davidru85/carApp/actions/runs/34578260010 on code
  HEAD `52d58f1` has ktlint, detekt and android-assemble green; the other seven jobs were running
  at observation time, with no failed required check. The documentation-only push triggers a new
  run on identical test/product code; the PR is the live source for that final run's status.
- Exact next step: owner reviews PR #66 and its ten required checks before merge. The agent has
  completed implementation, local verification, push and PR creation; no merge was requested.

## Scope Completed

- Added `Flow<T>.awaitState(expectation, timeout, predicate)` in shared commonTest, with a default
  five-second real-time deadline, a mandatory description and last-emission diagnostics.
- Kept collection on the caller context and cancellation structured; no production dispatcher changed.
- Confined the fuel graph fixtures to StandardTestDispatcher(testScheduler), including persisted
  settings setup. The graph-bootstrap test already used that dispatcher. A deterministic fixture
  test prevents replacing it with an eagerly executing unconfined dispatcher.
- Migrated all 37 raw predicate flow waits in seven files, including direct database settings and
  auth-flow waits adjacent to graph-backed holder tests.
- Added seven focused helper tests, including two independently bounded starvation fixtures,
  and one deterministic graph-fixture scheduling regression.
- Audited all 11 graph-mounting test files plus the directly constructed adoption-failure holder.

## Acceptance Evidence

- Both forced-starvation fixtures compiled and failed against the raw `first(predicate)` RED helper.
  GREEN uses a 20 ms expectation deadline and produces its diagnostic assertion before the independent two-second
  outer bound. One fixture also proves the collector has stopped before the assertion is returned.
- Helper tests cover first matching value, matching null, a virtual one-day delayed emission,
  cancellation propagation/collector completion, and preservation of the original upstream cause.
- Final stability: 30/30 forced complete runs per target on macOS 26.6.2 aarch64 (Apple Silicon),
  zero failures and zero skips. Each run executed 157 Android-host tests and 165 Native-simulator
  tests, including the eight new regressions. This exceeds the historical approximately 1-in-17
  occurrence rate; it is observed stability, not a claim that arbitrary machine starvation is impossible.
- Final shared GREEN reports: Android host 157 tests; iOS simulator 165 tests; 0 failures and 0 skipped.
- `graphFixtureWorkWaitsForTheCallerTestScheduler` failed before the dispatcher change with
  `expected: [] but was: [main, io, default]`. After the fix, no graph work runs until `runCurrent()`,
  and then all three dispatchers complete their queued work.
- Historical stability attempts, deliberately retained rather than discarded:

| Version | Android host | Native simulator | Diagnostic |
|---|---|---|---|
| Bounded waits only | 24 pass, 1 fail | 24 pass; iteration 25 not executed | Empty fuel list after save; five-second assertion fired. |
| Uncommitted UnconfinedTestDispatcher experiment | 28 pass, 0 fail | 27 pass, 1 fail | Liters overwritten to null while price remained 1789; five-second assertion fired. |

Unconfined execution can resume initialization after SQLite suspension on a worker thread while
form intents execute on the test thread. Both initialization and edits copy mutable form input,
so the fixture permits lost updates that the product's confined main dispatcher prevents.
Merely adding a timeout or substituting UnconfinedTestDispatcher did not remove that test race.
The final fixture uses StandardTestDispatcher on the caller scheduler and leaves product code intact.
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
- `AGENTS.md`, `docs/BACKLOG.md`, `docs/PROJECT_LOG.md` and `docs/handoff-E1-14.md`.

## Decisions Made

- The owner's explicit one-push-after-REFACTOR instruction supersedes the default phase-by-phase
  push cadence for this story. Separate RED, GREEN and REFACTOR commits are retained.
- No technical decision introduced: E1-14 already requires a reusable bounded expectation in tests.
  No new library, product behavior or normative policy is needed.
- Two TDD cycles were needed: the first makes unbounded waits diagnostic; repetition then exposed
  the independent test-fixture scheduling defect, which received its own deterministic RED test,
  GREEN implementation and REFACTOR verification. All cycle commits remain local until the final
  push requested by the owner. No TDD exemption or force push is used.

## Verification Run

- First RED: `./gradlew :shared:testAndroidHostTest --tests
  'com.ruizurraca.carapp.FlowExpectationTest'` — 7 compiled tests, 2 expected failures. The unbounded
  helper reached the independent two-second deadline instead of returning the required assertion.
- First GREEN: `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test` — passed
  (156 Android-host / 164 Native tests before the scheduling regression was added).
- First REFACTOR: `./gradlew :shared:testAndroidHostTest --tests
  'com.ruizurraca.carapp.FlowExpectationTest' :shared:ktlintCheck :shared:detekt` — passed. The
  separate fixture flake found during repetition is recorded above and was not waved through.
- Second RED: `./gradlew :shared:testAndroidHostTest --tests
  'com.ruizurraca.carapp.FuelEntryStateHolderTest.graphFixtureWorkWaitsForTheCallerTestScheduler'`
  — expected assertion failure, `expected: [] but was: [main, io, default]`.
- Second GREEN: `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test
  :shared:ktlintCheck :shared:detekt` — passed, 157 Android-host / 165 Native tests.
- Final REFACTOR stability: the following command was executed 30 consecutive times, stopping on
  any nonzero exit; every invocation returned zero. Both target reports were inspected after each
  successful invocation: 157 / 165 tests, no failures or skips, on every run.

```bash
./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test --rerun-tasks --quiet
```

- Full non-instrumented repository verification — BUILD SUCCESSFUL in 6s, 636 actionable tasks
  (41 executed, 595 up-to-date). Covers lint, coverage, architecture and its failing fixtures,
  contracts, Android assembly/unit tests, and the shared host/Native graph with the exact D-75
  exclusions. No PENDING contract assertions.

```bash
./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test
```

- Source audit: `rg -n 'kotlinx.coroutines.flow.first|\.first\s*\{' shared/src/commonTest
  shared/src/iosTest` — only the encapsulated call/import in `FlowExpectation.kt` remains.
- `git diff --check` — clean.
- CI: PR #66 triggered all ten required jobs; their live results are tracked in the PR.
  This test-only change does not alter the iOS application or Shared public framework surface.

## Contract Impact

- No contract changes.

## Decision Board Impact

- No decision changes.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [x] Entry appended

## Risks or Follow-ups

- The five-second bound is a real-time test expectation, not a product deadline. Under extreme host
  starvation, it can deliberately fail with a diagnostic assertion; it does not silently retry.
- E1-17 iOS host UI flakes and the historical D-89 production graph-close follow-up remain separate.
- Ordinary runtime warnings about Gradle 10 deprecations and expect/actual beta classes are unchanged.

## Human Review Gate

- Applies: `AGENTS.md` is a gated path. The change only updates repository status; the owner
  reviews and merges the PR. No product contract or runtime path is changed.
