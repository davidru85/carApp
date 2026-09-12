import XCTest

/// The two onboarding affordances the UI tests drive. The waiter resolves each element from
/// `accessibilityIdentifier`, so a retry action cannot target the wrong control.
enum OnboardingTapTarget: CaseIterable, Equatable {
    case guest
    case addVehicle

    var accessibilityIdentifier: String {
        switch self {
        case .guest:
            return "welcome_guest"
        case .addVehicle:
            return "add_vehicle"
        }
    }

    /// The step this target advances out of.
    var enteredStep: OnboardingWaitStep {
        switch self {
        case .guest:
            return .startingGuestSession
        case .addVehicle:
            return .openingVehicleCreation
        }
    }

    func element(in app: XCUIApplication) -> XCUIElement {
        app.buttons[accessibilityIdentifier]
    }
}

/// The onboarding segment the wait is currently in. It only ever moves forward and it stays on the
/// affordance ladder: seeing the vehicle form is a diagnostic observation tracked separately, not a
/// terminal step that could suppress a later retry.
enum OnboardingWaitStep: Hashable, Comparable {
    case waitingForAffordance
    case startingGuestSession
    case openingVehicleCreation

    /// Monotonic rank used to reject a backwards transition.
    private var rank: Int {
        switch self {
        case .waitingForAffordance:
            return 0
        case .startingGuestSession:
            return 1
        case .openingVehicleCreation:
            return 2
        }
    }

    static func < (lhs: OnboardingWaitStep, rhs: OnboardingWaitStep) -> Bool {
        lhs.rank < rhs.rank
    }
}

enum OnboardingWaitAction: Equatable {
    case tap(OnboardingTapTarget, at: OnboardingTapPosition)
    case wait
}

/// An application-relative tap position captured while an affordance is present, enabled and
/// hittable. A SwiftUI button stays `isHittable` while `.disabled(true)` (see `WelcomeView.swift`),
/// so `isEnabled` is part of the availability gate: "present but disabled" is a distinct waiting
/// state, not a tap opportunity. Snapshotting the centre and tapping through the coordinate also
/// avoids resolving an `XCUIElement` that a navigation transition can remove first.
struct OnboardingTapPosition: Equatable {
    let dx: CGFloat
    let dy: CGFloat

    init(dx: CGFloat, dy: CGFloat) {
        self.dx = dx
        self.dy = dy
    }

    /// Pure availability policy, separated so the disabled-element exclusion is directly testable.
    static func isAvailable(exists: Bool, isEnabled: Bool, isHittable: Bool) -> Bool {
        exists && isEnabled && isHittable
    }

    /// Resolves the element to a position, or `nil` when XCTest cannot take a snapshot. Reading
    /// several properties separately leaves windows in which a SwiftUI transition removes the
    /// element and XCTest fails with "Failed to get matching snapshot"; `exists` short-circuits and
    /// a single `snapshot()` supplies enabled/frame while hittability is read once, all inside a
    /// throwing scope so a vanished element yields `nil` instead of failing the test.
    static func resolve(_ element: XCUIElement, in app: XCUIApplication) -> OnboardingTapPosition? {
        guard element.exists else {
            return nil
        }
        guard let snapshot = try? element.snapshot(), let hittable = try? element.isHittable else {
            return nil
        }
        guard isAvailable(exists: true, isEnabled: snapshot.isEnabled, isHittable: hittable) else {
            return nil
        }
        let appFrame = app.frame
        let elementFrame = snapshot.frame
        guard appFrame.width > 0, appFrame.height > 0, !elementFrame.isEmpty else {
            return nil
        }
        return OnboardingTapPosition(
            dx: (elementFrame.midX - appFrame.minX) / appFrame.width,
            dy: (elementFrame.midY - appFrame.minY) / appFrame.height
        )
    }

    func tap(in app: XCUIApplication) {
        app.coordinate(withNormalizedOffset: CGVector(dx: dx, dy: dy)).tap()
    }
}

/// Absolute wall-clock budget for a single onboarding wait. It does not restart on a step change,
/// so a reappearing affordance cannot push the total runtime out.
///
/// CI evidence behind the value: the pre-fix helper reached the vehicle list after a guest step
/// measured at 62.090 seconds (run `34641152156`), and a same-head rerun of the corrected helper
/// exceeded a 120-second cap on a rate-limited runner. 180 seconds is the observed successful worst
/// case plus a large margin. It is a pragmatic bound, not a guarantee: the real Firebase anonymous
/// sign-in remains in the path, which is why the Debug-only seam is recommended to the owner in
/// `docs/handoff-E1-17.md`.
enum OnboardingWaitBudget {
    static let absoluteLimit: TimeInterval = 180

    /// Accepts the effective limit so `waitForOnboarding` calls this function for its loop
    /// condition instead of re-implementing the comparison inline. A per-step reset would have to
    /// change this function and would fail its tests.
    static func hasReachedDeadline(
        effectiveLimit: TimeInterval = absoluteLimit,
        startedAt: Date,
        now: Date
    ) -> Bool {
        now.timeIntervalSince(startedAt) >= effectiveLimit
    }
}

/// Pure retry policy for the onboarding wait. It is deliberately free of `XCUIElement` so its
/// branches are exercised without driving the UI.
struct OnboardingWaitState {
    private(set) var step = OnboardingWaitStep.waitingForAffordance
    private(set) var guestTapAttempts = 0
    private(set) var addVehicleTapAttempts = 0
    private(set) var vehicleFormVisibleIterations = 0
    private(set) var vehicleFormSubmissions = 0

    /// True once the vehicle form has been observed at least once.
    var vehicleFormWasSeen: Bool { vehicleFormVisibleIterations > 0 }

    /// Records that the vehicle form was visible on this iteration. `handlerActed` is true only when
    /// the caller's handler actually performed a submission; the two counts are kept separate
    /// because a self-latching handler leaves the form visible for many iterations after one real
    /// submission, and counting iterations as submissions would repeat the inflated-counter defect
    /// this story already diagnosed for `guestTapAttempts`. Seeing the form is a diagnostic
    /// observation: it does not move `step`, so the affordance-retry policy still runs.
    mutating func noteVehicleFormVisible(handlerActed: Bool) {
        vehicleFormVisibleIterations += 1
        if handlerActed {
            vehicleFormSubmissions += 1
        }
    }

    /// Advances the step only forward, so a reappearing affordance cannot reset the step (and with
    /// it the budget) or move the wait back to an earlier segment.
    private mutating func enter(_ candidate: OnboardingWaitStep) {
        if candidate > step {
            step = candidate
        }
    }

    mutating func nextAction(
        positions: [OnboardingTapTarget: OnboardingTapPosition]
    ) -> OnboardingWaitAction {
        switch step {
        case .waitingForAffordance, .startingGuestSession:
            if let position = positions[.guest] {
                enter(OnboardingTapTarget.guest.enteredStep)
                guestTapAttempts += 1
                return .tap(.guest, at: position)
            }
            if let position = positions[.addVehicle] {
                enter(OnboardingTapTarget.addVehicle.enteredStep)
                addVehicleTapAttempts += 1
                return .tap(.addVehicle, at: position)
            }
        case .openingVehicleCreation:
            // The step never moves backwards. A guest affordance that reappears after the vehicle
            // list was reached is retried, but it does not reset the step (and with it the budget).
            if let position = positions[.addVehicle] {
                addVehicleTapAttempts += 1
                return .tap(.addVehicle, at: position)
            }
            if let position = positions[.guest] {
                guestTapAttempts += 1
                return .tap(.guest, at: position)
            }
        }
        return .wait
    }

    var timeoutMessage: String {
        if vehicleFormWasSeen {
            return "The vehicle form stayed visible for \(vehicleFormVisibleIterations) iteration(s) " +
                "after \(vehicleFormSubmissions) real submission(s) and never dismissed"
        }
        switch step {
        case .waitingForAffordance:
            return "Onboarding did not expose an enabled welcome_guest or add_vehicle affordance before the timeout"
        case .startingGuestSession:
            return "Guest session did not reach the vehicle list before the timeout"
        case .openingVehicleCreation:
            return "Vehicle creation did not open before the timeout"
        }
    }
}

/// Per-step timing and tap counts for the CI measurement required by E1-17.
struct OnboardingWaitReport {
    let destination: String
    let total: TimeInterval
    let stepDurations: [OnboardingWaitStep: TimeInterval]
    let guestTapAttempts: Int
    let addVehicleTapAttempts: Int
    let vehicleFormVisibleIterations: Int
    let vehicleFormSubmissions: Int

    var summary: String {
        "E1-17 onboarding reached \(destination) in \(Self.format(total)) seconds " +
            "(waitingForAffordance=\(Self.format(duration(.waitingForAffordance))) " +
            "startingGuestSession=\(Self.format(duration(.startingGuestSession))) " +
            "openingVehicleCreation=\(Self.format(duration(.openingVehicleCreation))) " +
            "vehicleFormVisibleIterations=\(vehicleFormVisibleIterations) " +
            "vehicleFormSubmissions=\(vehicleFormSubmissions)) " +
            "after \(guestTapAttempts) welcome_guest and \(addVehicleTapAttempts) add_vehicle tap attempts"
    }

    private func duration(_ step: OnboardingWaitStep) -> TimeInterval {
        stepDurations[step] ?? 0
    }

    static func format(_ value: TimeInterval) -> String {
        String(format: "%.3f", value)
    }
}

/// Advances the anonymous onboarding until `isComplete` reports the destination is reached. It
/// retries any affordance that is present, enabled and hittable, and it is bounded by a single
/// absolute budget so the total runtime cannot grow with step transitions.
///
/// `handleVehicleForm` is optional and lets a caller that must also create the first vehicle fill
/// and submit the mandatory form instead of waiting for it. It runs whenever the vehicle-name field
/// is present and the destination has not been reached, and is responsible for its own one-shot
/// latch if it must only act once. It returns `true` only when it actually performed a submission,
/// so the two form counters distinguish iterations from real submissions.
@discardableResult
func waitForOnboarding(
    in app: XCUIApplication,
    destination: String,
    isComplete: () -> Bool,
    handleVehicleForm: (() -> Bool)? = nil,
    absoluteLimit: TimeInterval = OnboardingWaitBudget.absoluteLimit,
    file: StaticString = #filePath,
    line: UInt = #line
) -> OnboardingWaitReport {
    let startedAt = Date()
    var waitState = OnboardingWaitState()
    var stepStartedAt = startedAt
    var stepDurations: [OnboardingWaitStep: TimeInterval] = [:]

    while !OnboardingWaitBudget.hasReachedDeadline(
        effectiveLimit: absoluteLimit,
        startedAt: startedAt,
        now: Date()
    ) {
        if isComplete() {
            stepDurations[waitState.step, default: 0] += Date().timeIntervalSince(stepStartedAt)
            return finishOnboarding(
                destination: destination,
                startedAt: startedAt,
                stepDurations: stepDurations,
                waitState: waitState
            )
        }

        if let handleVehicleForm, app.textFields[OnboardingIdentifiers.vehicleName].exists {
            let acted = handleVehicleForm()
            waitState.noteVehicleFormVisible(handlerActed: acted)
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
            continue
        }

        var positions: [OnboardingTapTarget: OnboardingTapPosition] = [:]
        for target in OnboardingTapTarget.allCases {
            positions[target] = OnboardingTapPosition.resolve(target.element(in: app), in: app)
        }

        let previousStep = waitState.step
        switch waitState.nextAction(positions: positions) {
        case let .tap(_, position):
            position.tap(in: app)
        case .wait:
            break
        }

        if waitState.step != previousStep {
            stepDurations[previousStep, default: 0] += Date().timeIntervalSince(stepStartedAt)
            stepStartedAt = Date()
        }
        RunLoop.current.run(until: Date().addingTimeInterval(0.5))
    }

    let formWasVisible = handleVehicleForm != nil && app.textFields[OnboardingIdentifiers.vehicleName].exists
    XCTFail(
        "\(waitState.timeoutMessage) within the \(OnboardingWaitReport.format(absoluteLimit))-second absolute cap. " +
            "Last step: \(waitState.step). Vehicle form visible at timeout: \(formWasVisible). " +
            "Vehicle form iterations: \(waitState.vehicleFormVisibleIterations), " +
            "real submissions: \(waitState.vehicleFormSubmissions).",
        file: file,
        line: line
    )
    return finishOnboarding(
        destination: destination,
        startedAt: startedAt,
        stepDurations: stepDurations,
        waitState: waitState
    )
}

/// Builds and prints the completion report. Shared by the success and timeout exits so both carry
/// the same measurements.
private func finishOnboarding(
    destination: String,
    startedAt: Date,
    stepDurations: [OnboardingWaitStep: TimeInterval],
    waitState: OnboardingWaitState
) -> OnboardingWaitReport {
    let report = OnboardingWaitReport(
        destination: destination,
        total: Date().timeIntervalSince(startedAt),
        stepDurations: stepDurations,
        guestTapAttempts: waitState.guestTapAttempts,
        addVehicleTapAttempts: waitState.addVehicleTapAttempts,
        vehicleFormVisibleIterations: waitState.vehicleFormVisibleIterations,
        vehicleFormSubmissions: waitState.vehicleFormSubmissions
    )
    print(report.summary)
    return report
}

/// The accessibility identifiers the shared onboarding wait drives.
enum OnboardingIdentifiers {
    static let vehicleName = "vehicle_name"
}
