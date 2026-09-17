# Agent Handoff - E3-08

## Story

`E3-08 - App Graph and Firebase Wiring - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — `E3-08` completes the Kotlin-facing `AppGraph`, the
  Swift-facing `SwiftAppGraph` and `:wiring:firebase` in place, on top of the provider-free
  `buildAppGraph` and the single `createSwiftAppGraph(isDebugBuild)` declaration that `E0-07`
  already owns.
- [x] Acceptance criteria reviewed — the seven criteria of the story: surface parity with
  `docs/CONTRACTS.md §11.6` and `§20.10`; `:shared:testing` factory parity; `:wiring:firebase` as
  the only Firebase-constructing module; the checkable "product logic" definition of
  `docs/TECHNICAL_PLAN.md §4`; Koin-free graph construction from tests; a Swift facade that
  exposes a sync state holder rather than `SyncController` and owns its scopes; and cached,
  idempotent, close-guarded `SwiftAppGraph` holder factories.
- [x] Dependencies checked — `E0-07` owns the provider-free `buildAppGraph` and the framework
  topology (`D-58`/`D-59`); `E3-03` merged on 2026-09-17 through pull request #69 and delivered
  `SyncController`, the `AppGraph.syncStateHolder(scope)` member and the real sync wiring;
  `E3-17` merged on 2026-09-17 through pull request #70 and delivered the `D-172` close contract.
  `E3-06`, `E3-01`, `E3-02` and `E3-17` are complete. No `Proposed` or `Pending` decision gates
  this story: `D-149`, `D-150` and `D-173` gate `E3-15`, `E3-16` and `E3-18`, none of which this
  story touches.
- [x] Decisions checked — `D-3` (Koin is wiring-only), `D-16` (module rules are a custom Gradle
  check with a failing fixture per rule), `D-27`/`D-56` (the test factory lives in
  `:shared:testing`, consumed from `commonTest` only), `D-38`, `D-43`/`D-44` (explicit provider
  registry), `D-45`, `D-58`/`D-59` (composition root and provider port), `D-86`/`D-89` (Swift
  facade scope ownership and the single `DatabaseHandle`), `D-108`/`D-126` (native providers
  injected at the host boundary), and `D-172` (close releases the handle after graph work). All
  are `Accepted`.
- [x] Normative sections reviewed — `docs/CONTRACTS.md §11.6`, `§15`, `§18` (assertions 7, 13 and
  14, plus the new 34), `§20.3`, `§20.10`; `docs/TECHNICAL_PLAN.md §3`, `§4` and `§4.1`;
  `docs/SPECIFICATION.md §8.2`, `§8.5` and `§11`; `docs/SECURITY.md`; and the `E0-04`, `E0-07`,
  `E3-03`, `E3-06` and `E3-17` handoffs.
- [x] Expected verification identified — focused RED/GREEN fixtures for each new rule;
  `:build-logic:convention:test`, `architectureCheck`, `contractCheck`, `:shared` Android-host and
  `iosSimulatorArm64` tests, the provider-free graph proof, the Objective-C golden-header diff and
  the canonical full CI command.
- [x] Human review gates identified before work — `docs/CONTRACTS.md` and `docs/adr/**` are gated
  paths, and the Swift-facing API surface plus the module dependency rules are gated topics, so
  this story requires owner review before merge. `E3-08` is not one of the gated stories of
  `AGENTS.md`.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## In-Progress Checkpoint

- Date: 2026-09-18
- Branch and base: `story/E3-08-app-graph-and-firebase-wiring`, based on `origin/main` at `588ad00`
  (the `E3-17` merge).
- Current phase and latest commit: complete. RED `4c51b55`, GREEN `03f5f3f`, REFACTOR `5b48c4b`,
  records `1a19448`.
- Push and pull-request status: pushed to `origin/story/E3-08-app-graph-and-firebase-wiring`; pull
  request #71 is open against `main` and awaiting the owner's gated review. All ten required checks
  are green on run `35288272924`: `android-assemble`, `android-instrumented-tests`,
  `architecture-check`, `contract-check`, `detekt`, `ios-simulator-build`, `ktlint`,
  `objc-header-golden-check`, `provider-decoupling` and `shared-tests`.
- Completed since the previous checkpoint: the two architecture rules, the Swift surface contract
  (assertions 14 and 34), the `§20.10` clarification, `D-178` through `D-180` with their ADRs and
  the four mirror tables, the backlog and `AGENTS.md` reconciliation, and the story handoff.
- Verification evidence and known failures: the canonical CI command passes locally; mutation
  evidence for every new rule is recorded under "Acceptance Evidence" below. No known failure.
- Open decisions or blockers: none. `E3-08` introduced no open decision.
- Exact next step: push the branch and open the pull request against `main`.

## Scope Completed

- Executed the `:wiring:firebase` "product logic" rule of `docs/TECHNICAL_PLAN.md §4` as a
  declaration-shape check (`D-178`), the rule `E0-04` recorded as unowned because it needs a
  Kotlin declaration parser and the module.
- Added the source rule that keeps `com.ruizurraca.carapp.integration.` out of every module other
  than `:wiring:firebase` and `:integration:*` (`D-179`).
- Implemented `docs/CONTRACTS.md §18` assertion 14 and added assertion 34, both in a new
  `SwiftSurfaceContract` registered on `contractCheck` (`D-180`).
- Closed the live divergence `E3-03` shipped: `docs/CONTRACTS.md §20.10` now declares
  `syncStateHolder(scope)` on the Kotlin-facing `AppGraph`, which the interface already had.
- Extended the `SwiftAppGraph` lifecycle coverage to the cache-key and close-guard criteria the
  story names, and the graph-construction coverage to the Koin-free criterion.
- Registered `D-178`, `D-179` and `D-180` with ADR-0179, ADR-0180 and ADR-0181 and the four
  mirror tables.
- Reconciled `docs/BACKLOG.md`, `AGENTS.md`, `docs/CONTRACTS.md` and this handoff; appended the
  project-log entry.

## Acceptance Evidence

Each criterion, and the evidence that proves it.

**1. The surfaces match `§11.6` and `§20.10`.** `contractCheck` assertions 14 and 34 both report
`PASS` on the real repository. A read-only audit of every block of both sections against the real
declarations and the committed golden header found the surface otherwise conformant: the
Kotlin-facing `AppGraph`'s eight members, `createSwiftAppGraph`, all ten `SwiftAppGraph` members,
all six state-holder classes' contract-listed members, all eight `UiState` data classes and their
enums, and the whole `§11.6` block (`AppGraphDependencies`, `AppProviders`, `buildAppGraph`,
`testAppGraphDependencies`) match member for member, in order, with identical types and
nullability. The two extras that remain — `FuelEntryFormStateHolder.isLoading` and
`observeSaveCompletions()` — are `@HiddenFromObjC`, absent from the golden header, and are the
Kotlin-side seam the Android host consumes.

**2. `:shared:testing` factory parity.** `contractCheck` assertion 13 reports `PASS` with
`16 parameters in canonical order; every parameter defaulted`.

**3. Only `:wiring:firebase` constructs Firebase implementations.** Mutation evidence: with
`:wiring:firebase`'s auth edge switched from `implementation` to `api` and `:composition:ios`
naming `FirebaseAuthClient` in its own source, the module compiles the reference but
`architectureCheck` fails with `:composition:ios: firebase-implementation-outside-wiring` while
`contractCheck` assertion 7 stays green — the golden header cannot see a type that is named but
never exported, which is the hole `D-179` closes. Restoring the edge and the source restores a
green build.

**4. Every top-level declaration in `:wiring:firebase` is a Koin module, an abstraction factory or
a platform initialiser.** Mutation evidence: appending
`internal data class StrayMapping(val id: String)` to `FirebaseAppProviders.kt` makes
`architectureCheck` fail with `:wiring:firebase: wiring-product-logic`; removing it restores a
green build. The fixture asserts twelve rejected shapes and eight accepted ones, including every
shape the real module uses.

**5. Tests build the graph without starting Koin.** Koin is not a dependency of any module
(`grep` over every `build.gradle.kts` finds none). `TestAppGraphDependenciesTest` now constructs
the graph through `buildAppGraph` and `testAppProviders(...)` and serves a vehicle-list, session
and sync state holder from it, which would throw at the first container access if graph
construction needed one. The previous assertion in that file only inspected a flag.

**6. The Swift facade exposes a sync state holder, not `SyncController`, and owns the scopes.**
`SwiftAppGraph.kt` contains no `SyncController` reference and asserts this at the source
(assertion 14). `SwiftAppGraphLifecycleTest.releaseCancelsTheCachedCreationFormAndTheNextFlowCreatesANewVehicle`
(from `E1-07`) already proves a released holder's scope is cancelled.

**7. Holders are cached per argument set and throw after `close()`.**
`SwiftAppGraphLifecycleTest.identicalArgumentsReuseTheSameCachedHolderAndDifferentOnesDoNot`
asserts identity for repeated calls and distinctness across keys for the list, session, sync and
per-vehicle form holders; `everyFactoryThrowsAfterCloseExceptCloseItself` asserts
`IllegalStateException` from all six factories after `close()` and that a second `close()` is
idempotent.

**Assertion 14 and 34 mutation evidence.** Each mutation was applied to the real repository, the
check run, and the file restored:

| Mutation | Result |
|----------|--------|
| `vehicleFormStateHolder(vehicleId: String? = null)` on `SwiftAppGraph` | assertion 14 FAILs, naming the member |
| `scope: CoroutineScope = CoroutineScope(Job())` on `AppGraph` | assertion 14 FAILs |
| `selectVehicle(vehicleId: String? = null)` on `VehicleListStateHolder` | assertion 14 FAILs, naming file and class |
| `requestDelete(entryId: String = "")` on `FuelEntryListStateHolder` | assertion 14 FAILs |
| `syncController()` added to `SwiftAppGraph` | assertion 14 FAILs, `SwiftAppGraph references SyncController` |
| `withCallback(callback: (Int) -> Unit = {})` added to `AppGraph` | assertions 14 and 34 FAIL |
| `syncStateHolder(scope)` removed from `§20.10` | assertion 34 FAILs, naming the member |

The last two mutations are the regression tests for the two defects found during this story's own
refactor: a `[^)]*` parameter capture truncated a list at a function-typed parameter and hid a
later default, and a string-literal default escaped a `=\s*\w` heuristic. Both are fixed and both
mutations now fail the check.

**Canonical verification.** The full CI command of `AGENTS.md` passes locally: 642 actionable
tasks, `BUILD SUCCESSFUL`.

## Out of Scope / Not Done

- **The Konsist fixture `docs/CONTRACTS.md §20.10` requires** to ban `PostWriteDebounce`,
  `ConnectivityRecovered` and `Periodic` from any `iosMain` call site of
  `SyncStateHolder.requestSync`. `D-16` assigns package-level rules to Konsist, `E1-07` introduced
  it for the feature-layer rows, and Konsist is not yet a dependency of any module. No iOS call
  site of `requestSync` exists today (`iosApp/` and `iosMain` contain none), so no rule of the
  contract is contradicted; the fixture belongs with the story that first adds one. This is a
  SHOULD-level deferral, recorded here as its reason.
- `E3-04` owns enforcing `SYNC_POST_WRITE_DEBOUNCE_MS` and `SYNC_MIN_AUTOMATIC_INTERVAL_MS`; this
  story does not touch the trigger constants.
- `E3-09` owns `:integration:firebase-analytics` and `E4-04` owns
  `:integration:firebase-crashlytics`; `:wiring:firebase` keeps the no-op analytics tracker and
  the `CrashReporter` no-op already bound by `E0-08`.
- `E3-12` owns the permanent-account cross-device recovery proof.

## Files Changed

- `build-logic/convention/src/main/kotlin/.../architecture/ArchitectureChecker.kt` — the two new
  rules, their classification helpers and constants.
- `build-logic/convention/src/main/kotlin/.../contract/SwiftSurfaceContract.kt` — new; assertions
  14 and 34.
- `build-logic/convention/src/main/kotlin/.../contract/ContractCheck.kt` — registers it.
- `build-logic/convention/src/test/kotlin/.../architecture/ArchitectureCheckerTest.kt` — the two
  rule fixtures.
- `build-logic/convention/src/test/kotlin/.../contract/SwiftSurfaceContractTest.kt` — new; the
  regression that requires both assertions present and passing.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/SwiftAppGraphLifecycleTest.kt` — cache-key
  and close-guard coverage.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/TestAppGraphDependenciesTest.kt` — the
  Koin-free construction proof.
- `docs/CONTRACTS.md` — `§11.6` names assertion 34, `§18` declares it, `§20.10` declares
  `syncStateHolder(scope)`.
- `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`,
  `docs/adr/README.md` — `D-178` through `D-180`.
- `docs/adr/0179-…`, `docs/adr/0180-…`, `docs/adr/0181-…` — new ADRs.
- `docs/BACKLOG.md`, `AGENTS.md`, `docs/PROJECT_LOG.md`, this handoff.

## Decisions Made

- **`D-178` — `:wiring:firebase` product logic is a declaration shape.** Any function is admitted,
  a `val`/`var` only when `private`, a Koin `Module` by its type or initialiser, and every
  `class`/`interface`/`object`/`enum class`/`typealias`/`fun interface` is rejected at any
  visibility. Rejected alternatives: reject only non-private declarations and admit everything
  `private` (a private mapper is still a mapper, and the prose names mappers), and require an AST
  (a new build-logic dependency for one rule that the other sixteen line-based rules do not need).
  `fun interface` counts as an interface, because the `fun` there modifies a type declaration
  rather than introducing the abstraction factory `§4` admits. See ADR-0179.
- **`D-179` — integration implementations are named in `:wiring:firebase` only.** A source rule
  rejects any mention of `com.ruizurraca.carapp.integration.` outside `:wiring:firebase` and
  `:integration:*`. The direct case is already a compile error, so the rule earns its place on the
  transitive case a one-word `api` change opens, which the dependency graph and the golden header
  both miss. Rejected: tightening the architecture table to require `implementation` edges (needs
  a new configuration-aware dimension and still does not cover the composition root), and relying
  on the golden header (it cannot see a named-but-unexported type). See ADR-0180.
- **`D-180` — both `AppGraph` surfaces are guarded by parsing the contract.** Rejected: fixing the
  divergence and implementing assertion 14 alone (closes it without preventing it, and `E3-03`
  showed it happens), and generating `§20.10` from the sources (the contract is a human-reviewed
  normative document and is not assembled from a build step anywhere else). See ADR-0181.
- **TDD order exemption used for architecture-rule fixtures**, exactly as permitted by
  `docs/SPECIFICATION.md §11`: the fixtures and their checker implementation were completed in the
  same RED/GREEN cycle, and each forbidden shape has an executable failing fixture (`D-16`).
- **TDD order exemption used for the contract-check rules**, for the same reason: assertion 14 and
  assertion 34 are checks over committed repository files, and their failing fixtures are the
  mutation runs recorded above rather than a test that could fail before the check exists.
- **SHOULD-level deferral: the `§20.10` Konsist fixture.** Stated with its reason under Out of
  Scope above.

## Verification Run

- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test --stacktrace` — `BUILD SUCCESSFUL`, 642 actionable tasks.
- `./gradlew architectureCheck` — `16 rules from docs/TECHNICAL_PLAN.md §4, 23 modules`; passes.
- `./gradlew contractCheck` — every assertion `PASS`, including the new 14 and 34, with no
  `PENDING` line.
- `./gradlew :build-logic:convention:test` — 34 tests, 0 failures.
- `./gradlew -Pcarapp.excludeFirebaseProviders=true :shared:testAndroidHostTest` — passes; the
  provider-free graph is unaffected.
- Mutation runs recorded in the table under "Acceptance Evidence" — every new rule and assertion
  fails on a real injected defect and passes once the file is restored.
- `git status` after each mutation run — clean, with the working tree restored.

## Contract Impact

- `docs/CONTRACTS.md §20.10` — the Kotlin-facing `AppGraph` block gains
  `fun syncStateHolder(scope: CoroutineScope): SyncStateHolder`, the member `E3-03` shipped. This
  is a representational clarification of an already-implemented interface, not a behaviour change,
  and it is the contract being corrected to match the code rather than the reverse.
- `docs/CONTRACTS.md §11.6` — the `AppGraph` rule names assertion 34 as the check that keeps the
  block and the interface equal.
- `docs/CONTRACTS.md §18` — assertion 34 is declared, with the reason no other assertion can
  replace it.

## Decision Board Impact

- Added `D-178`, `D-179` and `D-180`, with ADR-0179, ADR-0180 and ADR-0181 and identical rows in
  `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`. `contractCheck`
  assertions 2 and 3 report `181 decisions` and `181 ADRs`, both `PASS`.

## Shared-Write Modules Touched

- None. `:core:database` is unmodified.

## Project Log Entry

- [x] Entry appended on 2026-09-18.

## Risks or Follow-ups

- **The `§20.10` Konsist fixture is deferred** to the story that first adds a `requestSync` call
  site under `iosMain`. Recorded under Out of Scope with its reason.
- **`E3-03` was corrected here, not there.** The `syncStateHolder` divergence was introduced by
  `E3-03` and merged, so this story corrected the contract rather than the code. The guard that
  would have caught it at the time is assertion 34, which did not exist; it does now.
- **The declaration classifier is textual.** A declaration shape the regular expression cannot see
  fails open. Bounded by the rule's direction and by the two modules it guards being small: an
  unparseable `AppGraph` or `SwiftAppGraph` reports that it could not be parsed rather than passing.
- **`docs/DECISION_BOARD.md` and `AGENTS.md` are the authoritative state.** This handoff preserves
  what was observed on 2026-09-18 at `story/E3-08-app-graph-and-firebase-wiring`.

## Human Review Gate

- Applied: `docs/CONTRACTS.md` and `docs/adr/**` are gated paths, and the Swift-facing API surface
  and the module dependency rules are gated topics. The pull request requires the owner's review
  before merge.
