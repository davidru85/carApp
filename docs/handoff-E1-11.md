# Agent Handoff

## Story

`E1-11 - :feature:vehicle Outbox Payload entityType Fix`

## Ready Check

- Backlog story: `E1-11 - :feature:vehicle Outbox Payload entityType Fix` (`docs/BACKLOG.md`).
- Acceptance criteria reviewed: yes, including the updated criterion that the required
  integration-boundary adaptation in `:integration:firebase-firestore` is included and no schema
  or migration changed.
- Dependencies checked: `E1-06` follow-up; no other story in flight.
- Decisions checked: D-110 (ADR-0111) Accepted; no `Proposed`/`Pending` blocks.
- Normative sections reviewed: `docs/CONTRACTS.md §8`, `§10`, `§16`, `§20.7`.
- Expected verification: focused tests + full non-instrumented command + `git diff --check` +
  `contractCheck` output inspection.
- Human review gates: Applies — gated paths `AGENTS.md`, `docs/SPECIFICATION.md`,
  `docs/DECISION_BOARD.md`, `docs/adr/**` (D-110 mirrors and ADR-0111). Also
  `integration/firebase-firestore` production code change (Firestore boundary). The gate is the
  owner's review and merge; the agent MUST NOT merge.
- Rule 0 acknowledged: chat replies in Spanish (es-ES), repository artifacts in technical English.

## In-Progress Checkpoint

- Date: 2026-09-02
- Branch and base: `story/E1-11-outbox-entitytype-fix-v2` from `main` (`76883c2`)
- Current phase and latest commit: complete; `971f31e` plus the E1-11 review-finding fix
- Push and pull-request status: pushed to `origin/story/E1-11-outbox-entitytype-fix-v2`; pull
  request #48 (`https://github.com/davidru85/carApp/pull/48`) is open and awaiting owner review.
  The ten required checks were green on `971f31e` and re-run on the pushed head; their live result
  is the authority. The agent MUST NOT merge.
- Completed since the previous checkpoint: resolved the blocking E1-11 review finding on
  `OutboxCoalescenceParityTest`. The second parity test re-applied the cascade payload, so it
  proved cascade-then-cascade while its name, the acceptance criterion and this handoff claimed
  cascade-then-direct. It now captures the direct payload from the real
  `SqlDelightFuelEntryRepository` create path before the cascade and re-applies that direct
  payload, and it asserts the direct and cascade payloads differ so the re-application cannot be a
  repeat of the cascade write. The test is renamed
  `cascadeDeleteThenDirectFuelEntryCoalescenceRetainsCanonicalKeySet` and the fixture helper is
  renamed `coalesceFuelEntryOutboxPayload` with a comment that describes what it actually does.
  Also added the missing trailing newline to this file. Earlier in the branch: all E1-11 work
  re-applied after the revert; production code (mapper + Firestore boundary), test code,
  documentation (D-110, ADR-0111, backlog, AGENTS.md, README.md, project log, handoff); the
  Firestore boundary reads the payload key through the file-local `ENTITY_TYPE_FIELD` constant,
  matching the existing `*_FIELD` convention of `FirebaseRemoteSyncSource.kt`.
- Verification evidence: `:feature:vehicle:testAndroidHostTest`, `:shared:testAndroidHostTest`,
  `:integration:firebase-firestore:testAndroidHostTest`, `ktlintCheck`, `detekt`,
  `architectureCheck` and `contractCheck` all pass. `contractCheck` reports 111 decisions,
  111 ADRs, no unresolved decisions and no `PENDING` assertions. `git diff --check` is clean.
  No known failures.
- Open decisions or blockers: none
- Exact next step: owner re-review of pull request #48 after the parity-test correction, and the
  merge decision. The agent MUST NOT merge.

## Scope Completed

- `VehicleOutboxMapper.toVehicleOutboxPayloadOrNull` emits `"entityType":"VEHICLE"` as the first
  key, covering create, update and vehicle tombstone call sites.
- `VehicleOutboxMapper.toFuelEntryTombstonePayload` emits `"entityType":"FUEL_ENTRY"` as the first
  key of the cascade-delete tombstone payload.
- `FirebaseRemoteSyncSource.toFirestoreWrite` requires the payload `entityType` to match
  `EntitySnapshot.entityType`, excludes `entityType` from `FirestoreWrite.fields`, and returns
  `RemoteError.InvalidArgument` for missing, unknown, or mismatched values. The payload key is a
  file-local `ENTITY_TYPE_FIELD` constant, matching the existing `*_FIELD` convention of that file.
- `VehicleOutboxMapperTest` added with unit coverage including
  `entityTypeEnumNamesMatchTheOutboxWireValues` anchor.
- `VehicleRepositoryCreateTest.permanentOwnerCreateEnqueuesTheFullVehicleSnapshot` exact key-set
  assertion updated to include `entityType`.
- `VehicleRepositoryUpdateTest.updateCoalescesTheOutboxAtTheOriginalSequence` extended to assert
  `entityType`.
- `VehicleRepositoryDeleteTest.permanentOwnerDeleteEnqueuesFuelTombstonesBeforeTheVehicleTombstone`
  extended to assert `entityType` in every coalesced Fuel Entry tombstone and in the vehicle
  tombstone, with exact canonical key set.
- `VehicleRepositoryDeleteTest.cascadeDeleteIsIdempotentWhenItIsTheLastWriterOfAFuelEntryOutboxRow`
  proves the cascade is idempotent.
- `OutboxCoalescenceParityTest` in `:shared` uses the real `SqlDelightFuelEntryRepository` and
  `SqlDelightVehicleRepository` to prove both coalescence orders — direct-then-cascade and
  cascade-then-direct — produce the same canonical key set. Both payloads come from the real
  repository write paths; neither is handcrafted.
- `FirebaseRemoteSyncSourceEntityTypeBoundaryTest` (5 tests) proves the Firestore boundary excludes
  `entityType` and rejects mismatches for both Vehicle and Fuel Entry.
- `VehicleFormStateHolderTest.vehicleOutboxPayloadWithEntityTypeReachesRemoteSyncSourceAsAValidSnapshot`
  end-to-end regression test.
- D-110 "Outbox entity-type token ownership" (ADR-0111) recorded with all four mirrors.
- E3-13 follow-up registered (GitHub issue #46).

## Acceptance Evidence

- `VehicleOutboxMapper.toFuelEntryTombstonePayload` emits `"entityType":"FUEL_ENTRY"`:
  `VehicleOutboxMapperTest.fuelEntryTombstonePayloadIncludesEntityTypeFuelEntry`.
- `VehicleOutboxMapper.toVehicleOutboxPayloadOrNull` emits `"entityType":"VEHICLE"`:
  `VehicleOutboxMapperTest.permanentOwnerVehiclePayloadIncludesEntityTypeVehicle`.
- `entityType` asserted for each write path: Vehicle create, Vehicle update, Vehicle tombstone,
  cascade Fuel Entry tombstone.
- Coalesced `(FUEL_ENTRY, entityId)` outbox row has the same canonical key set regardless of
  write order: `OutboxCoalescenceParityTest.directFuelEntryWriteThenCascadeDeleteProducesTheSameCanonicalKeySet`
  proves direct-then-cascade, and
  `OutboxCoalescenceParityTest.cascadeDeleteThenDirectFuelEntryCoalescenceRetainsCanonicalKeySet`
  proves cascade-then-direct: it captures the direct payload from the real
  `SqlDelightFuelEntryRepository` create path before the cascade, asserts the direct and cascade
  payloads both carry exactly the canonical key set, asserts the two payloads differ, and then
  coalesces the direct payload onto the cascade-written row so that the direct writer is the last
  writer. The final row equals the direct payload and keeps exactly the canonical key set and
  `"entityType":"FUEL_ENTRY"`.
- Parity test uses real `SqlDelightFuelEntryRepository` (not a handcrafted payload builder).
- Firestore boundary excludes `entityType` and validates it:
  `FirebaseRemoteSyncSourceEntityTypeBoundaryTest` (5 tests).
- End-to-end: `VehicleFormStateHolderTest.vehicleOutboxPayloadWithEntityTypeReachesRemoteSyncSourceAsAValidSnapshot`.
- `LOCAL_OWNER + PENDING + no outbox` invariant preserved.
- No schema or migration change.
- `docs/CONTRACTS.md §8` and `docs/TECHNICAL_PLAN.md` require no edit.

## Out of Scope / Not Done

- `DatabaseMutations` (`core/database/.../DatabaseMutations.kt`), the `:feature:fuel` and `:shared`
  literals, and the `.sq` `CHECK`/SQL literals keep their string literals because `:core:database`
  cannot depend on `:core:sync` (`docs/TECHNICAL_PLAN.md §4`) and unification requires a gated
  relocation of `EntityType`. Any centralization is deferred to E3-13 (issue #46).

## Files Changed

- `AGENTS.md`
- `README.md`
- `docs/BACKLOG.md`
- `docs/DECISION_BOARD.md`
- `docs/PROJECT_LOG.md`
- `docs/SPECIFICATION.md`
- `docs/TECHNICAL_PLAN.md`
- `docs/adr/0111-outbox-entity-type-token-ownership.md` (new)
- `docs/adr/README.md`
- `docs/handoff-E1-11.md`
- `feature/vehicle/src/commonMain/kotlin/com/ruizurraca/carapp/feature/vehicle/data/VehicleOutboxMapper.kt`
- `feature/vehicle/src/commonTest/kotlin/com/ruizurraca/carapp/feature/vehicle/data/VehicleOutboxMapperTest.kt` (new)
- `feature/vehicle/src/commonTest/kotlin/com/ruizurraca/carapp/feature/vehicle/data/VehicleRepositoryCreateTest.kt`
- `feature/vehicle/src/commonTest/kotlin/com/ruizurraca/carapp/feature/vehicle/data/VehicleRepositoryDeleteTest.kt`
- `feature/vehicle/src/commonTest/kotlin/com/ruizurraca/carapp/feature/vehicle/data/VehicleRepositoryTestScope.kt`
- `feature/vehicle/src/commonTest/kotlin/com/ruizurraca/carapp/feature/vehicle/data/VehicleRepositoryUpdateTest.kt`
- `integration/firebase-firestore/src/commonMain/kotlin/com/ruizurraca/carapp/integration/firebase/firestore/FirebaseRemoteSyncSource.kt`
- `integration/firebase-firestore/src/commonTest/kotlin/com/ruizurraca/carapp/integration/firebase/firestore/FirebaseRemoteSyncSourceEntityTypeBoundaryTest.kt` (new)
- `integration/firebase-firestore/src/commonTest/kotlin/com/ruizurraca/carapp/integration/firebase/firestore/FirebaseRemoteSyncSourceTest.kt`
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/OutboxCoalescenceParityTest.kt` (new)
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/VehicleFormStateHolderTest.kt`

## Decisions Made

- D-110 "Outbox entity-type token ownership" (ADR-0111): E1-11 keeps explicit contract tokens in
  production code. The `:feature:vehicle` commonTest files derive their outbox lookup keys from
  `EntityType.*.name`; payload value assertions stay as exact string literals. The
  `entityTypeEnumNamesMatchTheOutboxWireValues` anchor pins the enum to the contract. No
  refactorization is assigned to E3-03; E3-13 (issue #46) owns the future centralization.

## Verification Run

- `./gradlew :feature:vehicle:testAndroidHostTest :shared:testAndroidHostTest :integration:firebase-firestore:testAndroidHostTest --rerun-tasks` — BUILD SUCCESSFUL, 87 tasks executed
- `OutboxCoalescenceParityTest` runs 2 tests with 0 failures on both the Android host and
  `iosSimulatorArm64`, and both report the renamed
  `cascadeDeleteThenDirectFuelEntryCoalescenceRetainsCanonicalKeySet`
- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test` — BUILD SUCCESSFUL
- `contractCheck` — output inspected: every assertion `[PASS]`, 111 decisions, 111 ADRs,
  36 interfaces, 20 modules, no unresolved decisions and no `PENDING` assertions
- `git diff --check` — clean

## Contract Impact

- No contract changes. `docs/CONTRACTS.md §8` already mandates `entityType`; `§16` closes the
  remote schema. This story makes the code conform to both.

## Decision Board Impact

- D-110 (ADR-0111) recorded as Accepted. All four mirrors updated.

## Shared-Write Modules Touched

- None

## Project Log Entry

- [x] Entry appended

## Risks or Follow-ups

- Closes the `E1-06` follow-up and GitHub issue #36.
- E3-13 (GitHub issue #46) remains the Phase 3 follow-up for single source of truth.
- `DatabaseMutations`, `:feature:fuel`, `:shared` and `.sq` literals remain independent until E3-13.

## Human Review Gate

Applies: gated paths — `AGENTS.md`, `docs/SPECIFICATION.md`, `docs/DECISION_BOARD.md`,
`docs/adr/**` (D-110 mirrors and ADR-0111). Also `integration/firebase-firestore` production code
(Firestore boundary). The gate is the owner's review and merge; the agent MUST NOT merge it.
