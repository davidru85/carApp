# Agent Handoff

## Story

`E3-19 - Push-Boundary Payload Totality`

## Ready Check

- Backlog story: `E3-19 - Push-Boundary Payload Totality` (`docs/BACKLOG.md`), a Phase 3
  follow-up deferred by `E3-03`.
- Acceptance criteria reviewed: yes. (1) Every field read on the push boundary is total: a missing or
  wrong-typed key produces a closed `Outcome.Err` rather than an escaping `NoSuchElementException`.
  (2) A test drives a payload missing each of `id`, `ownerId` and `schemaVersion` and asserts a closed
  result, that the row does not stay `SYNCING` indefinitely, and that no `UnexpectedError` is reported.
- Dependencies checked: `E3-03` merged (the engine that consumes `pushSnapshot`). No `Proposed` or
  `Pending` decision gates this story; `D-149`, `D-150` and `D-173` gate `E3-15`, `E3-16` and `E3-18`
  only.
- Decisions checked: no new decision is required. The story restores conformance to the already
  `Accepted` `D-170` transport-totality principle and the `§6` `RemoteError` to `SyncError` mapping;
  it introduces no option the owner must choose between. `contractCheck` reports 195 decisions, 195
  ADRs and zero `PENDING` assertions.
- Normative sections reviewed: `docs/CONTRACTS.md` §6 (Result and Error Taxonomy), §8 (Outbox
  Contract), §9.3 (Push), §9.5 (Quarantine and malformed remote payloads), §10 (`RemoteSyncSource`
  Contract); `docs/TECHNICAL_PLAN.md` §4 (dependency rules) and §9 (sync tests);
  `docs/SPECIFICATION.md` §2 (P2 offline-first).
- Expected verification: `:integration:firebase-firestore:testAndroidHostTest` (focused, both new
  tests), the complete non-instrumented repository command of `AGENTS.md`, `contractCheck` output
  inspection, and a mutation probe proving the new tests are not vacuous.
- Human review gates identified before work: applies. `integration/firebase-firestore` production code
  is a Firestore boundary change, and `docs/CONTRACTS.md` §10 is a gated path. The gate is the owner's
  review and merge; the agent MUST NOT merge.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every repository
  artifact it produces is in technical English.

## In-Progress Checkpoint

- Date: 2026-09-28
- Branch and base: `story/E3-19-push-boundary-payload-totality` from `main` (`ba2c3c17`)
- Current phase and latest commit: RED, GREEN, REFACTOR and documentation committed
  (`d5ecd400`, `e740288c`, `c049d478`, `b2889948`); implementation complete and pushed.
- Push and pull-request status: pushed to
  `origin/story/E3-19-push-boundary-payload-totality`; [pull request
  #82](https://github.com/davidru85/carApp/pull/82) is open and awaiting the owner's review. The ten
  required checks are the live authority; the agent MUST NOT merge.
- Completed since the previous checkpoint: the ready check; both RED tests;
  `EntitySnapshot.toFirestoreWrite` made total; the detekt-driven refactor; the `docs/CONTRACTS.md`
  §10 push-boundary totality bullet; the handoff, backlog, AGENTS.md and project-log records; the
  branch push and the pull request.
- Verification evidence and known failures: the complete non-instrumented repository command of
  `AGENTS.md` passed (642 actionable tasks, 79 executed); `:integration:firebase-firestore` passes
  with the six `E3-19` cases; `ktlintCheck`, `detekt`, `architectureCheck`, `contractCheck` (195
  decisions, zero `PENDING`) pass. The mutation probe that restores the throwing `getValue` read for
  `id` fails exactly `everyMalformedPayloadFieldFailsClosedWithoutWritingAnything` and
  `aPayloadMissingIdPoisonsTheRowInsteadOfStrandingIt`. No known failure.
- Open decisions or blockers: none.
- Exact next step: owner review of pull request #82 and the merge decision, after the ten required
  checks settle green. The agent MUST NOT merge.

## Scope Completed

- `FirebaseRemoteSyncSource.toFirestoreWrite` is now total. It returns
  `Outcome<FirestoreWrite, RemoteError>`; the payload is parsed defensively, each identity field
  (`id`, `ownerId`, `schemaVersion`, `entityType`) is read through a nullable accessor, and every
  field value is converted through a converter that returns `null` instead of throwing. A missing key,
  a wrong-typed key, a non-object root and unparseable JSON all become
  `Outcome.Err(RemoteError.InvalidArgument)` before any remote write.
- `pushSnapshot` returns the conversion's `Outcome.Err` directly instead of catching only
  `IllegalArgumentException`.
- `docs/CONTRACTS.md` §10 gains the push-boundary totality bullet: `pushSnapshot` MUST NOT let an
  unchecked exception escape, and a malformed payload MUST be a closed `RemoteError.InvalidArgument`.

## Acceptance Evidence

- Criterion 1 — every push-boundary field read is total:
  `FirebaseRemoteSyncSourcePushPayloadTotalityTest.everyMalformedPayloadFieldFailsClosedWithoutWritingAnything`
  drives 19 malformed payload shapes (each identity key missing, wrong-typed, `JsonNull`, non-object,
  mismatched `id`, mismatched `entityType`, a mistyped `createdAt` and `deletedAt`, a non-object root,
  a scalar root and unparseable JSON) and asserts a closed `Outcome.Err(RemoteError.InvalidArgument)`
  with zero writes for each. `aWellFormedPayloadStillWritesTheDocument` proves the hardening does not
  reject a canonical payload.
- Criterion 2 — a payload missing `id`, `ownerId` and `schemaVersion` does not stay `SYNCING`:
  `FirebaseRemoteSyncSourcePushTotalityEndToEndTest` drives the real `FirebaseRemoteSyncSource`, the
  real `:core:sync` engine and the real staged database for each of the three omissions plus a
  wrong-typed `ownerId`. Each case asserts the entity row reaches `FAILED_POISONED`, the retained
  outbox row carries `error=REMOTE.INVALID_ARGUMENT`, `onPoisoned` receives exactly
  `SyncError.ValidationRejected`, no `UnexpectedError` is reported, and the cycle still returns
  `Ok(Unit)` because a push poison is a per-row classification, not a cycle failure (`§9.3`).
- Non-vacuity: reverting `stringOrNull(ID_FIELD)` to a throwing
  `(parsed.getValue(ID_FIELD) as JsonPrimitive).content` fails exactly the two `id` cases across the two
  new tests; the other cases keep passing, so each probe hits the read it names.

## Out of Scope / Not Done

- `E3-20` (pull-boundary quarantine totality for an unsupported provider value) is untouched: it is the
  sibling pull-side gap and a separate story.
- No Firestore rule, schema, migration or `:core:database` change.
- No new decision ID, ADR or mirrored decision row: the change implements the `Accepted` `D-170`
  totality principle on the push side and adds no owner-choosable option.

## Files Changed

- `docs/CONTRACTS.md` (§10 push-boundary totality bullet)
- `docs/BACKLOG.md` (E3-19 status and story-index row)
- `docs/PROJECT_LOG.md` (story entry)
- `docs/handoff-E3-19.md` (this file)
- `integration/firebase-firestore/build.gradle.kts` (`:core:database` common-test dependency)
- `integration/firebase-firestore/src/commonMain/kotlin/com/ruizurraca/carapp/integration/firebase/firestore/FirebaseRemoteSyncSource.kt`
- `integration/firebase-firestore/src/commonTest/kotlin/com/ruizurraca/carapp/integration/firebase/firestore/FirebaseRemoteSyncSourcePushPayloadTotalityTest.kt` (new)
- `integration/firebase-firestore/src/commonTest/kotlin/com/ruizurraca/carapp/integration/firebase/firestore/FirebaseRemoteSyncSourcePushTotalityEndToEndTest.kt` (new)

## Decisions Made

- No new decision. The conversion was fixed rather than the call site, because `getValue` and
  `jsonPrimitive` are the throwing reads and the closed `Outcome` return makes the totality explicit at
  the boundary the contract governs.
- A malformed push payload maps to `RemoteError.InvalidArgument`, matching the pre-existing behaviour
  for a payload whose `entityType` is missing or mismatched (`E1-11`) and the `§6` mapping to
  `SyncError.ValidationRejected`. Reusing an existing closed leaf is deliberate: no new `RemoteError`
  or `SyncError` leaf is introduced, since that would be an error-taxonomy change.
- The converter returns `null` for the `EPOCH_MILLISECOND_FIELDS` and primitive branches instead of
  throwing, which widens the pre-existing rejection set from "unsupported type" to "unsupported type
  or unrepresentable value". This is safe because such a payload is unrepresentable to the provider and
  can only arise from a producer defect; the alternative of throwing would keep a second unchecked
  escape on the same boundary, which is exactly the defect.
- The end-to-end test reads state through `SyncDatabaseAccess.debugLines()` rather than a generated
  SQLDelight query, because `:integration:*` MUST NOT reach a generated entity query (`D-38`).
- `integration/firebase-firestore/build.gradle.kts` adds `:core:database` as a `commonTest` dependency
  so the end-to-end test can drive the real engine over the real staged database factory. This is a
  test-only edge and does not change the module's production dependency surface.

## Verification Run

- `./gradlew :integration:firebase-firestore:testAndroidHostTest` — BUILD SUCCESSFUL, including the six
  `E3-19` cases.
- `./gradlew :integration:firebase-firestore:ktlintCheck :integration:firebase-firestore:detekt` —
  BUILD SUCCESSFUL.
- `./gradlew contractCheck` — BUILD SUCCESSFUL, 195 decisions, 195 ADRs, no unresolved decision and no
  `PENDING` assertion.
- The mutation probe described under Acceptance Evidence.
- The complete non-instrumented repository command of `AGENTS.md` and the live CI result are recorded in
  the pull request; this section is updated with their outcome when the ten checks settle.

## Contract Impact

- Updated `docs/CONTRACTS.md` §10: the payload conversion on the push boundary MUST be total, and a
  malformed payload MUST be classified as `Outcome.Err(RemoteError.InvalidArgument)` before any remote
  write. No interface, DTO or other normative section changed.

## Decision Board Impact

- No decision changes.

## Shared-Write Modules Touched

- None. `:core:database` is consumed from `commonTest` only; no schema, migration or production source
  is modified.

## Project Log Entry

- [x] Entry appended

## Risks or Follow-ups

- `E3-21` (explicit startup reset of stale `SYNCING` rows) remains open and is unrelated to this
  fix: a row stranded by process death is still handled by the surviving outbox row, and this change
  does not touch that path.
- `E3-20` remains open on the pull side.

## Human Review Gate

Applies: `integration/firebase-firestore` production code (Firestore boundary) and
`docs/CONTRACTS.md` §10 (gated path). The gate is the owner's review and merge; the agent MUST NOT
merge.
