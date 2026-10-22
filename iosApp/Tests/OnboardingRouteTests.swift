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

    func testAnUnreadableListIsDistinguishedFromAListThatIsStillArriving() {
        XCTAssertEqual(vehicleListGate(isLoading: true, hasMessage: false), .waiting)
        XCTAssertEqual(vehicleListGate(isLoading: true, hasMessage: true), .unreadable)
    }

    func testAKnownListIsResolvedEvenWhileItCarriesATransientMessage() {
        XCTAssertEqual(vehicleListGate(isLoading: false, hasMessage: false), .resolved)
        XCTAssertEqual(vehicleListGate(isLoading: false, hasMessage: true), .resolved)
    }

    func testOwnerScopedNavigationIsResetWhenAKnownListBecomesUnknownWithoutAnError() {
        XCTAssertTrue(shouldResetOwnerScopedNavigation(gate: .waiting, previousGate: .resolved))
    }

    func testOwnerScopedNavigationSurvivesAReadFailureAndAnUnchangedGate() {
        XCTAssertFalse(shouldResetOwnerScopedNavigation(gate: .unreadable, previousGate: .resolved))
        XCTAssertFalse(shouldResetOwnerScopedNavigation(gate: .resolved, previousGate: .resolved))
        XCTAssertFalse(shouldResetOwnerScopedNavigation(gate: .waiting, previousGate: .waiting))
    }

    func testFirstRunVehicleCreationOffersNoCancellation() {
        XCTAssertFalse(VehicleCreationPresentation(isFirstRun: true).offersCancellation)
        XCTAssertTrue(VehicleCreationPresentation(isFirstRun: false).offersCancellation)
    }

    /// `D-214`: a running deletion is drawn by the departure screen, not by a bare indicator that would
    /// hide its failure and retry (`docs/CONTRACTS.md §20.10`).
    func testADeletionInProgressRoutesToTheDepartureScreen() {
        XCTAssertEqual(resolveOnboardingDestination(sessionPhase: .deleting, vehicleCount: 0), .departure)
        XCTAssertEqual(resolveOnboardingDestination(sessionPhase: .deleting, vehicleCount: 2), .departure)
    }

    func testTheDepartureScreenOutranksEveryPhaseWhenRequested() {
        for phase in [SessionPhase.unknown, .local, .anonymous, .permanent, .signedOut, .deleting] {
            XCTAssertEqual(
                resolveOnboardingDestination(sessionPhase: phase, vehicleCount: 1, showsDepartureScreen: true),
                .departure,
                "\(phase)"
            )
        }
    }
}
