# ADR-0202 - Encode analytics Boolean flags as Long zero or one

## Status

Accepted

## Context

GitLive 2.6.0 converts Boolean parameters, but that does not prove that native Firebase Analytics
supports them. Android and Apple event APIs document String, integer and floating-point values.
The domain's two fuel-entry flags must retain their Boolean meaning without depending on an
unsupported provider parameter type. This corrects the Boolean wire premise of D-198.

## Options Considered

- 1A: keep domain Boolean flags and encode them as Long zero or one at the provider boundary.
- 1B: keep Boolean provider values and require a real-provider compatibility proof.
- 1C: stringify flags, losing native integer filtering.

## Decision

The owner selected **1A** on 2026-10-14. `false` is `0L`, `true` is `1L`, only in
`FirebaseAnalyticsTracker`'s provider mapping. Names, enum strings, domain types and the provider
failure policy remain unchanged. This partially supersedes D-198 rather than replacing its naming
and failure decisions.

## Consequences and Constraints

Every custom event parameter is an enum name or a closed 0L/1L flag. No notes, numeric product data,
identifiers or credentials are introduced. Tests must check both values and the exact Long type for
both flags and all four combinations.

## Verification

`FirebaseAnalyticsTrackerTest` exercises the provider map through `RecordingGateway`; RED and GREEN
are committed and pushed separately. The full CI task command, coverage and all ten required checks
must pass before owner review.

## References

- [Android FirebaseAnalytics API](https://firebase.google.com/docs/reference/android/com/google/firebase/analytics/FirebaseAnalytics)
- [Apple FIRAnalytics API](https://firebase.google.com/docs/reference/ios/firebaseanalytics/api/reference/Classes/FIRAnalytics)
- `docs/CONTRACTS.md §16.1`, `§20.9`; D-198 / ADR-0198.
