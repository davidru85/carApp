import Foundation

/// Measures how long the iOS app spent in the background, for the `docs/CONTRACTS.md §9.8`
/// foreground trigger.
///
/// The elapsed time is read from a monotonic source, so a wall-clock or time-zone change while the
/// app is in the background cannot produce a negative or wildly wrong duration.
/// `ProcessInfo.systemUptime` is the iOS counterpart of Android's `SystemClock.elapsedRealtime()`:
/// it counts from boot and never jumps. It stops while the *device* is asleep, where the Android
/// clock keeps running, so a stay that spans device sleep is measured as the awake part of it. That
/// is the safe direction for the threshold it feeds: an under-counted stay reports no foreground
/// trigger, and the reminder evaluation on the same transition is unconditional.
///
/// The reader is injected so the arithmetic can be exercised off-device by a throwaway harness;
/// production passes `ProcessInfo.processInfo.systemUptime`.
///
/// `nil` is the cold start: the first foreground entry has no preceding background stay, and `§9.8`
/// names the cold start as a trigger in its own right (`D-183`).
final class SceneBackgroundDuration {
    private var departureUptime: TimeInterval?
    private let monotonicNow: () -> TimeInterval

    init(monotonicNow: @escaping () -> TimeInterval = { ProcessInfo.processInfo.systemUptime }) {
        self.monotonicNow = monotonicNow
    }

    /// Records when the scene stopped being active. A repeated call keeps the earliest moment, so a
    /// stay is measured from its first departure rather than from the last one before the return.
    func recordDeparture() {
        if departureUptime == nil {
            departureUptime = monotonicNow()
        }
    }

    /// Reports the completed background stay in whole milliseconds and clears it, or `nil` when there
    /// is none to report. The measurement describes one transition and is never reused.
    func consumeBackgroundMillis() -> Int64? {
        guard let departureUptime else { return nil }
        self.departureUptime = nil
        return Int64(max(0, (monotonicNow() - departureUptime) * 1000))
    }
}
