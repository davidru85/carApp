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

/// The onboarding segment the wait is currently in. It only ever moves forward.
enum OnboardingWaitStep: Hashable {
    case waitingForAffordance
    case startingGuestSession
    case openingVehicleCreation
}

enum OnboardingWaitAction: Equatable {
    case complete
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

    init?(element: XCUIElement, in app: XCUIApplication) {
        // `isEnabled` and `isHittable` raise when there is no matching snapshot, so `exists` must
        // short-circuit before either is read.
        let exists = element.exists
        guard exists else {
            return nil
        }
        guard OnboardingTapPosition.isAvailable(exists: exists, isEnabled: element.isEnabled, isHittable: element.isHittable) else {
            return nil
        }
        let appFrame = app.frame
        let elementFrame = element.frame
        guard appFrame.width > 0, appFrame.height > 0, !elementFrame.isEmpty else {
            return nil
        }
        self.init(
            dx: (elementFrame.midX - appFrame.minX) / appFrame.width,
            dy: (elementFrame.midY - appFrame.minY) / appFrame.height
        )
    }

    /// Pure availability policy, separated so the disabled-element exclusion is directly testable.
    static func isAvailable(exists: Bool, isEnabled: Bool, isHittable: Bool) -> Bool {
        exists && isEnabled && isHittable
    }

    func tap(in app: XCUIApplication) {
        app.coordinate(withNormalizedOffset: CGVector(dx: dx, dy: dy)).tap()
    }
}

/// Absolute wall-clock budget for a single onboarding wait. It does not restart on a step change,
/// so a reappearing affordance cannot push the total runtime out. CI run `34641152156` measured the
/// guest step alone at 62.090 seconds on the pre-fix helper; this cap is that worst case plus a
/// stated margin (see `docs/handoff-E1-17.md`). The story's acceptance criterion requires the
/// deadline to rest on a CI measurement, so it MUST NOT be tightened without new CI evidence.
enum OnboardingWaitBudget {
    static let absoluteLimit: TimeInterval = 120

    static func hasReachedDeadline(startedAt: Date, now: Date) -> Bool {
        now.timeIntervalSince(startedAt) >= absoluteLimit
    }
}

/// Pure retry policy for the onboarding wait. It is deliberately free of `XCUIElement` so its
/// branches are exercised without driving the UI.
struct OnboardingWaitState {
    private(set) var step = OnboardingWaitStep.waitingForAffordance
    private(set) var guestTapAttempts = 0
    private(set) var addVehicleTapAttempts = 0

    mutating func nextAction(
        destinationReached: Bool,
        positions: [OnboardingTapTarget: OnboardingTapPosition]
    ) -> OnboardingWaitAction {
        if destinationReached {
            return .complete
        }
        switch step {
        case .waitingForAffordance, .startingGuestSession:
            if let position = positions[.guest] {
                step = .startingGuestSession
                guestTapAttempts += 1
                return .tap(.guest, at: position)
            }
            if let position = positions[.addVehicle] {
                step = .openingVehicleCreation
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
    let waitingForAffordance: TimeInterval
    let startingGuestSession: TimeInterval
    let openingVehicleCreation: TimeInterval
    let guestTapAttempts: Int
    let addVehicleTapAttempts: Int

    var summary: String {
        "E1-17 onboarding reached \(destination) in \(Self.format(total)) seconds " +
            "(waitingForAffordance=\(Self.format(waitingForAffordance)) " +
            "startingGuestSession=\(Self.format(startingGuestSession)) " +
            "openingVehicleCreation=\(Self.format(openingVehicleCreation))) " +
            "after \(guestTapAttempts) welcome_guest and \(addVehicleTapAttempts) add_vehicle tap attempts"
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
/// latch if it must only act once.
@discardableResult
func waitForOnboarding(
    in app: XCUIApplication,
    destination: String,
    isComplete: () -> Bool,
    handleVehicleForm: (() -> Void)? = nil,
    absoluteLimit: TimeInterval = OnboardingWaitBudget.absoluteLimit,
    file: StaticString = #filePath,
    line: UInt = #line
) -> OnboardingWaitReport {
    let startedAt = Date()
    var waitState = OnboardingWaitState()
    var stepStartedAt = startedAt
    var stepDurations: [OnboardingWaitStep: TimeInterval] = [:]
    let absoluteDeadline = startedAt.addingTimeInterval(absoluteLimit)

    while Date() < absoluteDeadline {
        if isComplete() {
            stepDurations[waitState.step, default: 0] += Date().timeIntervalSince(stepStartedAt)
            let report = OnboardingWaitReport(
                destination: destination,
                total: Date().timeIntervalSince(startedAt),
                waitingForAffordance: stepDurations[.waitingForAffordance] ?? 0,
                startingGuestSession: stepDurations[.startingGuestSession] ?? 0,
                openingVehicleCreation: stepDurations[.openingVehicleCreation] ?? 0,
                guestTapAttempts: waitState.guestTapAttempts,
                addVehicleTapAttempts: waitState.addVehicleTapAttempts
            )
            print(report.summary)
            return report
        }

        if let handleVehicleForm, app.textFields[OnboardingIdentifiers.vehicleName].exists {
            handleVehicleForm()
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
            continue
        }

        var positions: [OnboardingTapTarget: OnboardingTapPosition] = [:]
        for target in OnboardingTapTarget.allCases {
            positions[target] = OnboardingTapPosition(element: target.element(in: app), in: app)
        }

        let previousStep = waitState.step
        switch waitState.nextAction(destinationReached: false, positions: positions) {
        case .complete:
            break
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

    XCTFail(
        "\(waitState.timeoutMessage) within the \(OnboardingWaitReport.format(absoluteLimit))-second absolute cap. " +
            "Last step: \(waitState.step).",
        file: file,
        line: line
    )
    return OnboardingWaitReport(
        destination: destination,
        total: Date().timeIntervalSince(startedAt),
        waitingForAffordance: stepDurations[.waitingForAffordance] ?? 0,
        startingGuestSession: stepDurations[.startingGuestSession] ?? 0,
        openingVehicleCreation: stepDurations[.openingVehicleCreation] ?? 0,
        guestTapAttempts: waitState.guestTapAttempts,
        addVehicleTapAttempts: waitState.addVehicleTapAttempts
    )
}

/// The accessibility identifiers the shared onboarding wait drives.
enum OnboardingIdentifiers {
    static let vehicleName = "vehicle_name"
}
