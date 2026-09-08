import Shared
import SwiftUI

struct ContentView: View {
    @ObservedObject var model: WalkingSkeletonModel
    let graph: SwiftAppGraph

    private var destination: OnboardingDestination {
        if ProcessInfo.processInfo.environment["CARAPP_UI_TEST_FORCE_WELCOME"] == "1" {
            return .welcome
        }
        return resolveOnboardingDestination(
            sessionPhase: model.sessionState.phase,
            vehicleCount: model.vehicleListState.vehicles.count
        )
    }

    var body: some View {
        switch destination {
        case .waiting:
            ProgressView()
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
        }
    }
}
