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

    func testFirstVehicleCreationWaitsUntilTheVehicleListIsKnown() {
        XCTAssertFalse(
            shouldPresentFirstVehicleCreation(
                isVehicleListKnown: false,
                vehicleCount: 0,
                alreadyPresented: false
            )
        )
        XCTAssertTrue(
            shouldPresentFirstVehicleCreation(
                isVehicleListKnown: true,
                vehicleCount: 0,
                alreadyPresented: false
            )
        )
    }

    func testFirstVehicleCreationIsPresentedOnceAndNeverForAnOwnerThatHasVehicles() {
        XCTAssertFalse(
            shouldPresentFirstVehicleCreation(
                isVehicleListKnown: true,
                vehicleCount: 1,
                alreadyPresented: false
            )
        )
        XCTAssertFalse(
            shouldPresentFirstVehicleCreation(
                isVehicleListKnown: true,
                vehicleCount: 0,
                alreadyPresented: true
            )
        )
    }

    func testFirstRunVehicleCreationOffersNoCancellation() {
        XCTAssertFalse(VehicleCreationPresentation(isFirstRun: true).offersCancellation)
        XCTAssertTrue(VehicleCreationPresentation(isFirstRun: false).offersCancellation)
    }
}
