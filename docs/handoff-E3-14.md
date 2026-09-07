# Agent Handoff

## Story

`E3-14 - Orphan Cleanup Ticket Issuance Hardening - M`

## Ready Check

- Backlog story: `E3-14 - Orphan Cleanup Ticket Issuance Hardening - M`, added to `docs/BACKLOG.md`
  in this story from the two post-merge findings of the `E3-11` review of pull request #60. The
  interleaving that `E3-14` deliberately does not close is `E3-15`, which is **not Ready** because
  it depends on the `Proposed` decision `D-149`.
- Acceptance criteria reviewed: the five criteria of `E3-14`. The issuance/deletion interleaving is
  explicitly out of this story's scope and is escalated instead of worked around.
- Dependencies checked: `E3-10` (PR #58) and `E3-11` (PR #60) are merged into `main` at `7a79fab`,
  which is this branch's base. Pull requests #61 and #62 are open and untouched by this work.
- Decisions checked: `D-23`, `D-63`, `D-128`, `D-129`, `D-131`, `D-132`, `D-134`, `D-135`, `D-137`,
  `D-138`, `D-141`, `D-142` and `D-143` are `Accepted` and govern this code. No decision this story
  depends on is `Proposed` or `Pending` at intake; the story itself introduces `D-148` (`Accepted`)
  and `D-149` (`Proposed`, owner decision, needed by `E3-15`).
- Normative sections reviewed: `docs/CONTRACTS.md` §11.3, §11.5 (the deletion order, the ticket
  issuer, the consumption callable and their redaction rules), §16 (the internal server-only
  collection registry), §17 (logging and privacy), §18; `docs/SPECIFICATION.md` §3.2 (the closed
  Cloud Functions scope), §10, §12; `docs/TECHNICAL_PLAN.md` §2, §13 (`TD-01`, the sole 1st gen
  exception); `docs/DECISION_BOARD.md` (the Firebase decoupling rule and the awaiting-confirmation
  section); `docs/SECURITY.md`.
- Expected verification: the complete Functions unit suite, the Firestore emulator integration
  suite, the Firestore rules tests, the dependency audit, `contractCheck` with the build-logic
  fixture tests, the complete required Gradle command, the Functions and Firestore indexes
  dry-run, `git diff --check`, and the ten protected checks on the pull request.
- Human review gates identified before work: the story is marked "Human review required". Gated
  paths touched: `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/adr/**`,
  `docs/SPECIFICATION.md`, `AGENTS.md`. Gated topics: authentication, the remote backend, and
  logging and privacy rules.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every artifact it
  produces is in technical English.

## In-Progress Checkpoint

- Date: 2026-09-08
- Branch and base: `story/E3-14-orphan-ticket-issuance-hardening`, based on `main` at `7a79fab`.
- Current phase and latest commit: story intake recorded; no implementation commit yet.
- Push and pull-request status: not pushed; no pull request.
- Completed since the previous checkpoint: both findings reproduced by reading the merged code, the
  full issuance/deletion interleaving analysed, and the two follow-up stories written. The analysis
  established that finding 1 splits into a part that is forced and a part that requires an owner
  decision, which is why it is delivered as `E3-14` plus the escalated `E3-15`.
- Verification evidence and known failures: none yet; the base commit is the merged `main`.
- Open decisions or blockers: `D-149` is the owner decision that `E3-15` needs. It is recorded as
  `Proposed` with its options in ADR-0150 and is listed in the awaiting-confirmation table of
  `docs/DECISION_BOARD.md`.
- Exact next step: RED phase for finding 2, the sanitized trigger rejection.

## Scope Completed

-

## Acceptance Evidence

-

## Out of Scope / Not Done

-

## Files Changed

-

## Decisions Made

-

## Verification Run

-

## Contract Impact

-

## Decision Board Impact

-

## Shared-Write Modules Touched

-

## Project Log Entry

- [ ] Entry appended

## Risks or Follow-ups

-

## Human Review Gate

Applies. The story is marked "Human review required" in `docs/BACKLOG.md`, and it touches the gated
authentication, remote backend, and logging and privacy topics.
