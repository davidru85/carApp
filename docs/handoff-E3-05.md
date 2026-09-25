# Agent Handoff — E3-05 Backup Status UI

## Story

`E3-05 - Backup Status UI - S` (`docs/BACKLOG.md`).

## Ready Check

- Backlog story: `E3-05 - Backup Status UI - S`.
- Acceptance criteria reviewed: the four criteria of the backlog entry — (1) `SyncStatus` is rendered
  with the precedence `Failed > Syncing > Pending > Idle`; (2) being offline with pending rows, or
  with only connectivity-code retryable failures, renders as `Pending`, never as an error; (3) the
  failed state offers manual retry through `SyncController.retryFailed()`; (4) inherited from
  `E3-08`: make the `docs/CONTRACTS.md §11.6` rule executable, which requires a new `D-` decision on
  where the check lives.
- Dependencies checked: no `Depends on:` row exists for this story in `docs/BACKLOG.md`. Its inputs are
  all merged: `E3-03` (`SyncController` with the `§9.9` aggregate), `E3-04` (platform triggers),
  `E3-08` (the app graph and the two guarded surfaces) and `E3-12` (cross-device recovery, which made
  the aggregate observable on a real recovery path).
- Decisions checked: no `Proposed` or `Pending` decision applies, so the story depends on no open
  decision. `D-149`, `D-150` and `D-173` remain open, gate `E3-15`, `E3-16` and `E3-18` respectively,
  and are untouched. `D-190` is `Accepted` and merged inside pull request #73.
- Normative sections reviewed: `docs/SPECIFICATION.md §3.1` (the "discreet backup status indicator
  with manual retry" bullet), `§9` (remote backup and recovery), P2; `docs/CONTRACTS.md §9.9` (the
  aggregate and its precedence), `§11.6` (the rule criterion 4 makes executable), `§14` (the single
  relayed `SyncStatus`), `§18` (the protected checks and the assertion numbering), `§20.10` (the two
  hidden members that exist); `docs/DESIGN.md §6` and `§7`; `docs/SPECIFICATION.md §11` (TDD and its
  exemption list); `docs/handoff-E3-08.md` (the `§11.6` deferral and its expected owner),
  `docs/handoff-E3-12.md` and `docs/handoff-E1-18.md`.
- Expected verification: the complete non-instrumented `AGENTS.md` command,
  `:androidApp:connectedDebugAndroidTest` on the `D-84` API 36 emulator, the `iosApp` `xcodebuild`
  invocation from `docs/handoff-E0-06.md` with `ARCHS=arm64`, and — for criterion 4 — the new
  contract-check fixtures proving the rule fires.
- Human review gates identified before work: **Applies.** `E3-05` is not a gated story and the story
  index marks its gate `—`, but this change edits gated paths (`docs/CONTRACTS.md`,
  `docs/DECISION_BOARD.md`, `docs/adr/`) and touches a gated topic (the synchronization algorithm,
  through `§9.9`). It does **not** alter any exported declaration of `:shared`: the indicator renders
  members that already exist. The pull request is handed back unmerged.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every artifact it
  produces is in technical English.

## In-Progress Checkpoint

Update this section at every material state change and before yielding unfinished work (`D-105`).

- Date: 2026-09-25
- Branch and base: `story/E3-05-backup-status-ui`, based on `main` / `origin/main` at `5141e68`.
- Current phase and latest commit: REFACTOR, complete. RED is `d453d65`; the green phase follows it.
- Push and pull-request status: RED pushed; the green phase is pushed with the pull request.
- Completed since the previous checkpoint: the RED phase (three failing units), the green
  implementation for both hosts, assertion 36 with thirteen fixtures, the three decisions with their
  ADRs and the four mirroring rows each, and the normative updates.
- Verification evidence and known failures: see Verification Run. No known failure.
- Open decisions or blockers: none. `D-191`, `D-192` and `D-193` are `Accepted` by the owner's
  answers of 2026-09-25.
- Exact next step: hand the pull request to the owner's gated review.

## Scope Completed

- The host-side classification of the resolved aggregate, on both hosts: four published `SyncStatus`
  values to four visuals, the counts inside a status treated as detail, and `Failed` the only visual
  presented as an error. It takes no connectivity observation, so "offline means error" is
  unrepresentable in the host rather than merely untested.
- The indicator itself on both hosts, on the vehicle list screen (`D-192`), drawn as the status chip
  the platform designs already place there.
- The manual retry, wired to the existing `SyncStateHolder.retryFailed()`; its failure surfaces
  through the holder's existing typed `UiMessage` and needs no new channel.
- `docs/CONTRACTS.md §18` assertion 36, making the `§11.6` hidden-member rule executable in both
  directions and for both fail-open shapes, with a failing fixture per problem branch (`D-191`).
- The copy in both languages on both platforms, with the `Idle` label stating that nothing is
  outstanding rather than that a remote copy exists (`D-193`).

## Acceptance Evidence

| Criterion | Evidence |
|---|---|
| 1. `SyncStatus` rendered with the `§9.9` precedence | The host renders the resolved value; `SyncStatusVisualTest.everyPublishedStatusMapsToItsOwnVisual` (Android) and `SyncStatusVisualTests.testEveryPublishedStatusMapsToItsOwnVisual` (iOS) pin the four-way classification. The precedence itself is `:core:sync`'s and stays pinned there by `DefaultSyncControllerTest`, which `E3-05` does not touch |
| 2. Offline with pending rows, or connectivity-only retryable failures, renders as `Pending`, never an error | The classification takes no connectivity fact and its parameter is the already-resolved `SyncStatus`, so the host cannot re-derive the buckets: `syncStatusVisual` has no overload accepting an online flag. `DefaultSyncControllerTest.offlineWriteIsBackedUpAfterConnectivityReturns` and `connectivityFailureKeepsRowStateAndAggregateInAgreement` pin that the resolved value is `Pending` for both cases; `SyncStatusVisualTest.aPendingStatusIsNeverClassifiedAsFailed` and `SyncStatusIndicatorTest.everyNonFailedStatusRendersWithoutARetry` pin that the host does not present it as an error |
| 3. The failed state offers manual retry through `SyncController.retryFailed()` | `SyncStatusIndicatorTest.aFailedStatusOffersTheManualRetry` clicks the rendered affordance and observes the callback; iOS forwards `WalkingSkeletonModel.retryBackup()` to `SyncStateHolder.retryFailed()`, the same member `§20.10` declares, whose own failure path is already covered by `SyncStateHolderForegroundTest`'s sibling suites and `DefaultSyncControllerTest.retryFailedPropagatesOnlyLocalTransactionFailure` |
| 4. The `§11.6` rule is executable | `contractCheck` reports assertion 36 `PASS` on the real repository; `SwiftHiddenMemberContractTest` has thirteen fixtures, one per problem branch, each asserting the exact problem text, plus the first test that runs the real `contractCheck` so the fixtures cannot drift from the surface they guard |

**Fixtures and their observed failure before the check existed.** The whole
`SwiftHiddenMemberContractTest` class failed 13 of 13 before assertion 36 was implemented, because
`runAll()` returned no result with id 36. After implementation, the mutation-driven cases were also
run against deliberately wrong fixtures and failed as expected; three genuine defects in the fixtures
themselves were found and fixed that way (see Decisions Made).

**The RED phase, observed.** `:androidApp:testDebugUnitTest --tests SyncStatusVisualTest` reported
`5 tests completed, 5 failed` against the stub. The iOS target reported
`testEveryPublishedStatusMapsToItsOwnVisual` failing with `("idle") is not equal to ("syncing")`,
`("idle") is not equal to ("pending")`, `("idle") is not equal to ("failed")`,
`testAPendingStatusIsNeverClassifiedAsFailed` failing `("idle") is not equal to ("pending")` and
`testEveryVisualHasItsOwnLabel` failing `("4") is not equal to ("1")`. Every failure is the behaviour
under test, not a compile or setup error.

## Out of Scope / Not Done

- **The settings row of `docs/SPECIFICATION.md §3.1` is not delivered.** It stays with `E4-01`, whose
  scope is "the surface listed in `§3.1`". `E3-05` renders the same indicator on the vehicle list
  screen and does not claim the settings row (`D-192`).
- **No `SyncStatus` value, no new message channel and no new dependency.** The four values and the
  `UiMessage` channel are unchanged, and the indicator adds no library.
- **The `E1-18` record closure is not part of this pull request.** The stale in-flight wording that
  `E1-18`'s own acceptance criteria require removing ships in its own documentation-only branch and
  pull request, as the prompt requires. This story's `AGENTS.md` edit is limited to the
  "Remaining Phase 3" paragraph it must update because `E3-05` now appears in it.
- **`E3-15`, `E3-16` and `E3-18` and their decisions `D-149`, `D-150` and `D-173` are untouched.**
- **The iOS indicator is not exercised by an automated UI assertion.** The iOS unit-test target covers
  the classification and the copy, and the build plus the `xcodebuild` test run compile and execute the
  view; no iOS UI test drives the rendered chip, because the existing iOS UI suite has no sync-status
  scenario and adding one would need a provider-bound test fixture this story is not scoped for. The
  residual risk is recorded under Risks.

## Files Changed

- `androidApp/src/main/java/com/ruizurraca/carapp/SyncStatusVisual.kt` — the classification and the
  label mapping.
- `androidApp/src/main/java/com/ruizurraca/carapp/SyncStatusIndicatorView.kt` — the indicator.
- `androidApp/src/main/java/com/ruizurraca/carapp/MainActivity.kt` — the wiring and the list layout.
- `androidApp/src/main/res/values/strings.xml`, `values-es/strings.xml` — the copy.
- `androidApp/src/test/java/com/ruizurraca/carapp/SyncStatusVisualTest.kt` — the RED unit test.
- `androidApp/src/androidTest/java/com/ruizurraca/carapp/SyncStatusIndicatorTest.kt` — the rendered
  indicator.
- `iosApp/SyncStatusVisual.swift` — the same classification in Swift.
- `iosApp/SyncStatusIndicatorView.swift` — the indicator view.
- `iosApp/WalkingSkeletonModel.swift` — the observation and the retry forwarding.
- `iosApp/VehicleListView.swift` — renders the indicator.
- `iosApp/en.lproj/Localizable.strings`, `es.lproj/Localizable.strings` — the copy.
- `iosApp/Tests/SyncStatusVisualTests.swift` — the RED Swift test.
- `iosApp/project.yml`, `iosApp/carApp.xcodeproj/project.pbxproj` — the two new sources registered and
  the project regenerated with `iosApp/generate-project.sh`.
- `build-logic/convention/src/main/kotlin/.../contract/SwiftSurfaceContract.kt` — assertion 36.
- `build-logic/convention/src/test/kotlin/.../contract/SwiftHiddenMemberContractTest.kt` — its fixtures.
- `build-logic/convention/src/test/kotlin/.../contract/Pr71ReviewRegressionTest.kt` — the assertion-id
  list expectation, which grows by 36.
- `docs/CONTRACTS.md` `§11.6`, `§14`, `§18`.
- `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`,
  `docs/adr/README.md`, `docs/adr/0192-…`, `docs/adr/0193-…`, `docs/adr/0194-…`.
- `docs/BACKLOG.md`, `AGENTS.md`, `docs/PROJECT_LOG.md`.

## Decisions Made

- **`D-191` / ADR-0192** — the `§11.6` check lives in `SwiftSurfaceContract` as `§18` assertion 36.
  Owner-selected. Konsist cannot implement the rule because it parses Kotlin only and the rule is a
  comparison against `docs/CONTRACTS.md`.
- **`D-192` / ADR-0193** — the indicator renders on the vehicle list screen of both hosts.
  Owner-selected. It leaves the settings row to `E4-01` and reuses the status chip both designs already
  draw rather than inventing a visual for an undesigned state.
- **`D-193` / ADR-0194** — the `Idle` label does not claim the remote copy is current.
  Owner-selected, and it is the reason no state-machine value or persisted backup flag was added.
- **Three defects in my own fixtures were found by running them, and fixed.** The insertion helper put
  the fixture declaration in verbatim, so two fixtures were inserting ordinary members rather than
  hidden ones and passed against the stub; each call site now carries its own annotation. The
  code-side branch fires before the contract-side one for a member the contract declares without the
  annotation, so that fixture's expected text is the code-side message. The member signature is used
  as the label for functions and the bare name for properties, because `§20.10` names a property by
  its name.
- **`Pr71ReviewRegressionTest`'s assertion-id list expectation was updated** from `[14, 34, 35]` to
  `[14, 34, 35, 36]`. This is an API-growth update, not a re-pin of incidental behaviour: the test's
  subject is that an unbalanced contract block degrades to a result instead of throwing, and the list
  is how it identifies the results.
- **TDD exemption used: native UI code** (`docs/SPECIFICATION.md §11`, "Native UI code (SwiftUI,
  Compose host screens)"). The indicator composable and the SwiftUI view are verified by the
  instrumented Android suite rather than written test-first; their classification logic is not exempt
  and was written red-first.
- **The RED phase's Swift evidence was captured after the fact**, by restoring the stub from `d453d65`
  and re-running the iOS target, because the first iOS run started before the new sources were
  registered in the Xcode project and therefore compiled only the pre-existing tests. The RED commit
  itself is `d453d65` and precedes every implementation commit.

## Verification Run

```text
./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test \
          koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest \
          testAndroidHostTest iosSimulatorArm64Test \
          -x :integration:firebase-auth:iosSimulatorArm64Test \
          -x :integration:firebase-firestore:iosSimulatorArm64Test \
          -x :wiring:firebase:iosSimulatorArm64Test \
          -x :composition:ios:iosSimulatorArm64Test
BUILD SUCCESSFUL
```

```text
./gradlew :androidApp:testDebugUnitTest testAndroidHostTest --rerun-tasks
BUILD SUCCESSFUL
```

```text
./gradlew :build-logic:convention:test
190 tests, 0 failures
```

```text
./gradlew contractCheck --rerun-tasks
[PASS] 36. every public @HiddenFromObjC member of an exported state-holder class is declared in
          §20.10 carrying the annotation
no [FAIL] and no [PENDING] assertion
```

Android instrumented suite and the iOS `xcodebuild` run: see the pull request's verification comment.

## Contract Impact

- Updated `docs/CONTRACTS.md §11.6` (the rule now names assertion 36 as its executable check),
  `§14` (a host MUST NOT compute `SyncStatus` either; the indicator's classification is a presentation
  mapping of the resolved value) and `§18` (new assertion 36).

## Decision Board Impact

- Updated `docs/DECISION_BOARD.md` with `D-191`, `D-192` and `D-193`, each with its ADR
  (`docs/adr/0192`, `0193`, `0194`) and identical rows in `docs/SPECIFICATION.md §12`,
  `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`.

## Shared-Write Modules Touched

- None. `:core:database` is untouched.

## Project Log Entry

Appending an entry to `docs/PROJECT_LOG.md` is part of the Definition of Done.

- [x] Entry appended

## Risks or Follow-ups

- **The iOS indicator has no automated UI assertion.** Its classification and copy are covered by the
  iOS unit-test target, and the view compiles and runs under `xcodebuild test`, but no iOS UI test
  drives the rendered chip. A regression in the SwiftUI wiring alone would be caught by the build, not
  by an assertion. Recorded here rather than silently accepted; a later story that adds an iOS
  sync-status scenario should assert it.
- **The host classification is duplicated per platform**, as `D-183` already accepted for the
  foreground threshold. Only the rule is shared; each host has its own four-way mapping and its own
  copy, each covered by its own test. Four lines per side.
- **The `Idle` label is deliberately not exact about the remote copy** (`D-193`). An owner reading only
  the chip cannot tell whether a backup exists; a fuller statement needs a decision of its own, and the
  settings row where it would belong is `E4-01`'s.
- **`§3.1`'s settings row for backup status is still undelivered** until `E4-01`, which now inherits
  the requirement rather than sharing it.

## Human Review Gate

Applies — gated paths and a gated topic. `docs/CONTRACTS.md` (`§11.6`, `§14`, `§18`),
`docs/DECISION_BOARD.md` and `docs/adr/**` are gated paths enforced by `CODEOWNERS`; the change touches
the synchronization algorithm's aggregate through `§9.9` and `§14`, which is a gated topic. It does
not alter the Swift-facing API surface. The pull request is handed back to the owner and MUST NOT be
merged on agent judgement.
