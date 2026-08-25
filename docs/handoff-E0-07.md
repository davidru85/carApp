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
- [x] Decisions checked — the active decisions through D-59 that govern this story are `Accepted`;
  D-2 is superseded by D-58; D-53 records the
  owner's 2026-08-24 selection of separate Firebase-provisioned and restricted platform keys, and
  D-56 records the owner's 2026-08-25 correction of the test-factory module boundary; D-58 moves
  the iOS framework composition root out of `:shared`; and D-59 fixes the explicit `AppProviders`
  port selected by the owner. No
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
- D-55 stages the final module and public-contract topology in E0-07, limits real product behavior
  to the Vehicle walking-skeleton slice and reserves all remaining behavior for its owning stories.
- D-56 places `testAppGraphDependencies(...)` in `:shared:testing`, keeps `AppGraphDependencies`
  in `:shared` and preserves `:core:testing` as generic test support.
- D-57 pins Google Services Gradle plugin 4.5.0 for Android's debug-only Firebase configuration.
- D-58 makes `:composition:ios` the sole owner of the `Shared` framework and Swift factory while
  `:shared` remains provider-free.
- D-59 defines `AppProviders` as explicit typed properties for every graph dependency except
  `isDebugBuild`, which remains owned by `buildAppGraph`.
- TDD order exemption used for architecture-rule fixtures, exactly as permitted by
  `docs/SPECIFICATION.md §11`: the fixtures and their checker implementation were completed in the
  architecture RED/GREEN cycle, and each forbidden edge has an executable failing fixture.
- Coverage tests for the staged `:core:model` and `:core:sync` value models were added after their
  implementation, using the explicit coverage-test exemption in `docs/SPECIFICATION.md §11`; no
  production behavior changed and neither coverage threshold was lowered or excluded.

## Verification Run

- Pending implementation.

## Contract Impact

- Pending implementation assessment.

## Decision Board Impact

- Added D-53 through D-59 and ADR-0054 through ADR-0060 before implementation.

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
