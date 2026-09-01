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
  - `./gradlew :feature:vehicle:testDebugUnitTest` (or the commonTest equivalent) for the new and updated tests.
  - `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test`
- Human review gates identified before work: None. The fix is confined to `:feature:vehicle` mapper and test code. It does not touch `core/database/**`, `docs/CONTRACTS.md`, `docs/SPECIFICATION.md`, `docs/DECISION_BOARD.md`, `AGENTS.md`, `firestore/**`, `core/sync/**`, `core/auth/**`, `core/model/**` money types, or any gated topic from `AGENTS.md` Human Review Gates.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every repository artifact is in technical English.

## In-Progress Checkpoint

Update this section at every material state change and before yielding unfinished work (`D-105`).

- Date: 2026-09-01
- Branch and base: `story/E1-11-outbox-entitytype-fix` from `main` (`68842a2`)
- Current phase and latest commit: REFACTOR, about to commit
- Push and pull-request status: not pushed, no PR
- Completed since the previous checkpoint: GREEN committed; consolidated the two coalescence tests into a single parameterized test
- Verification evidence and known failures: `:feature:vehicle:testAndroidHostTest` passes (76 tests)
- Open decisions or blockers: none
- Exact next step: commit REFACTOR, run the full verification suite, update handoff/project log, push and open PR

## Scope Completed

-

## Acceptance Evidence

-

## Out of Scope / Not Done

-

## Files Changed

-

## Decisions Made

Include any `SHOULD` you deviated from, and why.

-

## Verification Run

Exact commands, and their result.

-

## Contract Impact

- No contract changes / Updated `docs/CONTRACTS.md` §:

## Decision Board Impact

- No decision changes / Updated `docs/DECISION_BOARD.md` (`D-n`) and ADR:

## Shared-Write Modules Touched

`:core:database` may be modified by only one story at a time.

- None

## Project Log Entry

Appending an entry to `docs/PROJECT_LOG.md` is part of the Definition of Done.

- [ ] Entry appended

## Risks or Follow-ups

-

## Human Review Gate

Not applicable / Applies (say which gate from `AGENTS.md`):

Not applicable. The change is confined to `:feature:vehicle` mapper and test code and does not touch any gated path or gated topic.