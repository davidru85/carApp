import Foundation

/// Monotonic elapsed time that keeps counting while the system is asleep.
///
/// `ProcessInfo.systemUptime` was the obvious choice and is wrong here twice over. It stops while the
/// *device* is asleep, so a background stay that spans a screen lock is measured as only its awake
/// part: a phone put down for an hour could report a few minutes and land below the five-minute
/// threshold of `docs/CONTRACTS.md §9.8`, turning a real foreground return into a cold start. It is
/// also a required-reason API, which would oblige the target to declare an access reason for a
/// measurement that does not need one.
///
/// `ContinuousClock` is monotonic, does not jump with the wall clock or a time-zone change, and
/// continues across device sleep, which is exactly the interval `§9.8` measures. Reading it from a
/// stored origin keeps the subtraction in the same clock domain as the readings.
private enum ContinuousElapsedTime {
    private static let clock = ContinuousClock()
    private static let origin = clock.now

    static func nowMilliseconds() -> Int64 {
        let components = origin.duration(to: clock.now).components
        return components.seconds * 1_000 + components.attoseconds / 1_000_000_000_000_000
    }
}

/// Process-scoped scene timing for the `docs/CONTRACTS.md §9.8` foreground trigger.
///
/// Two facts are consumed here, each exactly once, because each may be reported twice by the platform:
/// the process cold start, and one completed background stay. `consumeColdStart` makes the cold-start
/// trigger idempotent, and `consumeBackgroundMillis` clears the departure it reports so a later
/// transition cannot reuse it.
final class SceneBackgroundDuration {
    private var departureMillis: Int64?
    private var coldStartConsumed = false
    private let monotonicNowMillis: () -> Int64

    init(monotonicNowMillis: @escaping () -> Int64 = ContinuousElapsedTime.nowMilliseconds) {
        self.monotonicNowMillis = monotonicNowMillis
    }

    /// Reports the one process cold start. Every later call reports `false`, so a launch that also
    /// delivers an active scene-phase change cannot emit the cold-start trigger twice.
    func consumeColdStart() -> Bool {
        guard !coldStartConsumed else { return false }
        coldStartConsumed = true
        return true
    }

    /// Records the moment the scene left the foreground. A repeated call keeps the earliest moment.
    func recordDeparture() {
        if departureMillis == nil {
            departureMillis = monotonicNowMillis()
        }
    }

    /// Reports the completed background stay and clears it, or `nil` when there is none to report.
    func consumeBackgroundMillis() -> Int64? {
        guard let departureMillis else { return nil }
        self.departureMillis = nil
        return max(0, monotonicNowMillis() - departureMillis)
    }
}
