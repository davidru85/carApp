# Agent Handoff - E2-06

## Story

`E2-06 - Local Owner Adoption - M`

## Ready Check

- Backlog story: `E2-06 - Local Owner Adoption - M` is explicit in `docs/BACKLOG.md` and is the next
  open Phase 2 story. The owner selected it ahead of `E2-04`, which is also the backlog order.
- Acceptance criteria reviewed: one transaction rewrites every `LOCAL_OWNER` row to the new UID and
  bumps `localRevision`; existing `localMutationSeq` values are preserved; outbox rows are inserted
  in the push dependency order of `docs/CONTRACTS.md §8` and then by `localMutationSeq ASC, id ASC`;
  every non-`SYNCED` row, `FAILED_POISONED` included, is reset to `PENDING` with its error context
  cleared and a snapshot enqueued; adoption `seq` values are monotonic and the first one is
  `(max pre-existing seq) + 1`; the operation is idempotent; a test starts from a populated
  `LOCAL_OWNER` database with vehicles, fuel entries and interleaved edits and asserts that nothing
  is lost and that the outbox order is deterministic; and adoption is triggered automatically when
  connectivity returns rather than only from a UI action.
- Dependencies checked: `E1-11` fixed the Vehicle outbox payload `entityType` and MUST precede this
  story; it is merged. `E2-01` supplied `OwnerContext` and `AuthOwnerContext`; `E2-02` supplied the
  Firebase Auth credential exchange; `E2-03` supplied `SessionStateHolder` and F-1 routing and
  merged on 2026-09-06 as `fe9ed55`, which is this branch's base. `E2-06` blocks `E3-04`.
- Decisions checked: `docs/DECISION_BOARD.md` reports no `Proposed` or `Pending` decision awaiting
  owner confirmation, so no decision blocks the story. The decisions that govern the work are
  `D-38` (`DatabaseMutations` is the sole transaction boundary for synchronized entity writes),
  `D-110` (explicit outbox entity-type tokens in production code), `D-88` (`SyncController` stays
  staged until `E3-03`) and `D-105` (continuous progress documentation).
- Normative sections reviewed: `docs/CONTRACTS.md` §7 (`syncState` machine and its invariants), §8
  (outbox contract, coalescing, `localMutationSeq` and the four-group push dependency order), §11.2
  and §11.4 (local owner adoption), and §20.0 (`OwnerId` and `LOCAL_OWNER`);
  `docs/TECHNICAL_PLAN.md` §4 (dependency rule table), §5 (local data model and outbox DDL) and the
  required sync test 14; `docs/SPECIFICATION.md` §11 (TDD and the red/green/refactor workflow);
  and `AGENTS.md` Definition of Ready, Story Intake, Continuous Progress Documentation, Definition
  of Done and Human Review Gates.
- Expected verification: new `:core:database` common tests for the adoption transaction, ordering,
  idempotency and the populated-database scenario; new `:shared` common tests for the automatic
  trigger; the complete non-instrumented repository command of `AGENTS.md` §`Build and verify`; and
  `git diff --check`.
- Human review gates identified before work: **`E2-06` is a gated story** in the canonical gate list
  of `AGENTS.md`, and `core/database/**` is a gated path. The agent does not merge the pull request.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES); every repository
  artifact, branch, commit and pull-request field is technical English.
- TDD workflow: the owner requested local RED, GREEN and REFACTOR commits, then one push and one
  pull request. This replaces the default push-after-each-phase cadence for `E2-06` while preserving
  failing-test-first order and separate phase commits.

## In-Progress Checkpoint

### Intake and status realignment checkpoint (2026-09-06)

- Date: 2026-09-06. Branch and base: `story/E2-06-local-owner-adoption`, based on `main` at
  `fe9ed55`.
- Current phase and latest commit: intake, in the commit that contains this text. No product code
  has changed.
- Push and pull-request status: not pushed; no pull request exists.
- Completed since the previous checkpoint: the ready check above; and the realignment of the six
  documents that still described `E2-03` as awaiting review after pull request #54 merged
  (`AGENTS.md`, `README.md`, `docs/DEFINITION.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md` and
  a closure update in `docs/handoff-E2-03.md`), plus the project log entry recording the merge.
- Verification evidence and known failures: none yet; the story has not changed product code.
- Open decisions or blockers: none blocking the start. The design questions this story raises are
  being collected for the owner and will be recorded here as decisions before they are implemented.
- Exact next step: write the failing RED tests for the adoption transaction in `:core:database` and
  for the automatic connectivity trigger.

## Scope Completed

- Pending.

## Acceptance Evidence

- Pending.

## Out of Scope / Not Done

- Pending.

## Files Changed

- Pending.

## Decisions Made

Include any `SHOULD` you deviated from, and why.

- Pending.

## Verification Run

Exact commands, and their result.

- Pending.

## Contract Impact

- Pending.

## Decision Board Impact

- Pending.

## Shared-Write Modules Touched

`:core:database` may be modified by only one story at a time.

- `:core:database`. This story declares it, as `docs/CONTRIBUTING.md` requires. No other story is in
  flight.

## Project Log Entry

Appending an entry to `docs/PROJECT_LOG.md` is part of the Definition of Done.

- [ ] Entry appended

## Risks or Follow-ups

- Pending.

## Human Review Gate

Applies. `E2-06` is a gated story in the `AGENTS.md` canonical gate list, and `core/database/**` is
a gated path. The agent does not merge the pull request.
