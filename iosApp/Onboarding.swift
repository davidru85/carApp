import Foundation
import Shared

enum OnboardingDestination {
    case waiting
    case welcome
    case firstVehicle
    case vehicleList
}

func resolveOnboardingDestination(
    sessionPhase: SessionPhase,
    vehicleCount: Int
) -> OnboardingDestination {
    switch sessionPhase {
    case .unknown, .deleting:
        return .waiting
    case .signedOut:
        return .welcome
    case .local, .anonymous, .permanent:
        return vehicleCount == 0 ? .firstVehicle : .vehicleList
    }
}

/// F-1 routes an authenticated owner without vehicles to first-vehicle creation. The decision waits
/// for the vehicle list to be known, so an unresolved list never presents the form, and it is taken
/// once, so saving the first vehicle does not re-present it.
func shouldPresentFirstVehicleCreation(
    isVehicleListKnown: Bool,
    vehicleCount: Int,
    alreadyPresented: Bool
) -> Bool {
    isVehicleListKnown && vehicleCount == 0 && !alreadyPresented
}

/// First-run creation is the same form without a way out: the owner is already signed in, so the
/// only forward step is creating the vehicle.
struct VehicleCreationPresentation: Identifiable {
    let id = UUID()
    let isFirstRun: Bool

    var offersCancellation: Bool { !isFirstRun }
}
