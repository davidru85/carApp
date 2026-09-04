import Shared
import SwiftUI
import UIKit

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
            // The authenticated surface is mounted once. An unresolved vehicle list is covered
            // rather than replaced, so no navigation state is torn down while it resolves.
            VehicleListView(graph: graph, skeletonModel: model)
                .overlay {
                    if model.vehicleListState.isLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(Color(UIColor.systemBackground))
                    }
                }
        }
    }
}
