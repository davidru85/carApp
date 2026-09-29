# Agent Handoff — E2-04 Anonymous Account Conversion F-4

## Summary

- Complete and awaiting the gated owner review. F-4 anonymous account conversion links a permanent
  credential without changing the UID, turns a credential collision into a typed destructive
  confirmation, and runs the confirmed replacement behind a durable, replay-safe schema v3 marker
  that ends with the E3-11 orphan account deletion.

## Story

`E2-04 - Anonymous Account Conversion F-4 - M`

## Ready Check

- [x] Backlog story is explicit: `docs/BACKLOG.md` E2-04.
- [x] Acceptance criteria reviewed: UID-preserving linking; `SessionStateHolder` conversion intents; explicit `AdoptExistingAccount` confirmation; cancellation safety; durable snapshot and cleanup ticket before session switch; resumable idempotent remote and local replacement; E3-11 orphan deletion; interruption coverage; no automatic merge.
- [x] Dependencies checked: E2-02 and E3-11 are merged on `main`.
- [x] Required decisions are not `Proposed` or `Pending`: D-60, D-61, D-63, D-64, D-111 and D-132 through D-143 are `Accepted`. E3-15/D-149 and E3-16/D-150 are tracked follow-ups and are not declared E2-04 dependencies.
- [x] Normative sections reviewed: `docs/SPECIFICATION.md` §§3, 7 F-4, 11 and 12; `docs/CONTRACTS.md` §§8, 11.1, 11.3, 11.5, 16, 17, 18, 20.2, 20.8 and 20.10; `docs/DECISION_BOARD.md`; `docs/TECHNICAL_PLAN.md` §§2, 4 and 6.
- [x] Expected verification identified: focused RED/GREEN tests; the complete non-instrumented CI command; relevant Android instrumentation and iOS build/test commands if host surfaces change.
- [x] Human review gates identified before work: gated paths `core/auth/**`, `core/database/**`, the normative contract/decision paths if changed, and gated topics authentication, remote backend and Swift-facing API.
- [x] Rule 0 acknowledged: owner conversation is Spanish (es-ES); repository artifacts are technical English.

## In-Progress Checkpoint

- Date: 2026-09-09
- Branch and base: `story/E2-04-anonymous-account-conversion` from `origin/main` at `50eec16`
- Current phase and latest commit: REFACTOR complete. The branch is the three commits the owner
  asked for: RED `1257f1a`, GREEN `9361526` and the REFACTOR commit that carries this checkpoint.
- Push and pull-request status: published once after REFACTOR with `git push --force-with-lease`,
  replacing the superseded pre-amend RED commit `fe4d410` that the remote still held; pull request
  opened against `main` and awaiting the gated owner review.
- Completed since the previous checkpoint: the five detekt findings were resolved as genuine
  refactoring rather than suppression — `completePermanentSignIn` and `confirmAccountConversion` now
  delegate their outcome mapping to `permanentSignInState` and `confirmedConversionState`, the two
  nullable collision fields collapsed into one typed `PendingCollision`, the duplicated
  Google-or-Apple guard became `AuthProvider.isNativePermanentProvider()`, and the two long test
  fixtures were wrapped. `D-151` through `D-154` were registered with ADR-0152 to ADR-0155 and their
  four mirroring rows each, `docs/CONTRACTS.md` gained the port and durable-marker representation,
  `docs/TECHNICAL_PLAN.md §6` gained the schema v3 DDL, and the project log entry was appended.
- Verification evidence and known failures: the complete non-instrumented CI command passes with no
  failures, as do the iOS simulator build, the iOS test suite on an erased simulator and the Android
  instrumented suite on the D-84 API 36 emulator. No known failure remains. E1-14 and E1-17 are the
  documented pre-existing sources of ambiguity for red `shared-tests` and `ios-simulator-build` jobs.
- Open decisions or blockers: none blocking. `D-149` / `E3-15` and `D-150` / `E3-16` remain
  unresolved owner decisions and are outside this story.
- Exact next step: the owner's gated review. Agents MUST NOT merge this pull request.

## Scope Completed

- Ready Check, branch creation and the RED, GREEN and REFACTOR phases.
- UID-preserving `linkCredential` conversion and the typed `AdoptExistingAccount` collision
  confirmation, with cancellation safety.
- Durable schema v3 conversion persistence and its additive `2.sqm` migration.
- Replay-safe destructive replacement coordinator covering all six interruption boundaries.
- Complete Firestore Fuel Entry snapshot decoding, needed to read the permanent account's documents.
- The provider-free `OrphanCleanupClient` port and its GitLive Firebase Functions adapter.
- Provider dependency wiring through the test, staged and production graphs.
- Conversion start, success and failure analytics events.
- Android and iOS message mapping and localization for the destructive confirmation.
- iOS Firebase Functions product declaration and the regenerated Xcode project.
- `D-151` to `D-154` with ADR-0152 to ADR-0155 and all four mirroring rows each.

## Acceptance Evidence

Each backlog acceptance criterion, with the test that proves it:

- Successful linking preserves data and keeps the UID — `SessionStateHolderTest` asserts that a
  successful conversion reports the same UID and does not switch sessions.
- `startAccountConversion(provider)` calls `linkCredential`, not `signInWithCredential` —
  `SessionStateHolderTest` pins the client call for the conversion intent and the ordinary
  permanent sign-in intent separately.
- `confirmAccountConversion(confirmation)` handles the collision confirmation — covered for the
  confirmed path, for a non-matching confirmation and for a collision that is no longer actionable.
- The collision offers an explicit destructive choice gated by `Confirmation.AdoptExistingAccount` —
  `SessionStateHolderTest` asserts the typed `UiMessage` and its confirmation; the Android
  `nonAuthStringResource` mapping and `iosApp/UiMessageMapping.swift` resolve it to copy that states
  the replacement, with `UiMessageMappingTests` covering the iOS side.
- Cancelling leaves the anonymous session and local data untouched — `SessionStateHolderTest` covers
  `clearMessage()` and a native failure, both of which drop the collision and emit
  `AccountConversionFailed(CANCELLED)`.
- Confirmation persists a complete snapshot and obtains the cleanup ticket before switching
  sessions, replaces remote data idempotently and keeps snapshot and ticket durable until both
  replacement and cleanup succeed — `AccountConversionDatabaseAccessTest` covers atomic capture and
  durability until the operation is cleared; `AccountConversionCoordinatorTest` covers the ordering
  and the idempotent replacement.
- Interruption tests cover every boundary after confirmation, including after the session switch;
  retry resumes the captured replacement instead of pulling over it —
  `AccountConversionCoordinatorTest` replays from all six checkpoints of
  `AccountConversionCheckpoint` and asserts convergence on the same result.
- The flow calls the E3-11 callable to delete the orphaned anonymous identity, with no dependence on
  an Auth deletion trigger — `FirebaseOrphanCleanupClientTest` pins both callable names and the
  `cleanupTicket` payload key; the coordinator calls deletion last, before clearing the marker.
- Both callables are reached through the provider-free port — `architectureCheck` keeps Firebase and
  GitLive types inside `:integration:*` and `:shared` free of integrations.
- Automatic merge is not implemented — replacement tombstones every permanent-account document
  absent from the captured snapshot; no merge path exists.

## Out of Scope / Not Done

- Automatic merging of anonymous and permanent account data.
- E3-15 and E3-16 follow-up hardening.
- E2-05 sign-out and account deletion UI.
- E4-01 Settings UI controls. E2-04 delivers the callable `SessionStateHolder` intent and typed confirmation contract; the Settings surface remains owned by E4-01.

## Files Changed

- `core/auth/**/OrphanCleanupContracts.kt` — the provider-free `OrphanCleanupClient` port and its
  ticket type.
- `core/database/**/AccountConversionDatabaseAccess.kt`, `schema.sq`, `database.sq`, `2.sqm` and the
  migration and schema tests — the schema v3 durable marker, its typed queries and the additive
  migration with row-preservation evidence.
- `core/testing/**/GraphDependencyFakes.kt` — `FakeOrphanCleanupClient`, carrying `@HiddenFromObjC`
  because the port it implements is hidden.
- `shared/**/AccountConversion.kt` — the replay-safe coordinator, the six checkpoints and the
  snapshot-to-row and snapshot-to-remote mappings.
- `shared/**/StateHolders.kt` — conversion intents, the typed collision confirmation, the in-memory
  `PendingCollision` and the conversion analytics events.
- `shared/**/AppGraph.kt`, `AppGraphDependencies.kt`, `AppProviders.kt`, `BuildAppGraph.kt` and the
  `:shared:testing` factories — the port in the canonical dependency order.
- `integration/firebase-auth/**/FirebaseOrphanCleanupClient.kt`, its test, the version catalog and
  the module build file — the regional GitLive callable adapter and its error mapping.
- `integration/firebase-firestore/**/FirebaseRemoteSyncSource.kt` and its test — complete Fuel Entry
  snapshot decoding.
- `wiring/firebase/**/FirebaseAppProviders.kt` — staged and production callable providers.
- `androidApp/**/MainActivity.kt` and both `strings.xml` — the confirmation message mapping and copy.
- `iosApp/UiMessageMapping.swift`, its tests, both `Localizable.strings`, `project.yml` and the
  generated Xcode project — the iOS mapping, copy and the Firebase Functions product.
- `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md`,
  `docs/BACKLOG.md`, `AGENTS.md`, `docs/adr/README.md`, ADR-0152 to ADR-0155, `docs/PROJECT_LOG.md`
  and this handoff.

## Decisions Made

- `D-151` normalized durable marker storage (ADR-0152); `D-152` the provider-free callable transport
  (ADR-0153); `D-153` client-side destructive remote replacement ordering (ADR-0154); `D-154` the
  in-memory-only collision credential (ADR-0155). All four are `Accepted`, each with three options
  and their costs, and each mirrored in `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`,
  `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`.
- Story-specific owner exemption: keep separate RED, GREEN and REFACTOR commits but push them
  together once after REFACTOR, as the owner explicitly requested for E2-04. This deviates from the
  per-phase push cadence of `docs/SPECIFICATION.md §11` and is recorded here as required.
- The RED commit was amended four times, from `fe4d410` to `90f1404`, to add the callable-adapter
  RED tests before their implementation, correct the callable response key to `cleanupTicket`, add
  the conversion analytics coverage, and reduce the adapter declaration to a behavior-free shell so
  its first test failed for missing behavior rather than for a missing type. A fifth amend, to
  `1257f1a`, corrected literal escape sequences in the commit message body and changed no file. The
  remote held the superseded `fe4d410`, so publication replaced it with `--force-with-lease` rather
  than merging the two RED commits.
- Adding `dev.gitlive:firebase-functions` is not a new library or version decision: it is a further
  artifact of the GitLive release already `Accepted` for auth and Firestore, at the same catalog
  version reference, so the `D-65` Firebase Apple compatibility pin continues to govern it. It is
  recorded as the implementation decision `D-152` rather than escalated as an owner stack change.
- Rule 0 held for the whole story: every owner-facing reply was in Spanish (es-ES) and every
  repository artifact is in technical English. No violation occurred.

## Verification Run

- [x] Relevant tests pass
- [x] Lint passes (ktlint, detekt)
- [x] Coverage thresholds hold
- [x] Architecture checks pass
- [x] Contract check passes
- [x] Relevant builds pass (Android, iOS simulator, `Shared` framework from `:composition:ios`)
- [x] Documentation updated if behaviour, decisions or models changed

Commands or checks run:

```text
./gradlew :shared:testAndroidHostTest :core:database:testAndroidHostTest :integration:firebase-auth:testAndroidHostTest :integration:firebase-firestore:testAndroidHostTest
  PASS before the RED changes: the baseline is green, so any later failure belongs to E2-04.

./gradlew :core:database:testAndroidHostTest --tests ...AccountConversionDatabaseAccessTest :shared:testAndroidHostTest --tests ...AccountConversionCoordinatorTest --tests ...SessionStateHolderTest :integration:firebase-firestore:testAndroidHostTest --tests ...FirebaseRemoteSyncSourceTest
  EXPECTED RED: 4 database and 6 shared tests fail for missing E2-04 behavior; sources compile and
  unrelated focused tests stay green.

./gradlew :integration:firebase-auth:testAndroidHostTest --tests ...FirebaseOrphanCleanupClientTest
  EXPECTED RED against the behavior-free adapter shell; PASS after implementation (3 tests).

./gradlew :core:database:testAndroidHostTest :shared:testAndroidHostTest :wiring:firebase:testAndroidHostTest :integration:firebase-auth:testAndroidHostTest :integration:firebase-firestore:testAndroidHostTest
  PASS in GREEN: 196 tests, 0 failures (:core:database 46, :shared 90, :integration:firebase-auth 47,
  :integration:firebase-firestore 9, :wiring:firebase 4).

./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test
  FAIL in GREEN, then PASS in REFACTOR. The complete non-instrumented CI command surfaced two
  defects the earlier Android-host-only verification had hidden:
    1. `:core:testing:compileKotlinIosSimulatorArm64` failed because `FakeOrphanCleanupClient`
       implemented the `@HiddenFromObjC` `OrphanCleanupClient` port without the annotation. Fixed in
       GREEN, following the `Fakes.kt` convention.
    2. `:shared:detekt` reported five issues — `LongMethod`, `ComplexCondition`, `ReturnCount` and
       two `MaxLineLength`. Resolved in REFACTOR by extracting `permanentSignInState` and
       `confirmedConversionState`, collapsing the two collision fields into one `PendingCollision`,
       extracting `AuthProvider.isNativePermanentProvider()` and wrapping the two test fixtures. No
       suppression and no baseline file was added.
  Final run on the REFACTOR head: BUILD SUCCESSFUL, no failures.

./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator -configuration Debug ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build
  ** BUILD SUCCEEDED ** with the regenerated project carrying the Firebase Functions product.

xcrun simctl erase <device> && xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator -destination "id=<device>" test
  FAIL on the first run, then PASS. `UiMessageMappingTests.testAccountCollisionConfirmationExplains
  TheDestructiveReplacement` asserted English substrings of the running locale's copy, so it failed
  on a Spanish simulator while passing on an English CI runner. The mapping was correct; the test
  was locale-dependent. It now reads the `en` and `es` bundles directly and asserts that each copy
  states the replacement and its irreversibility, which is both locale-independent and stricter.
  Final run on an erased simulator: 41 unit tests and the UI suite pass, ** TEST SUCCEEDED **, with
  the one pre-existing skip that needs a registered simulator-only App Check debug token.

ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest
  PASS on the D-84 API 36 emulator: 17 instrumented tests, 0 failures. The serial is pinned because
  an API 28 physical device was also attached and would otherwise have been selected.
```

## Contract Impact

- [x] Updated `docs/CONTRACTS.md` §11.3 — the `OrphanCleanupClient` port, the `cleanupTicket`
  payload key, the durable operation marker's two tables, the meaning of `phase` as the resume
  point, and the in-memory-only lifetime of the colliding credential.
- [x] Updated `docs/CONTRACTS.md` §11.6 — `orphanCleanupClient` added to `AppGraphDependencies`,
  `AppProviders` and the canonical parameter order, immediately after `authClient`. Assertion 13 of
  §18 confirms the `:shared:testing` factory matches, with 16 parameters in that order.
- The behavioral contract of §11.3 is otherwise unchanged; this story implements it.

## Decision Board Impact

- [x] Updated `docs/DECISION_BOARD.md` with `D-151`, `D-152`, `D-153` and `D-154`, all `Accepted`,
  and the matching ADRs ADR-0152, ADR-0153, ADR-0154 and ADR-0155.
- [x] Mirrored identically in `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and
  `docs/adr/README.md`; `contractCheck` assertions 3 and 4 pass over 155 ADRs.

## Shared-Write Modules Touched

- [ ] None
- [x] `core/database` — expected for the durable replacement marker; confirm no other story is modifying it. The working tree was clean and no concurrent agent is active for this task.

## Project Log Entry

- [x] Entry appended to `docs/PROJECT_LOG.md`: *2026-09-09 — E2-04 anonymous account conversion
  implemented*.

## Human Review Gate

Gates are defined canonically in `AGENTS.md`.

- [ ] Not applicable
- [ ] E0-00 owner decision closure
- [ ] Phase 0 closure
- [ ] E0-07 walking skeleton
- [ ] E1-05 consumption calculation
- [ ] E2-06 local owner adoption
- [ ] E3-01 Firestore security rules
- [ ] E3-03 synchronization engine
- [x] Gated path (`core/auth/**`, `core/database/**`, and any normative or ADR path changed)
- [x] Gated topic (authentication, remote backend, and possibly Swift-facing API surface)

## Risks or Follow-ups

- The remote replacement is idempotent and resumable but **not atomic**. Between an interruption and
  its replay, the permanent account's remote data holds a mixture of both data sets. The MVP
  one-active-device rule bounds who can observe that window; a story that relaxes it MUST revisit
  `D-153`.
- `replaceLocalSnapshot` clears the local vehicle, fuel entry, outbox, sync cursor and quarantine
  tables before rebuilding from the captured snapshot. That is the intended no-merge semantics for a
  single active account, and it runs only inside the confirmed operation, but it is the most
  destructive statement in the story and deserves the reviewer's attention.
- The `cleanupTicket` payload key is a contract with the deployed E3-11 function that no compiler
  checks. `FirebaseOrphanCleanupClientTest` pins it in both directions; a change to the function
  MUST change both in the same pull request.
- A restart between the collision prompt and the confirmed sign-in loses the in-memory credential by
  design (`D-154`), so the owner reacquires it from the provider and starts the conversion again.
  Nothing destructive has happened at that point.
- `E3-15` / `D-149` and `E3-16` / `D-150` remain separate unresolved owner decisions on the E3-11
  authorization flow. E2-04 does not absorb them and does not depend on them.
- E1-14 and E1-17 remain the documented pre-existing flakes that make a red `shared-tests` or
  `ios-simulator-build` job ambiguous.
