import XCTest
import Shared
@testable import carApp

final class UiMessageMappingTests: XCTestCase {
    /// `docs/CONTRACTS.md §14.1` (`{D-B1-D9}`): the test-side copy of the code-to-copy table.
    /// `contract-check` assertion 41 fails when this list stops naming a code the shared layer can
    /// publish, so the tests below cannot fall behind the mapping they pin.
    private static let table: [String: String] = [
        "VALIDATION.REQUIRED_FIELD": "error_required_field",
        "VALIDATION.INVALID_LENGTH": "error_invalid_length",
        "VALIDATION.OUT_OF_RANGE": "error_out_of_range",
        "VALIDATION.EDIT_NOT_ALLOWED": "error_edit_not_allowed",
        "VALIDATION.DUPLICATE_NAME": "error_duplicate_vehicle_name",
        "VALIDATION.FUTURE_DATE": "error_future_date",
        "VALIDATION.INVALID_MONEY_INPUT": "error_money_input",
        "VALIDATION.NO_OP": "error_no_changes",
        "VALIDATION.INVALID_UNIT": "error_currency",
        "VALIDATION.ENTITY_DELETED": "error_entity_unavailable",
        "VALIDATION.ENTITY_NOT_FOUND": "error_entity_unavailable",
        "WARNING.ODOMETER_INCONSISTENT": "odometer_warning_body",
        "WARNING.PENDING_SYNC": "pending_sync_title",
        "AUTH.CANCELLED": "error_auth_cancelled",
        "AUTH.NETWORK_UNAVAILABLE": "error_auth_network",
        "AUTH.CREDENTIAL_ALREADY_IN_USE": "error_auth_credential_in_use",
        "AUTH.PROVIDER_UNAVAILABLE": "error_auth_provider",
        "AUTH.NO_ACCOUNT_AVAILABLE": "error_auth_no_account",
        "AUTH.TOKEN_EXPIRED": "error_auth_session_expired",
        "AUTH.PERMISSION_DENIED": "error_auth_permission_denied",
        "AUTH.REQUIRES_RECENT_LOGIN": "error_auth_requires_recent_login",
        "AUTH.UID_WOULD_CHANGE": "error_auth_uid_would_change",
        "AUTH.ACCOUNT_DELETION_REMOTE_FAILED": "error_account_deletion_remote_failed",
        "AUTH.UNKNOWN": "error_unexpected",
        "PERSISTENCE.DATABASE_UNAVAILABLE": "error_persistence",
        "PERSISTENCE.TRANSACTION_FAILED": "error_persistence",
        "PERSISTENCE.MIGRATION_FAILED": "error_persistence",
        "PERSISTENCE.SERIALIZATION_FAILED": "error_persistence",
        "PERSISTENCE.CONSTRAINT_VIOLATION": "error_persistence",
        "SYNC.RETRYABLE_NETWORK": "error_backup_offline",
        "SYNC.AUTH_EXPIRED": "error_backup_session",
        "SYNC.PERMISSION_DENIED": "error_backup_rejected",
        "SYNC.VALIDATION_REJECTED": "error_backup_rejected",
        "SYNC.PAYLOAD_POISONED": "error_backup_rejected",
        "SYNC.CONFLICT_UNRESOLVED": "error_restore",
        "SYNC.REMOTE_UNAVAILABLE": "error_backup_offline",
        "REMOTE.UNAVAILABLE": "error_backup_offline",
        "REMOTE.DEADLINE_EXCEEDED": "error_backup_offline",
        "REMOTE.PERMISSION_DENIED": "error_backup_rejected",
        "REMOTE.UNAUTHENTICATED": "error_backup_session",
        "REMOTE.INVALID_ARGUMENT": "error_backup_rejected",
        "REMOTE.NOT_FOUND": "error_restore",
        "REMOTE.UNKNOWN": "error_restore",
        "SECURITY.RULES_REJECTED": "error_backup_rejected",
        "SECURITY.OWNER_MISMATCH": "error_backup_rejected",
        "UNEXPECTED": "error_unexpected",
        "CONFIRMATION.OdometerInconsistent": "odometer_warning_body",
        "CONFIRMATION.DiscardPendingChanges": "pending_sync_title",
        "CONFIRMATION.DeleteAccount": "confirm_delete_account",
        "CONFIRMATION.DeleteLocalData": "confirm_delete_local_data",
        "CONFIRMATION.AdoptExistingAccount": "confirm_replace_existing_account",
        "INFO.CONFIRM_DELETE_VEHICLE": "delete_vehicle_confirmation",
        "INFO.CONFIRM_DELETE_FUEL_ENTRY": "delete_fuel_entry_confirmation",
    ]

    /// Every code renders the key its `§14.1` row assigns. Several codes share a key on purpose: the
    /// table assigns copy per user-actionable outcome (`{D-B1-D9}`).
    func testEveryCodeMapsToTheKeyTheTableAssigns() {
        for (code, key) in Self.table {
            XCTAssertEqual(uiMessageKey(for: code), key, "\(code) must render the copy docs/CONTRACTS.md §14.1 assigns")
        }
    }

    /// The key alone proves nothing: a bundle returns the key itself when it lacks it. Each bundle is
    /// therefore read directly, and the value must differ from the key in both shipped languages.
    func testEveryMappedKeyResolvesToCopyInEnglishAndSpanish() {
        for code in Self.table.keys.sorted() {
            let key = uiMessageKey(for: code)
            for language in ["en", "es"] {
                XCTAssertNotEqual(localizedCopy(key, language: language), key, "\(code) renders \(key), which has no \(language) copy")
            }
        }
    }

    /// The generic copy is reserved for the two unclassified failures; every other code tells the owner
    /// what to do (`docs/CONTRACTS.md §14.1`).
    func testOnlyTheUnclassifiedFailuresRenderTheGenericCopy() {
        let generic = Set(Self.table.keys.filter { uiMessageKey(for: $0) == "error_unexpected" })

        XCTAssertEqual(generic, ["AUTH.UNKNOWN", "UNEXPECTED"])
    }

    /// The `default` branch is the `§6` safety net: a code no row names renders the generic copy.
    func testACodeNoTableEntryNamesFallsBackToTheGenericKey() {
        XCTAssertEqual(uiMessageKey(for: "INFO.NOT_IN_THE_TABLE"), "error_unexpected")
    }

    /// `localizedUiMessage(for:)` is the call-site API every view uses; it renders the mapped key.
    func testLocalizedUiMessageRendersTheMappedKey() {
        XCTAssertEqual(localizedUiMessage(for: "REMOTE.UNAVAILABLE"), String(localized: "error_backup_offline"))
    }

    func testDeviceWithoutAnAvailableAccountGetsItsOwnMessage() {
        let message = localizedUiMessage(for: "AUTH.NO_ACCOUNT_AVAILABLE")

        XCTAssertEqual(
            message,
            String(localized: "error_auth_no_account"),
            "A device with no account to offer needs the actionable message, not the generic one."
        )
    }

    func testAccountCollisionConfirmationExplainsTheDestructiveReplacement() {
        let message = localizedUiMessage(for: "CONFIRMATION.AdoptExistingAccount")

        XCTAssertEqual(message, String(localized: "confirm_replace_existing_account"))

        // The copy MUST state both the replacement and its irreversibility in every shipped
        // language. Each bundle is read directly, because asserting the running locale's copy
        // would make the test pass or fail on the simulator's language rather than on the copy.
        let required = [
            "en": ["permanently replaces", "cannot be undone"],
            "es": ["sustituye permanentemente", "No se puede deshacer"],
        ]

        for (language, phrases) in required {
            let copy = localizedCopy("confirm_replace_existing_account", language: language)
            for phrase in phrases {
                XCTAssertTrue(copy.contains(phrase), "The \(language) copy must contain \"\(phrase)\".")
            }
        }
    }

    func testDepartureConfirmationsHaveTheirOwnDestructiveCopy() {
        let deleteAccount = localizedUiMessage(for: "CONFIRMATION.DeleteAccount")
        let deleteLocalData = localizedUiMessage(for: "CONFIRMATION.DeleteLocalData")

        XCTAssertEqual(deleteAccount, String(localized: "confirm_delete_account"))
        XCTAssertEqual(deleteLocalData, String(localized: "confirm_delete_local_data"))
        // Deleting an account and clearing this device are different consequences and must not
        // share copy, which is why they no longer share a confirmation either.
        XCTAssertNotEqual(deleteAccount, deleteLocalData)

        let required = [
            "en": ["cannot be undone", "cannot be undone"],
            "es": ["No se puede deshacer", "no se puede deshacer"],
        ]
        for (language, phrases) in required {
            XCTAssertTrue(localizedCopy("confirm_delete_account", language: language).contains(phrases[0]))
            XCTAssertTrue(localizedCopy("confirm_delete_local_data", language: language).contains(phrases[1]))
        }
    }

    /// `docs/CONTRACTS.md §11.5` step 1: a stale login suspends the deletion until the owner signs in
    /// again, and the departure screen says so in both languages instead of the generic fallback.
    func testAStaleLoginAsksTheOwnerToSignInAgain() {
        XCTAssertEqual(
            localizedUiMessage(for: "AUTH.REQUIRES_RECENT_LOGIN"),
            String(localized: "error_auth_requires_recent_login")
        )
        let required = ["en": "sign in again", "es": "vuelve a iniciar sesión"]
        for (language, phrase) in required {
            XCTAssertTrue(
                localizedCopy("error_auth_requires_recent_login", language: language).contains(phrase),
                "The \(language) copy must contain \"\(phrase)\"."
            )
        }
    }

    /// `§20.10`: a server failure preserves local data and does not report the account as deleted.
    func testARemoteDeletionFailureSaysTheDataIsStillOnThisDevice() {
        XCTAssertEqual(
            localizedUiMessage(for: "AUTH.ACCOUNT_DELETION_REMOTE_FAILED"),
            String(localized: "error_account_deletion_remote_failed")
        )
        let required = ["en": "still on this device", "es": "siguen en este dispositivo"]
        for (language, phrase) in required {
            XCTAssertTrue(
                localizedCopy("error_account_deletion_remote_failed", language: language).contains(phrase),
                "The \(language) copy must contain \"\(phrase)\"."
            )
        }
    }

    /// The warning has its own sentence. The prompt states the exact count separately through
    /// `pending_sync_body`, because `UiMessage` carries only the code (`D-157`).
    func testThePendingSyncWarningHasItsOwnMessage() {
        XCTAssertEqual(localizedUiMessage(for: "WARNING.PENDING_SYNC"), String(localized: "pending_sync_title"))
        XCTAssertNotEqual(localizedUiMessage(for: "WARNING.PENDING_SYNC"), String(localized: "error_unexpected"))
    }

    private func localizedCopy(_ key: String, language: String) -> String {
        for candidate in [Bundle.main, Bundle(for: type(of: self))] {
            if
                let path = candidate.path(forResource: language, ofType: "lproj"),
                let bundle = Bundle(path: path)
            {
                return bundle.localizedString(forKey: key, value: nil, table: nil)
            }
        }
        XCTFail("Missing \(language).lproj for \(key)")
        return ""
    }

    /// A failed manual backup retry reaches this host as `PERSISTENCE.TRANSACTION_FAILED`
    /// (`docs/adr/0193`). The vehicle-list indicator renders it through this mapping, so the code
    /// MUST keep resolving to the existing persistence copy rather than to the generic fallback.
    func testTransactionFailureUsesPersistenceMessage() {
        XCTAssertEqual(
            localizedUiMessage(for: "PERSISTENCE.TRANSACTION_FAILED"),
            String(localized: "error_persistence")
        )
    }

    /// Every `ConsumptionInvalidReason`, and the absent one, names a key with copy in both languages.
    /// `String(localized:)` never returns an empty string, so the previous `isEmpty` check could not fail.
    func testEveryConsumptionExplanationResolvesInEnglishAndSpanish() {
        let reasons: [ConsumptionInvalidReason?] = [
            .noPreviousFullTank,
            .endEntryNotFullTank,
            .missedEntriesInSegment,
            .inconsistentOdometerInSegment,
            .nonPositiveDistance,
            .duplicateOdometerInSegment,
            nil,
        ]

        for reason in reasons {
            let key = consumptionExplanationKey(for: reason)
            for language in ["en", "es"] {
                XCTAssertNotEqual(localizedCopy(key, language: language), key, "\(String(describing: reason)) has no \(language) copy")
            }
        }
    }

    /// `D-127`: every one of the five MVP fuel types has an English and a Spanish display string. A
    /// missing key would render the raw key at runtime, so both catalogues must resolve all five.
    func testBothLanguagesNameEveryMvpFuelType() {
        let keys = ["fuel_type_gasoline", "fuel_type_diesel", "fuel_type_lpg", "fuel_type_cng", "fuel_type_other"]

        for language in ["en", "es"] {
            for key in keys {
                let value = localizedCopy(key, language: language)
                XCTAssertFalse(value.isEmpty, "\(key) must exist in \(language)")
                XCTAssertNotEqual(value, key, "\(key) must resolve to display copy in \(language)")
            }
        }
    }
}
