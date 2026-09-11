import XCTest

private enum OnboardingWaitAction: Equatable {
    case complete
    case tapGuest
    case tapAddVehicle
    case wait
}

private enum OnboardingWaitStep {
    case waitingForAffordance
    case startingGuestSession
    case openingVehicleCreation

    var timeout: TimeInterval {
        switch self {
        case .waitingForAffordance, .openingVehicleCreation:
            return 10
        case .startingGuestSession:
            return 60
        }
    }

    var timeoutMessage: String {
        switch self {
        case .waitingForAffordance:
            return "Onboarding did not expose welcome_guest or add_vehicle before the 10-second timeout"
        case .startingGuestSession:
            return "Guest session did not reach the vehicle list after retrying welcome_guest for 60 seconds"
        case .openingVehicleCreation:
            return "Vehicle creation did not open after retrying add_vehicle for 10 seconds"
        }
    }
}

private struct OnboardingWaitState {
    private(set) var step = OnboardingWaitStep.waitingForAffordance
    private(set) var guestTapAttempts = 0
    private(set) var addVehicleTapAttempts = 0

    mutating func nextAction(
        vehicleNameExists: Bool,
        guestAvailable: Bool,
        addVehicleAvailable: Bool
    ) -> OnboardingWaitAction {
        if vehicleNameExists {
            return .complete
        }
        if guestAvailable {
            step = .startingGuestSession
            guestTapAttempts += 1
            return .tapGuest
        }
        if addVehicleAvailable {
            step = .openingVehicleCreation
            addVehicleTapAttempts += 1
            return .tapAddVehicle
        }
        return .wait
    }

    var timeoutMessage: String {
        step.timeoutMessage
    }
}

final class VehicleAndFuelFlowUITests: XCTestCase {
    private let timeout: TimeInterval = 10

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testVehicleSwipeDeleteShowsConfirmationDialog() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        addTeardownBlock { app.terminate() }

        let vehicleName = "AAA-Swipe-\(Int(Date().timeIntervalSince1970))"

        openVehicleCreation(in: app)

        let vehicleNameField = app.textFields["vehicle_name"]
        XCTAssertTrue(vehicleNameField.waitForExistence(timeout: timeout))
        vehicleNameField.tap()
        vehicleNameField.typeText(vehicleName)

        let odometerField = app.textFields["vehicle_odometer"]
        odometerField.tap()
        odometerField.typeText("10000")

        let saveVehicleButton = app.buttons["save_vehicle"]
        XCTAssertTrue(saveVehicleButton.isEnabled)
        saveVehicleButton.tap()
        XCTAssertTrue(saveVehicleButton.waitForNonExistence(timeout: timeout))

        let vehicleRow = app.staticTexts[vehicleName]
        if !vehicleRow.waitForExistence(timeout: 2) {
            app.swipeDown()
        }
        XCTAssertTrue(vehicleRow.waitForExistence(timeout: timeout))

        vehicleRow.swipeLeft()
        let deleteButton = app.buttons.matching(NSPredicate(format: "label CONTAINS 'Delete' OR label CONTAINS 'delete'")).firstMatch
        XCTAssertTrue(deleteButton.waitForExistence(timeout: timeout), "Swipe should reveal a delete action")
        deleteButton.tap()

        let confirmDeleteButton = app.alerts.buttons["Delete"].firstMatch
        XCTAssertTrue(confirmDeleteButton.waitForExistence(timeout: timeout), "A confirmation dialog should appear before the vehicle is deleted")
        confirmDeleteButton.tap()

        XCTAssertTrue(vehicleRow.waitForNonExistence(timeout: timeout), "The vehicle row should disappear after confirming deletion")
    }

    func testVehicleAndFuelEntryCreationFlow() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        addTeardownBlock { app.terminate() }

        let vehicleName = "AAA-Golf-\(Int(Date().timeIntervalSince1970))"

        // 1. Create a vehicle
        openVehicleCreation(in: app)

        let vehicleNameField = app.textFields["vehicle_name"]
        XCTAssertTrue(vehicleNameField.waitForExistence(timeout: timeout))
        vehicleNameField.tap()
        vehicleNameField.typeText(vehicleName)

        let odometerField = app.textFields["vehicle_odometer"]
        odometerField.tap()
        odometerField.typeText("142000")

        let saveVehicleButton = app.buttons["save_vehicle"]
        XCTAssertTrue(saveVehicleButton.isEnabled)
        saveVehicleButton.tap()

        // Wait for vehicle form sheet to dismiss
        XCTAssertTrue(saveVehicleButton.waitForNonExistence(timeout: timeout))

        // 2. Open vehicle detail
        let vehicleRow = app.staticTexts[vehicleName]
        if !vehicleRow.waitForExistence(timeout: 2) {
            app.swipeDown()
        }
        XCTAssertTrue(vehicleRow.waitForExistence(timeout: timeout))
        vehicleRow.tap()

        XCTAssertTrue(app.staticTexts["consumption_empty"].waitForExistence(timeout: timeout))
        XCTAssertTrue(app.staticTexts["first_fuel_invitation"].waitForExistence(timeout: timeout))

        // 3. Create a partial refuel entry
        let addFuelButton = app.buttons["add_fuel_entry"]
        XCTAssertTrue(addFuelButton.waitForExistence(timeout: timeout))
        addFuelButton.tap()

        let fuelOdometer = app.textFields["fuel_odometer"]
        XCTAssertTrue(fuelOdometer.waitForExistence(timeout: timeout))
        fuelOdometer.tap()
        if let currentText = fuelOdometer.value as? String, !currentText.isEmpty {
            let deleteString = String(repeating: XCUIKeyboardKey.delete.rawValue, count: currentText.count)
            fuelOdometer.typeText(deleteString)
        }
        fuelOdometer.typeText("142500")

        let fuelLiters = app.textFields["fuel_liters"]
        fuelLiters.tap()
        fuelLiters.typeText("45.200")

        let fuelPrice = app.textFields["fuel_price_per_liter"]
        fuelPrice.tap()
        fuelPrice.typeText("1.629")

        // Derived total cost should display
        let derivedValue = app.staticTexts["fuel_derived_value"]
        XCTAssertTrue(derivedValue.waitForExistence(timeout: timeout))

        // Toggle partial refuel
        let fullTankToggle = app.switches["fuel_full_tank"]
        XCTAssertTrue(fullTankToggle.waitForExistence(timeout: timeout))
        if (fullTankToggle.value as? String) == "1" {
            fullTankToggle.tap()
        }
        if (fullTankToggle.value as? String) == "1" {
            fullTankToggle.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)).tap()
        }

        let saveFuelButton = app.buttons["save_fuel_entry"]
        saveFuelButton.tap()

        // Wait for fuel form sheet to dismiss
        XCTAssertTrue(saveFuelButton.waitForNonExistence(timeout: timeout))

        // 4. Verify fuel entry row appears with partial tank badge
        let partialBadge = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'fuel_indicator_' AND identifier ENDSWITH '_partial'")).firstMatch
        XCTAssertTrue(partialBadge.waitForExistence(timeout: timeout))

        // 5. Create an inconsistent refuel to verify warning alert confirmation and inconsistent badge
        addFuelButton.tap()

        let fuelOdometer2 = app.textFields["fuel_odometer"]
        XCTAssertTrue(fuelOdometer2.waitForExistence(timeout: timeout))
        fuelOdometer2.tap()
        if let currentText2 = fuelOdometer2.value as? String, !currentText2.isEmpty {
            let deleteString = String(repeating: XCUIKeyboardKey.delete.rawValue, count: currentText2.count)
            fuelOdometer2.typeText(deleteString)
        }
        fuelOdometer2.typeText("142200") // lower than previous 142500

        let fuelLiters2 = app.textFields["fuel_liters"]
        fuelLiters2.tap()
        fuelLiters2.typeText("20.000")

        let fuelPrice2 = app.textFields["fuel_price_per_liter"]
        fuelPrice2.tap()
        fuelPrice2.typeText("1.500")

        let saveFuelButton2 = app.buttons["save_fuel_entry"]
        saveFuelButton2.tap()

        // Confirmation alert should appear
        let confirmButton = app.alerts.buttons["confirm_fuel_odometer_warning"].firstMatch
        XCTAssertTrue(confirmButton.waitForExistence(timeout: timeout))
        confirmButton.tap()

        // Wait for fuel form sheet to dismiss
        XCTAssertTrue(saveFuelButton2.waitForNonExistence(timeout: timeout))

        // Verify inconsistent odometer badge is displayed
        let inconsistentBadge = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'fuel_indicator_' AND identifier ENDSWITH '_odometer'")).firstMatch
        XCTAssertTrue(inconsistentBadge.waitForExistence(timeout: timeout))
    }

    /// Only first-run creation is mandatory. Creating a later vehicle from the list stays
    /// dismissible, including through the interactive gesture.
    func testLaterVehicleCreationRemainsInteractivelyDismissible() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        addTeardownBlock { app.terminate() }

        openVehicleCreation(in: app)
        let vehicleNameField = app.textFields["vehicle_name"]
        XCTAssertTrue(vehicleNameField.waitForExistence(timeout: timeout))
        XCTAssertTrue(app.buttons["cancel_vehicle"].exists, "Later creation keeps its cancellation control")

        app.swipeDown(velocity: .fast)

        XCTAssertTrue(
            vehicleNameField.waitForNonExistence(timeout: timeout),
            "A later creation sheet must still be dismissible with the interactive gesture"
        )
    }

    func testOnboardingWaitRetriesEachAffordanceThatRemainsAvailable() {
        var guestState = OnboardingWaitState()

        XCTAssertEqual(
            guestState.nextAction(vehicleNameExists: false, guestAvailable: true, addVehicleAvailable: false),
            .tapGuest
        )
        XCTAssertEqual(
            guestState.nextAction(vehicleNameExists: false, guestAvailable: true, addVehicleAvailable: false),
            .tapGuest,
            "A visible and hittable affordance must be retried after an ineffective tap"
        )

        var vehicleState = OnboardingWaitState()
        XCTAssertEqual(
            vehicleState.nextAction(vehicleNameExists: false, guestAvailable: false, addVehicleAvailable: true),
            .tapAddVehicle
        )
        XCTAssertEqual(
            vehicleState.nextAction(vehicleNameExists: false, guestAvailable: false, addVehicleAvailable: true),
            .tapAddVehicle,
            "A visible and hittable affordance must be retried after an ineffective tap"
        )
    }

    func testOnboardingWaitCarriesTheSnapshottedTapPosition() {
        let guestPosition = OnboardingTapPosition(dx: 0.25, dy: 0.75)
        var waitState = OnboardingWaitState()

        XCTAssertEqual(
            waitState.nextAction(
                vehicleNameExists: false,
                guestPosition: guestPosition,
                addVehiclePosition: nil
            ),
            .tapGuest(at: guestPosition),
            "The tap action must not retain an XCUIElement that can disappear before the tap"
        )
    }

    func testOnboardingWaitNamesGuestSessionTimeout() {
        var guestState = OnboardingWaitState()
        _ = guestState.nextAction(vehicleNameExists: false, guestAvailable: true, addVehicleAvailable: false)
        XCTAssertEqual(guestState.step.timeout, 60)
        XCTAssertEqual(
            guestState.timeoutMessage,
            "Guest session did not reach the vehicle list after retrying welcome_guest for 60 seconds"
        )
    }

    func testOnboardingWaitNamesVehicleCreationTimeout() {
        var vehicleState = OnboardingWaitState()
        _ = vehicleState.nextAction(vehicleNameExists: false, guestAvailable: false, addVehicleAvailable: true)
        XCTAssertEqual(vehicleState.step.timeout, 10)
        XCTAssertEqual(
            vehicleState.timeoutMessage,
            "Vehicle creation did not open after retrying add_vehicle for 10 seconds"
        )
    }

    private func openVehicleCreation(in app: XCUIApplication) {
        let guestButton = app.buttons["welcome_guest"]
        let addVehicleButton = app.buttons["add_vehicle"]
        let vehicleNameField = app.textFields["vehicle_name"]
        let startedAt = Date()
        var waitState = OnboardingWaitState()
        var deadline = Date().addingTimeInterval(waitState.step.timeout)

        while Date() < deadline {
            let previousStep = waitState.step
            switch waitState.nextAction(
                vehicleNameExists: vehicleNameField.exists,
                guestAvailable: guestButton.exists && guestButton.isHittable,
                addVehicleAvailable: addVehicleButton.exists && addVehicleButton.isHittable
            ) {
            case .complete:
                let elapsed = String(format: "%.3f", Date().timeIntervalSince(startedAt))
                print(
                    "E1-17 onboarding reached vehicle creation in \(elapsed) seconds " +
                        "after \(waitState.guestTapAttempts) welcome_guest and " +
                        "\(waitState.addVehicleTapAttempts) add_vehicle tap attempts"
                )
                return
            case .tapGuest:
                guestButton.tap()
            case .tapAddVehicle:
                addVehicleButton.tap()
            case .wait:
                break
            }
            if waitState.step != previousStep {
                deadline = Date().addingTimeInterval(waitState.step.timeout)
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        XCTFail(waitState.timeoutMessage)
    }
}
