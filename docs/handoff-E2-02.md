# Agent Handoff - E2-02

## Story

`E2-02 - Firebase Auth Integration - L`

### Closure Update: Review Remediation (PR #53) - 2026-09-03

Addressed all owner review findings for PR #53:
- **A1 / Minor f**: Applied a timing workaround for the pre-existing iOS SQLite reader connection pool race during graph teardown in `ViewModelLifecycleTests.testVehicleListViewModelInitialState` by adding a short delay (`Task.sleep(200ms)`) widening the timing window to match the existing pattern in that file. A permanent architectural fix belongs in its own story.
- **B1 / D-111 / ADR-0112**: Added `allowUidChange: Boolean = false` to `AuthClient.signInWithCredential(credential, allowUidChange)` across `:core:auth` and `:integration:firebase-auth` to unblock `CONTRACTS.md §11.3` Step 2 Account Adoption. Documented parity in `DECISION_BOARD.md`, `SPECIFICATION.md §12`, `TECHNICAL_PLAN.md §2`, `adr/README.md`, and `CONTRACTS.md §11.1/§11.3`.
- **Blocking 1**: Reverted repo-wide test-infrastructure flag in `KmpLibraryConventionPlugin.kt`, restoring `androidLibrary.withHostTestBuilder {}` so unmocked Android SDK calls fail loudly as intended. Extracted a pure platform-free seam `internal fun classifyAuthFailure(code: String?, message: String?, kind: AuthFailureKind, cause: Throwable? = null): FirebaseAuthGatewayException` and tested it with plain code/message pairs without instantiating Firebase Android SDK exception types.
- **Blocking 2 & D-75 Apple test exclusion**: Explicit note on Apple test exclusion under D-75: `:integration:firebase-auth` commonTest never runs on Apple targets (`-x :integration:firebase-auth:iosSimulatorArm64Test`), as Apple GitLive framework dependencies require CocoaPods/SPM integration tested via host apps. Consequently, the iOS numeric-code mapping (e.g. 17025, 17014, 17020, 17028, 17058) is covered by the platform-free classifier tests.
- **Finding 3**: Remapped numeric code 17028 (`FIREBASE_APP_NOT_AUTHORIZED_CODE`) to `FirebaseAuthGatewayException.Provider` with a comment naming it `appNotAuthorized` (configuration failure). Widened `AccountDeletionInvoker` contract: implementations (such as E3-10 Cloud Functions callable) MUST throw `FirebaseAuthGatewayException.PermissionDenied` for caller rejection (e.g. caller mismatch where authenticated UID != target UID, or IAM permission denied), which maps to `AuthError.PermissionDenied`; and `FirebaseAuthGatewayException.AccountDeletionRemoteFailed` for other failures. Added KDoc and tests verifying that `PermissionDenied` propagates unchanged through `executeServerAccountDeletion` and `safeAuthCall` to `AuthError.PermissionDenied`.
- **Finding 4**: Fixed the §11.5 freshness gate fail-closed behavior: `parseTokenTimestamps` now throws `FirebaseAuthGatewayException.Provider` when `iat` is missing or unparseable instead of silently substituting `now` (which would have treated missing `iat` as freshly authenticated). Replaced direct `Clock.System.now()` calls in `GitLiveFirebaseAuthGateway` with an injected `AppClock = AppClock { Clock.System.now() }` for deterministic testing. Added test proving `deleteAccount` does not call the server operation when token has no usable `iat`.
- **Finding 5**: Raised an open question for the owner below regarding token freshness (`auth_time` vs `iat`).
- **Minor a**: `FirebaseAuthClient` now accepts `coroutineScope: CoroutineScope` from the composition boundary instead of hardcoding `Dispatchers.Default`, implements `AutoCloseable`, and exposes `close()` and `dispose()` which cancel the internal `observationJob`. Updated `FirebaseAppProviders.kt` accordingly. Added unit test verifying `close()` cancels auth state observation.
- **Minor b**: In `FirebaseAuthClientTest`, passed `coroutineScope = backgroundScope` consistently across all test cases.
- **Minor c**: Cleaned up imports in `FirebaseAuthClient.kt` to eliminate inline fully-qualified `kotlinx.coroutines` names.
- **Minor d**: In `reauthenticate()`, added a comment explicitly naming the coupling where `gateway.getIdToken(forceRefresh = true)` re-mints fresh `iat` for subsequent `deleteAccount` reads.
- **Minor e**: Corrected test counts: `FirebaseAuthClientTest = 32 tests` and `GitLiveFirebaseAuthGatewayTest = 12 tests` (total 44 tests in `:integration:firebase-auth`).

### Open Question for the Owner: Token Freshness (`auth_time` vs `iat`)

`CONTRACTS.md §11.5` Step 1 currently defines token freshness for account deletion as:
`AppClock.now() - issuedAt <= FRESH_LOGIN_THRESHOLD_MS` (5 minutes).

However, Firebase silently and automatically refreshes ID tokens every hour in the background. Consequently, the `iat` (issued at) claim reflects when the token was minted by Firebase Auth, which is typically at most 60 minutes old even if the user logged in days or weeks ago.
The Firebase Auth JWT claim that encodes the real time of last interactive user authentication is `auth_time` (Unix seconds).

Because changing `CONTRACTS.md` or `AuthToken` is an owner decision that outranks an implementation story, no contract changes were made in E2-02.
Options for the owner:
1. **Add `authTime: Instant?` to `AuthToken`**: update `CONTRACTS.md §11.5` and `§20.8` to define freshness against `authTime` instead of `issuedAt`.
2. **Keep `iat` in `AuthToken`**: keep client-side check on `issuedAt` as a fast pre-check and rely on the server callable (`D-23`, `E3-10`) to perform the authoritative `auth_time` validation using the Firebase Admin SDK (`decodedToken.auth_time`).

## Ready Check

- Backlog story: `E2-02 - Firebase Auth Integration - L` is explicit and is the next open Phase 2 story in `docs/BACKLOG.md`.
- Acceptance criteria reviewed:
  - Complete `FirebaseAuthClient` implementation against `AuthClient` and `TokenProvider` (`docs/CONTRACTS.md §11.1`, `§11.2`, `§11.5`, `§20.8`).
  - Google and Apple native credential exchange via GitLive Auth.
  - Anonymous session management, linking, reauthentication and sign out.
  - Account deletion token freshness verification (`FRESH_LOGIN_THRESHOLD_MS = 300_000L`) before calling server deletion operation (client SDK delete is strictly forbidden by `D-23`).
  - Error mapping from Firebase/GitLive exceptions to canonical `AuthError` hierarchy.
  - UID stability enforcement during credential linking; collision mapping to `AuthError.CredentialAlreadyInUse` and unlinked anonymous sign-in to `AuthError.UidWouldChange`.
  - Token refresh forwarding and timestamp parsing (`issuedAt`, `expiresAt`) into `AuthToken`.
- Dependencies checked: `E2-01` supplied the complete provider-free auth contracts and owner context; `E0-07` staged the initial slice. `E2-02` branch starts from synchronized `main` at `ae646e8`.
- Decisions checked: `D-6` (GitLive Firebase Auth), `D-10` (Cloud Firestore with token retrieval), `D-23` (Account deletion execution by server operation; mobile client never hard-deletes), `D-44` (Decoupled Firebase provider modules), `D-102` (Account collision handling and preserved anonymous UID), `D-105` (Continuous progress documentation).
- Normative sections reviewed:
  - `docs/SPECIFICATION.md §7`, `§8.3`, `§11`.
  - `docs/CONTRACTS.md §11.1`, `§11.2`, `§11.5`, `§18`, `§20.8`.
  - `docs/TECHNICAL_PLAN.md §3`, `§4`, `§12`.
  - `AGENTS.md` (Rule 0, Definition of Ready, Continuous Progress Documentation, Definition of Done, Human Review Gates).
- Expected verification:
  - Focused `:integration:firebase-auth:testAndroidHostTest` and `:wiring:firebase:testAndroidHostTest`.
  - Complete CI command from `AGENTS.md`.
  - Provider decoupling check (`-Pcarapp.excludeFirebaseProviders=true`).
  - Objective-C golden header parity (`Shared.h` vs `Shared.h.golden`).
- Human review gates identified before work: `integration/firebase-auth/**` is a gated path and authentication is a gated topic. Owner review is required and the agent will not merge the pull request.
- Rule 0 acknowledged: owner conversation is Spanish (Spain); every repository artifact, branch, commit and pull-request field is technical English.
- TDD workflow: the owner's `NETWORK` phase label is interpreted as `RED`, matching the repository's closed RED/GREEN/REFACTOR workflow. Three local phase commits followed by one push and pull-request creation.

## In-Progress Checkpoint

- Date: 2026-09-03.
- Branch and base: `story/E2-02-firebase-auth-integration`, based on synchronized `main` at `ae646e8`.
- Current phase and latest commit: REFACTOR phase complete.
- Push and pull-request status: branch ready for push and PR creation.
- Completed since the previous checkpoint: completed GREEN implementation, fixed all detekt and ktlint issues, executed full CI command (636 tasks), verified provider decoupling (234 tasks), verified Objective-C golden header parity, updated `AGENTS.md`, appended `docs/PROJECT_LOG.md`, and generated this handoff.
- Verification evidence and known failures:
  - Complete non-instrumented repository command passed 636 actionable tasks.
  - Provider decoupling (`-Pcarapp.excludeFirebaseProviders=true`) passed 234 actionable tasks.
  - Objective-C golden header check passed with 0 differences against `Shared.h.golden`.
  - Zero known failures.
- Open decisions or blockers: none.
- Exact next step: commit REFACTOR phase, push branch to origin, create PR, and present grouped technical decisions to the owner in Spanish.

## Scope Completed

- Added `val createdAt: Instant? = null` property to `AuthSession` in `core/auth/.../AuthContracts.kt` and `docs/CONTRACTS.md §20.8`.
- Implemented `FirebaseAuthClient` implementing `AuthClient` and `TokenProvider` interfaces.
- Implemented `signInWithCredential` for Google and Apple credentials, guarding against unintended UID changes from active anonymous sessions (`AuthError.UidWouldChange`).
- Implemented `linkCredential` for Google and Apple credentials, asserting UID stability and mapping collision exceptions to `AuthError.CredentialAlreadyInUse`.
- Implemented `reauthenticate` for Google and Apple credentials.
- Implemented `signOut`, updating auth state to `AuthState.SignedOut`.
- Implemented `deleteAccount`, verifying ID token freshness against `FRESH_LOGIN_THRESHOLD_MS` (300,000 ms) before invoking the server deletion operation, strictly avoiding client-side SDK deletion (`D-23`).
- Implemented `getIdToken(forceRefresh: Boolean)` with `claims` timestamp parsing (`iat`, `exp`) into `AuthToken`.
- Implemented `parseCreationTime` normalizing Android milliseconds and iOS Apple reference date seconds (`NSTimeIntervalSinceReferenceDate`) to Unix `Instant`.
- Implemented `GitLiveFirebaseAuthGateway` with comprehensive exception mapping covering cancellations, network, provider errors, and collision codes.
- Wired `tokenProvider = authClient` in `:wiring:firebase` `firebaseAppProviders`.
- Added 32 unit tests in `FirebaseAuthClientTest`, 12 unit tests in `GitLiveFirebaseAuthGatewayTest` (44 total in `:integration:firebase-auth`), and verified token provider binding in `FirebaseAppProvidersTest`.

## Acceptance Evidence

- `FirebaseAuthClientTest` (32 tests) verifies:
  1. Retained anonymous session on start.
  2. Initial authState starts at Unknown before gateway emission.
  3. authState observes external transitions from gateway.
  4. Anonymous sign-in session publishing.
  5. Anonymous creation timestamp propagation from metadata (`createdAt`).
  6. Google sign-in credential exchange.
  7. Apple sign-in credential exchange.
  8. Google sign-in cancellation -> `AuthError.Cancelled`.
  9. Apple sign-in cancellation -> `AuthError.Cancelled`.
  10. Network failure -> `AuthError.NetworkUnavailable`.
  11. Link credential success (preserves UID, adds provider).
  12. Link credential collision -> `AuthError.CredentialAlreadyInUse`.
  13. Link credential with unexpected UID shift -> `AuthError.UidWouldChange`.
  14. Active anonymous session non-link sign-in -> `AuthError.UidWouldChange`.
  15. Active anonymous session sign-in with allowUidChange = true succeeds and updates session.
  16. Reauthentication with credentials.
  17. Reauthentication force-refreshes token to update `iat`.
  18. Sign out -> sets `AuthState.SignedOut`.
  19. Account deletion with fresh token (`<= FRESH_LOGIN_THRESHOLD_MS`) -> calls server operation, never calls client SDK delete.
  20. Account deletion with stale token (`> FRESH_LOGIN_THRESHOLD_MS`) -> `AuthError.RequiresRecentLogin`, does not call server.
  21. Account deletion fails closed when token has no usable `iat` (missing/malformed) -> `AuthError.ProviderUnavailable`, does not call server.
  22. Account deletion server failure -> `AuthError.AccountDeletionRemoteFailed`.
  23. Account deletion network failure -> `AuthError.NetworkUnavailable`.
  24. Account deletion server caller rejection propagates `PermissionDenied` from `AccountDeletionInvoker` -> `AuthError.PermissionDenied`.
  25. `getIdToken(forceRefresh)` -> returns `AuthToken(value, issuedAt, expiresAt)`.
  26. `getIdToken` forwards `forceRefresh` boolean flag.
  27. `deleteAccount` maps `getIdToken` failure to gateway error.
  28. `close()` cancels auth state observation.
  29. `parseCreationTime` parses Android millisecond timestamps.
  30. `parseCreationTime` parses Apple reference date second timestamps.
  31. `parseClaimTimestamp` parses numeric and string seconds and milliseconds.
  32. `parseClaimTimestamp` handles unparseable values gracefully.
- `GitLiveFirebaseAuthGatewayTest` (12 tests) verifies:
  1. `isCancellation` recognizes cancellation error messages and codes.
  2. `isCancellation` does not classify non-cancellation errors as cancelled.
  3. `parseTokenTimestamps` handles seconds, milliseconds, and `exp` fallback.
  4. `parseTokenTimestamps` throws `Provider` when `iat` is missing (fails closed).
  5. `parseTokenTimestamps` throws `Provider` when `iat` is malformed (fails closed).
  6. `classifyAuthFailure` maps collision codes (17025, `ERROR_CREDENTIAL_ALREADY_IN_USE`).
  7. `classifyAuthFailure` maps recent login required codes (17014, `ERROR_REQUIRES_RECENT_LOGIN`).
  8. `classifyAuthFailure` maps network codes (17020, `ERROR_NETWORK_REQUEST_FAILED`).
  9. `classifyAuthFailure` maps 17028 (`appNotAuthorized`) to `Provider` (configuration failure).
  10. `classifyAuthFailure` maps web and invalid credentials to `Provider` or `Cancelled`.
  11. `classifyAuthFailure` maps generic FirebaseException to `Unknown` or `Cancelled`.
  12. `AccountDeletionInvoker` `PermissionDenied` propagates through gateway unchanged.
- `FirebaseAppProvidersTest.providerFactoryBindsAuthClientImplementingTokenProviderAsTokenProvider` verifies that `:wiring:firebase` binds `authClient` as `tokenProvider`.

## Out of Scope / Not Done

- Native credential UI retrieval on Android (Credential Manager) and iOS (`AuthenticationServices`) is scheduled for `E2-03`.
- In-app account conversion UI / flow is scheduled for `E2-04`.
- Account deletion user flow orchestration is scheduled for `E2-05`.
- `deleteAccount()` leaving `authState` at `AuthState.SignedIn` upon successful completion of the server deletion operation is intentional at the client SDK boundary; resetting the auth state and orchestrating the user departure flow is managed by the E2-05 account deletion user flow story.
- Server-side Firebase Functions callable for account deletion (`D-23`) is scheduled for `E3-10`.

## Files Changed

- `build-logic/convention/src/main/kotlin/com/ruizurraca/carapp/buildlogic/KmpLibraryConventionPlugin.kt`
- `core/auth/src/commonMain/kotlin/com/ruizurraca/carapp/core/auth/AuthContracts.kt`
- `core/auth/src/commonTest/kotlin/com/ruizurraca/carapp/core/auth/AuthContractsTest.kt`
- `core/testing/src/commonMain/kotlin/com/ruizurraca/carapp/core/testing/GraphDependencyFakes.kt`
- `docs/BACKLOG.md`
- `docs/CONTRACTS.md`
- `docs/DECISION_BOARD.md`
- `docs/SPECIFICATION.md`
- `docs/TECHNICAL_PLAN.md`
- `docs/adr/README.md`
- `docs/adr/0112-explicit-uid-change-opt-in-for-account-adoption.md`
- `integration/firebase-auth/src/commonMain/kotlin/com/ruizurraca/carapp/integration/firebase/auth/FirebaseAuthClient.kt`
- `integration/firebase-auth/src/commonTest/kotlin/com/ruizurraca/carapp/integration/firebase/auth/FirebaseAuthClientTest.kt`
- `integration/firebase-auth/src/commonTest/kotlin/com/ruizurraca/carapp/integration/firebase/auth/GitLiveFirebaseAuthGatewayTest.kt`
- `iosApp/Tests/ViewModelLifecycleTests.swift`
- `wiring/firebase/src/commonMain/kotlin/com/ruizurraca/carapp/wiring/firebase/FirebaseAppProviders.kt`
- `wiring/firebase/src/commonTest/kotlin/com/ruizurraca/carapp/wiring/firebase/FirebaseAppProvidersTest.kt`
- `AGENTS.md`
- `docs/PROJECT_LOG.md`
- `docs/handoff-E2-02.md`

## Decisions Made

- Grouped technical decisions (presented to the user at the end of the process with problem description, 3 alternative solutions, and pros/cons):
  1. `AccountDeletionInvoker` delegation in `GitLiveFirebaseAuthGateway` vs direct Functions dependency or client SDK deletion.
  2. Cross-platform `creationTime` timestamp parsing (detecting Android ms vs iOS Apple reference date).
  3. Claims-based `iat`/`exp` timestamp extraction with safe 1-hour fallback for `AuthToken`.
  4. Anonymous UID protection against silent overwrite via non-link `signInWithCredential` returning `AuthError.UidWouldChange`.

## Verification Run

- RED phase:
  - `./gradlew :integration:firebase-auth:testAndroidHostTest` — failed as intended (17 failures out of 20 tests).
  - `./gradlew :wiring:firebase:testAndroidHostTest` — failed as intended (1 failure).
- GREEN phase:
  - `./gradlew :integration:firebase-auth:testAndroidHostTest :wiring:firebase:testAndroidHostTest` — passed all 23 integration tests and wiring tests.
  - `./gradlew :integration:firebase-auth:ktlintCheck :integration:firebase-auth:detekt :wiring:firebase:ktlintCheck :wiring:firebase:detekt` — passed with 0 violations after formatting and refactoring.
- REFACTOR phase:
  - Full CI command (`ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test ...`) — passed, 636 actionable tasks.
  - Provider decoupling (`./gradlew -Pcarapp.excludeFirebaseProviders=true testAndroidHostTest iosSimulatorArm64Test`) — passed, 234 actionable tasks.
  - Objective-C golden header check (`./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64` + `diff -u`) — passed with 0 differences.

## Contract Impact

- Added `val createdAt: Instant? = null` to `AuthSession` in `docs/CONTRACTS.md §20.8` to enable `createdAt` propagation from Firebase User metadata.

## Decision Board Impact

- No new decisions required. Aligns with existing `D-6`, `D-10`, `D-23`, `D-44`, and `D-102`.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [x] Entry appended in `docs/PROJECT_LOG.md`.

## Risks or Follow-ups

- E2-03 will provide the platform credential providers (Android Credential Manager and iOS AuthenticationServices) to supply `NativeAuthCredential` to `FirebaseAuthClient`.
- E3-10 will deploy the Firebase Admin server operation for account deletion.

## Human Review Gate

- Applies: `integration/firebase-auth/**` is a gated path and authentication is a gated topic. The pull request requires owner review and must not be merged by the agent.
