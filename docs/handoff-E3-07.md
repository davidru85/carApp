# Agent Handoff

Fill in every section. This template is the canonical field list; `AGENTS.md` links here rather than restating it, and `.github/pull_request_template.md` is a superset of it.

## Story

`E3-07 - Tombstone Purge - S`

## Ready Check

- Backlog story: `E3-07 - Tombstone Purge - S` (`docs/BACKLOG.md` §`### E3-07`).
- Acceptance criteria reviewed: the four criteria of the backlog entry, verbatim: (1) a tombstone is
  purged only when `SYNCED`, older than 90 days by `serverUpdatedAt`, and with no outbox row; (2) purge
  runs at most once per app start, in one transaction; (3) a test proves a pending tombstone is never
  purged; (4) a fresh device pulling a tombstone for an entity it has never seen inserts it as a
  tombstone instead of failing.
- Dependencies checked: **none**. The story lists no `Depends on:` row in `docs/BACKLOG.md`, and the
  purge rule is already normative in `docs/CONTRACTS.md §8`.
- Decisions checked: **no open decision applies**. `D-149`, `D-150` and `D-173` gate `E3-15`, `E3-16`
  and `E3-18` only. The purge policy of `§8` is normative, so this story needs no new `D-` id and no
  ADR, and none is created.
- Normative sections reviewed: `docs/CONTRACTS.md §8` (tombstone purge, outbox, push dependency
  order), `§9.4` (tombstones arrive like any document), `§9.3` (ack deletes the outbox row and stamps
  `serverUpdatedAt`), `§7` (sync-state machine), `§3.1` (database-owned read-model invariants),
  `§20.0.1` (named constants), `§20.7` (`SyncController` surface), `§18` (contract-check assertions),
  `docs/SPECIFICATION.md §9` and `§3.2` (out-of-scope list), `docs/TECHNICAL_PLAN.md §3`, `§4` and
  `§9` (module rules, `:core:database` shared-write rule, required `:core:sync` tests).
- Expected verification: the complete non-instrumented command of `AGENTS.md` §Build and verify
  (`ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify
  :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest
  iosSimulatorArm64Test` with the four D-75 exclusions), `contractCheck --rerun-tasks` and
  `git diff --check`. No emulator or simulator is launched: the story is pure shared Kotlin.
- Human review gates identified before work: the story is not on the gated-story list, but
  `core/sync/**` and `core/database/**` are **gated paths** (`CODEOWNERS`), so the pull request
  requires the owner's review. The change touches no gated topic beyond those paths: no scope change,
  no stack or version change, no backend, no auth, no sync-algorithm or state-machine change, no money
  representation, no error-taxonomy, logging or Firestore-rule change, and no Swift-facing surface
  change.
- Rule 0 is acknowledged: chat replies for this story are in Spanish (es-ES) and every artifact it
  produces is in technical English.

## In-Progress Checkpoint

Update this section at every material state change and before yielding unfinished work (`D-105`).

- Date: 2026-09-26
- Branch and base: `story/E3-07-tombstone-purge`, branched from `origin/main` at `ea48ecc5`
  ("Merge pull request #77"). Work happens in a `git worktree` (`../carApp-e3-07`).
- Current phase and latest commit: intake; no commit yet.
- Push and pull-request status: nothing pushed; no pull request.
- Completed since the previous checkpoint: the ready check above; `core/database/.story-lock` does not
  exist, so no other in-flight story holds the shared-write module.
- Verification evidence and known failures: none yet.
- Open decisions or blockers: none.
- Exact next step: write the RED tests for the four criteria and commit them.

## Scope Completed

- (in progress)

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

- `:core:database`

## Project Log Entry

Appending an entry to `docs/PROJECT_LOG.md` is part of the Definition of Done.

- [ ] Entry appended

## Risks or Follow-ups

- 

## Human Review Gate

Not applicable / Applies (say which gate from `AGENTS.md`):
