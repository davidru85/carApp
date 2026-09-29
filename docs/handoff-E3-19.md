# Agent Handoff

> **Closure update (2026-09-29):** `E3-19` merged through pull request #82 after the owner's gated
> review, as a true merge of `story/E3-19-push-boundary-payload-totality` into `main` (merge commit
> `cfccfe1c`, whose second parent is the branch head `47aef3fe`). It took four review correction
> rounds: correction 1 made the identity and epoch-millisecond reads require the JSON type itself,
> correction 2 moved the `UPDATED_AT_FIELD` branch before `JsonNull` so a present `updatedAt` is
> always server-owned, correction 3 restricted booleans and integers to the RFC 8259 `true` / `false`
> and `[ minus ] int` tokens, and correction 4 corrected the `D-170` scope attribution. It introduced
> no decision. The `E3-19` status block and its index row in `docs/BACKLOG.md`, the `Remaining
> Phase 3` list and the open-decision paragraph in `AGENTS.md` now state the merge and its pull
> request. The in-flight `In-Progress Checkpoint` below is superseded by this update and preserved as
> observed; the original acceptance evidence and verification results below are likewise preserved as
> observed. On the final record-only head `47aef3fe`, which is an ancestor of `main`, all ten required
> checks are green on attempt `1` (run 36468868905), and the implementation/documentation head
> `891167d` passed all ten on attempt `1` (run 36466988921).

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
- Decisions checked: no new decision is required. `D-170` was reviewed only as the pull-side
  contrast: it governs raw `pullChanges` transport and quarantine ownership, not push conversion.
  This story closes the explicit `E3-19` acceptance gap by using the existing `§6` mapping and by
  extending the E1-11 `RemoteError.InvalidArgument` push-boundary precedent from malformed
  `entityType` to the other malformed identity shapes. It introduces no option the owner must choose
  between. `contractCheck` reports 195 decisions, 195 ADRs and zero `PENDING` assertions.
- Normative sections reviewed: `docs/CONTRACTS.md` §6 (Result and Error Taxonomy), §8 (Outbox
  Contract), §9.3 (Push), §9.5 (Quarantine and malformed remote payloads), §10 (`RemoteSyncSource`
  Contract); `docs/TECHNICAL_PLAN.md` §4 (dependency rules) and §9 (sync tests);
  `docs/SPECIFICATION.md` §2 (P2 offline-first).
- Expected verification: `:integration:firebase-firestore:testAndroidHostTest` (focused, both new
  tests), the complete non-instrumented repository command of `AGENTS.md`, `contractCheck` output
  inspection, and a mutation probe proving the new tests are not vacuous.
- Human review gates identified before work: applies. `integration/firebase-firestore` production code
  is a Firestore boundary change, and `docs/CONTRACTS.md` §10 and `AGENTS.md` are gated paths. The gate
  is the owner's review and merge; the agent MUST NOT merge.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every repository
  artifact it produces is in technical English.

## In-Progress Checkpoint

- Date: 2026-09-28
- Branch and base: `story/E3-19-push-boundary-payload-totality` from `main` (`ba2c3c17`)
- Current phase and latest commit: review correction 4 complete, correction commit `891167d`. First
  round: RED `d5ecd400`, GREEN
  `e740288c`, REFACTOR `c049d478`, records `b2889948`, `ddd9f09f` and `142fcf8d`. Review correction
  1: RED `ddd8c3c`, GREEN `07f6e6f`, test KDoc correction `fddfaea`, records `69043a0` and `67519db`.
  Review correction 2: RED `db2a563`, GREEN `d03e60c`, style `1b1928a4`, records `d3a13fc8`,
  `ba8b43fe` and `f9eb525e`. Review correction 3: RED `5e90630e`, GREEN `16800310`, records `e33e84f6`
  and `2c87f21b`. Review correction 4: `891167d`, and the record that carries this checkpoint.
- Push and pull-request status: pull request #82 is open against `main` and awaits the owner's gated
  review; its description was rewritten in review correction 3 to mirror this handoff and its
  attribution is corrected in review correction 4. Earlier green
  heads: 36409500477 (`142fcf8d`), 36414077708 (`69043a05`), 36440303984 (`d3a13fc8`),
  36442950495 (`ba8b43fe`), 36447550134 (`e33e84f6`) and 36449596374 (`2c87f21b`).
  Review-correction 4 checks: all ten passed on run 36466988921 at `891167d`.
  GitHub's live pull-request status is authoritative for any later record-only commit. The agent MUST
  NOT merge.
- Completed since the previous checkpoint: review correction 4 — the `D-170` scope attribution in the
  Ready Check, the Out of Scope section and the project log, so the story is attributed to its own
  backlog acceptance gap, the `§6` mapping, the `§8` outbox contract, the `§9.3` push behavior, the
  exact `§10` push-boundary rule and the E1-11 push-boundary precedent.
- Verification evidence and known failures: see "Review Correction 4" and "Verification Run". No known
  failure.
- Open decisions or blockers: none.
- Exact next step: owner review of pull request #82 on the review-correction 4 head and the merge
  decision. The agent MUST NOT merge.

## Review Correction 4

The fourth review found one architectural-record error: the Ready Check, Out of Scope section,
project-log story entry and pull-request description said E3-19 implemented the accepted D-170
totality principle on the push side. D-170 / ADR-0171 is narrower: it selects raw per-document
transport for `pullChanges` and assigns pulled-document `MalformedPayload` classification to
`:core:sync`. It does not govern conversion of a local outbox snapshot for `pushSnapshot`.

The current records now attribute E3-19 to its explicit backlog acceptance gap, the `§6`
`RemoteError.InvalidArgument` to `SyncError.ValidationRejected` mapping, the `§8` local outbox
contract, the `§9.3` push behavior, the exact `§10` push-boundary rule and the E1-11 push-boundary
precedent. The historical project-log entry remains unchanged under the append-only rule and a new
entry corrects it. No production source, test, contract behavior, decision, ADR or acceptance
criterion changes.

## Review Correction 3

The owner's third review of pull request #82 found four issues; all are corrected on this branch.

1. **Non-canonical JSON tokens were coerced.** `Json.parseToJsonElement` accepts any unquoted token as
   a literal, kotlinx `booleanOrNull` ignores case, and kotlinx `longOrNull` accepts leading zeros and
   exponents. `TRUE`, `False`, `007`, `1e3`, `01`, `1e0`, `17E11` and `01700000000000` were therefore
   written as provider booleans, integers or timestamps, and `01` and `1e0` passed the `schemaVersion`
   identity check, although RFC 8259 defines none of them as a boolean or an integer token and the
   `§10` norm requires such a payload to fail closed. `jsonIntegerOrNull` now requires the RFC 8259
   `[ minus ] int` token and `jsonBooleanOrNull` the exact lowercase `true` or `false`, and `§10`
   states both definitions.
2. **Untested `§10` clauses.** No test drove a correctly typed but different `ownerId` or
   `schemaVersion`, or a mistyped `date`, so weakening either equality to a presence check or removing
   `date` from `EPOCH_MILLISECOND_FIELDS` passed every test. Three guard cases now pin them.
3. **Stale pull-request description.** The description of pull request #82 still carried the
   first-round text, including the `D-38` misattribution that review correction 1 removed from this
   handoff and from the test KDoc. It now mirrors this handoff.
4. **Imprecise escape location.** `docs/BACKLOG.md` and the first `E3-19` entry of
   `docs/PROJECT_LOG.md` said the escaping exception reached "the generic `drainCycles` catch". The
   catch is in `SyncEngine.runCycle`, which `drainCycles` calls; the backlog now names it, and the
   review-correction 3 log entry records the correction.

Evidence:

- RED: `aNonCanonicalJsonTokenFailsClosedWithoutWritingAnything` failed on the review-correction 2 code
  and named all eight tokens (34 tests completed, 1 failed). The three guard cases and
  `aCanonicalTombstonePayloadStillWritesStrictlyTypedValues` passed there: they are regression guards,
  not RED tests.
- GREEN: every `:integration:firebase-firestore` test passes (34 tests, 0 failures).
- Non-vacuity: weakening `parsed.stringOrNull(OWNER_ID_FIELD) == ownerId.value` to `!= null`,
  weakening `parsed.longOrNull(SCHEMA_VERSION_FIELD) == schemaVersion.toLong()` to `!= null`, and
  removing `"date"` from `EPOCH_MILLISECOND_FIELDS` each fail exactly one test,
  `everyMalformedPayloadFieldFailsClosedWithoutWritingAnything`, at the "mismatched ownerId",
  "mismatched schemaVersion" and "date is a numeric string" case respectively. Each probe was reverted.

## Review Correction 2

`docs/CONTRACTS.md §10` states that every present `updatedAt` payload value is ignored and replaced
with the server timestamp (`§9.3`). `JsonElement.toFirestoreValue` evaluated `this === JsonNull`
before `field == UPDATED_AT_FIELD`, so a payload carrying `"updatedAt": null` was converted to
`FirestoreNull` instead of `FirestoreServerTimestamp`, and the resulting provider write contradicted
the server-owned timestamp contract and was rejected by the Firestore schema instead of receiving the
server timestamp.

Moving the `UPDATED_AT_FIELD` branch before the `JsonNull` branch restores the existing contract for
every present `updatedAt` value — a JSON string, number, boolean, object, array or `null` — while
`JsonNull` remains the second branch so nullable product fields such as `brand`, `model`, `notes` and
`deletedAt` are still representable as `FirestoreNull`.

`aNullUpdatedAtIsAlwaysReplacedWithTheServerTimestamp` failed before the reorder with
`expected:<FirestoreServerTimestamp> but was:<FirestoreNull>` and passed after it. No decision, ADR,
schema, Firestore-rule, dependency or public API change is introduced; `docs/CONTRACTS.md` is
unchanged because its sentence was already correct and is the requirement the code now satisfies.

## Review Correction 1

The owner's review of pull request #82 found five issues; all are corrected on this branch.

1. **Coercing identity and timestamp reads.** `stringOrNull` returned the content of any primitive and
   `longOrNull` parsed a JSON string, so `"schemaVersion":"1"` passed the identity check and was
   written as a provider string, an unquoted `entityType` literal matched the entity type name, and a
   numeric string in `createdAt` or `deletedAt` was coerced into a provider timestamp. The first shape
   reached a remote write that the Firestore rules reject, poisoning the row as
   `REMOTE.PERMISSION_DENIED` instead of `REMOTE.INVALID_ARGUMENT` with zero writes. The readers and the
   epoch-millisecond branch now require the JSON type itself: `id`, `ownerId` and `entityType` MUST be
   JSON strings, and `schemaVersion`, `createdAt`, `date` and `deletedAt` MUST be JSON integers (the
   epoch fields MAY also be `null`).
2. **Overbroad `§10` norm.** The norm required "a missing required key" and "a wrong-typed key" to be
   classified before any write, and required `pushSnapshot` never to let an unchecked exception escape.
   The conversion reads only the identity keys and never validates the per-entity `§16` schema, and the
   provider write path is outside the conversion. The norm now lists exactly what the conversion
   classifies, states that the Firestore rules enforce the `§16` schema, and states that for this
   conversion an unparseable payload is `RemoteError.InvalidArgument` rather than the `§6`
   `PersistenceError.SerializationFailed`, which the closed `RemoteError` return type cannot carry.
3. **Incorrect escape analysis.** The KDoc, the test KDoc and the project log stated that
   `JsonElement.jsonPrimitive` threw `IllegalStateException` past the boundary. In kotlinx.serialization
   1.11.0 it throws `IllegalArgumentException`, which the previous `catch` handled; only the
   `JsonObject.getValue` read of a missing identity key (`NoSuchElementException`) escaped.
4. **Misattributed `D-38` rule.** The end-to-end test KDoc and this handoff stated that `D-38` forbids
   `:integration:*` from reading through a generated SQLDelight query. `D-38` forbids generated
   entity-mutation calls outside `:core:database`; `docs/TECHNICAL_PLAN.md §4` keeps read queries
   available.
5. **Incomplete records.** `docs/BACKLOG.md` still read "PR pending", `AGENTS.md` did not name pull
   request #82, and this handoff omitted `AGENTS.md` from "Files Changed" and from the review gate.

Evidence:

- RED: `aCoercibleValueOfTheWrongJsonTypeFailsClosedWithoutWritingAnything` failed on the first-round
  code and named all four accepted shapes. `everyMalformedPayloadFieldFailsClosedWithoutWritingAnything`,
  extended with an array `brand` and a fractional `initialOdometerKm` to reach the two converter
  branches no earlier case reached, kept passing because those shapes were already rejected.
- GREEN: every `:integration:firebase-firestore` test passes with the strict readers.
- Against the story-base (`ba2c3c17`) version of `FirebaseRemoteSyncSource.kt`, five of the seven
  `E3-19` tests fail: `everyMalformedPayloadFieldFailsClosedWithoutWritingAnything`,
  `aCoercibleValueOfTheWrongJsonTypeFailsClosedWithoutWritingAnything` and the three missing-key
  end-to-end tests. `aWellFormedPayloadStillWritesTheDocument` and
  `aWrongTypedOwnerIdPoisonsTheRowInsteadOfStrandingIt` pass there, so the wrong-typed `ownerId` case is
  a regression guard, not a RED test.

## Scope Completed

- `FirebaseRemoteSyncSource.toFirestoreWrite` is now total. It returns
  `Outcome<FirestoreWrite, RemoteError>`; the payload is parsed defensively, each identity field is
  read through a nullable, strictly typed accessor (`id`, `ownerId` and `entityType` MUST be JSON
  strings, `schemaVersion` a JSON integer), and every field value is converted through a converter
  that returns `null` instead of throwing or coercing. Unparseable JSON, a non-object root, a missing
  or wrong-typed identity key, a non-integer epoch-millisecond value and a value with no provider
  representation all become `Outcome.Err(RemoteError.InvalidArgument)` before any remote write. The
  per-entity `§16` schema stays enforced by the Firestore rules.
- `pushSnapshot` returns the conversion's `Outcome.Err` directly instead of catching only
  `IllegalArgumentException`.
- `docs/CONTRACTS.md` §10 gains the push-boundary totality bullet: the payload conversion MUST NOT
  throw, and the bullet lists exactly which payloads MUST be a closed `RemoteError.InvalidArgument`
  before any remote write.
- Review correction 3: booleans and integers are read through `jsonBooleanOrNull` and
  `jsonIntegerOrNull`, which accept only the RFC 8259 lowercase `true` / `false` and `[ minus ] int`
  tokens, so a non-canonical token such as `TRUE`, `007` or `1e3` fails closed instead of being
  coerced, including in the `schemaVersion` identity check and the epoch-millisecond fields.

## Acceptance Evidence

- Criterion 1 — every push-boundary field read is total:
  `FirebaseRemoteSyncSourcePushPayloadTotalityTest.everyMalformedPayloadFieldFailsClosedWithoutWritingAnything`
  drives 24 malformed payload shapes (each identity key missing, wrong-typed, `JsonNull`, non-object,
  mismatched `id`, `ownerId`, `schemaVersion` and `entityType`, a mistyped `createdAt`, `date` and
  `deletedAt`, an array `brand`, a fractional `initialOdometerKm`, a non-object root, a scalar root and
  unparseable JSON) and asserts a
  closed `Outcome.Err(RemoteError.InvalidArgument)` with zero writes for each.
  `aCoercibleValueOfTheWrongJsonTypeFailsClosedWithoutWritingAnything` (review correction 1) drives a
  numeric-string `schemaVersion`, an unquoted `entityType` literal and numeric-string `createdAt` and
  `deletedAt` values and asserts each fails closed with zero writes.
  `aWellFormedPayloadStillWritesTheDocument` proves the hardening does not reject a canonical payload.
  `aNullUpdatedAtIsAlwaysReplacedWithTheServerTimestamp` (review correction 2) drives a payload whose
  `updatedAt` is JSON `null` and asserts the written field is `FirestoreServerTimestamp`, proving the
  server-owned timestamp of `§10` survives that shape.
  `aNonCanonicalJsonTokenFailsClosedWithoutWritingAnything` (review correction 3) drives eight unquoted
  tokens that kotlinx.serialization parses but RFC 8259 does not define as a boolean or an integer
  (`TRUE`, `False`, `007`, `1e3`, `01`, `1e0`, `17E11`, `01700000000000`) and asserts each fails closed
  with zero writes. `aCanonicalTombstonePayloadStillWritesStrictlyTypedValues` (review correction 3)
  proves the strict readers still write the lowercase `true`, a multi-digit integer and an
  epoch-millisecond `deletedAt` with the expected provider types.
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
- No new decision ID, ADR or mirrored decision row: the change closes the explicit `E3-19`
  push-boundary gap using the existing `§6` mapping and the E1-11 push-boundary error precedent.
  `D-170` remains limited to pull-side raw transport and quarantine ownership; this story neither
  implements nor modifies it.

## Files Changed

- `AGENTS.md` (Repository State: `E3-19` status and pull request #82)
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
- The end-to-end test reads state through `SyncDatabaseAccess.debugLines()` because that one call
  exposes the entity row state and the retained outbox retry context. This is a convenience, not a
  rule: `D-38` forbids generated entity-mutation calls outside `:core:database`, not read queries
  (`docs/TECHNICAL_PLAN.md §4`).
- Review correction 1 reads `id`, `ownerId` and `entityType` only as JSON strings and `schemaVersion`,
  `createdAt`, `date` and `deletedAt` only as JSON integers. This is not a new decision: it is what the
  `docs/CONTRACTS.md` §10 bullet this story added already required ("a wrong-typed key ... before any
  remote write").
- `integration/firebase-firestore/build.gradle.kts` adds `:core:database` as a `commonTest` dependency
  so the end-to-end test can drive the real engine over the real staged database factory. This is a
  test-only edge and does not change the module's production dependency surface.
- Review correction 3 defines a JSON integer as the RFC 8259 `[ minus ] int` token and a JSON boolean
  as the exact lowercase `true` or `false` token, in the code and in `docs/CONTRACTS.md §10`. This is
  not a new decision: it makes precise the terms "JSON integer" and "boolean" that the `§10` bullet this
  story added already used, and applies the review-correction 1 rule that a value MUST fail closed even
  when its content would coerce to the expected value. The `§8` producers emit only these canonical
  tokens, so no payload they write is newly rejected.

## Verification Run

- `./gradlew :integration:firebase-firestore:testAndroidHostTest` — BUILD SUCCESSFUL, including every
  `E3-19` test (ten after review correction 3).
- `./gradlew :integration:firebase-firestore:ktlintCheck :integration:firebase-firestore:detekt` —
  BUILD SUCCESSFUL.
- `./gradlew contractCheck` — BUILD SUCCESSFUL, 195 decisions, 195 ADRs, no unresolved decision and no
  `PENDING` assertion.
- The mutation probe described under Acceptance Evidence.
- The complete non-instrumented repository command of `AGENTS.md` — BUILD SUCCESSFUL, 642 actionable
  tasks (79 executed).
- The ten required checks of pull request #82 — all green on run
  [36407639430](https://github.com/davidru85/carApp/actions/runs/36407639430): `android-assemble`,
  `android-instrumented-tests`, `architecture-check`, `contract-check`, `detekt`, `ios-simulator-build`,
  `ktlint`, `objc-header-golden-check`, `provider-decoupling` and `shared-tests`.
- Review correction 1: `./gradlew :integration:firebase-firestore:testAndroidHostTest`, the complete
  non-instrumented repository command of `AGENTS.md` and `./gradlew contractCheck` — BUILD SUCCESSFUL,
  zero `PENDING` assertions. The ten required checks of the review-correction head are recorded in the
  In-Progress Checkpoint once they settle.
- Review correction 2: `./gradlew :integration:firebase-firestore:testAndroidHostTest --rerun-tasks` —
  RED before the reorder on `aNullUpdatedAtIsAlwaysReplacedWithTheServerTimestamp`
  (`expected:<FirestoreServerTimestamp> but was:<FirestoreNull>`, 32 tests completed, 1 failed) and
  BUILD SUCCESSFUL after it (32 tests, 0 failures).
  `./gradlew :integration:firebase-firestore:ktlintCheck :integration:firebase-firestore:detekt`,
  `./gradlew contractCheck --rerun-tasks` (zero `PENDING` assertions), the complete non-instrumented
  repository command of `AGENTS.md` (642 actionable tasks) and `git diff --check origin/main...HEAD` —
  all pass. The ten required checks of the review-correction 2 head passed on run
  [36440303984](https://github.com/davidru85/carApp/actions/runs/36440303984) at `d3a13fc8`, the
  implementation/documentation head, and again on run
  [36442950495](https://github.com/davidru85/carApp/actions/runs/36442950495) at the record-only commit
  `ba8b43fe`; GitHub's live status is authoritative for any later record-only commit.
- Review correction 3: `./gradlew :integration:firebase-firestore:testAndroidHostTest` — RED before the
  token readers on `aNonCanonicalJsonTokenFailsClosedWithoutWritingAnything` (all eight tokens named,
  34 tests completed, 1 failed) and BUILD SUCCESSFUL after them (34 tests, 0 failures). The three
  non-vacuity probes of "Review Correction 3" each failed exactly one test at the named case and were
  reverted. `./gradlew :integration:firebase-firestore:ktlintCheck :integration:firebase-firestore:detekt`,
  `./gradlew contractCheck --rerun-tasks` (zero `PENDING` assertions), the complete non-instrumented
  repository command of `AGENTS.md` and `git diff --check origin/main...HEAD` — all pass.
  The ten required checks of the review-correction 3 head passed on run
  [36447550134](https://github.com/davidru85/carApp/actions/runs/36447550134) at `e33e84f6`.
- Review correction 4: `./gradlew contractCheck --rerun-tasks` — BUILD SUCCESSFUL, 195 decisions, 195
  ADRs, no failed assertion and zero `PENDING` assertions. The complete non-instrumented repository
  command of `AGENTS.md` — BUILD SUCCESSFUL (642 actionable tasks). `git diff --check
  origin/main...HEAD` — clean. The ten required checks of the review-correction 4 head passed on run
  [36466988921](https://github.com/davidru85/carApp/actions/runs/36466988921) at `891167d`.

## Contract Impact

- Updated `docs/CONTRACTS.md` §10: the payload conversion on the push boundary MUST be total, and the
  bullet lists exactly which payloads MUST be classified as `Outcome.Err(RemoteError.InvalidArgument)`
  before any remote write; the per-entity `§16` schema stays with the Firestore rules. No interface,
  DTO or other normative section changed.
- Review correction 3 adds to the same bullet the RFC 8259 definition of a JSON integer token and a
  JSON boolean token; no interface, DTO or other normative section changed.

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

Applies: `integration/firebase-firestore` production code (Firestore boundary) and the gated paths
`docs/CONTRACTS.md` §10 and `AGENTS.md`. The gate is the owner's review and merge; the agent MUST NOT
merge.
