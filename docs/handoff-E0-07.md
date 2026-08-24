# Agent Handoff - E0-07

## Story

`E0-07 - Walking Skeleton - L` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — E0-07 builds the single native-UI-to-SQLDelight-to-Firebase
  vehicle slice and validates the Swift-facing application graph.
- [x] Acceptance criteria reviewed — real anonymous authentication; a complete contract-valid
  vehicle backed up from one platform and restored on a clean second device; disabled Firestore
  persistence; direct SPM; generated Objective-C golden header; app-graph fake parity; protected
  multiplatform CI.
- [x] Dependencies checked — E0-01 through E0-06, E0-08, E1-01, E3-06 and E3-01 are merged; the
  development Firebase project and `europe-west1` Firestore database exist. E0-07 owns creation
  and restriction of the two missing debug app registrations before configuration files land.
- [x] Decisions checked — D-0 through D-53 that govern this story are `Accepted`; D-53 records the
  owner's 2026-08-24 selection of separate Firebase-provisioned and restricted platform keys. No
  `Proposed` or `Pending` decision blocks the story.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §2`, `§7 F-1` and `F-2`, `§8`, `§9`,
  `§10`, `§11`; `docs/CONTRACTS.md §1.1`, `§3`, `§11`, `§14`, `§15`, `§16`, `§18`, `§20.3`,
  `§20.3.2`, `§20.7`, `§20.8` and `§20.10`; `docs/TECHNICAL_PLAN.md §3`–`§5`, `§10`, `§12`;
  `docs/SECURITY.md`; `docs/identifiers.md`; `docs/versions-matrix.md`; and the E1-01, E3-06 and
  E3-01 handoffs.
- [x] Expected verification identified — focused RED/GREEN shared behavior tests; graph
  construction and provider integration tests; Android host and `iosSimulatorArm64` tests;
  provider-free graph proof; generated-header diff; Android and iOS builds; real two-device
  Android-to-iOS backup/restore evidence; and the complete local and protected CI commands.
- [x] Human review gates identified before work — E0-07 is a gated story; Firebase/auth/backend,
  module boundaries and Swift ABI are gated topics; `docs/SPECIFICATION.md`,
  `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/adr/**`, `docs/identifiers.md`,
  `docs/versions-matrix.md`, `core/auth/**`, `core/sync/**` and `core/database/**` are gated paths.
  Owner review is required before merge.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Pending implementation.

## Acceptance Evidence

- Pending implementation.

## Out of Scope / Not Done

- The complete vehicle feature remains owned by E1-02 and E1-03.
- The complete backup/recovery engine and `RemoteSyncSource` remain owned by E3-02 and E3-03.
- Permanent Google/Apple sign-in, local-owner adoption and account deletion remain later stories.

## Files Changed

- Pending implementation.

## Decisions Made

- D-53 selects separate Android and iOS debug app registrations and restricted platform keys.
- D-54 isolates both development configuration files to debug builds and makes release builds fail
  closed until reviewed production configuration exists.

## Verification Run

- Pending implementation.

## Contract Impact

- Pending implementation assessment.

## Decision Board Impact

- Added D-53, D-54, ADR-0054 and ADR-0055 before implementation.

## Shared-Write Modules Touched

- None at intake. Any later `:core:database` change requires declaring exclusive ownership here.

## Project Log Entry

- [ ] Entry appended.

## Risks or Follow-ups

- E0-07 is the Phase 1 opening gate and cannot merge without the human-reviewed real-device
  backup/restore and Swift ABI evidence.

## Human Review Gate

- Applies: E0-07 and every gated path/topic listed in the Ready Check. The owner must review and
  merge this pull request.
