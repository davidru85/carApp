import Shared
import XCTest
@testable import carApp

final class OnboardingRouteTests: XCTestCase {
    func testUnknownAuthenticationNeverRoutes() {
        XCTAssertEqual(resolveOnboardingDestination(sessionPhase: .unknown, vehicleCount: 0), .waiting)
        XCTAssertEqual(resolveOnboardingDestination(sessionPhase: .unknown, vehicleCount: 3), .waiting)
    }

    func testSignedOutAuthenticationRoutesToWelcome() {
        XCTAssertEqual(resolveOnboardingDestination(sessionPhase: .signedOut, vehicleCount: 0), .welcome)
    }

    func testAuthenticatedOwnerWithoutVehiclesRoutesToFirstVehicleCreation() {
        for phase in [SessionPhase.local, .anonymous, .permanent] {
            XCTAssertEqual(
                resolveOnboardingDestination(sessionPhase: phase, vehicleCount: 0),
                .firstVehicle
            )
        }
    }

    func testAuthenticatedOwnerWithVehiclesRoutesToVehicleList() {
        for phase in [SessionPhase.local, .anonymous, .permanent] {
            XCTAssertEqual(
                resolveOnboardingDestination(sessionPhase: phase, vehicleCount: 2),
                .vehicleList
            )
        }
    }
}
