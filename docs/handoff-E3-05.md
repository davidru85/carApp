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

- Date: 2026-09-26
- Branch and base: `story/E3-05-backup-status-ui`, based on `main` / `origin/main` at `5141e68`.
- Current phase and latest commit: review correction 4 complete. Its RED commits are `7d39394`
  (untyped hidden properties) and `687bdc6` (backup-status error tag and iOS accessible description),
  its GREEN commits are `6ab80f5` and `1315491`, its REFACTOR commit is `a0472e5` (exhaustive
  iOS classification), and the documentation commit that carries this checkpoint sits on top of them.
  Review correction 3 is `8b91eaf` and `f7e234f` (RED) and `af79fa4` and `4b604d2` (GREEN); review
  correction 2 is `875c137` (RED) and `e69bc61` (GREEN); the first review correction is `121a33e`
  (RED) and `32ea3fd` (GREEN); the original story phases are `d453d65` (RED), `ebfd8cf` (GREEN) and
  `f336b54` (records).
- Push and pull-request status: pushed; pull request #74 is open and awaiting the owner's gated
  review. Its description carries the review corrections.
- Completed since the previous checkpoint: review correction 4 — assertion 36 attributes each
  `@HiddenFromObjC` annotation to the declaration that follows it and reports a public hidden property
  that declares no explicit type; the Android retry error carries its own `backup_status_error` tag;
  the iOS status text carries the accessible description Android already had; and the iOS
  classification is an exhaustive switch over the sealed status. See Decisions Made.
- Verification evidence and known failures: see Verification Run, including the review correction 4
  subsection. No known failure introduced by this story. The Android emulator and the iOS simulators
  launched for this correction were closed after each use.
- Open decisions or blockers: none. `D-191`, `D-192` and `D-193` are unchanged and `Accepted`.
- Exact next step: the owner's gated review of pull request #74.

## Scope Completed

- The host-side classification of the resolved aggregate, on both hosts: four published `SyncStatus`
  values to four visuals, the counts inside a status treated as detail, and `Failed` the only visual
  presented as an error. It takes no connectivity observation, so "offline means error" is
  unrepresentable in the host rather than merely untested.
- The indicator itself on both hosts, on the vehicle list screen (`D-192`), drawn as the status chip
  the platform designs already place there.
- The manual retry, wired to the existing `SyncStateHolder.retryFailed()`; its failure surfaces
  through the holder's existing typed `UiMessage` and needs no new channel. Both hosts render that
  message through their existing mapping (`ErrorText` on Android, `UiMessage.localizedText` on iOS),
  so a failed attempt is visible rather than an inert button, and the holder clears the previous
  failure before each later attempt and withdraws it when the status leaves `Failed`, so neither a
  success nor a later automatic cycle can leave a stale error on screen. Each host draws that message
  only beside the `Failed` visual, and on iOS the indicator row observes the model so a sync-only
  change redraws it.
- `docs/CONTRACTS.md §18` assertion 36, making the `§11.6` hidden-member rule executable in both
  directions and for both fail-open shapes, with a failing fixture per problem branch (`D-191`).
- The copy in both languages on both platforms, with the `Idle` label stating that nothing is
  outstanding rather than that a remote copy exists (`D-193`).

## Acceptance Evidence

| Criterion | Evidence |
|---|---|
| 1. `SyncStatus` rendered with the `§9.9` precedence | The host renders the resolved value; `SyncStatusVisualTest.everyPublishedStatusMapsToItsOwnVisual` (Android) and `SyncStatusVisualTests.testEveryPublishedStatusMapsToItsOwnVisual` (iOS) pin the four-way classification. The precedence itself is `:core:sync`'s and stays pinned there by `DefaultSyncControllerTest`, which `E3-05` does not touch |
| 2. Offline with pending rows, or connectivity-only retryable failures, renders as `Pending`, never an error | The classification takes no connectivity fact and its parameter is the already-resolved `SyncStatus`, so the host cannot re-derive the buckets: `syncStatusVisual` has no overload accepting an online flag. `DefaultSyncControllerTest.offlineWriteIsBackedUpAfterConnectivityReturns` and `connectivityFailureKeepsRowStateAndAggregateInAgreement` pin that the resolved value is `Pending` for both cases; `SyncStatusVisualTest.aPendingStatusIsNeverClassifiedAsFailed` and `SyncStatusIndicatorTest.everyNonFailedStatusRendersWithoutARetry` pin that the host does not present it as an error; `SyncStateHolderRetryTest.retryFailureIsWithdrawnWhenTheStatusLeavesFailed` pins that a retry failure is withdrawn once the status leaves `Failed`, and `SyncStatusIndicatorTest.aRetryFailureIsNotRenderedBesideANonFailedStatus` pins that the Android indicator never draws one beside `Idle`, `Syncing` or `Pending` |
| 3. The failed state offers manual retry through `SyncController.retryFailed()` | `SyncStatusIndicatorTest.aFailedStatusOffersTheManualRetry` clicks the rendered affordance and observes the callback, and `SyncStatusIndicatorTest.retryFailureRendersMappedPersistenceMessage` renders the typed failure and keeps Retry separately actionable; iOS forwards `WalkingSkeletonModel.retryBackup()` to `SyncStateHolder.retryFailed()`, the same member `§20.10` declares. The holder's failure path is proved by `SyncStateHolderRetryTest.retryFailurePublishesTypedMessage` and `SyncStateHolderRetryTest.successfulRetryClearsPreviousFailureMessage`, and `SyncStateHolderRetryTest.retryFailureSurvivesAChangeBetweenFailedAggregates` keeps it while the status stays `Failed`; on iOS the indicator is drawn by `BackupStatusRow`, which observes `WalkingSkeletonModel` so a sync-only change redraws it; the iOS rendering of that code is pinned by `UiMessageMappingTests.testTransactionFailureUsesPersistenceMessage`, and `DefaultSyncControllerTest.retryFailedPropagatesOnlyLocalTransactionFailure` pins the controller's own failure classification |
| 4. The `§11.6` rule is executable | `contractCheck` reports assertion 36 `PASS` on the real repository; `SwiftHiddenMemberContractTest` has twenty-one tests: fifteen failing fixtures that cover the six problem branches, the masked header cut and the attribution of an annotation to its own declaration, each asserting the exact problem text; five passing fixtures for members outside the rule; and the first test, which runs the real `contractCheck` so the fixtures cannot drift from the surface they guard |

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
- `shared/src/commonMain/kotlin/com/ruizurraca/carapp/StateHolders.kt` — `SyncStateHolder`'s
  retry-message lifetime and retry-generation ownership; private members only, no exported
  declaration changed.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/SyncStateHolderRetryTest.kt` — the manual-retry
  outcome tests.
- `iosApp/Tests/UiMessageMappingTests.swift` — the persistence-code mapping a failed retry renders.
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
- **Running that instrumented suite found two defects of mine, and both were fixed.**
  - The composable cleared the row's semantics to announce the status sentence as one node, which also
    removed the label and the indicator's own test tag. The suite reported the indicator as "not
    displayed" rather than as a missing description, so the defect was an unreachable node, not a
    styling preference. The description is now set on the coloured dot, which is what a screen reader
    had no other way to read; the row keeps its own semantics and its tag. The first review
    correction below superseded this: the visible label owns the description and the dot carries none.
  - The first version of the suite called `setContent` once per status inside a loop, which the Compose
    test rule rejects. It now composes once and varies the published status through a mutable state,
    which is also the closer model of a status that changes under a running UI.
- **The RED phase's Swift evidence was captured after the fact**, by restoring the stub from `d453d65`
  and re-running the iOS target, because the first iOS run started before the new sources were
  registered in the Xcode project and therefore compiled only the pre-existing tests. The RED commit
  itself is `d453d65` and precedes every implementation commit.

### Review correction (2026-09-25)

A review of this pull request found three gaps between what the records claimed and what the code
did. All three are corrected here; no decision changed and `D-191` to `D-193` stand as accepted.

- **Android read the status from the wrong holder.** `VehicleListScreen` passed
  `syncState.status` — `SyncStateHolder`'s own relay — while `docs/adr/0193` assigns Android status
  rendering to `VehicleListUiState.syncStatus`. Two holders publish on different coroutine turns, so
  the screen could briefly render a value that disagreed with the vehicle-list state it was drawing.
  The call site now passes `state.syncStatus`, and the holder keeps only what `ADR-0193` gives it:
  the retry message and the retry command.
- **A failed retry was invisible on both hosts.** `SyncStateHolder.retryFailed()` publishes its
  failure as a typed `UiMessage`, but neither indicator rendered `SyncUiState.message`, so the button
  looked inert after a failure. Both hosts now render that message through the mapping they already
  had (`ErrorText` on Android, `UiMessage.localizedText` on iOS), and the Retry control stays a
  separate actionable element — combining it into one accessibility node would have made manual
  recovery unreachable with a screen reader.
- **The holder kept a stale failure.** `retryFailed()` published a failure but never cleared the
  previous one, so an error outlived the attempt that succeeded. It now clears `message` before each
  attempt, which is what `successfulRetryClearsPreviousFailureMessage` was written to prove.
- **Android semantics ownership contradicted its own comment.** The coloured dot owned the status
  `contentDescription` while the comment called it decorative, which could announce the status twice.
  The visible label now owns the description and the dot carries none.
- **TDD, this round.** `SyncStateHolderRetryTest` was written and pushed first. At that head
  `retryFailurePublishesTypedMessage` passed and `successfulRetryClearsPreviousFailureMessage` failed
  with the stale `UiMessage(id=7, kind=ERROR, code=PERSISTENCE.TRANSACTION_FAILED)` still published
  after a successful attempt — the third defect above, observed rather than described. The clear was
  then added and both pass. No exemption was used, and the Swift and Compose rendering added here is
  covered by the existing instrumented and iOS unit targets rather than by a new UI harness.

### Review correction 2 (2026-09-26)

A second review of this pull request found three defects and several record inaccuracies. All are
corrected here; no decision changed, no decision ID was added, and `D-191` to `D-193` stand as
accepted. The two behavioural corrections apply the second acceptance criterion and the `ADR-0193`
constraint that a retry failure surfaces through the existing typed `UiMessage`; they choose no new
option.

- **The iOS indicator did not observe the model.** `VehicleListView` holds `WalkingSkeletonModel` as
  a plain `let`. `ContentView` observes the model and re-evaluates its own body on every publication,
  but SwiftUI does not re-evaluate a child whose stored references are unchanged, so a status change
  or a retry failure that arrived without a vehicle-list change was never drawn. A retry failure
  never changes the vehicle-list state, so the first correction's visible retry failure held on
  Android only, and the claim above that the Swift rendering is covered by the iOS unit target was
  wrong: that target does not render views. A standalone SwiftUI probe reproduced the mechanism: with
  the model held as `let`, the parent body ran three times and the child body once, frozen on the
  first value; with an `@ObservedObject` row the child followed every change. The indicator is now
  drawn by `BackupStatusRow`, which observes the model, so only that row redraws on a sync-only
  change. The iOS status source is unchanged: `SyncStateHolder.state`, as `ADR-0193` assigns.
- **A retry failure outlived the `Failed` status.** `SyncStateHolder` cleared `message` only at the
  start of the next manual retry, and both hosts drew it regardless of the status. After a failed
  retry, an automatic cycle that moved the aggregate to `Pending` or `Idle` left the persistence error
  drawn in red beside "Waiting to back up" or "Synced locally", which is the error presentation the
  second criterion forbids for `Pending`. The holder now withdraws the message as soon as the relayed
  status is not `Failed`, and both hosts draw it only beside the `Failed` visual, which also covers
  the turn in which Android's two relays have not converged. `docs/CONTRACTS.md §14` states the rule.
- **Assertion 36 could pass an undeclared hidden member.** `hiddenMembers` cut the text between two
  members at its last `{`, `}` or `;` on the raw source and only then masked the fragment. A brace, a
  semicolon or a string template inside the previous member's string literal split that literal, the
  fragment re-lexed as an unterminated string, and the annotation was masked away: assertion 36
  returned `PASS` for a public `@HiddenFromObjC` member absent from `§20.10` behind
  `val label: String = "{}"`, `val separator: String = ";"` and `val summary: String get() = "${1}"`.
  The cut and the match now both run on the offset-preserving code view of the body, and three new
  fixtures pin the three shapes.
- **Records corrected.** `ADR-0194` quoted labels that no catalogue contains; it now quotes the
  catalogue wording. `ADR-0193`'s Verification claimed both unit tests check both catalogues; only the
  iOS test does. `ADR-0192`'s Verification lists the new fixtures. This handoff's checkpoint named
  `f336b54` as the latest commit after five later commits, its fixture count described thirteen tests
  as thirteen problem-branch fixtures, and its iOS risk claimed the build would catch a wiring
  regression. All are corrected in this revision.
- **TDD, this round.** The RED commit `875c137` added
  `retryFailureIsWithdrawnWhenTheStatusLeavesFailed`, which failed with the stale
  `UiMessage(id=7, kind=ERROR, code=PERSISTENCE.TRANSACTION_FAILED)` still published beside
  `Pending`; the guard `retryFailureSurvivesAChangeBetweenFailedAggregates`, which passed at RED and
  pins that the withdrawal is tied to leaving `Failed`; and the three masked-cut fixtures, which
  failed because assertion 36 returned `PASS`. The GREEN commit `e69bc61` made all of them pass.
  The Compose and SwiftUI changes use the native UI exemption of `docs/SPECIFICATION.md §11`: the
  Android guard is covered by the new instrumented test
  `aRetryFailureIsNotRenderedBesideANonFailedStatus`, and the iOS row has no automated UI assertion,
  which stays recorded under Risks.

### Review correction 3 (2026-09-26)

A third review found two behavioural defects in the manual retry and one fail-open shape in
assertion 36. All are corrected here; no decision changed, no decision ID was added, and `D-191` to
`D-193` stand as accepted.

- **A retry that resolved after the status had left `Failed` republished an error.**
  `retryFailed()` published the failure whenever the controller returned `Err`, using the holder
  snapshot it had read before suspending. A cycle that moved the aggregate to `Pending` while the
  retry was still suspended therefore ended with the persistence error drawn beside `Pending`, which
  `docs/CONTRACTS.md §14` forbids. The function now reads `controller.status.value` **after** the
  suspended retry returns and publishes a message only while that value is still `SyncStatus.Failed`.
- **Overlapping retries published by completion order.** Two manual retries could be in flight, and
  an older one that failed after a newer one had succeeded became the final `message`, contradicting
  `§14`'s statement that the field carries the latest manual-retry outcome. Each call now takes a
  generation from a holder counter, and only the newest generation may publish; a superseded call
  clears nothing and publishes nothing. `close()` increments the counter too, so an attempt already
  suspended on `dispatchers.io` cannot publish after closure.
- **Assertion 36 could pass a contract-only holder class.** The assertion enumerated contract blocks
  by iterating the class names it found in production sources, so a `§20.10` block whose class exists
  nowhere in production was never inspected and its hidden members passed silently. The contract side
  is now enumerated independently — from `§20.10` itself — and every block is traversed, so a
  contract-only holder class reaches the existing reverse-direction problem text. The enumeration
  cannot use the Kotlin lexer on the contract: `docs/CONTRACTS.md` is Markdown, and an apostrophe in
  its prose opens a character literal for `KotlinSourceText`, which then masks thousands of
  characters up to the next apostrophe and swallows every `§20.10` holder declaration. Names are read
  from the raw contract and resolved through the braced-block lookup, which discards a prose mention
  that has no block.
- **A fixture's premise was false.** `aContractWithNoHolderBlocksAtAllIsReported` called
  `class NothingStateHolder {}` a contract with no holder block. It passed only because contract-side
  classes were not discovered; once they are, that name is a holder declaration. The fixture now uses
  `class Nothing`, so it asserts what it claims.
- **TDD, this round.** The RED commit `8b91eaf` added
  `retryFailureThatCompletesAfterStatusLeavesFailedIsNotPublished` and
  `anOlderFailureCannotOverwriteANewerSuccessfulRetry`; both failed at that head with the stale
  `UiMessage(id=7, kind=ERROR, code=PERSISTENCE.TRANSACTION_FAILED)` still published. The RED commit
  `f7e234f` added `aContractHiddenMemberInAContractOnlyHolderClassIsRejected`, which failed with
  `expected:<FAIL> but was:<PASS>`, and corrected the false-premise fixture. The GREEN commits
  `af79fa4` and `4b604d2` made all of them pass. Two existing retry fixtures moved from the implicit
  `Idle` default to an explicit `Failed` status: under the new rule a failure is publishable only
  while the aggregate is `Failed`, so their old premise asserted a state the contract forbids. No
  exemption was used; the Compose and SwiftUI surfaces were not touched, so the native UI exemption
  does not apply this round.

### Review correction 4 (2026-09-26)

A fourth review found one fail-open shape in assertion 36, two host parity gaps, one non-exhaustive
classification and two record inaccuracies. All are corrected here; no decision changed, no decision
ID was added, and `D-191` to `D-193` stand as accepted.

- **Assertion 36 passed an undeclared public hidden property with no explicit type, and blamed the
  wrong member.** `hiddenMembers` attributed an annotation to the next parsed member through the text
  between two parsed members. The member parser does not model a property with no explicit type
  (`val x = …` or `val x by lazy { … }`), so its annotation was absorbed by a following member that was
  hidden anyway, lost at a brace, or lost as the last member — and assertion 36 returned `PASS` — or
  it was lent to the next parsed member, and the problem text named `clearMessage()`, which is not
  hidden. Each annotation is now attributed to the declaration keyword that follows it, and a public
  hidden property that declares no explicit type is reported under its own name, because `§20.10`
  declares every property with its type. This makes assertion 36 execute the existing `§11.6` rule
  without choosing a new option, which is why no decision ID was added. `ADR-0192`'s negative
  consequence, which listed the untyped property as an accepted limit, is corrected.
- **The Android retry error reused the vehicle list's test tag.** `ErrorText` hard-coded
  `VehicleTestTags.ERROR`, so the retry failure and the vehicle list's own error were both
  `vehicle_error` on one screen, while iOS already used `backup_status_error`. `ErrorText` now takes
  the tag as a parameter whose default keeps every existing call site unchanged, and the indicator
  passes `SyncStatusTestTags.ERROR`.
- **The iOS status text had no accessible description.** Android announces the chip as
  "Backup status: <label>" through `backup_status_description`; iOS announced the bare label. Both iOS
  catalogues now carry `backup_status_description`, and the status text uses it as its accessibility
  label.
- **The iOS classification was not total.** `syncStatusVisual` fell through to `.idle` for a subtype
  it did not recognise, so a future `SyncStatus` value would have been drawn as "Synced locally" — the
  label `D-193` constrains — with nothing failing. It now switches over SKIE's `onEnum(of:)`, so a new
  subtype is a compile error, as the Android `when` already is.
- **Records corrected.** The criterion-4 evidence row still described sixteen tests after review
  correction 3 had made them seventeen, and `Files Changed` omitted `StateHolders.kt`,
  `SyncStateHolderRetryTest.kt` and `UiMessageMappingTests.swift`.
- **TDD, this round.** The RED commit `7d39394` added `anUntypedHiddenPropertyIsRejected`,
  `aDelegatedUntypedHiddenPropertyIsRejected` and
  `anUntypedHiddenPropertyDoesNotLendItsAnnotationToTheNextMember`, which failed, and the guard
  `aPrivateUntypedHiddenPropertyIsOutsideTheRule`, which passed; the GREEN commit `6ab80f5` made all
  of them pass. The RED commit `687bdc6` added the `backup_status_error` assertions to
  `SyncStatusIndicatorTest` and `testTheAccessibleDescriptionHasCopyInBothLanguages`, which failed on
  the pinned `E1_07_API_36` emulator and on a freshly created simulator; the GREEN commit `1315491`
  made them pass. The REFACTOR commit `a0472e5` replaced the iOS type checks with the exhaustive
  switch and re-indented the list-row modifiers with every test green. No exemption was used: every
  test was written and observed failing before its code.


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


```text
./gradlew :androidApp:connectedDebugAndroidTest        (E1_07_API_36 emulator)
BUILD SUCCESSFUL — SyncStatusIndicatorTest 4 tests, 0 failures; the pre-existing
Vehicle/Fuel Compose flows unchanged
```

```text
xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator \
           -destination "id=<iPhone 17 Pro>" ARCHS=arm64 ONLY_ACTIVE_ARCH=NO test
SyncStatusVisualTests  7 tests, 0 failures
```

### Review correction (2026-09-25) — commands actually run

```text
./gradlew :shared:testAndroidHostTest --tests 'com.ruizurraca.carapp.SyncStateHolderRetryTest'
RED:   2 tests completed, 1 failed — successfulRetryClearsPreviousFailureMessage, the stale
       UiMessage(id=7, kind=ERROR, code=PERSISTENCE.TRANSACTION_FAILED) still published
GREEN: BUILD SUCCESSFUL
./gradlew :androidApp:testDebugUnitTest                                   BUILD SUCCESSFUL
./gradlew :androidApp:connectedDebugAndroidTest   (E1_07_API_36 emulator) BUILD SUCCESSFUL —
       including retryFailureRendersMappedPersistenceMessage
xcodebuild ... -only-testing:carAppTests test    53 tests, 0 failures, TEST SUCCEEDED —
       including UiMessageMappingTests.testTransactionFailureUsesPersistenceMessage
./gradlew :shared:iosSimulatorArm64Test --rerun-tasks                     99 tests, BUILD SUCCESSFUL
./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test \
          koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest \
          testAndroidHostTest iosSimulatorArm64Test -x ...                 BUILD SUCCESSFUL
./gradlew contractCheck --rerun-tasks   assertion 36 PASS, no PENDING
git diff --check                        exits 0
```

Two failures observed while verifying, both investigated rather than re-run away:

- **`ktlint` failed on the first pushed correction head.** `git add` listed `shared/src/commonMain`
  and missed `shared/src/commonTest`, so the `?.code` wrapping `ktlintFormat` had applied to
  `SyncStateHolderRetryTest` stayed in the working tree: the local command linted the working tree and
  passed while CI linted the committed file and failed. Fixed by committing the formatting.
- **`provider-decoupling` failed its Kotlin/Native step on the documentation-only head** with
  `Child process terminated with signal 11: Segmentation fault` at
  `LocalOwnerAdoptionTest.theFirstLocalOwnerWriteWhileOnlineTriggersAcquisitionAfterAMissedConnectivityEmission`
  — a segfault, not an assertion. It is the intermittent residual risk `docs/handoff-E3-12.md` records
  for the `D-190` non-blocking release path, whose two candidate mechanisms are both in that release
  contract; the identical code had already passed the same job on `5e9150d`, and
  `:shared:iosSimulatorArm64Test --rerun-tasks` passed 99 tests locally afterwards.

### Review correction 2 (2026-09-26) — commands actually run

```text
./gradlew :shared:testAndroidHostTest --tests 'com.ruizurraca.carapp.SyncStateHolderRetryTest'
RED:   4 tests completed, 1 failed — retryFailureIsWithdrawnWhenTheStatusLeavesFailed, the stale
       UiMessage(id=7, kind=ERROR, code=PERSISTENCE.TRANSACTION_FAILED) published beside Pending
GREEN: BUILD SUCCESSFUL, 4 tests, 0 failures
./gradlew :build-logic:convention:test --tests '...contract.SwiftHiddenMemberContractTest'
RED:   16 tests completed, 3 failed — the three masked-cut fixtures, assertion 36 returned PASS
GREEN: BUILD SUCCESSFUL, 16 tests, 0 failures
./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test \
          koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest \
          testAndroidHostTest iosSimulatorArm64Test -x ...                 BUILD SUCCESSFUL
./gradlew :build-logic:convention:test                                    193 tests, 0 failures
./gradlew contractCheck --rerun-tasks                                     assertion 36 PASS, no PENDING
./gradlew :androidApp:connectedDebugAndroidTest  (E1_07_API_36 emulator)  BUILD SUCCESSFUL —
       SyncStatusIndicatorTest 6 tests, 0 failures
xcodebuild ... -only-testing:carAppTests test                             53 tests, 0 failures,
       TEST SUCCEEDED
git diff --check                                                          exits 0
```

The Android emulator and the iOS simulator launched for this correction were both closed after use.

### Review correction 3 (2026-09-26) — commands actually run

```text
./gradlew :shared:testAndroidHostTest --tests 'com.ruizurraca.carapp.SyncStateHolderRetryTest' --rerun-tasks
RED:   6 tests completed, 2 failed — retryFailureThatCompletesAfterStatusLeavesFailedIsNotPublished
       and anOlderFailureCannotOverwriteANewerSuccessfulRetry, both leaving the stale
       UiMessage(id=7, kind=ERROR, code=PERSISTENCE.TRANSACTION_FAILED) published
GREEN: BUILD SUCCESSFUL, 6 tests, 0 failures
./gradlew :shared:iosSimulatorArm64Test --rerun-tasks                       BUILD SUCCESSFUL
./gradlew :build-logic:convention:test --tests '...SwiftHiddenMemberContractTest' --rerun-tasks
RED:   17 tests completed, 1 failed — aContractHiddenMemberInAContractOnlyHolderClassIsRejected,
       expected:<FAIL> but was:<PASS>
GREEN: BUILD SUCCESSFUL, 17 tests, 0 failures
./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test \
          koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest \
          testAndroidHostTest iosSimulatorArm64Test -x ...                 BUILD SUCCESSFUL
./gradlew contractCheck --rerun-tasks                                     assertion 36 PASS, no PENDING
git diff --check                                                          exits 0
```

No Android emulator or iOS simulator was launched for this correction: every command above runs on
the host.

### Review correction 4 (2026-09-26) — commands actually run

```text
./gradlew :build-logic:convention:test --tests '...contract.SwiftHiddenMemberContractTest' --rerun-tasks
RED:   21 tests completed, 3 failed — anUntypedHiddenPropertyIsRejected,
       aDelegatedUntypedHiddenPropertyIsRejected and
       anUntypedHiddenPropertyDoesNotLendItsAnnotationToTheNextMember
GREEN: BUILD SUCCESSFUL, 21 tests, 0 failures
./gradlew :build-logic:convention:test --rerun-tasks                      198 tests, 0 failures
./gradlew contractCheck --rerun-tasks                                     assertion 36 PASS, no PENDING
ANDROID_SERIAL=emulator-5580 ./gradlew :androidApp:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ruizurraca.carapp.SyncStatusIndicatorTest
RED:   6 tests, 1 failed — retryFailureRendersMappedPersistenceMessage
xcodebuild ... -only-testing:carAppTests/SyncStatusVisualTests test      (fresh simulator)
RED:   8 tests, 4 failed assertions inside the one expected failing test —
       testTheAccessibleDescriptionHasCopyInBothLanguages (2 assertions x 2 languages)
ANDROID_SERIAL=emulator-5580 ./gradlew :androidApp:connectedDebugAndroidTest
GREEN: BUILD SUCCESSFUL, 23 tests, 0 failures
xcodebuild ... -only-testing:carAppTests test                             (fresh simulator)
GREEN: 54 tests, 0 failures, TEST SUCCEEDED
./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test \
          koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest \
          testAndroidHostTest iosSimulatorArm64Test -x ...                 BUILD SUCCESSFUL
git diff --check                                                          exits 0
```

The `E1_07_API_36` emulator was booted twice, once for the RED check and once for the GREEN check,
and killed after each; each iOS check ran on a simulator created for it and deleted after it.

## Contract Impact

- Updated `docs/CONTRACTS.md §11.6` (the rule now names assertion 36 as its executable check),
  `§14` (a host MUST NOT compute `SyncStatus` either; the indicator's classification is a presentation
  mapping of the resolved value; review correction 2 adds that `SyncUiState.message` is withdrawn
  when the status leaves `Failed` and is drawn only beside it) and `§18` (new assertion 36).
- Review correction 4 adds to `§11.6` and `§18` assertion 36 that a public hidden property which
  declares no explicit type is reported rather than skipped.

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
  drives the rendered chip. A regression in the SwiftUI wiring alone is caught by neither the build
  nor an assertion: review correction 2 found one, the indicator not observing the model, that
  compiled and passed every existing test. Recorded here rather than silently accepted; a later story
  that adds an iOS sync-status scenario should assert it.
- **The host classification is duplicated per platform**, as `D-183` already accepted for the
  foreground threshold. Only the rule is shared; each host has its own four-way mapping and its own
  copy, each covered by its own test. Four lines per side.
- **The error-code-to-copy mapping is not duplicated**: both hosts reuse the one they already had
  (`ErrorText` on Android, `UiMessage.localizedText` on iOS), so a new error code needs no change in
  either indicator. `E4-02` still owns the full accessibility audit.
- **The `Idle` label is deliberately not exact about the remote copy** (`D-193`). An owner reading only
  the chip cannot tell whether a backup exists; a fuller statement needs a decision of its own, and the
  settings row where it would belong is `E4-01`'s.
- **`§3.1`'s settings row for backup status is still undelivered** until `E4-01`, which now inherits
  the requirement rather than sharing it.
- **A `provider-decoupling` run on this branch failed on `D-190`'s wall-clock cost, not on this
  change.** `VehicleFormStateHolderTest.savePushesTheSnapshotOnlyAfterTheLocalTransactionCommits` timed
  out after 30 s with `Last value: Idle` - the real-time deadline `D-190`'s real `io` dispatcher
  introduced when it widened that budget from 5 s, explicitly *not* the deadlock `E1-18` removed, whose
  signature is a stall with zero assertion failures rather than a timeout with a specific assertion.
  The test passed 10 of 10 in isolation locally, the whole suite passed locally, and the other nine
  required checks passed on the same head. Recorded rather than waved away: if this recurs it is
  evidence that the 30 s budget is still too tight for a slow runner, and it is `E1-18`'s residual risk
  that this branch merely met first.

## Human Review Gate

Applies — gated paths and a gated topic. `docs/CONTRACTS.md` (`§11.6`, `§14`, `§18`),
`docs/DECISION_BOARD.md` and `docs/adr/**` are gated paths enforced by `CODEOWNERS`; the change touches
the synchronization algorithm's aggregate through `§9.9` and `§14`, which is a gated topic. It does
not alter the Swift-facing API surface. The pull request is handed back to the owner and MUST NOT be
merged on agent judgement.
