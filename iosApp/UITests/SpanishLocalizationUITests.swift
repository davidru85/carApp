import XCTest

/// `E4-02`: the app runs in Spanish with Spanish number formatting under `-AppleLanguages (es)
/// -AppleLocale es_ES`. The expected copy is the `es.lproj` value of the named key: a UI-test process
/// cannot read the app bundle's catalogue, so each literal names its key.
final class SpanishLocalizationUITests: XCTestCase {
    private let spanish = AccessibilityUITestSupport.spanishArguments

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testWelcomeActionsUseSpanishCopy() {
        let app = launchCarApp(arguments: spanish, environment: AccessibilityUITestSupport.forceWelcome)
        let guest = app.buttons["welcome_guest"]
        XCTAssertTrue(guest.waitForExistence(timeout: 10))
        XCTAssertEqual(guest.label, "Continuar sin cuenta") // es.lproj continue_without_account
        XCTAssertEqual(app.buttons["welcome_google"].label, "Continuar con Google") // es.lproj continue_with_google
        attachAccessibilityEvidence(of: app, named: "es-welcome")
    }

    func testVehicleOdometerUsesSpanishGrouping() {
        let app = launchCarApp(arguments: spanish)
        createVehicleAndOpenDetail(in: app, odometer: "142500")
        // The odometer is read on the vehicle list, so the row is opened and left again: the detail is
        // where a first vehicle lands (F-2), and its back affordance returns to the list.
        XCTAssertTrue(returnToTheListFromTheDetail(in: app), "the back affordance is not hittable")
        let odometer = app.staticTexts.matching(NSPredicate(format: "label == %@", "142.500 km")).firstMatch
        XCTAssertTrue(odometer.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        attachAccessibilityEvidence(of: app, named: "es-vehicle-list")
    }

    func testFuelDerivedTotalUsesTheSpanishDecimalComma() {
        let app = launchCarApp(arguments: spanish)
        createVehicleAndOpenDetail(in: app, odometer: "1000")
        openFuelEntryForm(in: app)
        typeFuelEntry(in: app, odometer: "1100", liters: "40", price: "2")
        let derived = app.staticTexts["fuel_derived_value"]
        XCTAssertTrue(derived.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        XCTAssertTrue(derived.label.contains("80,00"), "Saw \(derived.label)")
        attachAccessibilityEvidence(of: app, named: "es-fuel-form")
    }

    func testSettingsUseSpanishCopy() {
        let app = launchCarApp(arguments: spanish)
        // Settings lives on the vehicle list, so the list is reached directly.
        reachTheVehicleListForAccessibility(in: app)
        openSettings(in: app)
        XCTAssertTrue(
            app.navigationBars["Ajustes"].waitForExistence(timeout: AccessibilityUITestSupport.timeout),
            "the Settings title must be the es.lproj settings_title"
        )
        let distance = app.staticTexts["settings_distance_unit"]
        XCTAssertTrue(distance.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        XCTAssertTrue(distance.label.contains("Unidad de distancia"), "Saw \(distance.label)")
        XCTAssertTrue(distance.label.contains("Kilómetros (km)"), "Saw \(distance.label)")
        // es.lproj backup_status_idle, read through the backup_status_description label.
        let status = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", "Sincronizado localmente"))
            .firstMatch
        XCTAssertTrue(status.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        // The backup row sits below the fold on a shorter device; scroll to it before reading its copy.
        XCTAssertTrue(scrollUntilHittable(status, in: app), "the backup status row must be reachable")
        attachAccessibilityEvidence(of: app, named: "es-settings")
    }

    func testDepartureFailureUsesSpanishCopy() {
        let app = launchCarApp(arguments: spanish, environment: AccessibilityUITestSupport.forceDepartureFailure)
        // es.lproj error_persistence
        let error = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", "No se ha podido guardar el cambio"))
            .firstMatch
        XCTAssertTrue(error.waitForExistence(timeout: 10))
        attachAccessibilityEvidence(of: app, named: "es-departure")
    }

    // MARK: - R5-I: explicit Spanish presentation evidence for the review fixtures

    func testReauthenticationRendersSpanishCopy() {
        let app = launchReviewSurface("reauthentication", arguments: spanish)
        requireReviewControls(["departure_reauth_apple", "departure_reauth_google", "departure_reauth_cancel"], in: app)
        XCTAssertEqual(app.buttons["departure_reauth_google"].label, "Continuar con Google")
        XCTAssertEqual(app.buttons["departure_reauth_cancel"].label, "Cancelar")
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(NSPredicate(format: "identifier == %@", "departure_reauth_message"))
                .firstMatch.label.contains("vuelve a iniciar sesión"),
            "the recent-login reason must be Spanish"
        )
        attachAccessibilityEvidence(of: app, named: "es-reauthentication")
    }

    func testRetainedReauthenticationFailureRendersSpanishCopy() {
        let app = launchReviewSurface("reauthenticationFailure", arguments: spanish)
        requireReviewControls(["departure_reauth_google", "departure_reauth_cancel", "departure_failure"], in: app)
        let failure = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier == %@", "departure_failure"))
            .firstMatch
        XCTAssertTrue(
            failure.label.contains("Esta acción necesita conexión"),
            "the mapped auth network error must be Spanish, saw \(failure.label)"
        )
        attachAccessibilityEvidence(of: app, named: "es-reauthentication-failure")
    }

    func testDeleteAccountConfirmationRendersSpanishCopy() {
        let app = launchReviewSurface("deleteAccount", arguments: spanish)
        requireAlertControls(["Eliminar cuenta", "Cancelar"], in: app)
        let alert = app.alerts.firstMatch
        XCTAssertTrue(
            alert.descendants(matching: .any)
                .matching(NSPredicate(format: "label CONTAINS %@", "Esto elimina permanentemente tu cuenta"))
                .firstMatch.exists,
            "the complete Spanish irreversible-action body must be present"
        )
        XCTAssertTrue(alert.buttons["Eliminar cuenta"].exists)
        XCTAssertTrue(alert.buttons["Cancelar"].exists)
        attachAccessibilityEvidence(of: app, named: "es-delete-account")
    }

    func testDeleteLocalDataConfirmationRendersSpanishCopy() {
        let app = launchReviewSurface("deleteLocalData", arguments: spanish)
        requireAlertControls(["Eliminar datos locales", "Cancelar"], in: app)
        let alert = app.alerts.firstMatch
        XCTAssertTrue(
            alert.descendants(matching: .any)
                .matching(NSPredicate(format: "label CONTAINS %@", "Esto elimina permanentemente todos los vehículos"))
                .firstMatch.exists,
            "the complete Spanish irreversible-action body must be present"
        )
        XCTAssertTrue(alert.buttons["Eliminar datos locales"].exists)
        XCTAssertTrue(alert.buttons["Cancelar"].exists)
        attachAccessibilityEvidence(of: app, named: "es-delete-local-data")
    }

    func testPendingSyncPromptRendersSpanishCopy() {
        let app = launchReviewSurface("pendingSync", arguments: spanish)
        requireAlertControls(["Esperar a la copia", "Descartar y cerrar sesión", "Cancelar"], in: app)
        let alert = app.alerts.firstMatch
        XCTAssertTrue(
            alert.descendants(matching: .any)
                .matching(NSPredicate(format: "label CONTAINS %@", "aún sin copia de seguridad: 3"))
                .firstMatch.exists,
            "the Spanish pending-sync body with its count must be present"
        )
        XCTAssertTrue(alert.buttons["Esperar a la copia"].exists)
        XCTAssertTrue(alert.buttons["Cancelar"].exists)
        attachAccessibilityEvidence(of: app, named: "es-pending-sync")
    }

    func testConversionRowRendersSpanishCopy() {
        let app = launchReviewSurface("conversion", arguments: spanish)
        requireReviewControls(["settings_link_account_apple", "settings_link_account_google"], in: app)
        XCTAssertEqual(app.buttons["settings_link_account_google"].label, "Continuar con Google")
        // `LinkAccountRows` is one container, so its title is its own `staticTexts` node inside it.
        XCTAssertTrue(
            app.staticTexts["Vincular una cuenta"].waitForExistence(timeout: 10)
                || app.descendants(matching: .any)
                    .matching(NSPredicate(format: "label CONTAINS %@", "Vincular"))
                    .firstMatch.exists,
            "the Spanish link-account title must be present"
        )
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(NSPredicate(format: "label CONTAINS %@", "Vincula una cuenta de Apple o de Google"))
                .firstMatch.exists,
            "the Spanish link-account body must be present"
        )
        XCTAssertFalse(
            app.buttons["settings_link_account_apple"].label.isEmpty,
            "the system Apple button must still expose an accessible label"
        )
        attachAccessibilityEvidence(of: app, named: "es-conversion")
    }

    func testCollisionRendersSpanishCopy() {
        let app = launchReviewSurface("collision", arguments: spanish)
        requireAlertControls(["Sustituir datos", "Cancelar"], in: app)
        let alert = app.alerts.firstMatch
        XCTAssertTrue(
            alert.descendants(matching: .any)
                .matching(NSPredicate(format: "label CONTAINS %@", "sustituye permanentemente"))
                .firstMatch.exists,
            "the complete Spanish collision body must be present"
        )
        XCTAssertTrue(alert.buttons["Sustituir datos"].exists)
        XCTAssertTrue(alert.buttons["Cancelar"].exists)
        attachAccessibilityEvidence(of: app, named: "es-collision")
    }

    func testDepartureFailureRendersSpanishRetryCopy() {
        let app = launchReviewSurface("departureFailure", arguments: spanish)
        requireReviewControls(["departure_failure", "departure_retry"], in: app)
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(NSPredicate(format: "identifier == %@", "departure_failure"))
                .firstMatch.label.contains("No se ha podido guardar el cambio"),
            "the Spanish persistence error must be present"
        )
        XCTAssertEqual(
            app.buttons["departure_retry"].label,
            "Reintentar la eliminación de datos locales",
            "the retry control must render es.lproj departure_retry_local_clear"
        )
        attachAccessibilityEvidence(of: app, named: "es-departure-fixture")
    }
}
