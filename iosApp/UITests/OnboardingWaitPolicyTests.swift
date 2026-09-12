import XCTest

final class OnboardingWaitPolicyTests: XCTestCase {
    func testOnboardingWaitRetriesEachAffordanceThatRemainsAvailable() {
        let guestPosition = OnboardingTapPosition(dx: 0.5, dy: 0.5)
        let addVehiclePosition = OnboardingTapPosition(dx: 0.4, dy: 0.6)

        var guestState = OnboardingWaitState()
        XCTAssertEqual(
            guestState.nextAction(positions: [.guest: guestPosition]),
            .tap(.guest, at: guestPosition)
        )
        XCTAssertEqual(
            guestState.nextAction(positions: [.guest: guestPosition]),
            .tap(.guest, at: guestPosition),
            "An enabled, hittable affordance must be retried after an ineffective tap"
        )

        var vehicleState = OnboardingWaitState()
        XCTAssertEqual(
            vehicleState.nextAction(positions: [.addVehicle: addVehiclePosition]),
            .tap(.addVehicle, at: addVehiclePosition)
        )
        XCTAssertEqual(
            vehicleState.nextAction(positions: [.addVehicle: addVehiclePosition]),
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

        _ = state.nextAction(positions: [.guest: guestPosition])
        XCTAssertEqual(state.step, .startingGuestSession)

        _ = state.nextAction(positions: [.addVehicle: addVehiclePosition])
        XCTAssertEqual(state.step, .openingVehicleCreation)

        let action = state.nextAction(positions: [.guest: guestPosition, .addVehicle: addVehiclePosition])
        XCTAssertEqual(state.step, .openingVehicleCreation, "The step must not regress to a previous step")
        XCTAssertEqual(action, .tap(.addVehicle, at: addVehiclePosition))
    }

    /// The budget is a single absolute interval measured from the start, independent of step
    /// transitions, so total runtime is bounded. It accepts the effective limit so the driver can
    /// call the same function the test exercises instead of re-implementing the comparison.
    func testOnboardingAbsoluteCapDoesNotResetOnStepChange() {
        let start = Date(timeIntervalSince1970: 1_000_000)
        let limit = OnboardingWaitBudget.absoluteLimit
        XCTAssertFalse(
            OnboardingWaitBudget.hasReachedDeadline(
                effectiveLimit: limit,
                startedAt: start,
                now: start.addingTimeInterval(limit - 0.001)
            )
        )
        XCTAssertTrue(
            OnboardingWaitBudget.hasReachedDeadline(
                effectiveLimit: limit,
                startedAt: start,
                now: start.addingTimeInterval(limit)
            )
        )
        XCTAssertTrue(
            OnboardingWaitBudget.hasReachedDeadline(
                effectiveLimit: limit,
                startedAt: start,
                now: start.addingTimeInterval(limit * 4)
            )
        )
    }

    /// A caller-supplied effective limit is honoured, so the driver's loop condition and the test
    /// use one implementation.
    func testOnboardingAbsoluteCapHonoursTheEffectiveLimit() {
        let start = Date(timeIntervalSince1970: 1_000_000)
        XCTAssertFalse(
            OnboardingWaitBudget.hasReachedDeadline(
                effectiveLimit: 5,
                startedAt: start,
                now: start.addingTimeInterval(4.999)
            )
        )
        XCTAssertTrue(
            OnboardingWaitBudget.hasReachedDeadline(
                effectiveLimit: 5,
                startedAt: start,
                now: start.addingTimeInterval(5)
            )
        )
    }

    /// A vehicle form that never dismisses burns the budget without advancing the step, so the
    /// timeout must name the form and distinguish the iterations it stayed visible from the
    /// submissions the handler actually performed. Reporting the guest step instead, or counting
    /// iterations as submissions, described the wrong failure.
    func testOnboardingWaitDistinguishesAVehicleFormThatNeverDismisses() {
        var state = OnboardingWaitState()
        state.noteVehicleFormVisible(handlerActed: true)
        state.noteVehicleFormVisible(handlerActed: false)
        state.noteVehicleFormVisible(handlerActed: false)

        XCTAssertEqual(
            state.step,
            .waitingForAffordance,
            "Seeing the form is a non-terminal observation and must not move the affordance ladder"
        )
        XCTAssertTrue(state.vehicleFormWasSeen)
        XCTAssertEqual(state.vehicleFormVisibleIterations, 3)
        XCTAssertEqual(state.vehicleFormSubmissions, 1)

        let message = state.timeoutMessage(formVisibleAtTimeout: true)
        XCTAssertTrue(message.contains("vehicle form"), message)
        XCTAssertTrue(message.contains("1 real submission"), message)
    }

    /// The form message must key off the form being visible at the timeout, not off it having ever
    /// been seen. Once the form is dismissed and the wait advances, the message must name the step
    /// that was never reached instead of claiming the form is still there.
    func testOnboardingTimeoutNamesTheUnreachedStepAfterAFormWasSeen() {
        let addVehiclePosition = OnboardingTapPosition(dx: 0.4, dy: 0.6)
        var state = OnboardingWaitState()
        state.noteVehicleFormVisible(handlerActed: true)
        _ = state.nextAction(positions: [.addVehicle: addVehiclePosition])
        XCTAssertEqual(state.step, .openingVehicleCreation)

        let message = state.timeoutMessage(formVisibleAtTimeout: false)
        XCTAssertTrue(message.contains("Vehicle creation"), message)
        XCTAssertFalse(
            message.contains("never dismissed"),
            "A dismissed form must not be reported as still present: \(message)"
        )
    }

    /// When the form was seen earlier but is gone at the timeout, the step message may still carry
    /// the form counters as secondary context, so the earlier work is not lost.
    func testOnboardingTimeoutKeepsFormContextAfterTheFormWasSeen() {
        let addVehiclePosition = OnboardingTapPosition(dx: 0.4, dy: 0.6)
        var state = OnboardingWaitState()
        state.noteVehicleFormVisible(handlerActed: true)
        _ = state.nextAction(positions: [.addVehicle: addVehiclePosition])

        let message = state.timeoutMessage(formVisibleAtTimeout: false)
        XCTAssertTrue(message.contains("Vehicle creation"), message)
        XCTAssertTrue(message.contains("1"), message)
    }

    /// The handler self-latches after one real submission, so the form stays visible for hundreds of
    /// iterations. The submission count must track the handler acting, not the iteration count.
    func testOnboardingFormSubmissionsTrackRealSubmissionsNotIterations() {
        var state = OnboardingWaitState()
        for _ in 0..<360 {
            state.noteVehicleFormVisible(handlerActed: false)
        }

        XCTAssertEqual(state.vehicleFormVisibleIterations, 360)
        XCTAssertEqual(
            state.vehicleFormSubmissions,
            0,
            "Iterations where the handler did not act must not count as submissions"
        )
    }

    /// Seeing the vehicle form is a diagnostic observation, not a terminal step. If the form
    /// dismisses without the destination being reached (a save that fails and returns to the list),
    /// the retry policy must still tap a reappearing `add_vehicle` rather than idling to the cap.
    func testOnboardingWaitStillRetriesAddVehicleAfterTheFormWasSeen() {
        let addVehiclePosition = OnboardingTapPosition(dx: 0.4, dy: 0.6)
        var state = OnboardingWaitState()
        state.noteVehicleFormVisible(handlerActed: true)

        let action = state.nextAction(positions: [.addVehicle: addVehiclePosition])
        XCTAssertEqual(
            action,
            .tap(.addVehicle, at: addVehiclePosition),
            "A dismissed vehicle form must not strand the retry policy"
        )
    }

    /// Completion wins over the deadline: a destination that becomes true during the final
    /// scheduler wait must be reported as reached, not as a timeout. The previous driver checked the
    /// deadline before completion and failed the test even when the app had arrived.
    func testOnboardingCompletionWinsOverTheDeadline() {
        XCTAssertEqual(
            OnboardingWaitBudget.resolveOutcome(deadlineReached: true, isComplete: true),
            .reached,
            "Completion must be re-checked after the budget expires"
        )
        XCTAssertEqual(
            OnboardingWaitBudget.resolveOutcome(deadlineReached: true, isComplete: false),
            .timedOut
        )
        XCTAssertNil(
            OnboardingWaitBudget.resolveOutcome(deadlineReached: false, isComplete: false),
            "A wait inside its budget is not terminal"
        )
    }

    /// The timeout report must not claim the destination was reached, and it must close the step
    /// that consumed the budget so the per-step durations do not under-report.
    func testOnboardingSummaryDistinguishesReachedFromTimedOut() {
        let reached =
            OnboardingWaitReport(
                outcome: .reached,
                destination: "vehicle creation",
                total: 5,
                stepDurations: [.openingVehicleCreation: 5],
                guestTapAttempts: 1,
                addVehicleTapAttempts: 1,
                vehicleFormVisibleIterations: 0,
                vehicleFormSubmissions: 0
            )
        let timedOut =
            OnboardingWaitReport(
                outcome: .timedOut,
                destination: "vehicle creation",
                total: 180,
                stepDurations: [.openingVehicleCreation: 180],
                guestTapAttempts: 1,
                addVehicleTapAttempts: 20,
                vehicleFormVisibleIterations: 0,
                vehicleFormSubmissions: 0
            )

        XCTAssertTrue(reached.summary.contains("reached"), reached.summary)
        XCTAssertTrue(timedOut.summary.contains("did not reach"), timedOut.summary)
    }

    /// Closing the in-progress step before building the timeout report keeps the measurement honest.
    func testOnboardingReportClosesTheCurrentStep() {
        var stepDurations: [OnboardingWaitStep: TimeInterval] = [:]
        let startedAt = Date(timeIntervalSince1970: 1_000_000)
        OnboardingWaitReport.record(
            stepDurations: &stepDurations,
            step: .openingVehicleCreation,
            since: startedAt,
            now: startedAt.addingTimeInterval(42)
        )

        XCTAssertEqual(stepDurations[.openingVehicleCreation], 42)
    }

    /// Hittability is derived from snapshot geometry instead of the non-throwing `isHittable`
    /// property, so a vanished element cannot raise an uncatchable XCTest exception: an empty or
    /// off-screen frame is not hittable, and a frame inside the app is.
    func testOnboardingHittabilityIsDerivedFromGeometry() {
        let appFrame = CGRect(x: 0, y: 0, width: 400, height: 800)
        XCTAssertFalse(
            OnboardingTapPosition.isHittable(frame: .zero, in: appFrame),
            "An empty frame is not hittable"
        )
        XCTAssertFalse(
            OnboardingTapPosition.isHittable(frame: CGRect(x: 0, y: 900, width: 100, height: 40), in: appFrame),
            "An off-screen frame is not hittable"
        )
        XCTAssertTrue(
            OnboardingTapPosition.isHittable(frame: CGRect(x: 100, y: 300, width: 100, height: 40), in: appFrame)
        )
    }

    /// The tap action must not retain an `XCUIElement` that can disappear before the tap; it carries
    /// a snapshotted application-relative position instead.
    func testOnboardingWaitCarriesTheSnapshottedTapPosition() {
        let guestPosition = OnboardingTapPosition(dx: 0.25, dy: 0.75)
        var waitState = OnboardingWaitState()

        XCTAssertEqual(
            waitState.nextAction(positions: [.guest: guestPosition]),
            .tap(.guest, at: guestPosition),
            "The tap action must not retain an XCUIElement that can disappear before the tap"
        )
    }

    func testOnboardingWaitNamesGuestSessionTimeout() {
        var guestState = OnboardingWaitState()
        _ = guestState.nextAction(positions: [.guest: OnboardingTapPosition(dx: 0.5, dy: 0.5)])
        XCTAssertEqual(guestState.step, .startingGuestSession)
        let message = guestState.timeoutMessage(formVisibleAtTimeout: false)
        XCTAssertTrue(message.contains("vehicle list"), message)
    }

    func testOnboardingWaitNamesVehicleCreationTimeout() {
        var vehicleState = OnboardingWaitState()
        _ = vehicleState.nextAction(positions: [.addVehicle: OnboardingTapPosition(dx: 0.5, dy: 0.5)])
        XCTAssertEqual(vehicleState.step, .openingVehicleCreation)
        let message = vehicleState.timeoutMessage(formVisibleAtTimeout: false)
        XCTAssertTrue(message.contains("Vehicle creation"), message)
    }

    /// Completion is owned solely by the caller's `isComplete` closure: `nextAction` has no
    /// completion case, so there is exactly one completion path.
    func testOnboardingNextActionNeverCompletesOnItsOwn() {
        var state = OnboardingWaitState()
        XCTAssertEqual(state.nextAction(positions: [:]), .wait)
        XCTAssertEqual(state.nextAction(positions: [.guest: OnboardingTapPosition(dx: 0.5, dy: 0.5)]), .tap(.guest, at: OnboardingTapPosition(dx: 0.5, dy: 0.5)))
    }

    /// `enteredStep` is the single source of the step each target advances into, so the assertion
    /// in `testOnboardingTapTargetsMapToTheirAccessibilityIdentifiers` protects the driver.
    func testOnboardingEnteredStepIsWiredIntoNextAction() {
        let guestPosition = OnboardingTapPosition(dx: 0.5, dy: 0.5)
        var guestState = OnboardingWaitState()
        _ = guestState.nextAction(positions: [.guest: guestPosition])
        XCTAssertEqual(guestState.step, OnboardingTapTarget.guest.enteredStep)

        let addVehiclePosition = OnboardingTapPosition(dx: 0.4, dy: 0.6)
        var vehicleState = OnboardingWaitState()
        _ = vehicleState.nextAction(positions: [.addVehicle: addVehiclePosition])
        XCTAssertEqual(vehicleState.step, OnboardingTapTarget.addVehicle.enteredStep)
    }
}
