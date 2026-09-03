# Agent Handoff - E2-02

## Story

`E2-02 - :integration:firebase-auth - M`

## Ready Check

- Backlog story: `E2-02 - :integration:firebase-auth - M` is explicit and is the next open Phase 2 story in `docs/BACKLOG.md`.
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
- Added 23 unit tests in `FirebaseAuthClientTest` and verified token provider binding in `FirebaseAppProvidersTest`.

## Acceptance Evidence

- `FirebaseAuthClientTest` verifies:
  1. Retained anonymous session on start.
  2. Anonymous sign-in session publishing.
  3. Anonymous creation timestamp propagation from metadata (`createdAt`).
  4. Google sign-in credential exchange.
  5. Apple sign-in credential exchange.
  6. Google sign-in cancellation -> `AuthError.Cancelled`.
  7. Apple sign-in cancellation -> `AuthError.Cancelled`.
  8. Network failure -> `AuthError.NetworkUnavailable`.
  9. Link credential success (preserves UID, adds provider).
  10. Link credential collision -> `AuthError.CredentialAlreadyInUse`.
  11. Link credential with unexpected UID shift -> `AuthError.UidWouldChange`.
  12. Active anonymous session non-link sign-in -> `AuthError.UidWouldChange`.
  13. Reauthentication with credentials.
  14. Sign out -> sets `AuthState.SignedOut`.
  15. Account deletion with fresh token (`<= FRESH_LOGIN_THRESHOLD_MS`) -> calls server operation, never calls client SDK delete.
  16. Account deletion with stale token (`> FRESH_LOGIN_THRESHOLD_MS`) -> `AuthError.RequiresRecentLogin`, does not call server.
  17. Account deletion server failure -> `AuthError.AccountDeletionRemoteFailed`.
  18. Account deletion network failure -> `AuthError.NetworkUnavailable`.
  19. `getIdToken(forceRefresh)` -> returns `AuthToken(value, issuedAt, expiresAt)`.
  20. `getIdToken` forwards `forceRefresh` boolean flag.
  21. `parseCreationTime` parses Android millisecond timestamps.
  22. `parseCreationTime` parses Apple reference date second timestamps.
  23. `parseClaimTimestamp` parses numeric and string seconds and milliseconds.
- `FirebaseAppProvidersTest.providerFactoryBindsAuthClientImplementingTokenProviderAsTokenProvider` verifies that `:wiring:firebase` binds `authClient` as `tokenProvider`.

## Out of Scope / Not Done

- Native credential UI retrieval on Android (Credential Manager) and iOS (`AuthenticationServices`) is scheduled for `E2-03`.
- In-app account conversion UI / flow is scheduled for `E2-04`.
- Account deletion user flow orchestration is scheduled for `E2-05`.
- Server-side Firebase Functions callable for account deletion (`D-23`) is scheduled for `E3-10`.

## Files Changed

- `core/auth/src/commonMain/kotlin/com/ruizurraca/carapp/core/auth/AuthContracts.kt`
- `core/auth/src/commonTest/kotlin/com/ruizurraca/carapp/core/auth/AuthContractsTest.kt`
- `docs/CONTRACTS.md`
- `integration/firebase-auth/src/commonMain/kotlin/com/ruizurraca/carapp/integration/firebase/auth/FirebaseAuthClient.kt`
- `integration/firebase-auth/src/commonTest/kotlin/com/ruizurraca/carapp/integration/firebase/auth/FirebaseAuthClientTest.kt`
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
