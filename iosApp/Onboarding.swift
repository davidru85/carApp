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

/// What the host may do with the vehicle list. `waiting` and `unreadable` both mean the list is not
/// known, but only `unreadable` has an error to show and a retry to offer (`D-120`).
enum VehicleListGate {
    case resolved
    case waiting
    case unreadable
}

func vehicleListGate(isLoading: Bool, hasMessage: Bool) -> VehicleListGate {
    if !isLoading {
        return .resolved
    }
    return hasMessage ? .unreadable : .waiting
}

/// A known list that becomes unknown without an error is an owner transition: the shared holder
/// cleared that owner's list, selection and message, so navigation built for the previous owner must
/// not survive. A read failure is not a transition and keeps the navigation it had.
func shouldResetOwnerScopedNavigation(gate: VehicleListGate, previousGate: VehicleListGate) -> Bool {
    previousGate == .resolved && gate == .waiting
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

    /// F-1 first-vehicle creation is the only forward step, so it also refuses the interactive
    /// dismissal gesture. Later creation stays dismissible.
    var isMandatory: Bool { isFirstRun }
}
