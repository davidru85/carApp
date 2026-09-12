import XCTest

final class OnboardingWaitPolicyTests: XCTestCase {
    func testOnboardingWaitRetriesEachAffordanceThatRemainsAvailable() {
        let guestPosition = OnboardingTapPosition(dx: 0.5, dy: 0.5)
        let addVehiclePosition = OnboardingTapPosition(dx: 0.4, dy: 0.6)

        var guestState = OnboardingWaitState()
        XCTAssertEqual(
            guestState.nextAction(destinationReached: false, positions: [.guest: guestPosition]),
            .tap(.guest, at: guestPosition)
        )
        XCTAssertEqual(
            guestState.nextAction(destinationReached: false, positions: [.guest: guestPosition]),
            .tap(.guest, at: guestPosition),
            "An enabled, hittable affordance must be retried after an ineffective tap"
        )

        var vehicleState = OnboardingWaitState()
        XCTAssertEqual(
            vehicleState.nextAction(destinationReached: false, positions: [.addVehicle: addVehiclePosition]),
            .tap(.addVehicle, at: addVehiclePosition)
        )
        XCTAssertEqual(
            vehicleState.nextAction(destinationReached: false, positions: [.addVehicle: addVehiclePosition]),
            .tap(.addVehicle, at: addVehiclePosition),
            "An enabled, hittable affordance must be retried after an ineffective tap"
        )
    }

    /// A disabled XCUIElement still reports `isHittable == true`, so availability must also require
    /// `isEnabled`. `WelcomeView.swift` disables `welcome_guest` while the Firebase request is in
    /// flight, and treating that as a tap opportunity produced the CI run's 50 no-op taps.
    func testOnboardingAvailabilityExcludesDisabledAffordances() {
        XCTAssertFalse(
            OnboardingTapPosition.isAvailable(exists: true, isEnabled: false, isHittable: true),
            "A present but disabled affordance is a waiting state, not a tap opportunity"
        )
        XCTAssertFalse(
            OnboardingTapPosition.isAvailable(exists: true, isEnabled: true, isHittable: false)
        )
        XCTAssertFalse(
            OnboardingTapPosition.isAvailable(exists: false, isEnabled: true, isHittable: true)
        )
        XCTAssertTrue(
            OnboardingTapPosition.isAvailable(exists: true, isEnabled: true, isHittable: true)
        )
    }

    /// The retry action must carry the target whose accessibility identifier it drives. Without this
    /// guard, wiring `.tap(.guest)` to the add-vehicle button would pass the policy tests unchanged.
    func testOnboardingTapTargetsMapToTheirAccessibilityIdentifiers() {
        XCTAssertEqual(OnboardingTapTarget.guest.accessibilityIdentifier, "welcome_guest")
        XCTAssertEqual(OnboardingTapTarget.addVehicle.accessibilityIdentifier, "add_vehicle")
        XCTAssertEqual(OnboardingTapTarget.guest.enteredStep, .startingGuestSession)
        XCTAssertEqual(OnboardingTapTarget.addVehicle.enteredStep, .openingVehicleCreation)
        XCTAssertEqual(Set(OnboardingTapTarget.allCases.map(\.accessibilityIdentifier)), ["welcome_guest", "add_vehicle"])
    }

    /// The step never moves backwards, so a reappearing affordance cannot reset the wait budget. The
    /// previous implementation restarted its deadline on every step change, leaving runtime unbounded.
    func testOnboardingStepNeverMovesBackwardsWhenAGuestReappears() {
        let guestPosition = OnboardingTapPosition(dx: 0.5, dy: 0.5)
        let addVehiclePosition = OnboardingTapPosition(dx: 0.4, dy: 0.6)
        var state = OnboardingWaitState()

        _ = state.nextAction(destinationReached: false, positions: [.guest: guestPosition])
        XCTAssertEqual(state.step, .startingGuestSession)

        _ = state.nextAction(destinationReached: false, positions: [.addVehicle: addVehiclePosition])
        XCTAssertEqual(state.step, .openingVehicleCreation)

        let action = state.nextAction(destinationReached: false, positions: [.guest: guestPosition, .addVehicle: addVehiclePosition])
        XCTAssertEqual(state.step, .openingVehicleCreation, "The step must not regress to a previous step")
        XCTAssertEqual(action, .tap(.addVehicle, at: addVehiclePosition))
    }

    /// The budget is a single absolute interval measured from the start, independent of step
    /// transitions, so total runtime is bounded.
    func testOnboardingAbsoluteCapDoesNotResetOnStepChange() {
        let start = Date(timeIntervalSince1970: 1_000_000)
        let limit = OnboardingWaitBudget.absoluteLimit
        XCTAssertFalse(OnboardingWaitBudget.hasReachedDeadline(startedAt: start, now: start.addingTimeInterval(limit - 0.001)))
        XCTAssertTrue(OnboardingWaitBudget.hasReachedDeadline(startedAt: start, now: start.addingTimeInterval(limit)))
        XCTAssertTrue(OnboardingWaitBudget.hasReachedDeadline(startedAt: start, now: start.addingTimeInterval(limit * 4)))
    }

    /// The tap action must not retain an `XCUIElement` that can disappear before the tap; it carries
    /// a snapshotted application-relative position instead.
    func testOnboardingWaitCarriesTheSnapshottedTapPosition() {
        let guestPosition = OnboardingTapPosition(dx: 0.25, dy: 0.75)
        var waitState = OnboardingWaitState()

        XCTAssertEqual(
            waitState.nextAction(destinationReached: false, positions: [.guest: guestPosition]),
            .tap(.guest, at: guestPosition),
            "The tap action must not retain an XCUIElement that can disappear before the tap"
        )
    }

    func testOnboardingWaitNamesGuestSessionTimeout() {
        var guestState = OnboardingWaitState()
        _ = guestState.nextAction(destinationReached: false, positions: [.guest: OnboardingTapPosition(dx: 0.5, dy: 0.5)])
        XCTAssertEqual(guestState.step, .startingGuestSession)
        XCTAssertTrue(guestState.timeoutMessage.contains("vehicle list"), guestState.timeoutMessage)
    }

    func testOnboardingWaitNamesVehicleCreationTimeout() {
        var vehicleState = OnboardingWaitState()
        _ = vehicleState.nextAction(destinationReached: false, positions: [.addVehicle: OnboardingTapPosition(dx: 0.5, dy: 0.5)])
        XCTAssertEqual(vehicleState.step, .openingVehicleCreation)
        XCTAssertTrue(vehicleState.timeoutMessage.contains("Vehicle creation"), vehicleState.timeoutMessage)
    }

    func testOnboardingWaitCompletesWhenTheDestinationIsReached() {
        var state = OnboardingWaitState()
        XCTAssertEqual(state.nextAction(destinationReached: true, positions: [:]), .complete)
    }
}
