import Shared
import SwiftUI

struct ContentView: View {
    @ObservedObject var model: WalkingSkeletonModel
    let graph: SwiftAppGraph

    private var destination: OnboardingDestination {
        if ProcessInfo.processInfo.environment["CARAPP_UI_TEST_FORCE_WELCOME"] == "1" {
            return .welcome
        }
        let session = model.sessionState
        return resolveOnboardingDestination(
            sessionPhase: session.phase,
            vehicleCount: model.vehicleListState.vehicles.count,
            showsDepartureScreen: requiresDepartureScreen(
                phase: session.phase,
                messageCode: session.message?.code,
                hasPendingDepartureRetry: session.pendingDepartureRetry != nil,
                isReauthenticating: model.isReauthenticating
            )
        )
    }

    var body: some View {
        #if DEBUG
        // `E4-02`: UI tests cannot make a real departure fail, so this Debug-only seam renders the
        // B1-D4 departure screen in its failed state for the accessibility audits. It cannot exist in
        // a Release build, like CARAPP_UI_TEST_FORCE_WELCOME.
        if ProcessInfo.processInfo.environment["CARAPP_UI_TEST_FORCE_DEPARTURE_FAILURE"] == "1" {
            DepartureView(
                state: forcedDepartureFailureState(),
                isReauthenticating: false,
                onRetry: {},
                onDismissMessage: {},
                onReauthenticateWithGoogle: {},
                onPrepareAppleReauthentication: { _ in },
                onCompleteAppleAuthorization: { _ in },
                onCancelReauthentication: {}
            )
        } else {
            routedContent
        }
        #else
        routedContent
        #endif
    }

    @ViewBuilder
    private var routedContent: some View {
switch destination {
        case .waiting:
            ProgressView()
        case .departure:
            // `B1-D4`: progress, the typed failure and its retry, and the re-authentication of a
            // stale-login deletion stay reachable while the signed-in surface is unmounted.
            DepartureView(
                state: model.sessionState,
                isReauthenticating: model.isReauthenticating,
                onRetry: model.retryDeparture,
                onDismissMessage: model.dismissSessionMessage,
                onReauthenticateWithGoogle: model.startGoogleReauthentication,
                onPrepareAppleReauthentication: model.prepareAppleReauthentication,
                onCompleteAppleAuthorization: model.completeAppleAuthorization,
                onCancelReauthentication: model.cancelReauthentication
            )
        case .welcome:
            WelcomeView(
                state: model.sessionState,
                signInCoordinator: model.signInCoordinator,
                onContinueWithoutAccount: model.startAnonymousSession
            )
        case .firstVehicle, .vehicleList:
            // The authenticated surface is mounted once, and it owns its own unresolved-list
            // indicator. Gating it from here would cover the list from a second observer of the
            // same state holder, which can lag behind the one that decides what to present.
            VStack(spacing: 0) {
                // The notice sits above the product surface instead of over it, so it never gates
                // a feature.
                if let reminderIndex = model.sessionState.anonymousReminderIndex {
                    AnonymousReminderView(
                        index: reminderIndex.int32Value,
                        onDismiss: model.dismissAnonymousReminder
                    )
                }
                VehicleListView(graph: graph, skeletonModel: model)
            }
            // F-5 prompts and departure outcomes are drawn above the navigation stack, so the pushed
            // Settings screen and the list share one presenter.
            .modifier(DeparturePromptPresenter(model: model))
            // `E4-05`: the credential collision is presented only here, beside the presenter. The
            // presenter ignores `AdoptExistingAccount`, and this alert ignores everything else.
            .conversionCollisionAlert(
                confirmation: model.sessionState.message?.confirmation,
                onConfirm: model.confirmAccountConversion,
                onCancel: model.cancelAccountConversion
            )
        }
    }
}

#if DEBUG
/// The failed local clear the departure seam renders (`E4-02`); no real departure produces it here.
private func forcedDepartureFailureState() -> SessionUiState {
    SessionUiState(
        phase: .deleting,
        providers: [],
        isBusy: false,
        message: UiMessage(id: 1, kind: .error, code: "PERSISTENCE.TRANSACTION_FAILED", confirmation: nil),
        anonymousReminderIndex: nil,
        pendingSyncCount: nil,
        pendingDepartureRetry: .localClear
    )
}
#endif
