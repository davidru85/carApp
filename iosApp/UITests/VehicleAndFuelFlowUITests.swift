import XCTest

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

    /// `D-127`: the Vehicle form exposes a native `Picker` over the five MVP fuel types, defaulting
    /// to petrol, and a non-default selection persists on creation and on edit.
    func testFuelTypeSelectionPersistsOnCreationAndEdit() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        addTeardownBlock { app.terminate() }

        let vehicleName = "AAA-Diesel-\(Int(Date().timeIntervalSince1970))"

        openVehicleCreation(in: app)

        let vehicleNameField = app.textFields["vehicle_name"]
        XCTAssertTrue(vehicleNameField.waitForExistence(timeout: timeout))
        vehicleNameField.tap()
        vehicleNameField.typeText(vehicleName)

        let odometerField = app.textFields["vehicle_odometer"]
        odometerField.tap()
        odometerField.typeText("90000")

        let fuelTypePicker = app.buttons["fuel_type_picker"]
        XCTAssertTrue(fuelTypePicker.waitForExistence(timeout: timeout), "The fuel type picker must be offered")
        XCTAssertTrue(
            fuelTypePicker.label.hasSuffix("Petrol"),
            "A new vehicle must default to petrol, saw \(fuelTypePicker.label)"
        )
        fuelTypePicker.tap()
        app.buttons["Diesel"].firstMatch.tap()

        let saveVehicleButton = app.buttons["save_vehicle"]
        XCTAssertTrue(saveVehicleButton.isEnabled)
        saveVehicleButton.tap()
        XCTAssertTrue(saveVehicleButton.waitForNonExistence(timeout: timeout))

        // Reopen in edit mode: the persisted selection must load, not the creation default.
        let vehicleRow = app.staticTexts[vehicleName]
        if !vehicleRow.waitForExistence(timeout: 2) {
            app.swipeDown()
        }
        XCTAssertTrue(vehicleRow.waitForExistence(timeout: timeout))
        vehicleRow.tap()
        XCTAssertTrue(
            app.collectionViews["vehicle_detail_name"].waitForExistence(timeout: timeout),
            "The detail screen must open"
        )
        openVehicleEditor(in: app)

        let editFuelTypePicker = app.buttons["fuel_type_picker"]
        XCTAssertTrue(editFuelTypePicker.waitForExistence(timeout: timeout))
        XCTAssertTrue(
            editFuelTypePicker.label.hasSuffix("Diesel"),
            "Edit must load the persisted fuel type, saw \(editFuelTypePicker.label)"
        )

        // Update the selection and save; the next edit must show the updated value.
        editFuelTypePicker.tap()
        app.buttons["LPG"].firstMatch.tap()
        let editSaveButton = app.buttons["save_vehicle"]
        editSaveButton.tap()
        XCTAssertTrue(editSaveButton.waitForNonExistence(timeout: timeout))

        let reopenedRow = app.staticTexts[vehicleName]
        XCTAssertTrue(reopenedRow.waitForExistence(timeout: timeout))
        reopenedRow.tap()
        openVehicleEditor(in: app)
        let reopenedPicker = app.buttons["fuel_type_picker"]
        XCTAssertTrue(reopenedPicker.waitForExistence(timeout: timeout))
        XCTAssertTrue(
            reopenedPicker.label.hasSuffix("LPG"),
            "The updated fuel type must persist, saw \(reopenedPicker.label)"
        )
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

    /// The detail screen keeps editing behind the navigation bar's overflow `Menu`, so the editor is
    /// reached in two taps: open the menu, then its `edit_vehicle` item.
    private func openVehicleEditor(in app: XCUIApplication) {
        let moreButton = app.buttons["More"].firstMatch
        XCTAssertTrue(moreButton.waitForExistence(timeout: timeout), "The overflow menu must be offered")
        moreButton.tap()

        let editButton = app.buttons["edit_vehicle"].firstMatch
        XCTAssertTrue(editButton.waitForExistence(timeout: timeout), "The edit action must be in the overflow menu")
        editButton.tap()
    }

    private func openVehicleCreation(in app: XCUIApplication) {
        let vehicleNameField = app.textFields["vehicle_name"]
        waitForOnboarding(
            in: app,
            destination: "vehicle creation",
            isComplete: { vehicleNameField.exists }
        )
    }
}
