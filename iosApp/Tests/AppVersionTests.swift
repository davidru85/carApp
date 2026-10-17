import Foundation
import XCTest
@testable import carApp

/// `B1-D2`: Settings shows the version the bundle declares, as `"<marketing version> (<build number>)"`.
final class AppVersionTests: XCTestCase {
    func testTheVersionJoinsTheMarketingVersionAndTheBuildNumber() {
        XCTAssertEqual(AppVersion.format(shortVersion: "0.0.1", buildNumber: "1"), "0.0.1 (1)")
    }

    func testAMissingBuildNumberShowsTheMarketingVersionAlone() {
        XCTAssertEqual(AppVersion.format(shortVersion: "0.0.1", buildNumber: nil), "0.0.1")
    }

    func testAMissingMarketingVersionShowsTheBuildNumberAlone() {
        XCTAssertEqual(AppVersion.format(shortVersion: "  ", buildNumber: "7"), "7")
    }

    /// The row reads the installed bundle, not a literal, so `E4-04` can change the scheme without
    /// touching Settings. The application bundle is anchored to a class the application target owns.
    func testTheApplicationBundleVersionIsReadFromItsInfoDictionary() throws {
        let bundle = Bundle(for: WalkingSkeletonModel.self)
        let marketing = try XCTUnwrap(bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String)
        let build = try XCTUnwrap(bundle.object(forInfoDictionaryKey: "CFBundleVersion") as? String)

        XCTAssertEqual(AppVersion.displayString(bundle: bundle), "\(marketing) (\(build))")
    }
}
