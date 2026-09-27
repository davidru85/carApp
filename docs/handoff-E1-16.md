# Agent Handoff - E1-16

## Story

`E1-16 - Vehicle UI Fuel Type Selector - S` (`docs/BACKLOG.md`, `## Follow-Ups Outside the Phase
Milestones`).

## Ready Check

- Backlog story: `E1-16 - Vehicle UI Fuel Type Selector - S`, under `### Deferred scope, now
  scheduled`, with the eight acceptance criteria of `docs/BACKLOG.md` §`### E1-16`.
- Acceptance criteria reviewed: all eight. The selector on both platforms defaulting to `GASOLINE`;
  five localized values in English and Spanish; the selection reaching
  `VehicleFormStateHolder.setFuelType`; persistence on creation; load and update on edit; UI tests on
  both platforms for a non-default value on both paths; and no schema, migration or sync rule change.
- Dependencies checked: **none**. The `E1-16` backlog entry carries no `Depends on:` row. Its only
  named blocker was `E2-03` (post-save routing for first-run creation), which merged on 2026-09-06,
  so the story is unblocked.
- Decisions checked: `D-127` (`Accepted`, 2026-09-06) is the decision that authorises this story; it
  supersedes only the "no MVP UI selector" clause of `D-4` and constrains the selector to exactly the
  five MVP values with `GASOLINE` as the default and no schema, migration or Firestore rule change.
  `D-4` keeps status `Accepted` and everything else in it stands. The three open decisions (`D-149`,
  `D-150`, `D-173`) gate `E3-15`, `E3-16` and `E3-18` only and are irrelevant here.
- Normative sections reviewed: `docs/SPECIFICATION.md` §5.1, §7 F-2, §11 and §3.2;
  `docs/CONTRACTS.md` §20.4, §20.5, §20.8 and §20.10; `docs/TECHNICAL_PLAN.md` §4;
  `docs/adr/0128`; `docs/adr/0005`; `AGENTS.md` §Product Rules and §Architecture Rules.
- Expected verification: the complete non-instrumented command of `AGENTS.md` §Build and verify, plus
  `./gradlew contractCheck --rerun-tasks`, the Android instrumented suite on the `D-84` API 36
  emulator (`./gradlew :androidApp:connectedDebugAndroidTest`) and the iOS app built with
  `xcodebuild` and exercised on a booted iPhone simulator.
- Human review gate identified before work: **gated path**. The product implementation touches no
  gated product path, but this pull request changes `AGENTS.md`, `docs/SPECIFICATION.md` and
  `docs/CONTRACTS.md`, which are gated paths under `AGENTS.md` §Human Review Gates. The owner's intake
  authorisation permits the documented realignment; it does not waive the required owner review before
  merge. The pull request MUST NOT be merged on agent judgement alone.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every artifact it
  produces is in technical English.

## Blocking Finding Raised At Intake

`D-127` is `Accepted` and authorises the selector, but the normative prose that forbade it was never
corrected when `D-127` was accepted. `docs/SPECIFICATION.md` §5.1 ("Stored in the MVP, not exposed as
a selector") and §7 F-2 ("`fuelType` is not exposed in the MVP UI") still forbade the control, and
`docs/CONTRACTS.md` §20.10 reinforced the prohibition with a `MUST NOT`. `D-127` is a behavioural
decision and `docs/SPECIFICATION.md` is the behavioural authority, so the story as written could not
be implemented without correcting those sentences in the same change, which `AGENTS.md` §Document
Authority requires ("every document repeating the same rule MUST be corrected in the same change").

Two executable pins also encoded the old prohibition and would have failed a correct implementation:
`PlatformHostContractTest.androidHostBindsThePersistentGraphToSharedStateHolders` asserted
`assertFalse(host.contains("setFuelType"))`, and `VehicleCreationTest` asserted
`VehicleTestTags.FUEL_TYPE_INPUT` does not exist. The `E1-07` acceptance criterion in
`docs/BACKLOG.md` repeated the same prohibition.

All four are gated paths or gated-adjacent, so this was escalated to the owner rather than resolved by
the agent. The owner authorised the realignment inside `E1-16` and the inversion of both executable
pins; both are recorded under "Decisions Made".

## In-Progress Checkpoint

- Date: 2026-09-27.
- Branch and base: `story/E1-16-vehicle-fuel-type-selector`, based on `origin/main` at `72640ffd`
  ("Merge pull request #79").
- Current phase and implementation commits: RED `0f1d4a84` and GREEN `2cdbdd70`, both pushed. No
  REFACTOR phase was needed; the `LongMethod` extraction done during GREEN was a lint correction, not
  a refactor. Later commits changed delivery documentation only; no Kotlin, Swift, SQL or workflow file
  changed after GREEN.
- Push and pull-request status: all commits are pushed and pull request #80 is open against `main`.
  Run `36346316418` on reviewed head `37fe0e64` passed all ten required checks. This is historical
  evidence for that head; GitHub's live PR status is authoritative for any later correction head.
  Not merged.
- Completed since the previous checkpoint: the two selectors, the ten localized keys, the document
  realignment, the full verification run and the delivery documentation.
- Verification evidence and known failures: see "Verification Run" and "Re-run Investigation". No
  failure attributable to the E1-16 product change is known.
- Open decisions or blockers: none. Merge remains blocked on green checks for the final head and the
  owner's gated review.
- Exact next step: let GitHub's live pull-request status confirm that the final head has all ten green
  required checks, then obtain the owner's gated review. Do not merge on agent judgement.

## Re-run Investigation

Five CI runs covered this pull request before the owner's review, and no Kotlin, Swift, SQL or workflow
file changed after the first run:

| Run | Head | Change after the product run | Observed result |
|---|---|---|---|
| `36341671213` | `0eb9ec37` | — | all ten checks green on the first attempt |
| `36342494016` | `e85753ee` | handoff only | `provider-decoupling` and `ios-simulator-build` red; both green on the next run |
| `36343592583` | `5ff678d0` | handoff only | `shared-tests` red; green on the next run |
| `36344530243` | `de170f3b` | handoff only | `shared-tests` red on its first attempt; green on re-run |
| `36346316418` | `37fe0e64` | handoff only | all ten checks green on the first attempt |

Each red result was read before another run was accepted as evidence:

- `provider-decoupling` in `36342494016` ended with `Test running process exited unexpectedly` in
  `:shared:iosSimulatorArm64Test`, on
  `LocalOwnerAdoptionTest.theFirstLocalOwnerWriteWhileOnlineTriggersAcquisitionAfterAMissedConnectivityEmission`.
  PR80 changes no file under `shared/` or `core/`, and the provider-free suite passed locally and on
  later CI heads.
- `ios-simulator-build` in `36342494016` failed at
  `VehicleAndFuelFlowUITests.swift:161` inside the pre-existing
  `testVehicleAndFuelEntryCreationFlow`. PR80's changed hunk in that file starts after that test, and
  the new E1-16 UI test passed in the same run.
- `shared-tests` in `36343592583` named
  `LocalOwnerAdoptionTriggerTest.aFuelEntryWriteTriggersAcquisitionAndAdoptionAfterAnEarlierAttemptFailed[iosSimulatorArm64]`.
  `shared-tests` in `36344530243` named
  `VehicleListStateHolderTest.failedRefreshPublishesTheErrorAndLetsTheNextRefreshRun[iosSimulatorArm64]`.
  Earlier `docs/PROJECT_LOG.md` entries record prior occurrences of both tests, but those historical
  entries described E1-14 and E1-17 before they merged. They are evidence of prior occurrence, not a
  current rule that a red required check may be re-run without investigation.

The current repository rule is the opposite: `AGENTS.md` and `docs/BACKLOG.md` state that a red
`shared-tests` or `provider-decoupling` result is evidence to investigate, never a reason to re-run
blindly. The investigation above, the absence of changes under `shared/`, `core/` and `firestore/`, the
repeatable local `:shared:iosSimulatorArm64Test --rerun-tasks` result, the first-attempt green product
run `36341671213`, and the first-attempt all-green reviewed-head run `36346316418` together separate
the E1-16 change from the intermediate failures. GitHub's live status remains authoritative after any
later correction commit; do not create another documentation-only commit solely to record that live
status.

## Scope Completed

- `androidApp/src/main/java/com/ruizurraca/carapp/MainActivity.kt`: a `FuelTypeSelector` built on
  `ExposedDropdownMenuBox` over the five MVP values, placed in `VehicleForm` and wired to
  `stateHolder::setFuelType`; the text fields were extracted into `VehicleTextFields` to keep
  `VehicleForm` under the detekt method-length limit; `VehicleTestTags.FUEL_TYPE_OPTION` was added.
- `iosApp/VehicleFormView.swift` and `iosApp/ViewModels.swift`: a native `Picker` over the five MVP
  values driven by `VehicleFormViewModel.fuelType`, with `setFuelType(_:)` forwarding to the shared
  state holder and edit-facts emissions updating the picker only until the owner chooses a value.
- Five `fuel_type_*` keys plus the `fuel_type` label in both languages in the four catalogues.
- The document realignment of `docs/SPECIFICATION.md` §5.1 and §7 F-2, `docs/CONTRACTS.md` §20.10 and
  the `E1-07` acceptance criterion in `docs/BACKLOG.md`.
- Tests: three instrumented tests on the Android form, a contract test per platform in
  `PlatformHostContractTest` (`bothLanguagesNameEveryMvpFuelType` and
  `iosVehicleFormWiresTheFuelTypePickerThroughTheViewModel`), an iOS unit test for the five
  localized keys and an iOS UI test for creation and edit persistence.

## Acceptance Evidence

1. **Android creation and edit expose a selector defaulting to `GASOLINE`.**
   `VehicleCreationTest.createsVehicleAndRoutesToEmptyDetailWithTheDefaultFuelType` asserts the
   `fuel_type_input` field is displayed and shows the `GASOLINE` label before saving.
2. **iOS creation and edit expose a native `Picker` defaulting to `GASOLINE`.**
   `VehicleAndFuelFlowUITests.testFuelTypeSelectionPersistsOnCreationAndEdit` asserts the
   `fuel_type_picker` exists and its label ends in `Petrol` before saving. The manual Android run
   showed the equivalent default.
3. **Localized display strings in English and Spanish for all five values.**
   `PlatformHostContractTest.bothLanguagesNameEveryMvpFuelType` asserts the four catalogues contain
   all five keys; `UiMessageMappingTests.testBothLanguagesNameEveryMvpFuelType` resolves each key
   through `en.lproj` and `es.lproj`.
4. **Changing the selection dispatches `setFuelType`.** The Compose selector calls `onSelect` →
   `stateHolder::setFuelType`; the iOS picker binds to `viewModel.setFuelType(_:)`, which calls
   `stateHolder.setFuelType(value:)`. `PlatformHostContractTest` asserts the Android host contains
   `setFuelType`, and the iOS contract test asserts `setFuelType` in the view model and its call from
   the form.
5. **Saving a new vehicle persists the selected `FuelType`.**
   `VehicleCreationTest.selectedFuelTypePersistsOnCreationAndOnEdit` creates a vehicle as `Diesel`.
   The iOS UI test creates one as `Diesel`.
6. **Edit loads the persisted value and saving persists an update.**
   The same two tests reopen the vehicle and assert `Diesel`, then change to `LPG`, save, reopen and
   assert `LPG`. The manual Android run reproduced the `Diesel` load.
7. **UI tests on both platforms for a non-default value on creation and edit.** The three instrumented
   tests and the iOS UI test above; `:androidApp:connectedDebugAndroidTest` passes 25/25.
8. **No schema, migration or sync rule change.** `git diff origin/main` touches nothing under
   `firestore/`, `core/database/` or `core/model/`, and no `.sqm` file was added or edited.

## Out of Scope / Not Done

- No change to `FuelType`: still exactly `GASOLINE`, `DIESEL`, `LPG`, `CNG`, `OTHER`, with no
  `ELECTRIC` or `HYBRID` (`E5-01` owns them).
- No change to the schema, migrations, Firestore rules or the outbox payload.
- No change to `VehicleFormStateHolder`'s contract, validation or persistence.
- Android saving an edit still does not navigate away (`VehicleFormScreen` calls `onSaved` only when
  `originalVehicleId` is `null`). This is pre-existing, out of this story's scope, and reported as a
  follow-up.

## Files Changed

- `androidApp/src/main/java/com/ruizurraca/carapp/MainActivity.kt`
- `androidApp/src/main/res/values/strings.xml`
- `androidApp/src/main/res/values-es/strings.xml`
- `androidApp/src/androidTest/java/com/ruizurraca/carapp/VehicleCreationTest.kt`
- `build-logic/convention/src/test/kotlin/com/ruizurraca/carapp/buildlogic/PlatformHostContractTest.kt`
- `iosApp/VehicleFormView.swift`
- `iosApp/ViewModels.swift`
- `iosApp/en.lproj/Localizable.strings`
- `iosApp/es.lproj/Localizable.strings`
- `iosApp/Tests/UiMessageMappingTests.swift`
- `iosApp/UITests/VehicleAndFuelFlowUITests.swift`
- `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/BACKLOG.md`, `AGENTS.md`,
  `docs/PROJECT_LOG.md`, `docs/handoff-E1-16.md`

## Decisions Made

- **Blocking intake finding, resolved by the owner on 2026-09-27.** The normative prose forbidding the
  selector (`docs/SPECIFICATION.md` §5.1 and §7 F-2, `docs/CONTRACTS.md` §20.10) and the `E1-07`
  acceptance criterion in `docs/BACKLOG.md` are corrected in this story, with a `D-127` supersession
  note. The two executable pins are inverted: the host contract test asserts the selector is wired,
  and the instrumented test asserts it persists instead of asserting its absence. This is a document
  realignment to an existing `Accepted` decision, not a new decision, so it introduces no `D-` id, no
  ADR and no mirrored rows.
- The iOS picker uses `.pickerStyle(.menu)` rather than `.segmented`, because the five localized
  labels would not fit a segmented control in either language. No decision: the style is an
  implementation choice inside the acceptance criterion's "native `Picker`".
- The Android selector uses `ExposedDropdownMenuBox` from the already-present Material 3 dependency;
  no dependency was added.
- No `SHOULD` was deviated from.

## Verification Run

All commands run in the worktree on 2026-09-27, in order.

- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
  koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest
  iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test
  -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test
  -x :composition:ios:iosSimulatorArm64Test` → `BUILD SUCCESSFUL`.
- `./gradlew contractCheck --rerun-tasks` → every assertion `PASS`; no `FAIL`, no `PENDING`.
- `./gradlew :androidApp:connectedDebugAndroidTest` on `E1_07_API_36` (emulator-5554) → 25 tests, 0
  failures. The RED run of the same command failed 3 tests, each because `fuel_type_input` did not
  exist (`VehicleCreationTest.kt:67`, `:42` and `:91`).
- `./gradlew :build-logic:convention:test --tests '*PlatformHostContractTest*'` → RED with three
  `AssertionError`s (`:82`, `:100`, `:145`); GREEN after the implementation.
- `xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator -configuration Debug
  ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build` → `** BUILD SUCCEEDED **`.
- `xcodebuild … -destination "id=<iPhone 17 Pro>" ARCHS=arm64 ONLY_ACTIVE_ARCH=NO test` on a freshly
  erased simulator → all suites passed, `VehicleAndFuelFlowUITests` 4/4 including the new test.
- `git diff --check` → exit 0.
- Manual end-to-end run on the installed Android debug app: the form opens on `Petrol`; the menu
  offers exactly `Petrol`, `Diesel`, `LPG`, `CNG` and `Other`; saving a vehicle as `Diesel` routes to
  its detail, and reopening it in edit mode shows `Diesel`.
- Device cleanup: `adb devices` shows no `emulator-<port>` line and `xcrun simctl list devices booted`
  lists no device. The helper log under `/tmp/` was deleted.

## Contract Impact

- Updated `docs/CONTRACTS.md` §20.10: the `fuelType` paragraph now states that both platforms render a
  selector over the five MVP values, defaulting to `GASOLINE`, and that `ELECTRIC` and `HYBRID` remain
  owned by `E5-01`. This is a realignment to `D-127`, not a new contract.

## Decision Board Impact

- No decision changes. `D-127` was already `Accepted` and remains the decision that authorises the
  story; no new `D-` id and no ADR were created, because no new decision was taken.

## Shared-Write Modules Touched

- None. `:core:database` is not touched; no schema, migration or sync rule change.

## Project Log Entry

- [x] Entry appended (`docs/PROJECT_LOG.md`, 2026-09-27, "E1-16: the fuel type selector on both
  Vehicle forms, and the prose D-127 left behind").
- [x] Run `36346316418` passed all ten required checks on reviewed head `37fe0e64`; GitHub's live
  status is authoritative for later correction heads.

## Risks or Follow-ups

- Android saving an edit does not navigate away, because `VehicleFormScreen` calls `onSaved` only when
  `originalVehicleId` is `null`. Pre-existing, unrelated to the selector, and untouched here; it
  deserves its own story if the behaviour is not intended.
- The iOS UI suite is sensitive to simulator state: `testVehicleAndFuelFlowUITests` line 84 and
  `testVehicleSwipeDeleteShowsConfirmationDialog` line 38 each failed once on a simulator polluted by
  repeated diagnostic runs and passed after `simctl erase`. A red `ios-simulator-build` on that pair
  is worth a clean simulator before it is read as a regression.
- The pre-existing `FUEL_TYPE_INPUT` test tag was kept in use and a sibling `FUEL_TYPE_OPTION` tag was
  added for the menu items; both are host-only test tags and reach neither the shared graph nor iOS.

## Human Review Gate

**Gated path.** The story identifier is not in the gated-story list and no gated product path is
touched, but PR80 changes `AGENTS.md`, `docs/SPECIFICATION.md` and `docs/CONTRACTS.md`. All three are
gated paths under `AGENTS.md` §Human Review Gates. The owner's intake authorisation allowed the
realignment to D-127; it did not waive the final gate. PR80 MUST NOT be merged on agent judgement and
requires the owner's review after all ten required checks are green on the final head.
