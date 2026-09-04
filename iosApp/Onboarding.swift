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
