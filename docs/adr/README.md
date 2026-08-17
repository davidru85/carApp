# ADR Index

One ADR per decision ID. The decision ID is stable across project documents; ADR numbers are file identifiers only.

`docs/DECISION_BOARD.md` is the sole registry of decision IDs, and the ADR `Status` field MUST equal the board status for the same ID. `contract-check` asserts that the decision ID set and status values here are identical to `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12` and `docs/TECHNICAL_PLAN.md §2`.

| Decision ID | ADR | Decision | Status |
|-------------|-----|----------|--------|
| D-0 | [ADR-0001](0001-backend-cloud-firestore.md) | Use Cloud Firestore as remote backend. | Accepted |
| D-1 | [ADR-0002](0002-local-database-room-kmp.md) | Use Room 3.0 KMP for the local database. | Accepted |
| D-2 | [ADR-0003](0003-ios-interop-skie.md) | Use SKIE for Kotlin-to-Swift interop. | Accepted |
| D-3 | [ADR-0004](0004-koin-dependency-injection.md) | Use Koin KMP for dependency injection. | Accepted |
| D-4 | [ADR-0005](0005-vehicle-fuel-type-from-day-one.md) | Store vehicle fuel type from day one. | Accepted |
| D-5 | [ADR-0006](0006-firestore-remote-sync-source.md) | Use Firebase Firestore behind `RemoteSyncSource`. | Accepted |
| D-6 | [ADR-0007](0007-firebase-auth-gitlive.md) | Use Firebase Auth through GitLive behind `AuthClient`. | Accepted |
| D-7 | [ADR-0008](0008-native-navigation.md) | Use native navigation per platform. | Accepted |
| D-8 | [ADR-0009](0009-shared-presentation-state-holders.md) | Share presentation state holders in KMP. | Accepted |
| D-9 | [ADR-0010](0010-disable-firestore-offline-persistence.md) | Disable Firestore offline persistence. | Accepted |
| D-10 | [ADR-0011](0011-firebase-analytics.md) | Use Firebase Analytics behind `AnalyticsTracker`. | Accepted |
| D-11 | [ADR-0012](0012-defer-ktor-api-client.md) | Defer Ktor until an API-based remote implementation exists. | Deferred |
| D-12 | [ADR-0013](0013-defer-coil-image-loading.md) | Use Coil if image loading becomes necessary. | Deferred |
| D-13 | [ADR-0014](0014-firestore-location-europe-west1.md) | Create the Firestore database in `europe-west1`. | Accepted |
| D-14 | [ADR-0015](0015-firebase-project-topology.md) | One development Firebase project plus the emulator; production topology deferred. | Accepted |
| D-15 | [ADR-0016](0016-logging-kermit.md) | Use Kermit behind the `Logger` abstraction. | Accepted |
| D-16 | [ADR-0017](0017-architecture-checks-konsist.md) | Konsist for package rules, custom Gradle check for module rules. | Accepted |
| D-17, D-18 | [ADR-0019](0019-test-tooling-turbine-kover.md) | Turbine for Flow testing, Kover for coverage thresholds. | Proposed |
| D-19 | [ADR-0018](0018-outcome-result-type.md) | Custom `Outcome<T, E>` as the result channel. | Proposed |
| D-20 | [ADR-0020](0020-localization-native-resources.md) | Native resources; no user-facing text in `UiState`. | Proposed |
| D-21 | — | Crash reporting. No ADR yet; still `Pending` on the board, decided in Phase 4. | Pending |
| D-22 | [ADR-0021](0021-application-identifiers.md) | Application identifiers are owner-decided and fixed in `docs/identifiers.md`. | Accepted |

New ADRs start from [0000-template.md](0000-template.md).
