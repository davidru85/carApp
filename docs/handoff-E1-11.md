# Agent Handoff

## Story

`E1-11 - :feature:vehicle Outbox Payload entityType Fix`

## Ready Check

- Backlog story: `E1-11 - :feature:vehicle Outbox Payload entityType Fix` (`docs/BACKLOG.md`).
- Acceptance criteria reviewed:
  - `VehicleOutboxMapper.toFuelEntryTombstonePayload` emits `"entityType":"FUEL_ENTRY"`.
  - `VehicleOutboxMapper.toVehicleOutboxPayloadOrNull` emits `"entityType":"VEHICLE"`.
  - A test asserts `entityType` presence and value for each write path: Vehicle create, Vehicle update, Vehicle tombstone and cascade Fuel Entry tombstone.
  - A coalesced `(FUEL_ENTRY, entityId)` outbox row has the same key set regardless of whether the last write came from the direct Fuel Entry path or the Vehicle cascade-delete path.
  - `VehicleRepositoryDeleteTest.permanentOwnerDeleteEnqueuesFuelTombstonesBeforeTheVehicleTombstone` extended to assert `entityType` in every coalesced Fuel Entry tombstone payload and in the vehicle tombstone payload.
  - `VehicleRepositoryCreateTest.permanentOwnerCreateEnqueuesTheFullVehicleSnapshot` key-set assertion updated to include `entityType`; the assertion stays exact.
  - No outbox row while `ownerId == LOCAL_OWNER`; the `LOCAL_OWNER + PENDING + no outbox` invariant is preserved.
  - No schema or migration change; the fix is confined to `:feature:vehicle` mapper and test code.
  - `docs/CONTRACTS.md §8` and `docs/TECHNICAL_PLAN.md` require no edit.
- Dependencies checked: `E1-06` delivered the mappers and the follow-up this story closes; no other story is in flight on `:feature:vehicle` or `:feature:fuel`.
- Decisions checked: no `Proposed` or `Pending` decision blocks this story; the contract already mandates `entityType` (`docs/CONTRACTS.md §8`).
- Normative sections reviewed: `docs/CONTRACTS.md §8` (outbox payload format), `docs/CONTRACTS.md §3` (canonical field names), `docs/BACKLOG.md` E1-11 acceptance criteria, `docs/handoff-E1-06.md` follow-up.
- Expected verification:
  - `./gradlew :feature:vehicle:testAndroidHostTest` for the new and updated tests.
  - The full non-instrumented command from `AGENTS.md`.
- Human review gates identified before work: None. The fix is confined to `:feature:vehicle` mapper and test code. It does not touch `core/database/**`, `docs/CONTRACTS.md`, `docs/SPECIFICATION.md`, `docs/DECISION_BOARD.md`, `AGENTS.md`, `firestore/**`, `core/sync/**`, `core/auth/**`, `core/model/**` money types, or any gated topic from `AGENTS.md` Human Review Gates.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every repository artifact is in technical English.

## In-Progress Checkpoint

Update this section at every material state change and before yielding unfinished work (`D-105`).

- Date: 2026-09-01
- Branch and base: `story/E1-11-outbox-entitytype-fix` from `main` (`68842a2`)
- Current phase and latest commit: complete, latest commit `2d5522e`
- Push and pull-request status: about to push and open PR
- Completed since the previous checkpoint: RED, GREEN and REFACTOR committed; full verification suite passes
- Verification evidence and known failures: the exact non-instrumented command from `AGENTS.md` passes with 627 actionable tasks
- Open decisions or blockers: none
- Exact next step: push, open PR, append project log entry

## Scope Completed

- `VehicleOutboxMapper.toVehicleOutboxPayloadOrNull` now emits `"entityType":"VEHICLE"` as the first key of the payload, covering all three `SqlDelightVehicleRepository` call sites (create, update, vehicle tombstone).
- `VehicleOutboxMapper.toFuelEntryTombstonePayload` now emits `"entityType":"FUEL_ENTRY"` as the first key of the cascade-delete tombstone payload.
- `VehicleOutboxMapperTest` added with unit coverage of the Vehicle snapshot and cascade Fuel Entry tombstone payloads, including `LOCAL_OWNER` no-outbox behavior, `entityType` value, canonical key set and absence of local-only metadata.
- `VehicleRepositoryCreateTest.permanentOwnerCreateEnqueuesTheFullVehicleSnapshot` exact key-set assertion updated to include `entityType`; the assertion stays exact so a future omission fails the build.
- `VehicleRepositoryUpdateTest.updateCoalescesTheOutboxAtTheOriginalSequence` extended to assert `entityType` in the coalesced payload.
- `VehicleRepositoryDeleteTest.permanentOwnerDeleteEnqueuesFuelTombstonesBeforeTheVehicleTombstone` extended to assert `entityType` in every coalesced Fuel Entry tombstone payload and in the vehicle tombstone payload.
- `VehicleRepositoryDeleteTest.cascadeDeleteCoalescesWithAnExistingFuelEntryOutboxRowKeepingEntityType` added: a parameterized test proving a cascade delete preceded by a direct Fuel Entry outbox write (two stale payload variants) yields a coalesced payload that still carries `entityType`.
- `VehicleFormStateHolderTest.saveEnqueuesTheClosedRemoteVehicleSnapshot` in `:shared` updated to include `entityType` in the exact key-set assertion, matching the corrected mapper output.
- `VehicleRepositoryTestScope.seedFuelEntryOutbox` helper added to seed an existing `FUEL_ENTRY` outbox row for coalescence tests.

## Acceptance Evidence

- `VehicleOutboxMapper.toFuelEntryTombstonePayload` emits `"entityType":"FUEL_ENTRY"`: `VehicleOutboxMapperTest.fuelEntryTombstonePayloadIncludesEntityTypeFuelEntry`.
- `VehicleOutboxMapper.toVehicleOutboxPayloadOrNull` emits `"entityType":"VEHICLE"`: `VehicleOutboxMapperTest.permanentOwnerVehiclePayloadIncludesEntityTypeVehicle`.
- `entityType` asserted for each write path:
  - Vehicle create: `VehicleRepositoryCreateTest.permanentOwnerCreateEnqueuesTheFullVehicleSnapshot`.
  - Vehicle update: `VehicleRepositoryUpdateTest.updateCoalescesTheOutboxAtTheOriginalSequence`.
  - Vehicle tombstone: `VehicleRepositoryDeleteTest.permanentOwnerDeleteEnqueuesFuelTombstonesBeforeTheVehicleTombstone` (vehicle payload).
  - Cascade Fuel Entry tombstone: `VehicleRepositoryDeleteTest.permanentOwnerDeleteEnqueuesFuelTombstonesBeforeTheVehicleTombstone` (Fuel Entry payloads).
- Coalesced `(FUEL_ENTRY, entityId)` outbox row has `entityType` regardless of the prior write source: `VehicleRepositoryDeleteTest.cascadeDeleteCoalescesWithAnExistingFuelEntryOutboxRowKeepingEntityType`.
- `VehicleRepositoryDeleteTest.permanentOwnerDeleteEnqueuesFuelTombstonesBeforeTheVehicleTombstone` extended to assert `entityType` in every coalesced Fuel Entry tombstone payload and in the vehicle tombstone payload.
- `VehicleRepositoryCreateTest.permanentOwnerCreateEnqueuesTheFullVehicleSnapshot` key-set assertion updated to include `entityType`; the assertion stays exact.
- No outbox row while `ownerId == LOCAL_OWNER`: `VehicleOutboxMapperTest.localOwnerNeverProducesAnOutboxPayload`, `VehicleRepositoryCreateTest.localOwnerCreateStoresPendingWithoutOutbox`, `VehicleRepositoryDeleteTest.localOwnerDeleteCreatesNoOutboxRows`.
- No schema or migration change; the fix is confined to mapper and test code.
- `docs/CONTRACTS.md §8` and `docs/TECHNICAL_PLAN.md` require no edit.

## Out of Scope / Not Done

- Nothing. The story is complete.

## Files Changed

- `feature/vehicle/src/commonMain/kotlin/com/ruizurraca/carapp/feature/vehicle/data/VehicleOutboxMapper.kt`
- `feature/vehicle/src/commonTest/kotlin/com/ruizurraca/carapp/feature/vehicle/data/VehicleOutboxMapperTest.kt` (new)
- `feature/vehicle/src/commonTest/kotlin/com/ruizurraca/carapp/feature/vehicle/data/VehicleRepositoryCreateTest.kt`
- `feature/vehicle/src/commonTest/kotlin/com/ruizurraca/carapp/feature/vehicle/data/VehicleRepositoryDeleteTest.kt`
- `feature/vehicle/src/commonTest/kotlin/com/ruizurraca/carapp/feature/vehicle/data/VehicleRepositoryTestScope.kt`
- `feature/vehicle/src/commonTest/kotlin/com/ruizurraca/carapp/feature/vehicle/data/VehicleRepositoryUpdateTest.kt`
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/VehicleFormStateHolderTest.kt`
- `docs/handoff-E1-11.md` (new)
- `docs/PROJECT_LOG.md`

## Decisions Made

Include any `SHOULD` you deviated from, and why.

- No decisions. The fix is a contract-compliance bug fix. No new decision ID, ADR, or mirroring rows are required.

## Verification Run

Exact commands, and their result.

- `./gradlew :feature:vehicle:testAndroidHostTest` — passes (76 tests).
- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test` — BUILD SUCCESSFUL, 627 actionable tasks.

## Contract Impact

- No contract changes. `docs/CONTRACTS.md §8` already mandates `entityType`; this story makes the code conform to it.

## Decision Board Impact

- No decision changes / Updated `docs/DECISION_BOARD.md` (`D-n`) and ADR: none.

## Shared-Write Modules Touched

`:core:database` may be modified by only one story at a time.

- None

## Project Log Entry

Appending an entry to `docs/PROJECT_LOG.md` is part of the Definition of Done.

- [x] Entry appended

## Risks or Follow-ups

- Closes the `E1-06` follow-up, GitHub issue #36 and the additional Vehicle payload finding folded into this story.

## Human Review Gate

Not applicable / Applies (say which gate from `AGENTS.md`):

Not applicable. The change is confined to `:feature:vehicle` mapper and test code and does not touch any gated path or gated topic.