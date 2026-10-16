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
