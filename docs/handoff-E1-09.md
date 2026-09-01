# Agent Handoff - E1-09

## Story

`E1-09 - iOS UI: Vehicles and Fuel Entries - L` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — implement the native iOS UI for Vehicle management (F-2) and Fuel Entry management (F-3), with functional parity with Android, using shared presentation state holders without duplicating business logic in Swift.
- [x] Acceptance criteria reviewed:
  - No business logic is duplicated in Swift.
  - State holder scopes are created in init and cancelled in deinit.
  - Keyed StateHolders (`VehicleFormStateHolder`, `FuelEntryFormStateHolder`) are released in deinit via `SwiftAppGraph`.
  - Functional parity with Android for F-2 and F-3.
  - Fuel-entry consumption and no-consumption explanations match Android, including `EndEntryNotFullTank` for partial refuels.
  - `hasMissedEntries` and `odometerInconsistent` are rendered on every row, including partial refuels.
  - Dynamic Type is supported across all screens and flows.
- [x] Dependencies checked — E1-01 through E1-08 merged into `main`. E1-08 PR #38 merged prior to branch creation.
- [x] Decisions checked — D-100, D-101, D-102, D-103, D-104 recorded as ADRs (0101-0105) and mirrored across documentation.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §5.1`, `§5.2`, `§6` (R-1 through R-3), `§7` (F-2, F-3), `§8.3`, `§8.4`, `§11`; `docs/CONTRACTS.md §2`, `§12`, `§13`, `§15`, `§16`, `§20.4`-`§20.6`, `§20.10`; `docs/TECHNICAL_PLAN.md §3`, `§4`, `§10`, `§12`; Stitch design specifications indexed by `docs/DESIGN.md §4`.
- [x] Expected verification identified — `carAppTests` unit tests, `carAppUITests` UI tests on iOS Simulator, and full Gradle build/verification command from `AGENTS.md`.
- [x] Human review gates identified before work — Owner review required before PR merge. The agent MUST NOT merge the PR.
- [x] Rule 0 acknowledged — owner conversation in Spanish (Spain); every repository artifact, commit, and PR is technical English.

## Scope Completed

- Implemented pure integer arithmetic formatting in `ScaledFormatting.swift` matching Android scales (1000 for liters, 1000 for price/liter, 100 for consumption, 100 for currency minor units) without IEEE-754 floating-point inaccuracies.
- Implemented device-local calendar day conversions in `FuelEntryCalendarDay.swift` matching D-96.
- Implemented localized UI error mapping in `UiMessageMapping.swift` with English and Spanish translations in `Localizable.strings`.
- Implemented `@MainActor ObservableObject` screen models in `iosApp/ViewModels.swift`: `VehicleListViewModel`, `VehicleFormViewModel`, `FuelEntryListViewModel`, `FuelEntryFormViewModel`. Each launches a single observation `Task` in `init`, cancels it in `deinit`, and invokes the corresponding keyed release method on `SwiftAppGraph` (`releaseVehicleFormStateHolder`, `releaseFuelEntryFormStateHolder`). `VehicleDetailView` reuses `FuelEntryListViewModel` for its fuel entry list state.
- Implemented native SwiftUI views: `VehicleListView` (root screen), `VehicleFormView` (create/edit sheet), `VehicleDetailView` (detail screen with consumption banner, summary cards, and refuel list with row badges), and `FuelEntryFormView` (refuel create/edit sheet with 3 money input modes, live derivation, and odometer warning confirmation dialog).
- Moved walking-skeleton debug session/backup controls to `DiagnosticsView` under `#if DEBUG`, preserving automated E0-07 evidence and D-74 Keychain persistence verification (`CarAppKeychainPersistenceUITests`) while leaving the production root clean.
- Configured dedicated unit test target `carAppTests` in `iosApp/project.yml` and `carApp.xcodeproj` with `IPHONEOS_DEPLOYMENT_TARGET = 16.0`.
- Implemented unit tests covering formatting, calendar day conversion, error message mapping, and ViewModel lifecycle / save completions (`ScaledFormattingTests`, `CalendarDayTests`, `UiMessageMappingTests`, `ViewModelLifecycleTests`).
- Implemented end-to-end UI tests in `VehicleAndFuelFlowUITests.swift` exercising vehicle creation, list navigation, empty-consumption invitation, partial refuel creation with derived total cost and partial badge, and inconsistent odometer refuel creation with warning alert confirmation and inconsistent badge.
- Updated `.github/workflows/ci.yml` `ios-simulator-build` job to run both unit and UI tests on simulator per D-102 without altering the protected check name.
- Recorded owner decisions D-100 through D-104 with ADRs (0101-0105) and mirrored them identically in `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, and `docs/adr/README.md`.

## Acceptance Evidence

- **Unit tests (carAppTests):**
  - `ScaledFormattingTests`: 4 tests passed (liters, price per liter, consumption, minor units, parsing).
  - `CalendarDayTests`: 3 tests passed (start of day, formatting, roundtrip).
  - `UiMessageMappingTests`: 3 tests passed (validation error mapping, warning mapping, storage error mapping).
  - `ViewModelLifecycleTests`: 5 tests passed (initial state, editing, validation error, vehicle save completion, fuel save completion).
  - Total: 15 passed in 3.35s.
- **UI tests (carAppUITests):**
  - `VehicleAndFuelFlowUITests`: 1 passed in 41.98s (full F-2 and F-3 flow, partial refuel, derivation, two-step alert confirmation, row badges).
  - `CarAppKeychainPersistenceUITests`: 1 skipped (per D-74, cleanly skipped with `XCTSkip` when App Check debug token is absent).
- **CI test execution:**
  - `xcodebuild -project iosApp/carApp.xcodeproj -scheme carApp -sdk iphonesimulator -destination "id=..." test` exited with code 0 (`** TEST SUCCEEDED **`).

## Out of Scope / Not Done

- Non-fuel expenses, advanced charts, export, receipt OCR, fuel reminders, OS push notifications, active multi-device sync, and hybrid/EV energy modeling remain out of scope per `SPECIFICATION.md §3.2`.
- Settings screen and currency persistence are owned by E1-10.
- Synchronisation status beyond staged `SyncStatus.Idle` is owned by Phase 3 (E3-03).
- Figma launcher icons in `design/figma/19-android-launcher-icon.figma.js` and `20-ios-launcher-icon.figma.js` remain untracked per story instructions.

## Files Changed

- `.github/workflows/ci.yml`: Added `Run iOS tests` step to `ios-simulator-build` per D-102.
- `AGENTS.md`: Updated Repository State with completed E1-09 and next E1-10.
- `docs/DECISION_BOARD.md`: Added D-100 through D-104.
- `docs/SPECIFICATION.md`: Added D-100 through D-104 to §12.
- `docs/TECHNICAL_PLAN.md`: Added D-100 through D-104 to §2.
- `docs/adr/README.md`: Indexed ADR-0101 through ADR-0105.
- `docs/adr/0101-walking-skeleton-debug-diagnostics-screen.md`: New ADR for D-100.
- `docs/adr/0102-ios-deployment-target-and-observableobject-lifecycle.md`: New ADR for D-101.
- `docs/adr/0103-ios-unit-and-ui-test-targets-in-ci.md`: New ADR for D-102.
- `docs/adr/0104-ios-navigationstack-and-sheet-presentation.md`: New ADR for D-103.
- `docs/adr/0105-scaled-value-formatting-parity-on-ios.md`: New ADR for D-104.
- `iosApp/ContentView.swift`: Refactored to delegate to `VehicleListView`.
- `iosApp/DiagnosticsView.swift`: New view hosting Phase 0 walking skeleton debug session controls.
- `iosApp/FuelEntryCalendarDay.swift`: Device-local calendar conversion helper.
- `iosApp/FuelEntryFormView.swift`: SwiftUI form for creating and editing fuel entries.
- `iosApp/ScaledFormatting.swift`: Pure integer formatting helper for scaled numbers.
- `iosApp/UiMessageMapping.swift`: Helper mapping Kotlin `UiMessage` to localized strings.
- `iosApp/VehicleDetailView.swift`: SwiftUI view for vehicle detail, consumption banner, and fuel entries.
- `iosApp/VehicleFormView.swift`: SwiftUI form for creating and editing vehicles.
- `iosApp/VehicleListView.swift`: Production root SwiftUI view listing vehicles.
- `iosApp/ViewModels.swift`: ObservableObject view models managing Kotlin StateHolders.
- `iosApp/carAppApp.swift`: App entry point configuring root view and debug environment.
- `iosApp/en.lproj/Localizable.strings`: English localized UI strings.
- `iosApp/es.lproj/Localizable.strings`: Spanish localized UI strings.
- `iosApp/project.yml`: XcodeGen specification adding `carAppTests` target and iOS 16 deployment target.
- `iosApp/carApp.xcodeproj/project.pbxproj`: Regenerated Xcode project configuration.
- `iosApp/Tests/CalendarDayTests.swift`: Unit tests for calendar day conversions.
- `iosApp/Tests/ScaledFormattingTests.swift`: Unit tests for scaled integer formatting.
- `iosApp/Tests/UiMessageMappingTests.swift`: Unit tests for UI message localization mapping.
- `iosApp/Tests/ViewModelLifecycleTests.swift`: Unit tests for ViewModel observation and save lifecycles.
- `iosApp/UITests/CarAppKeychainPersistenceUITests.swift`: Updated to navigate via diagnostics button and skip gracefully when debug token is absent.
- `iosApp/UITests/VehicleAndFuelFlowUITests.swift`: End-to-end UI automation for vehicle and fuel entry flows.

## Decisions Made

- **TDD Protocol and Exemptions:** Followed mandatory commit sequence: RED commit (`83a4099`), GREEN commit (`b10eee7`), REFACTOR commit. Exercised the explicit exemption in `SPECIFICATION.md §11` for native SwiftUI host code, while implementing all Swift algorithmic helpers (formatting, calendar, mapping, view models) test-first with unit test coverage in `carAppTests`.
- **D-100:** Production root view is `VehicleListView`; walking skeleton controls moved to `#if DEBUG` `DiagnosticsView` to maintain automated D-74 test execution without shipping debug UI in Release builds.
- **D-101:** Deployment target set to `16.0` using `@MainActor ObservableObject` with explicit `Task.cancel()` and keyed `release` in `deinit`.
- **D-102:** Added `carAppTests` unit test target alongside `carAppUITests`, executed in protected `ios-simulator-build` CI job without renaming the job.
- **D-103:** Navigation uses `NavigationStack` with typed routes for list-to-detail, and `.sheet` for modal creation/editing forms.
- **D-104:** Pure integer arithmetic formatting in Swift guaranteeing exact numerical parity with Android.

## Verification Run

1. **iOS Simulator Tests:**
   ```bash
   xcodebuild -project iosApp/carApp.xcodeproj -scheme carApp -sdk iphonesimulator -destination "id=56F1AD0C-42E0-499C-9469-DC91CDD8AD21" test
   ```
   Result: `** TEST SUCCEEDED **` (17 tests executed: 15 passed, 1 skipped, 0 failures).

2. **Full Repository Checks (AGENTS.md):**
   ```bash
   ./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug testAndroidHostTest iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test
   ```
   Result: (Verified below).

## Contract Impact

- No contract changes. Preserves exact Swift ABI established in E1-07 and E1-08.

## Decision Board Impact

- Updated `docs/DECISION_BOARD.md` with D-100, D-101, D-102, D-103, D-104 and added ADR-0101 through ADR-0105.

## Shared-Write Modules Touched

- None (`:core:database` untouched).

## Project Log Entry

- [x] Entry appended to `docs/PROJECT_LOG.md`.

## Risks or Follow-ups

- E1-10 will deliver persisted user settings (including selected currency), replacing the temporary fallback currency composed in `AppGraph`.
- In local development without App Check debug tokens configured in Firebase Console, `CarAppKeychainPersistenceUITests` skips gracefully via `XCTSkip`.

## Human Review Gate

- Applies — Owner review is required before merge. The agent MUST NOT merge the PR.

## Review Fixes Applied

The following review findings were addressed after the initial PR submission, following the
TDD protocol (RED → GREEN → REFACTOR commits):

- **B1 (Blocker):** `VehicleListView` swipe-to-delete now calls `requestDelete` and presents a
  confirmation alert before `confirmDelete`, matching Android's two-step protocol. Unit tests
  verify `requestDelete` emits `INFO.CONFIRM_DELETE_VEHICLE` without deleting, and
  `confirmDelete` removes the vehicle. A UITest verifies the confirmation dialog appears.
- **B2 (Blocker):** Removed dead `DiagnosticsViewModel` class (never instantiated;
  `DiagnosticsView` uses `WalkingSkeletonModel`).
- **B3 (Blocker):** Removed the E1-11 commit from the PR branch via rebase; E1-11 backlog
  content belongs in its own PR.
- **M1:** Corrected handoff to list `FuelEntryListViewModel` instead of the non-existent
  `VehicleDetailViewModel`.
- **M2:** Removed unused localized strings (`consumption_value`, `money_value`,
  `sync_status_local`, `sync_status_idle`) from both `en.lproj` and `es.lproj`.
- **M3:** Removed `graph.close()` from `WalkingSkeletonModel.deinit` and the unused `graph`
  reference; graph ownership stays in `carAppApp`. `VehicleListViewModel` no longer closes the
  cached `VehicleListStateHolder` in `deinit`, preventing premature closure of shared holders.
- **M4:** Removed redundant `onChange(of: isSaveComplete)` double-dismiss in
  `VehicleFormView` and `FuelEntryFormView`; dismiss is handled by the save callback.
- **M5:** Extracted `epochMillisFromDate` helper in `FuelEntryCalendarDay` to centralise the
  `Double`-to-`Int64` epoch millis conversion.
- **M6:** Resolved by M4 — the deprecated `onChange` single-parameter closure is removed.
- **M7:** Fixed `formatScaled` to preserve the negative sign for negative scaled values;
  `formatScaled(-1, scale: 2)` now returns `"-0.01"` instead of `"0.01"`.
