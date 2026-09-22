import XCTest
@testable import carApp

/// The scene timing of `docs/CONTRACTS.md §9.8` on the iOS host.
///
/// The clock is injected, so the arithmetic and its boundary cases are exercised here instead of only
/// being observed on a device: one cold start per process, one report per completed departure, and the
/// earliest departure winning when the platform reports an entry more than once.
final class SceneBackgroundTrackingTests: XCTestCase {
    func testColdStartIsConsumedExactlyOnce() {
        let tracker = SceneBackgroundDuration(monotonicNowMillis: { 0 })
        XCTAssertTrue(tracker.consumeColdStart())
        XCTAssertFalse(tracker.consumeColdStart())
    }

    func testACompletedDepartureIsConsumedExactlyOnce() {
        var now: Int64 = 1_000
        let tracker = SceneBackgroundDuration(monotonicNowMillis: { now })
        tracker.recordDeparture()
        now = 401_001

        XCTAssertEqual(tracker.consumeBackgroundMillis(), 400_001)
        XCTAssertNil(tracker.consumeBackgroundMillis())
    }

    func testRepeatedDepartureKeepsTheEarliestInstant() {
        var now: Int64 = 1_000
        let tracker = SceneBackgroundDuration(monotonicNowMillis: { now })
        tracker.recordDeparture()
        now = 2_000
        tracker.recordDeparture()
        now = 3_500

        XCTAssertEqual(tracker.consumeBackgroundMillis(), 2_500)
    }
}
