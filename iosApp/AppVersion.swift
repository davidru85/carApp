import Foundation

/// The app version shown by Settings (`docs/SPECIFICATION.md §3.1`).
///
/// `B1-D2`: each host reads its own bundle, so no shared contract carries the version. The version
/// scheme itself (`CFBundleShortVersionString`, `CFBundleVersion`) is decided by `E4-04`; this type only
/// formats what the bundle declares.
enum AppVersion {
    /// The installed version as `"<marketing version> (<build number>)"`, for example `"0.0.1 (1)"`.
    static func displayString(bundle: Bundle = .main) -> String {
        format(
            shortVersion: bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String,
            buildNumber: bundle.object(forInfoDictionaryKey: "CFBundleVersion") as? String
        )
    }

    /// Joins the two bundle values. A missing or blank part is left out rather than drawn as a
    /// placeholder, so a bundle without a build number still shows its marketing version.
    static func format(shortVersion: String?, buildNumber: String?) -> String {
        let marketing = shortVersion?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let build = buildNumber?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if marketing.isEmpty {
            return build
        }
        if build.isEmpty {
            return marketing
        }
        return String(format: String(localized: "settings_version_value"), marketing, build)
    }
}
