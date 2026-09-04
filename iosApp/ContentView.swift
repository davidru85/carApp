import Shared
import SwiftUI

struct ContentView: View {
    @ObservedObject var model: WalkingSkeletonModel
    let graph: SwiftAppGraph

    private var destination: OnboardingDestination {
        if ProcessInfo.processInfo.environment["CARAPP_UI_TEST_FORCE_WELCOME"] == "1" {
            return .welcome
        }
        let isAuthenticated = [.local, .anonymous, .permanent].contains(model.sessionState.phase)
        if isAuthenticated && model.vehicleListState.isLoading {
            return .waiting
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
        case .firstVehicle:
            VehicleFormView(graph: graph, vehicleId: nil)
        case .vehicleList:
            VehicleListView(graph: graph, skeletonModel: model)
        }
    }
}
