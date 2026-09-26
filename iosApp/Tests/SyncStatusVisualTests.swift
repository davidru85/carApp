import Shared
import XCTest
@testable import carApp

/// The host-side classification of the aggregate `docs/CONTRACTS.md §9.9` publishes.
///
/// The precedence and the connectivity rule belong to `:core:sync`, not to the host: the shared layer
/// publishes one resolved `SyncStatus` and `§14` forbids a second computation. What the host owns is
/// which of those four published values is drawn as an error and offers the manual retry, so these
/// tests pin that classification and nothing about how the aggregate was derived.
final class SyncStatusVisualTests: XCTestCase {
    func testEveryPublishedStatusMapsToItsOwnVisual() {
        XCTAssertEqual(syncStatusVisual(SyncSyncStatus.Idle()), .idle)
        XCTAssertEqual(syncStatusVisual(SyncSyncStatus.Syncing()), .syncing)
        XCTAssertEqual(syncStatusVisual(SyncSyncStatus.Pending(count: 3)), .pending)
        XCTAssertEqual(
            syncStatusVisual(SyncSyncStatus.Failed(retryableCount: 1, poisonedCount: 2)),
            .failed
        )
    }

    /// The counts inside a status are detail, not classification. A `Failed` of retryable rows and one
    /// of poisoned rows are the same visual, because `§9.9` already placed them in the same bucket and
    /// the host is in no position to re-open that decision.
    func testTheCountsInsideAStatusDoNotChangeItsVisual() {
        XCTAssertEqual(
            syncStatusVisual(SyncSyncStatus.Failed(retryableCount: 0, poisonedCount: 1)),
            .failed
        )
        XCTAssertEqual(
            syncStatusVisual(SyncSyncStatus.Failed(retryableCount: 9, poisonedCount: 0)),
            .failed
        )
        XCTAssertEqual(syncStatusVisual(SyncSyncStatus.Pending(count: 1)), .pending)
        XCTAssertEqual(syncStatusVisual(SyncSyncStatus.Pending(count: 250)), .pending)
    }

    /// `§9.9` reserves error presentation for `Failed`. An offline owner with outstanding work
    /// reaches the host as `Pending`, so classifying it as `Failed` would draw an error for a
    /// condition that is not one and would contradict the shared value it came from.
    func testAPendingStatusIsNeverClassifiedAsFailed() {
        let visual = syncStatusVisual(SyncSyncStatus.Pending(count: 12))

        XCTAssertEqual(visual, .pending)
        XCTAssertNotEqual(visual, .failed)
    }

    func testOnlyTheFailedVisualIsAnError() {
        let errors = [
            SyncStatusVisual.idle,
            SyncStatusVisual.syncing,
            SyncStatusVisual.pending,
            SyncStatusVisual.failed,
        ].filter(syncStatusIsError)

        XCTAssertEqual(errors, [.failed])
        XCTAssertFalse(syncStatusIsError(.pending))
    }

    /// `Syncing` outranks `Pending` in `§9.9`; the host draws the two differently and must not merge
    /// them into one label.
    func testTheSyncingVisualIsDistinctFromPending() {
        XCTAssertNotEqual(syncStatusVisual(SyncSyncStatus.Syncing()), syncStatusVisual(SyncSyncStatus.Pending(count: 1)))
    }

    func testEveryVisualHasItsOwnLabel() {
        let keys = [SyncStatusVisual.idle, .syncing, .pending, .failed].map(syncStatusLabelKey)

        XCTAssertEqual(
            keys.count,
            Set(keys).count,
            "Four states drawn with repeated labels cannot be told apart by the owner."
        )
    }

    /// Both catalogues are asserted explicitly. The indicator is worthless in the language it fails
    /// to describe the state in, and the language of the test device must not decide what is checked.
    func testEveryVisualHasCopyInBothLanguages() throws {
        for language in ["en", "es"] {
            let catalogue = try localization(language)
            for visual in [SyncStatusVisual.idle, .syncing, .pending, .failed] {
                let key = syncStatusLabelKey(visual)
                let copy = catalogue.localizedString(forKey: key, value: nil, table: nil)

                XCTAssertNotEqual(copy, key, "\(language) is missing \(key).")
                XCTAssertFalse(copy.isEmpty, "\(language) has an empty value for \(key).")
            }
        }
    }

    /// The status text is announced as "Backup status: <label>", the same accessible description the
    /// Android indicator carries, so a screen reader names what the chip reports. Both catalogues MUST
    /// carry the format and it MUST keep exactly one `%@` placeholder for the label.
    func testTheAccessibleDescriptionHasCopyInBothLanguages() throws {
        for language in ["en", "es"] {
            let catalogue = try localization(language)
            let key = "backup_status_description"
            let copy = catalogue.localizedString(forKey: key, value: nil, table: nil)

            XCTAssertNotEqual(copy, key, "\(language) is missing \(key).")
            XCTAssertEqual(copy.components(separatedBy: "%@").count - 1, 1, "\(language) \(key) needs one %@.")
        }
    }

    /// The catalogues live in the application bundle, not in the test bundle, so the lookup is
    /// anchored to a class the application target owns.
    private func localization(_ language: String) throws -> Bundle {
        let applicationBundle = Bundle(for: WalkingSkeletonModel.self)
        let path = try XCTUnwrap(
            applicationBundle.path(forResource: language, ofType: "lproj"),
            "The application bundle carries no \(language) localization."
        )
        return try XCTUnwrap(Bundle(path: path))
    }
}
