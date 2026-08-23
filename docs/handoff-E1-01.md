# Agent Handoff - E1-01

## Story

`E1-01 - :core:database - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — `E1-01`, the next story after Phase 0.
- [x] Acceptance criteria reviewed — multiplatform persistence; schema v1 and every required table;
  synchronized control-column constraints; nullable `serverUpdatedAt`; shared mutation sequence;
  exact outbox DDL and coalescing; database-owned odometer invariants; committed SQL schema and
  migration verification; observable `Flow` queries and suspending one-shot operations.
- [x] Dependencies checked — Phase 0 is complete; `E0-01` through `E0-06` and `E0-08` are merged;
  `E0-07` depends on this story and therefore has not started.
- [x] Decisions checked — `D-36` was accepted by the owner on 2026-08-22 and supersedes `D-1`;
  `D-37` was accepted on 2026-08-23 and limits Kotlin/Native iOS targets to ARM64;
  `D-38` was accepted on 2026-08-24 and selects the transaction facade for entity mutations;
  no `Proposed` or `Pending` decision remains.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §2`, `§3.1`, `§6`, `§8`, `§9`, `§11`;
  `docs/CONTRACTS.md §2`–`§5`, `§7`–`§9`, `§15.1`, `§18`, `§20.3.2`;
  `docs/DECISION_BOARD.md` (`D-1`, `D-36`); `docs/TECHNICAL_PLAN.md §3`, `§4`, `§6`, `§12`;
  `docs/versions-matrix.md`; [ADR-0037](adr/0037-local-database-sqldelight-androidx-sqlite.md).
- [x] Expected verification identified — focused `:core:database` Android host and
  `iosSimulatorArm64` tests; SQLDelight migration verification; architecture fixtures; and the
  complete repository command from `AGENTS.md`.
- [x] Human review gates identified before work — gated paths `docs/SPECIFICATION.md`,
  `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/adr/**`, `docs/versions-matrix.md` and
  `core/database/**`; gated topics technical stack, pinned versions and module boundaries.
- [x] Rule 0 acknowledged — owner conversation remains Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- In progress.

## Acceptance Evidence

- Pending implementation and verification.

## Out of Scope / Not Done

- `E0-07` and the remaining Phase 1 stories are not part of this pull request.

## Files Changed

- Pending final handoff.

## Decisions Made

- `D-36` supersedes `D-1`; see [ADR-0037](adr/0037-local-database-sqldelight-androidx-sqlite.md).
- `D-37` removes the already-unlinked `iosX64` simulator target because the complete accepted
  bundled-SQLite dependency set publishes only ARM64 iOS variants; see
  [ADR-0038](adr/0038-supported-ios-targets-are-arm64.md).
- `D-38` routes synchronized entity writes through `DatabaseMutations` and rejects direct
  generated entity-mutation calls outside `:core:database`; see
  [ADR-0039](adr/0039-database-mutations-use-transaction-facade.md).
- The TDD order exemption for SQLDelight schemas and migrations will be used as permitted by
  `docs/SPECIFICATION.md §11`; product behaviours around sequence allocation, coalescing and
  read-model recomputation remain subject to RED/GREEN/refactoring commits.

## Verification Run

- Pending.

## Contract Impact

- Updated `docs/CONTRACTS.md` terminology and database-type ownership for `D-36`; product data and
  behavioural contracts remain unchanged.

## Decision Board Impact

- Updated `docs/DECISION_BOARD.md`: `D-1` is `Superseded`; `D-36`, `D-37` and `D-38` are
  `Accepted` with ADR-0037, ADR-0038 and ADR-0039 respectively.

## Shared-Write Modules Touched

- `:core:database`; no other story is modifying it.

## Project Log Entry

- [ ] Entry appended at story completion.

## Risks or Follow-ups

- Pending final handoff.

## Human Review Gate

- Applies: gated paths and gated topics listed in the Ready Check.
