import Foundation
import Shared
import XCTest
@testable import carApp

/// The host decisions behind the F-5 departure surface: when the root shows the departure screen
/// (`D-214`), what that screen draws, and which prompt a published session message becomes.
///
/// Every decision reads published `SessionUiState` facts only. The departure rules themselves belong to
/// `AccountDepartureFlow` and are tested in `:shared` (`SessionDeparture*Test`); these tests pin how
/// the host draws them.
final class DeparturePresentationTests: XCTestCase {
    // MARK: - Routing

    func testARunningDeletionRequiresTheDepartureScreen() {
        XCTAssertTrue(
            requiresDepartureScreen(phase: .deleting, messageCode: nil, hasPendingDepartureRetry: false, isReauthenticating: false)
        )
    }

    /// `D-234`: the hand-over between a successful remote step and the local clear is published as
    /// `UNKNOWN` with `isBusy`, and the departure screen keeps the window, exactly as on Android.
    func testTheHandoverAfterARemoteStepRequiresTheDepartureScreen() {
        XCTAssertTrue(
            requiresDepartureScreen(
                phase: .unknown,
                messageCode: nil,
                hasPendingDepartureRetry: false,
                isReauthenticating: false,
                isBusy: true
            )
        )
    }

    /// `D-234`: an `UNKNOWN` session that is not busy is the cold start and keeps waiting.
    func testAnIdleUnknownSessionDoesNotRequireTheDepartureScreen() {
        XCTAssertFalse(
            requiresDepartureScreen(
                phase: .unknown,
                messageCode: nil,
                hasPendingDepartureRetry: false,
                isReauthenticating: false,
                isBusy: false
            )
        )
    }

    /// `D-234`: `isBusy` routes only the `UNKNOWN` hand-over; a busy signed-in or signed-out session keeps
    /// its `D-214` routing.
    func testABusySessionOutsideUnknownDoesNotRequireTheDepartureScreen() {
        for phase in [SessionPhase.local, .anonymous, .permanent, .signedOut] {
            XCTAssertFalse(
                requiresDepartureScreen(
                    phase: phase,
                    messageCode: nil,
                    hasPendingDepartureRetry: false,
                    isReauthenticating: false,
                    isBusy: true
                ),
                "\(phase) must not route a busy session to the departure screen"
            )
        }
    }

    /// A failed step keeps the phase it failed in: `DELETING`, `UNKNOWN` after the remote handover, or
    /// `PERMANENT` for a sign-out whose provider sign-out failed. The retry is reachable in every one.
    func testAPublishedRetryRequiresTheDepartureScreenInEveryPhase() {
        for phase in [SessionPhase.unknown, .local, .anonymous, .permanent, .signedOut, .deleting] {
            XCTAssertTrue(
                requiresDepartureScreen(
                    phase: phase,
                    messageCode: "PERSISTENCE.TRANSACTION_FAILED",
                    hasPendingDepartureRetry: true,
                    isReauthenticating: false
                ),
                "\(phase) must route a published retry to the departure screen"
            )
        }
    }

    func testAStaleLoginRequiresTheDepartureScreenForAPermanentOwner() {
        XCTAssertTrue(
            requiresDepartureScreen(
                phase: .permanent,
                messageCode: "AUTH.REQUIRES_RECENT_LOGIN",
                hasPendingDepartureRetry: false,
                isReauthenticating: false
            )
        )
    }

    /// The native Apple button must stay mounted until its completion arrives, so an attempt the owner
    /// started keeps the screen even after `startReauthentication` withdrew the message.
    func testAReauthenticationInProgressKeepsTheDepartureScreen() {
        XCTAssertTrue(
            requiresDepartureScreen(phase: .permanent, messageCode: nil, hasPendingDepartureRetry: false, isReauthenticating: true)
        )
    }

    func testAnOrdinarySignedInSessionDoesNotRequireTheDepartureScreen() {
        XCTAssertFalse(
            requiresDepartureScreen(phase: .permanent, messageCode: nil, hasPendingDepartureRetry: false, isReauthenticating: false)
        )
        XCTAssertFalse(
            requiresDepartureScreen(
                phase: .anonymous,
                messageCode: "AUTH.NETWORK_UNAVAILABLE",
                hasPendingDepartureRetry: false,
                isReauthenticating: false
            )
        )
    }

    func testTheReauthenticationFlagIsIgnoredOutsideAPermanentSession() {
        XCTAssertFalse(
            requiresDepartureScreen(phase: .signedOut, messageCode: nil, hasPendingDepartureRetry: false, isReauthenticating: true)
        )
    }

    // MARK: - Departure screen stage

    func testARunningDepartureShowsProgress() {
        XCTAssertEqual(
            departureStage(phase: .deleting, isBusy: true, messageCode: nil, pendingDepartureRetry: nil, isReauthenticating: false),
            .inProgress
        )
    }

    func testAFailedStepOffersThePublishedRetry() {
        XCTAssertEqual(
            departureStage(
                phase: .unknown,
                isBusy: false,
                messageCode: "PERSISTENCE.TRANSACTION_FAILED",
                pendingDepartureRetry: .localClear,
                isReauthenticating: false
            ),
            .failed(code: "PERSISTENCE.TRANSACTION_FAILED", retry: .localClear)
        )
    }

    /// `clearMessage()` keeps `pendingDepartureRetry`, so the retry stays offered without its message.
    func testAPublishedRetryIsOfferedEvenWithoutAMessage() {
        XCTAssertEqual(
            departureStage(phase: .permanent, isBusy: false, messageCode: nil, pendingDepartureRetry: .sessionCleanup, isReauthenticating: false),
            .failed(code: nil, retry: .sessionCleanup)
        )
    }

    func testAFailureWithoutARetryOffersADismissal() {
        XCTAssertEqual(
            departureStage(
                phase: .deleting,
                isBusy: false,
                messageCode: "PERSISTENCE.TRANSACTION_FAILED",
                pendingDepartureRetry: nil,
                isReauthenticating: false
            ),
            .failed(code: "PERSISTENCE.TRANSACTION_FAILED", retry: nil)
        )
    }

    func testAStaleLoginAsksForReauthentication() {
        XCTAssertEqual(
            departureStage(
                phase: .permanent,
                isBusy: false,
                messageCode: "AUTH.REQUIRES_RECENT_LOGIN",
                pendingDepartureRetry: nil,
                isReauthenticating: false
            ),
            .reauthenticationRequired(failureCode: nil)
        )
    }

    /// `docs/CONTRACTS.md §20.10`: a failed re-authentication keeps the request, so the providers stay
    /// offered and the failure is shown beside them.
    func testAFailedReauthenticationStaysOnTheStageAndShowsTheFailure() {
        XCTAssertEqual(
            departureStage(
                phase: .permanent,
                isBusy: false,
                messageCode: "AUTH.NETWORK_UNAVAILABLE",
                pendingDepartureRetry: nil,
                isReauthenticating: true
            ),
            .reauthenticationRequired(failureCode: "AUTH.NETWORK_UNAVAILABLE")
        )
    }

    func testAReauthenticationInFlightKeepsTheProviderButtonsMounted() {
        XCTAssertEqual(
            departureStage(phase: .permanent, isBusy: true, messageCode: nil, pendingDepartureRetry: nil, isReauthenticating: true),
            .reauthenticationRequired(failureCode: nil)
        )
    }

    // MARK: - Prompts

    func testAPendingSyncWarningForAPermanentOwnerDiscardsBySigningOut() {
        XCTAssertEqual(
            departurePrompt(
                phase: .permanent,
                messageCode: "WARNING.PENDING_SYNC",
                messageKind: .warning,
                confirmation: .discardPendingChanges,
                pendingSyncCount: 7
            ),
            .pendingSync(count: 7, intent: .signOut)
        )
    }

    func testAPendingSyncWarningForALocalOrAnonymousOwnerDiscardsBeforeDeletingLocalData() {
        for phase in [SessionPhase.local, .anonymous] {
            XCTAssertEqual(
                departurePrompt(
                    phase: phase,
                    messageCode: "WARNING.PENDING_SYNC",
                    messageKind: .warning,
                    confirmation: .discardPendingChanges,
                    pendingSyncCount: 2
                ),
                .pendingSync(count: 2, intent: .deleteLocalData),
                "\(phase)"
            )
        }
    }

    func testTheAccountDeletionConfirmationHasItsOwnPrompt() {
        XCTAssertEqual(
            departurePrompt(
                phase: .permanent,
                messageCode: "CONFIRMATION.DeleteAccount",
                messageKind: .warning,
                confirmation: .deleteAccount,
                pendingSyncCount: nil
            ),
            .confirmDeleteAccount
        )
    }

    func testTheLocalDataConfirmationHasItsOwnPrompt() {
        XCTAssertEqual(
            departurePrompt(
                phase: .anonymous,
                messageCode: "CONFIRMATION.DeleteLocalData",
                messageKind: .warning,
                confirmation: .deleteLocalData,
                pendingSyncCount: nil
            ),
            .confirmDeleteLocalData
        )
    }

    func testAnErrorOnTheSignedInSurfaceIsReportedAsAFailure() {
        XCTAssertEqual(
            departurePrompt(
                phase: .permanent,
                messageCode: "AUTH.ACCOUNT_DELETION_REMOTE_FAILED",
                messageKind: .error,
                confirmation: nil,
                pendingSyncCount: nil
            ),
            .failure(code: "AUTH.ACCOUNT_DELETION_REMOTE_FAILED")
        )
    }

    /// A failed anonymous sign-in publishes `LOCAL` with a `WARNING` (`StateHolders.kt:180-195`). It
    /// is not a departure outcome and MUST NOT interrupt the signed-in surface with an alert.
    func testAWarningIsNeverReportedAsAFailure() {
        XCTAssertNil(
            departurePrompt(
                phase: .local,
                messageCode: "AUTH.NETWORK_UNAVAILABLE",
                messageKind: .warning,
                confirmation: nil,
                pendingSyncCount: nil
            )
        )
    }

    func testAStaleLoginIsLeftToTheDepartureScreen() {
        XCTAssertNil(
            departurePrompt(
                phase: .permanent,
                messageCode: "AUTH.REQUIRES_RECENT_LOGIN",
                messageKind: .error,
                confirmation: nil,
                pendingSyncCount: nil
            )
        )
    }

    /// The F-4 collision confirmation belongs to the `E4-05` conversion surface.
    func testTheConversionCollisionIsLeftToItsOwnSurface() {
        XCTAssertNil(
            departurePrompt(
                phase: .anonymous,
                messageCode: "CONFIRMATION.AdoptExistingAccount",
                messageKind: .warning,
                confirmation: .adoptExistingAccount,
                pendingSyncCount: nil
            )
        )
    }

    func testNoPromptIsRaisedOutsideTheSignedInSurface() {
        for phase in [SessionPhase.unknown, .signedOut, .deleting] {
            XCTAssertNil(
                departurePrompt(
                    phase: phase,
                    messageCode: "AUTH.NETWORK_UNAVAILABLE",
                    messageKind: .error,
                    confirmation: nil,
                    pendingSyncCount: nil
                ),
                "\(phase)"
            )
        }
    }

    /// `§20.10`: only a permanent owner can sign out, and a permanent deletion never counts the outbox,
    /// so the phase that published the warning names the request the discard answers.
    func testOnlyAPermanentOwnerDiscardsBySigningOut() {
        XCTAssertEqual(departureDiscardIntent(for: .permanent), .signOut)
        XCTAssertEqual(departureDiscardIntent(for: .local), .deleteLocalData)
        XCTAssertEqual(departureDiscardIntent(for: .anonymous), .deleteLocalData)
        XCTAssertNil(departureDiscardIntent(for: .deleting))
    }

    // MARK: - Copy

    /// `D-157`: the count travels as typed state because `UiMessage` carries only a code, so the host
    /// MUST state it.
    func testThePendingSyncPromptStatesTheExactCount() {
        XCTAssertTrue(departurePromptMessage(.pendingSync(count: 7, intent: .signOut)).contains("7"))
    }

    func testThePendingSyncBodyCarriesOneCountPlaceholderInBothLanguages() throws {
        for language in ["en", "es"] {
            let copy = try localization(language).localizedString(forKey: "pending_sync_body", value: nil, table: nil)
            XCTAssertNotEqual(copy, "pending_sync_body", "\(language) is missing pending_sync_body.")
            XCTAssertEqual(copy.components(separatedBy: "%ld").count - 1, 1, "\(language) pending_sync_body needs one %ld.")
        }
    }

    func testAFailurePromptShowsTheMappedMessage() {
        XCTAssertEqual(
            departurePromptMessage(.failure(code: "PERSISTENCE.TRANSACTION_FAILED")),
            localizedUiMessage(for: "PERSISTENCE.TRANSACTION_FAILED")
        )
    }

    /// Each destructive prompt shows the `§20.2` copy of its own confirmation (`D-158`).
    func testEachConfirmationPromptShowsItsOwnDestructiveCopy() {
        XCTAssertEqual(departurePromptMessage(.confirmDeleteAccount), localizedUiMessage(for: "CONFIRMATION.DeleteAccount"))
        XCTAssertEqual(departurePromptMessage(.confirmDeleteLocalData), localizedUiMessage(for: "CONFIRMATION.DeleteLocalData"))
    }

    func testEveryPromptHasItsOwnTitleWithCopyInBothLanguages() throws {
        let keys = [
            DeparturePrompt.pendingSync(count: 1, intent: .signOut),
            .confirmDeleteAccount,
            .confirmDeleteLocalData,
            .failure(code: "UNEXPECTED"),
        ].map(departurePromptTitleKey)

        XCTAssertEqual(Set(keys).count, 4, "Four prompts drawn with repeated titles cannot be told apart")
        try assertCopyExists(for: keys)
    }

    func testEachDiscardHasItsOwnLabelWithCopyInBothLanguages() throws {
        let keys = [DepartureDiscardIntent.signOut, .deleteLocalData].map(departureDiscardLabelKey)

        XCTAssertEqual(Set(keys).count, 2, "Discarding before a sign-out and before a deletion do different things")
        try assertCopyExists(for: keys)
    }

    func testEachRetryHasItsOwnLabelWithCopyInBothLanguages() throws {
        let keys = [DepartureRetry.sessionCleanup, .localClear].map(departureRetryLabelKey)

        XCTAssertEqual(Set(keys).count, 2, "The two retries repeat different steps")
        try assertCopyExists(for: keys)
    }

    func testTheDepartureScreenCopyExistsInBothLanguages() throws {
        try assertCopyExists(for: ["departure_in_progress", "reauth_title", "ok", "pending_sync_wait"])
    }

    // MARK: - Re-authentication providers

    /// Firebase re-authenticates with a credential of the provider the account is linked to, so only
    /// those providers are offered, Apple first as on the welcome screen.
    func testReauthenticationOffersOnlyTheProvidersLinkedToTheAccount() {
        XCTAssertEqual(reauthenticationProviders([.google]), [.google])
        XCTAssertEqual(reauthenticationProviders([.google, .apple]), [.apple, .google])
    }

    func testAnAccountWithoutANativeProviderIsOfferedBothProviders() {
        XCTAssertEqual(reauthenticationProviders([]), [.apple, .google])
        XCTAssertEqual(reauthenticationProviders([.anonymous]), [.apple, .google])
    }

    private func assertCopyExists(
        for keys: [String],
        file: StaticString = #filePath,
        line: UInt = #line
    ) throws {
        for language in ["en", "es"] {
            let catalogue = try localization(language)
            for key in keys {
                let copy = catalogue.localizedString(forKey: key, value: nil, table: nil)
                XCTAssertNotEqual(copy, key, "\(language) is missing \(key).", file: file, line: line)
                XCTAssertFalse(copy.isEmpty, "\(language) has an empty value for \(key).", file: file, line: line)
            }
        }
    }

    /// The catalogues live in the application bundle, not in the test bundle, so the lookup is
    /// anchored to a class the application target owns.
    private func localization(_ language: String) throws -> Bundle {
        let applicationBundle = Bundle(for: WalkingSkeletonModel.self)
        let path = try XCTUnwrap(
            applicationBundle.path(forResource: language, ofType: "lproj"),
            "The application bundle carries no \(language) localization."
        )
        return try XCTUnwrap(Bundle(path: path))
    }
}
