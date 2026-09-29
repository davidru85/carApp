# Agent Handoff - E3-09

## Story

`E3-09 - Firebase Analytics Integration - M` (`docs/BACKLOG.md`).

Implement `:integration:firebase-analytics`, the Firebase-backed `AnalyticsTracker` from `E0-08`, bind
it in `:wiring:firebase`, and own the complete `docs/CONTRACTS.md §16.1` emission surface (`D-196`).

## Ready Check

- Backlog story: `E3-09` (`docs/BACKLOG.md`, story section and story index row, resized from `S` to
  `M` by this story under `D-196`).
- Acceptance criteria reviewed: the five original criteria plus the emission criterion this story
  added — every `AnalyticsEvent` leaf maps exhaustively to an event name and parameter set with no
  `else`; collection is disabled at startup and enabled only after an explicit opt-in, including on a
  fresh install, with a test that nothing is buffered while disabled; no forbidden payload of
  `§16.1` can be sent; only `:wiring:firebase` constructs the implementation and no Firebase type
  crosses the boundary; excluding the module leaves the app on the `:core:analytics` no-op; and every
  leaf is emitted from `:shared` orchestration with the `setUserProperties` cadence.
- Dependencies checked: `E0-08` (the `:core:analytics` abstraction, merged) is the hard prerequisite;
  `E3-06` (provider decoupling, merged) governs the no-op criterion; `E2-02`, `E2-03`, `E2-04`,
  `E2-05` (merged) supply the auth, onboarding and departure surfaces the emitters sit in.
- Decisions checked: none was `Proposed` or `Pending` for this story. Applies `D-10` (`Accepted`),
  `D-65`, `D-75`, `D-43`/`D-44`, `D-178`/`D-179`, `D-24`, `D-161`, `D-105`. This story introduced
  `D-196` to `D-199`.
- Normative sections reviewed: `docs/CONTRACTS.md §16.1`, `§20.9`, `§9.9`, `§18`, `§11.6`, `§14`,
  `§20.10`; `docs/SPECIFICATION.md §3.1`, `§7 F-1`, `§11`; `docs/TECHNICAL_PLAN.md §3`, `§4`, `§13`.
- Expected verification: the complete CI task command of `AGENTS.md`.
- Human review gates identified before work: `E3-09` is not a gated story, but the change touches the
  gated topics "logging and privacy rules" and "module boundaries" and the gated path `docs/adr/**`
  (plus `docs/CONTRACTS.md` and `docs/versions-matrix.md`). Merge is owner-reviewed.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every artifact it
  produces is in technical English.

## In-Progress Checkpoint

- Date: 2026-09-29.
- Branch and base: `story/E3-09-firebase-analytics-integration`, based on `main` at `02dffc54`.
- Current phase and latest commit: complete; the pull request's ten required checks are green.
- Push and pull-request status: pushed; pull request
  [#84](https://github.com/davidru85/carApp/pull/84) is open, mergeable, and all ten required checks
  passed on the head `3244c691` (run `36574307384`).
- Completed since the previous checkpoint: the whole story. `:core:analytics` gained the `§20.9`
  collapse and the closed provider-error taxonomy; `:integration:firebase-analytics` was created with
  the tracker, its narrow gateway and its tests; `:wiring:firebase` binds it and links the iOS
  product; `:core:database` gained the two read-only count queries; `:shared` gained the emission
  orchestrator, the two notification decorators and the session/onboarding events; the four decisions
  were recorded with ADRs and their four mirrors; two contract assertions and the fifth D-75
  exclusion were added.
- Verification evidence and known failures: the complete CI task command of `AGENTS.md` ran
  `BUILD SUCCESSFUL` locally, including `iosSimulatorArm64Test`, `koverVerify`, `architectureCheck`,
  `contractCheck`, `ktlintCheck` and `detekt`. `contractCheck` reports assertions 21, 37 and 38
  `PASS` and no `PENDING` assertion anywhere. The `objc-header-golden-check` diff is identical
  locally, the iOS app builds and links the new `FirebaseAnalytics` product locally, and the
  provider-free route (`-Pcarapp.excludeFirebaseProviders=true :shared:testAndroidHostTest
  :shared:iosSimulatorArm64Test`) was reproduced locally before pushing, and `:shared:testAndroidHostTest`
  was re-run with `--rerun-tasks` to rule out a stale pass. Two CI iterations were needed: the first
  failed on two pre-existing fixtures that forced the tracker on while the graph's own bootstrap
  disabled it, and the second exposed that the wall-clock wait introduced to fix them competed with
  the wall-clock budgets other graph tests use. Both are fixed by the two internal test seams and a
  fixture with no real-time wait at all. No known failure.
- Open decisions or blockers: none. The one owner decision this story needed — the emission scope —
  was taken on 2026-09-29 (`D-196`).
- Exact next step: push the branch, open the pull request and watch the ten required checks.

## Scope Completed

- `:core:analytics`: `SyncStatus.toSyncStatusCategory()` (`§20.9`) and the closed
  `AnalyticsProviderError` taxonomy.
- `:integration:firebase-analytics` (new): `FirebaseAnalyticsTracker`, `AnalyticsGateway`,
  `GitLiveAnalyticsGateway`, and three test classes.
- `:wiring:firebase`: the tracker is a parameter of both `firebaseAppProviders` overloads, the
  production overload constructs the Firebase implementation, and the inline staged no-op was replaced
  by a single private no-op.
- `:core:database`: `countActiveVehiclesByOwner` and `countActiveFuelEntriesByOwner`, and the
  read-only `OwnerActiveRowCountDatabaseAccess`.
- `:shared`: `AnalyticsEmissions`, `AnalyticsNotifyingVehicleRepository`,
  `AnalyticsNotifyingFuelEntryRepository`, the two decorators placed in the existing chains, and the
  `SessionStateHolder` selection and onboarding events.
- `:core:testing`: `RecordingAnalyticsTracker.enableCommands`.
- Build and CI: the module leaves `NOT_YET_INTRODUCED_MODULES`, joins D-75's roots, gains its
  `ci.yml` exclusion, the iOS app links `FirebaseAnalytics`, and contract assertions 37 and 38 are
  implemented with their failing fixtures.
- Documentation: `D-196`–`D-199` with ADR-0196/0197/0198 and their four mirrors; `§18` assertions;
  `docs/BACKLOG.md` scope and size; `AGENTS.md` D-75 count; this handoff; the project log entry.

## Acceptance Evidence

| Criterion | Evidence |
|-----------|----------|
| Every leaf maps exhaustively with no `else` | `FirebaseAnalyticsTracker.toProviderEvent()` is an exhaustive `when` with no `else`; `FirebaseAnalyticsTrackerTest.everyEventLeafMapsToAnEventNameAndParameterSet` pins all thirteen mappings through `expectedSurface()`, itself an exhaustive `when`, so adding, renaming or removing a leaf stops the test compiling. |
| Collection disabled at startup, enabled only on opt-in, nothing buffered while disabled | `FirebaseAnalyticsOptInTest.aFreshInstallStartsWithCollectionDisabledAtTheProvider` (the provider is commanded **off at construction**, so the SDK's own opt-in default is overridden); `nothingIsBufferedWhileDisabledAndNothingIsReplayedOnEnable`; `disablingStopsCollectionImmediately`. |
| No forbidden payload | `FirebaseAnalyticsTrackerTest.noParameterValueIsDerivedFromForbiddenPayload` pins the closed key set (`provider`, `is_full_tank`, `had_notes`, `status`, `reason`) and the allowed value types across every leaf; the closed hierarchy cannot carry a `String`, so odometer, volume, cost, notes, entity IDs and the UID have no representation. |
| Only `:wiring:firebase` constructs it | Contract assertion 37 (name appears only in `:wiring:firebase` and its own module, and the default is disabled) plus the pre-existing architecture rule over the `:integration:*` package; `GitLiveAnalyticsGateway` is `internal`, so no GitLive type crosses the boundary. |
| Excluding the module leaves the app on the no-op | `:integration:firebase-analytics` is in the closed provider registry, so `-Pcarapp.excludeFirebaseProviders=true` omits it, and the `provider-decoupling` job exercises that graph; the internal factory's default is a silent private no-op, pinned by `FirebaseAppProvidersTest.theStagedGraphWithoutAHostCollectsNothing`. |
| Emission boundary and `setUserProperties` cadence | `SharedAnalyticsEmissionTest` (five cases: successful create emits the event and refreshes the buckets, a rejected create emits and refreshes nothing, a fuel create carries only the two booleans, a delete refreshes without an event, tombstones are not counted) and `SharedAnalyticsCadenceTest` (the opt-in pair fires once, a repeated `true` repeats nothing, disabling is silent, a later re-opt-in repeats the pair, only a sync-category change emits, and the three session/onboarding cases). |
| `SyncStatus -> SyncStatusCategory` (`§20.9`) | `SyncStatusCategoryTest` pins the collapse for every `SyncStatus` shape; the connectivity qualification is applied once, in `:core:sync`, and is pinned there by `DefaultSyncControllerTest.connectivityFailureKeepsRowStateAndAggregateInAgreement`. |

## Out of Scope / Not Done

- The provider-failure sink is not wired to production: `AnalyticsEmissions` and
  `firebaseAppProviders` leave `onProviderError` at its no-op default, so a dropped metric is
  observable only in tests. Wiring it to `CrashReporter` would need a dependency the integration does
  not have; recorded as a risk rather than silently left.
- No analytics call site exists for `OnboardingStarted` on a device whose very first observed phase
  is already a session; by construction such a device did not go through onboarding in that run, and
  the rule is stated in `SessionStateHolder.trackOnboarding`.
- `docs/adr/0076` requires the Firebase Apple coverage list to be reviewed whenever that surface
  grows. The list is not amended here; the new iOS product is recorded by assertion 38 and the D-75
  root change, and the review stays owner-owned.

## Files Changed

- `core/analytics/src/commonMain/.../SyncStatusCategoryMapping.kt`, `AnalyticsProviderError.kt` (new).
- `core/analytics/src/commonTest/.../SyncStatusCategoryTest.kt` (new).
- `integration/firebase-analytics/` (new module): `build.gradle.kts`, `FirebaseAnalyticsTracker.kt`,
  `AnalyticsGateway.kt`, `GitLiveAnalyticsGateway.kt`, and the three test classes plus
  `RecordingGateway.kt`.
- `core/database/src/commonMain/sqldelight/.../database.sq` (two queries),
  `OwnerActiveRowCountDatabaseAccess.kt` (new), `OwnerActiveRowCountDatabaseAccessTest.kt` (new).
- `shared/src/commonMain/.../AnalyticsEmissions.kt`,
  `AnalyticsNotifyingVehicleRepository.kt`, `AnalyticsNotifyingFuelEntryRepository.kt` (new);
  `AppGraph.kt`, `VehicleSliceRuntime.kt`, `StateHolders.kt` (edited).
- `shared/src/commonTest/.../SharedAnalyticsEmissionTest.kt`, `SharedAnalyticsCadenceTest.kt` (new);
  the three pre-existing session analytics assertions scoped to the events they own.
- `core/testing/src/commonMain/.../AnalyticsFakes.kt` (`enableCommands`).
- `wiring/firebase/build.gradle.kts` and `FirebaseAppProviders.kt`; its test file.
- `build-logic/convention/src/main/.../architecture/ArchitectureChecker.kt`,
  `contract/AnalyticsIntegrationContract.kt` (new), `contract/ContractCheck.kt`,
  `contract/NativeTestExemptionContract.kt`; two test files.
- `gradle/libs.versions.toml` (`gitlive-firebase-analytics`).
- `iosApp/project.yml` and the regenerated `carapp.xcodeproj/project.pbxproj`.
- `.github/workflows/ci.yml` (the fifth Native exclusion).
- `AGENTS.md`, `docs/BACKLOG.md`, `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`,
  `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md`, `docs/adr/README.md`,
  `docs/adr/0196-…`, `0197-…`, `0198-…`, `docs/PROJECT_LOG.md`, this handoff.

## Decisions Made

- `D-196` / ADR-0196 — the owner chose that `E3-09` owns the complete `§16.1` surface rather than the
  integration alone. `§16.1` attributes the cadence fixture to this story by name and seven leaves
  had no emitter, so a mapper-only story would have left a named obligation unfulfilled. The story is
  resized to `M`.
- `D-197` / ADR-0197 — the count buckets come from the owner's active rows in `:core:database`
  through two read-only, owner-scoped, `deleted = 0` queries.
- `D-198` / ADR-0198 — `snake_case` event and parameter names, enum parameters as the enum `name`,
  and `is_full_tank`/`had_notes` as booleans. The GitLive 2.6.0 Android conversion was read out of
  the published bytecode: it handles `String`, `Int`, `Long`, `Double` and `Boolean` and silently
  drops anything else, so a boolean is representable and `§20.9` calls the two flags bucket-level
  booleans.
- `D-198` / ADR-0198 — a provider failure is classified into the closed `AnalyticsProviderError` and
  handed to an injected sink; `CancellationException` is rethrown. `§16.1` declares three
  non-suspending `Unit` methods, so propagation would be an unhandled exception on a product path.
- `D-199` / ADR-0198 — the tracker is injectable into `firebaseAppProviders`, which is what makes the
  single-construction-site criterion assertable without a runtime.
- `SHOULD` deviations: the TDD order exemption of `docs/SPECIFICATION.md §11` for "Koin wiring and
  provider integration code (Firebase, GitLive)" was used for the `:wiring:firebase` binding and the
  GitLive gateway, whose tests were written against the interface first and the binding second.
- A SQLDelight query cannot be moved between commits by itself, so the two new queries ship with
  their RED commit: the test cannot reference generated code that does not exist, and the SQL is
  declarative data rather than the behaviour under test.
- `SyncStatus -> SyncStatusCategory` is total with no connectivity parameter, because `§9.9` applies
  the connectivity qualification once, where the aggregate is derived. A second observation at the
  mapping site would be the second implementation of the aggregate that `§14`, `D-192` and `D-193`
  forbid. Recorded in `SyncStatusCategoryMapping` and its test.

## Verification Run

```text
./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify \
  :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test \
  -x :integration:firebase-auth:iosSimulatorArm64Test \
  -x :integration:firebase-firestore:iosSimulatorArm64Test \
  -x :integration:firebase-analytics:iosSimulatorArm64Test \
  -x :wiring:firebase:iosSimulatorArm64Test \
  -x :composition:ios:iosSimulatorArm64Test
```

Result: `BUILD SUCCESSFUL` (670 actionable tasks). Module-scoped runs during development:
`:core:analytics:testAndroidHostTest`, `:integration:firebase-analytics:testAndroidHostTest`,
`:core:database:testAndroidHostTest`, `:wiring:firebase:testAndroidHostTest`, `:shared:testAndroidHostTest`
(237 tests), `:build-logic:convention:test` (205 tests).

`contractCheck` output: assertions 21, 37 and 38 `PASS`; no assertion reports `PENDING`.

## Contract Impact

- Updated `docs/CONTRACTS.md §18`: assertions 37 and 38, and the D-75 derived-set prose now names
  three roots and five modules.

## Decision Board Impact

- Updated `docs/DECISION_BOARD.md` (`D-196`, `D-197`, `D-198`, `D-199`) and ADR-0196, ADR-0197,
  ADR-0198; `D-75`'s resolution prose was corrected to the three-root/two-integration reality.

## Shared-Write Modules Touched

`:core:database` may be modified by only one story at a time.

- `:core:database` — modified: two read-only owner-scoped count queries and a read-only accessor. No
  schema change, so no database-version bump and no `.sqm` migration. `core/database/.story-lock`
  does not exist, so no other in-flight story owns the module (`docs/handoff-E3-07.md` records that
  `E3-07` left it absent).

## Project Log Entry

- [x] Entry appended

## Risks or Follow-ups

- The provider-failure sink is not wired in production (see "Out of Scope / Not Done").
- `docs/adr/0076`'s Firebase Apple coverage review is triggered by the new iOS product and stays
  owner-owned.
- The emission fixtures need a bounded real-time wait (`awaitReal`) because a database continuation
  runs on a real dispatcher that virtual time cannot drain. The wait is bounded, so a condition that
  never becomes true fails the fixture instead of hanging it.
- `AnalyticsEmissions` deliberately keeps no local opt-in mirror: while disabled, a refresh issues one
  count query whose result the tracker drops. That is the honest trade for having a single gate, and
  it is recorded in the class KDoc.

## Human Review Gate

Applies: the change touches the gated topics "logging and privacy rules" and "module boundaries", and
the gated paths `docs/adr/**`, `docs/CONTRACTS.md` and `docs/DECISION_BOARD.md`.
