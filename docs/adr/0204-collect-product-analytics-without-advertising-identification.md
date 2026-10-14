# ADR-0204 - Collect product analytics without advertising identification

## Status

Accepted

## Context

Settings opt-in gates collection, but linking the default Firebase Analytics products also adds
advertising-ID support, Android advertising permissions and automatic provider events. A closed
custom event hierarchy does not prevent SDK-generated events or metadata. The owner must decide
which measurement is permitted after consent without claiming that all collected data is anonymous.

## Options Considered

- 3A: retain the SDK's default advertising and automatic measurement configuration.
- 3B: retain product and technical SDK measurement, disabling advertising identification,
  personalized ad signals, IDFV and automatic screen reporting.
- 3C: prohibit every SDK-generated event, requiring a different measurement implementation.

## Decision

The owner selected **3B** on 2026-10-14. Collection stays disabled at process startup and is enabled
only by explicit Settings opt-in. Custom events remain the closed thirteen-leaf hierarchy. Standard
technical SDK events (including session start and engagement) and the SDK app instance ID are
accepted after opt-in for pseudonymous measurement. No analytics user ID is set or linked to Auth.

Android disables `google_analytics_adid_collection_enabled`,
`google_analytics_default_allow_ad_personalization_signals` and
`google_analytics_automatic_screen_reporting_enabled`. Manifest merge removes AD_ID and the
AdServices AD_ID, ATTRIBUTION and TOPICS permissions.

iOS disables `GOOGLE_ANALYTICS_IDFV_COLLECTION_ENABLED`,
`GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS` and
`FirebaseAutomaticScreenReportingEnabled`. The app links `FirebaseAnalyticsWithoutAdIdSupport`
from the already-pinned Apple SDK, supplying the same FIRAnalytics framework without IDFA support.
No SDK version is changed. The project specification and generated Xcode project must agree.

## Consequences and Constraints

This is pseudonymous product measurement, not fully anonymous data and not a promise of only thirteen
provider events. Automatic screen reporting is disabled. Product contents and Auth identifiers
remain forbidden in custom payloads. No new Settings controls or advertising use cases are added.

## Verification

Contract assertion 38 requires the exact no-ad-ID SwiftPM product. Assertion 40 requires every host
privacy flag and Android permission-removal declaration, with mutation fixtures for each. Build
Debug and inspect the merged Android manifest for absent advertising permissions. Build the iOS app
and verify the built plist flags and the no-AdIdSupport link dependency. Run the full local command,
provider-free tests and all ten required PR checks; close any device used immediately afterwards.

## References

- [Android collection controls](https://firebase.google.com/docs/analytics/android/configure-data-collection)
- [iOS collection controls](https://firebase.google.com/docs/analytics/ios/configure-data-collection)
- [Screen reporting controls](https://firebase.google.com/docs/analytics/screenviews)
- [Firebase Apple 11.8.0 package products](https://github.com/firebase/firebase-ios-sdk/blob/11.8.0/Package.swift)
- [Automatically collected events](https://support.google.com/analytics/answer/9234069)
