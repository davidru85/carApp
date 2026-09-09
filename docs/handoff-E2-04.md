# Agent Handoff — E2-04 Anonymous Account Conversion F-4

## Summary

- In progress: GREEN implementation is materially complete for anonymous account linking, durable credential-collision replacement and the E3-11 callable boundary. Production-graph integration and cross-platform verification are the next work.

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
- Current phase and latest commit: GREEN complete. RED is commit `90f1404`
  (`test(E2-04): specify anonymous account conversion`). The original RED commit `fe4d410` was
  amended four times, ending at `90f1404`, to add the callable-adapter RED tests before their
  implementation, correct the callable response key to `cleanupTicket`, add the conversion analytics
  coverage, and reduce the adapter declaration to a behavior-free shell so its first test failed for
  missing behavior rather than for a missing type.
- Push and pull-request status: no pull request. The remote branch still holds the superseded
  pre-amend RED commit `fe4d410`, so local and remote diverge. Do not pull, merge or rebase that
  commit. Final publication uses `git push --force-with-lease` after REFACTOR.
- Completed since the previous checkpoint: the pending `:core:database` rerun is green on schema v3;
  the five affected modules pass together (`:core:database` 46, `:shared` 90,
  `:integration:firebase-auth` 47, `:integration:firebase-firestore` 9, `:wiring:firebase` 4 — 196
  tests, 0 failures). Style violations left by the GREEN implementation were resolved across
  `:shared`, `:shared:testing`, `:wiring:firebase`, `:integration:firebase-auth` and `:core:testing`.
  One genuine cross-platform defect was found and fixed: `FakeOrphanCleanupClient` implemented the
  `@HiddenFromObjC` `OrphanCleanupClient` port without carrying the annotation itself, which broke
  `:core:testing:compileKotlinIosSimulatorArm64`. It now follows the `Fakes.kt` convention. The
  earlier checkpoint recorded only Android-host verification, so this failure was not visible before.
- Verification evidence and known failures: the complete non-instrumented CI command now fails on
  `:shared:detekt` alone, with five issues and nothing else. Every other task in that command passes,
  including `ktlintCheck`, `architectureCheck`, `contractCheck`, `:build-logic:convention:test`,
  `koverVerify`, `:androidApp:assembleDebug`, `:androidApp:testDebugUnitTest`, `testAndroidHostTest`
  and `iosSimulatorArm64Test`. The five issues are the declared REFACTOR work: `LongMethod` on
  `completePermanentSignIn`, `ComplexCondition` on the `startAccountConversion` guard, `ReturnCount`
  on `confirmAccountConversion`, and two `MaxLineLength` lines in
  `AccountConversionCoordinatorTest.kt`. E1-14 and E1-17 remain the documented pre-existing sources
  of ambiguity for red `shared-tests` and `ios-simulator-build` jobs.
- Open decisions or blockers: no owner decision blocks implementation. Four implementation decisions
  must be registered during REFACTOR with three alternatives and pros and cons: normalized durable
  marker storage; the GitLive callable transport, which adds the `dev.gitlive:firebase-functions`
  artifact at the already-`Accepted` GitLive version rather than a new library or version; the
  client-side destructive remote replacement ordering; and the ephemeral collision-credential
  lifetime. D-149 and D-150 stay out of scope and unresolved.
- Exact next step: complete REFACTOR — resolve the five detekt issues, register the four decisions
  with their ADRs and the four mirroring rows each, update `docs/CONTRACTS.md` for the durable store
  and the callable port, append the `docs/PROJECT_LOG.md` entry and fill in the acceptance evidence.
  Then rerun the complete CI command, verify the iOS simulator build and the Android instrumented
  tests, publish with `--force-with-lease` and open the gated pull request.

## Scope Completed

- Ready Check, branch creation and RED phase.
- Durable v3 conversion persistence and migration implementation.
- Replay-safe destructive replacement coordinator with all six interruption boundaries.
- Session conversion/link/collision/confirmation behavior.
- Complete Firestore Fuel Entry snapshot decoding.
- Firebase callable adapter and Android-host adapter tests.
- Provider dependency wiring through test, staged and production graphs.
- iOS Firebase Functions product declaration and regenerated project.

## Acceptance Evidence

- Pending.

## Out of Scope / Not Done

- Automatic merging of anonymous and permanent account data.
- E3-15 and E3-16 follow-up hardening.
- E2-05 sign-out and account deletion UI.
- E4-01 Settings UI controls. E2-04 delivers the callable `SessionStateHolder` intent and typed confirmation contract; the Settings surface remains owned by E4-01.

## Files Changed

- `docs/handoff-E2-04.md` — in-progress story record and Ready Check.
- `core/auth/**/OrphanCleanupContracts.kt` — behavior-free E3-11 client port used by RED tests.
- `core/database/**/AccountConversionDatabaseAccess*` — behavior-free durable-store seam and RED tests.
- `shared/**/AccountConversion*` and `SessionStateHolderTest.kt` — behavior-free coordinator seam and RED behavior coverage.
- `integration/firebase-firestore/**/FirebaseRemoteSyncSourceTest.kt` — closed Fuel Entry snapshot coverage needed by replacement.
- `core/database/**/schema.sq`, `database.sq`, `2.sqm` and migration tests — schema v3 durable marker, normalized captured rows and preservation evidence.
- `integration/firebase-auth/**/FirebaseOrphanCleanupClient*` and dependency catalog/build files — regional GitLive callable implementation and provider-error mapping.
- `shared/**/AppGraph*`, `AppProviders.kt`, `BuildAppGraph.kt`, `StateHolders.kt` and shared-testing fakes — conversion orchestration, session behavior and explicit dependency wiring.
- `wiring/firebase/**/FirebaseAppProviders.kt` — staged and production callable providers.
- `iosApp/project.yml` and generated Xcode project — Firebase Functions native product.

## Decisions Made

- Story-specific owner exemption: retain separate RED, GREEN and REFACTOR commits but push them together once after REFACTOR, as explicitly requested for E2-04. This differs from the default per-phase push cadence in `docs/SPECIFICATION.md` §11.
- The RED commit was amended from `fe4d410` to `57cd1b9` only to add the callable-adapter RED tests discovered before implementing that adapter. The remote currently retains `fe4d410`; use a lease-protected replacement at final publication and never merge the two RED commits.
- Draft implementation choice for later registration: normalized SQLDelight operation and snapshot tables, rather than an opaque serialized marker or overloading the outbox.
- Draft implementation choice for later registration: a provider-free `OrphanCleanupClient` with a GitLive Firebase Functions adapter, rather than host-specific bridges or raw HTTP.
- Draft implementation choice for later registration: idempotent client-side full replacement using complete snapshot writes and dependency-ordered tombstones, rather than merge semantics or a new server write operation.
- Draft implementation choice for later registration: keep the collision credential in memory only; before the Firebase session switch a restart requires provider credential reacquisition, while the durable marker resumes automatically after the switch.

## Verification Run

- [ ] Relevant tests pass
- [ ] Lint passes (ktlint, detekt)
- [ ] Coverage thresholds hold
- [ ] Architecture checks pass
- [ ] Contract check passes
- [ ] Relevant builds pass (Android, iOS simulator, `Shared` framework from `:composition:ios`)
- [ ] Documentation updated if behaviour, decisions or models changed

Commands or checks run:

```text
./gradlew :shared:testAndroidHostTest :core:database:testAndroidHostTest :integration:firebase-auth:testAndroidHostTest :integration:firebase-firestore:testAndroidHostTest
  PASS before RED changes.

./gradlew :core:database:testAndroidHostTest --tests com.ruizurraca.carapp.core.database.AccountConversionDatabaseAccessTest :shared:testAndroidHostTest --tests com.ruizurraca.carapp.AccountConversionCoordinatorTest --tests com.ruizurraca.carapp.SessionStateHolderTest :integration:firebase-firestore:testAndroidHostTest --tests com.ruizurraca.carapp.integration.firebase.firestore.FirebaseRemoteSyncSourceTest
  EXPECTED RED: 4 database tests and 6 shared tests fail for missing E2-04 behavior; sources compile and unrelated focused tests pass.

./gradlew :shared:ktlintCheck :core:auth:ktlintCheck :core:database:ktlintCheck :integration:firebase-firestore:ktlintCheck
  PASS.

./gradlew :core:database:testAndroidHostTest --tests com.ruizurraca.carapp.core.database.AccountConversionDatabaseAccessTest
  PASS in GREEN.

./gradlew :shared:testAndroidHostTest --tests com.ruizurraca.carapp.AccountConversionCoordinatorTest
  PASS in GREEN, including all six interruption boundaries.

./gradlew :shared:testAndroidHostTest --tests com.ruizurraca.carapp.SessionStateHolderTest
  PASS in GREEN.

./gradlew :integration:firebase-auth:testAndroidHostTest --tests com.ruizurraca.carapp.integration.firebase.auth.FirebaseOrphanCleanupClientTest
  EXPECTED RED before implementation due to missing adapter; PASS after implementation (3 tests).

./gradlew :shared:testAndroidHostTest :wiring:firebase:testAndroidHostTest :integration:firebase-firestore:testAndroidHostTest --tests com.ruizurraca.carapp.integration.firebase.firestore.FirebaseRemoteSyncSourceTest
  PASS in GREEN.

./gradlew :core:database:testAndroidHostTest
  EXPECTED TRANSIENT GREEN FAILURE: only the two pre-existing schema-v2 expectations failed after the v3 bump. Both test expectations and a populated v2-to-v3 migration test are now updated; rerun pending.
```

## Contract Impact

- [ ] The existing E2-04 behavioral contract is unchanged, but representation details for the new durable store and callable port must be added during REFACTOR.
- [ ] Updated `docs/CONTRACTS.md` §:

## Decision Board Impact

- [ ] Four implementation decisions and ADRs must be registered during REFACTOR; draft choices are recorded above.
- [ ] Updated `docs/DECISION_BOARD.md` (`D-n`) and the related ADR:

## Shared-Write Modules Touched

- [ ] None
- [x] `core/database` — expected for the durable replacement marker; confirm no other story is modifying it. The working tree was clean and no concurrent agent is active for this task.

## Project Log Entry

- [ ] Entry appended to `docs/PROJECT_LOG.md`

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

- E3-15/D-149 and E3-16/D-150 remain separate known follow-ups to the E3-11 authorization flow; E2-04 must not silently absorb their unresolved owner choices.
- The local branch intentionally diverges from its remote only because RED was amended. Resolve with a final lease-protected replacement, not pull/merge/rebase.
