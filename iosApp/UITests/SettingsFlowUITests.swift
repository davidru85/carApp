import XCTest

/// `E4-01` Settings on iOS (`docs/SPECIFICATION.md §3.1`), driven through the real app on an erased
/// simulator.
///
/// Every test reaches Settings through the vehicle-list toolbar, so the entry point is exercised each
/// time. The guest path is the only session these tests can create: CI holds no App Check debug token,
/// so the owner is `LOCAL` there, and `ANONYMOUS` on a development machine that has one. Both are
/// offered the same account row (`B1-D5`), so every assertion holds for both.
///
/// No launch hook is added. A fresh simulator shows mandatory first-vehicle creation (F-1); the helper
/// completes it and leaves the vehicle detail F-2 routes to, because the Settings entry is on the list.
final class SettingsFlowUITests: XCTestCase {
    private let timeout: TimeInterval = 30

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testSettingsIsReachableFromTheVehicleList() {
        let app = launchApp()

        openSettingsFromTheToolbar(in: app)

        // The screen is proved by a row of its own content, not by its localized title ("Settings" or
        // "Ajustes") nor by the version row, which sits below the fold on a shorter device and is
        // scrolled to before it is asserted.
        XCTAssertTrue(
            app.buttons["settings_currency_picker"].waitForExistence(timeout: timeout),
            "Settings must open from the vehicle list"
        )
        let versionRow = app.staticTexts["settings_version"]
        XCTAssertTrue(scrollUntilHittable(versionRow, in: app), "Settings must show the app version row")
    }

    /// `§3.1`: the distance and volume units are visible and read-only in the MVP. Design 06 draws a
    /// chevron on both, which would invite a tap that can do nothing; they are static text instead.
    func testUnitsAreShownAsStaticTextAndNotAsButtons() {
        let app = launchApp()
        openSettingsFromTheToolbar(in: app)
        waitForStoredSettings(in: app)

        let distance = app.staticTexts["settings_distance_unit"]
        let volume = app.staticTexts["settings_volume_unit"]
        XCTAssertTrue(distance.exists, "The distance unit must be visible")
        XCTAssertTrue(volume.exists, "The volume unit must be visible")
        XCTAssertTrue(distance.label.contains("Kilometres (km)"), "saw \(distance.label)")
        XCTAssertTrue(volume.label.contains("Litres (L)"), "saw \(volume.label)")
        XCTAssertFalse(app.buttons["settings_distance_unit"].exists, "The distance unit must not be a button")
        XCTAssertFalse(app.buttons["settings_volume_unit"].exists, "The volume unit must not be a button")

        distance.tap()

        XCTAssertTrue(app.navigationBars["Settings"].exists, "Tapping a read-only unit must not navigate")
    }

    /// `docs/CONTRACTS.md §16.1` and `D-203`: collection is off until the owner opts in.
    ///
    /// Precondition: no earlier test opted in. No test in this target turns analytics on, and CI and a
    /// locally erased simulator start with no stored settings.
    func testAnalyticsSharingIsOffByDefault() {
        let app = launchApp()
        openSettingsFromTheToolbar(in: app)
        waitForStoredSettings(in: app)

        let toggle = app.switches["settings_analytics_toggle"]
        XCTAssertTrue(toggle.waitForExistence(timeout: timeout))
        XCTAssertEqual(toggle.value as? String, "0", "Analytics sharing must be off by default")
    }

    func testTheSelectedCurrencyIsKeptWhenSettingsIsReopened() {
        let app = launchApp()
        openSettingsFromTheToolbar(in: app)
        selectCurrency(endingWith: "(CHF)", in: app)

        app.navigationBars["Settings"].buttons.element(boundBy: 0).tap()
        let settingsButton = app.buttons["settings_open"]
        XCTAssertTrue(settingsButton.waitForExistence(timeout: timeout))
        XCTAssertTrue(waitUntil(settingsButton, matches: NSPredicate(format: "isHittable == true")))
        settingsButton.tap()

        let picker = app.buttons["settings_currency_picker"]
        XCTAssertTrue(picker.waitForExistence(timeout: timeout))
        XCTAssertTrue(
            picker.label.hasSuffix("(CHF)"),
            "The stored currency must survive reopening Settings, saw \(picker.label)"
        )
    }

    /// F-5 and `D-158`: "Delete local data" is a request followed by its destructive confirmation, and
    /// a guest owner with rows still waiting for backup is first asked to discard them. Afterwards the
    /// device is back at first run: the welcome screen, a mandatory first-vehicle form because no
    /// vehicle survived, and settings recreated from defaults (`docs/CONTRACTS.md §11.5`).
    func testDeletingLocalDataReturnsToFirstRunWithDefaultSettings() {
        let app = launchApp()
        openSettingsFromTheToolbar(in: app)
        // A non-default currency proves below that the settings row was recreated from defaults.
        selectCurrency(endingWith: "(GBP)", in: app)

        let deleteLocalData = app.buttons["settings_delete_local_data"]
        XCTAssertTrue(deleteLocalData.waitForExistence(timeout: timeout))
        XCTAssertFalse(app.buttons["settings_sign_out"].exists, "A guest owner has no sign-out (B1-D5)")
        XCTAssertFalse(app.buttons["settings_delete_account"].exists, "A guest owner has no account to delete (B1-D5)")
        deleteLocalData.tap()

        confirmLocalDataDeletion(in: app)

        XCTAssertTrue(
            app.buttons["welcome_guest"].waitForExistence(timeout: 60),
            "Deleting local data must return to the welcome screen"
        )
        let vehicleNameField = app.textFields["vehicle_name"]
        waitForOnboarding(
            in: app,
            destination: "first-vehicle creation after deleting local data",
            isComplete: { vehicleNameField.exists }
        )
        XCTAssertFalse(
            app.buttons["cancel_vehicle"].exists,
            "No vehicle survives the deletion, so first-vehicle creation is mandatory again"
        )

        openSettingsFromTheToolbar(in: app)
        waitForStoredSettings(in: app)
        let picker = app.buttons["settings_currency_picker"]
        XCTAssertFalse(picker.label.hasSuffix("(GBP)"), "The currency must be back to its default, saw \(picker.label)")
        XCTAssertEqual(app.switches["settings_analytics_toggle"].value as? String, "0")
    }

    private func launchApp() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        addTeardownBlock { app.terminate() }
        return app
    }

    /// Reaches the vehicle list from wherever onboarding stands, creating the first vehicle when F-1
    /// asks for it, and opens Settings from the toolbar.
    /// Named apart from the shared `XCTestCase.openSettings(in:)` of `E4-02`, which it would
    /// otherwise override with a narrower access level.
    private func openSettingsFromTheToolbar(in app: XCUIApplication) {
        let settingsButton = app.buttons["settings_open"]
        let vehicleNameField = app.textFields["vehicle_name"]
        let odometerField = app.textFields["vehicle_odometer"]
        let saveVehicleButton = app.buttons["save_vehicle"]
        let vehicleName = "AAA-Settings-\(Int(Date().timeIntervalSince1970))"
        var createdFirstVehicle = false

        waitForOnboarding(
            in: app,
            destination: "the vehicle list settings entry",
            isComplete: { settingsButton.exists && !vehicleNameField.exists },
            handleVehicleForm: {
                guard !createdFirstVehicle, vehicleNameField.exists else {
                    return false
                }
                createdFirstVehicle = true
                vehicleNameField.tap()
                vehicleNameField.typeText(vehicleName)
                odometerField.tap()
                odometerField.typeText("1000")
                saveVehicleButton.tap()
                // F-2 opens the first vehicle's detail; the Settings entry lives on the list.
                let detailBar = app.navigationBars[vehicleName]
                if detailBar.waitForExistence(timeout: self.timeout) {
                    detailBar.buttons.element(boundBy: 0).tap()
                }
                return true
            }
        )

        // The list is covered while it resolves, so the toolbar is tapped only once it is hittable.
        XCTAssertTrue(
            waitUntil(settingsButton, matches: NSPredicate(format: "isHittable == true")),
            "The Settings entry must become hittable on the vehicle list"
        )
        settingsButton.tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: timeout), "Settings must open")
    }

    /// The currency picker appears once the holder has read the stored settings.
    private func waitForStoredSettings(in app: XCUIApplication) {
        XCTAssertTrue(
            app.buttons["settings_currency_picker"].waitForExistence(timeout: timeout),
            "Settings must load the stored currency"
        )
    }

    private func selectCurrency(endingWith suffix: String, in app: XCUIApplication) {
        let picker = app.buttons["settings_currency_picker"]
        XCTAssertTrue(picker.waitForExistence(timeout: timeout))
        picker.tap()
        let option = app.buttons.matching(NSPredicate(format: "label ENDSWITH %@", suffix)).firstMatch
        XCTAssertTrue(option.waitForExistence(timeout: timeout), "The currency menu must offer \(suffix)")
        option.tap()
        XCTAssertTrue(
            waitUntil(picker, matches: NSPredicate(format: "label ENDSWITH %@", suffix)),
            "The picker must show \(suffix) once it is stored"
        )
    }

    /// Answers every confirmation the shared flow asks for. A guest owner with rows waiting for backup
    /// is first asked to discard them (`WARNING.PENDING_SYNC`); every guest owner is then asked the
    /// destructive `CONFIRMATION.DeleteLocalData`. On CI the owner is `LOCAL`, whose outbox is always
    /// empty (`§9.1` enqueues nothing for `LOCAL_OWNER`), so only the destructive confirmation appears.
    private func confirmLocalDataDeletion(in app: XCUIApplication) {
        let discard = app.alerts.buttons["departure_pending_sync_discard"].firstMatch
        let confirm = app.alerts.buttons["departure_confirm_delete_local_data"].firstMatch
        let deadline = Date().addingTimeInterval(timeout)
        while !confirm.exists, Date() < deadline {
            if discard.exists {
                discard.tap()
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        XCTAssertTrue(confirm.exists, "The destructive local-data confirmation must be asked")
        confirm.tap()
    }

    private func waitUntil(_ element: XCUIElement, matches predicate: NSPredicate) -> Bool {
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: element)
        return XCTWaiter().wait(for: [expectation], timeout: timeout) == .completed
    }

    /// `R3`/`R5-G` U0–K2 through the production renderers of `SettingsUnitRows` and
    /// `SettingsAnalyticsControl`, rendered by the Debug review fixture. The fixture is rendering
    /// evidence only: the shared Settings tests are what prove that a write moves the stored state.
    func testUnknownSettingsRenderLoadingUnitValuesAndNoAnalyticsSwitch() {
        let app = launchReviewSurface("settingsUnknown", arguments: englishArguments)

        let loading = app.descendants(matching: .any)["settings_analytics_loading"]
        XCTAssertTrue(loading.waitForExistence(timeout: timeout))
        XCTAssertFalse(app.switches["settings_analytics_toggle"].exists, "no switch may claim a stored opt-out")
        requireUnitRowsShowLoading(in: app)
    }

    func testFailedSettingsReadRendersLoadingValuesPlusTheTypedError() {
        let app = launchReviewSurface("settingsReadError", arguments: englishArguments)

        let loading = app.descendants(matching: .any)["settings_analytics_loading"]
        XCTAssertTrue(loading.waitForExistence(timeout: timeout))
        XCTAssertFalse(app.switches["settings_analytics_toggle"].exists, "no switch may claim a stored opt-out")
        requireUnitRowsShowLoading(in: app)
        let message = app.staticTexts["settings_message"]
        XCTAssertTrue(message.waitForExistence(timeout: timeout))
        XCTAssertTrue(
            message.label.contains("The change could not be saved on this device"),
            "saw \(message.label)"
        )
    }

    func testKnownOffAnalyticsRendersAnEnabledOffSwitch() {
        let app = launchReviewSurface("settingsOff", arguments: englishArguments)
        requireKnownAnalyticsSwitch(value: "0", isEnabled: true, in: app)
    }

    func testKnownOnAnalyticsRendersAnEnabledOnSwitch() {
        let app = launchReviewSurface("settingsOn", arguments: englishArguments)
        requireKnownAnalyticsSwitch(value: "1", isEnabled: true, in: app)
    }

    func testSavingAnalyticsRendersACheckedDisabledSwitch() {
        let app = launchReviewSurface("settingsSaving", arguments: englishArguments)
        requireKnownAnalyticsSwitch(value: "1", isEnabled: false, in: app)
    }

    private var englishArguments: [String] { AccessibilityUITestSupport.englishArguments }

    private func requireUnitRowsShowLoading(in app: XCUIApplication) {
        let loading = "Loading"
        for identifier in ["settings_distance_unit", "settings_volume_unit"] {
            let row = app.descendants(matching: .any)[identifier]
            XCTAssertTrue(row.waitForExistence(timeout: timeout), "missing \(identifier)")
            XCTAssertTrue(
                row.label.contains(loading),
                "\(identifier) must show the loading resource while the settings are unknown, saw \(row.label)"
            )
        }
    }

    private func requireKnownAnalyticsSwitch(value: String, isEnabled: Bool, in app: XCUIApplication) {
        let toggle = app.switches["settings_analytics_toggle"]
        XCTAssertTrue(toggle.waitForExistence(timeout: timeout), "the analytics switch must exist once the settings are known")
        XCTAssertEqual(toggle.value as? String, value)
        XCTAssertEqual(toggle.isEnabled, isEnabled)
        XCTAssertFalse(app.descendants(matching: .any)["settings_analytics_loading"].exists)
    }
}
