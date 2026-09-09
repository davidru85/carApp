# Agent Handoff — E2-05 Sign-Out and Account Deletion F-5

## Summary

- Implemented on pull request #65 and awaiting the owner's gated review. Sign-out is restricted to a
  permanent session, the three `DELETING` operations are separated, the departure runs as a
  resumable state machine that never reports success while local data survives, and a stale login is
  recovered through a host-driven re-authentication intent.

## Story

`E2-05 - Sign-Out and Account Deletion F-5 - M`

## Ready Check

- [x] Backlog story is explicit: `docs/BACKLOG.md` E2-05.
- [x] Acceptance criteria reviewed: sign-out only for permanent users; anonymous "delete local data" with two-step confirmation; `ValidationWarning.PendingSyncBeforeSignOut(pendingCount)` + `Confirmation.DiscardPendingChanges`; account deletion order of `docs/CONTRACTS.md §11.5`; outbox dropped only after server success; `AuthError.AccountDeletionRemoteFailed` preserves local data; `user_settings` deleted and recreated from defaults; deletion reachable from settings; Session state holders stay in `:shared` (owner decision D-155).
- [x] Dependencies checked: `E2-02`, `E3-10` and `E3-11` are merged on `main`. `E2-04` merged through pull request #64.
- [x] Decisions checked: D-23, D-63, D-85/D-97, D-105, D-155. No `Proposed`/`Pending` decision blocks the work.
- [x] Normative sections reviewed: `docs/SPECIFICATION.md` §3.1, §7 F-5, §11, §12; `docs/CONTRACTS.md` §11.5, §20.2, §20.10; `docs/TECHNICAL_PLAN.md` §4, §6; `docs/DECISION_BOARD.md`; `AGENTS.md`.
- [x] Expected verification: focused RED/GREEN tests on `:shared` and `:core:database`; the complete non-instrumented CI command; Objective-C golden header parity.
- [x] Human review gates identified before work: gated paths `core/database/**`, `core/auth/**`; gated topics authentication, module boundaries/dependency rules, Swift-facing API surface.
- [x] Rule 0 acknowledged: owner conversation is Spanish (es-ES); repository artifacts are technical English.

## In-Progress Checkpoint

- Date: 2026-09-10
- Branch and base: `story/E2-05-sign-out-and-account-deletion` from `origin/main` at `b521bda`
- Current phase and latest commit: RED. This commit is the executable specification.
- Push and pull-request status: pull request #65 is open and under the owner's gated review. This
  branch history is being rebuilt with the owner's explicit approval, because the previous RED commit
  `6e07e6a` did not compile and so was not the executable behavioural RED `AGENTS.md` requires.
- Completed since the previous checkpoint: the RED specification for sign-out eligibility, the three
  `DELETING` operations, the departure lifecycle, recent-login recovery, typed failures and warning
  data, and a complete atomic local clear. The new production declarations are behavior-free seams.
- Verification evidence and known failures: EXPECTED RED. The sources compile and the tests fail for
  the missing behavior — 4 of 5 `LocalDataClearDatabaseAccessTest` tests and 17 of 23
  `SessionDepartureTest` tests. The rest assert that an ineligible or unsolicited intent does
  nothing, which a behavior-free seam already satisfies.
- Open decisions or blockers: none blocking.
- Exact next step: GREEN, then REFACTOR, then republish and update pull request #65.

## Scope Completed

- Ready Check, branch creation and the RED phase.

## Acceptance Evidence

- Pending; the RED specification names the behaviours that will carry it.

## Out of Scope / Not Done

- The Settings UI surface (E4-01) that renders the sign-out and deletion controls.
- The native credential acquisition for re-authentication; the platform picker is host UI (E4-01).
- The D-85 / ADR-0086 move of Session presentation into `:feature:session` (superseded by D-155).

## Human Review Gate

- Applies: gated paths `core/database/**`, `core/auth/**`; gated topics authentication, module
  boundaries/dependency rules, Swift-facing API surface.

## Risks or Follow-ups

- **For the owner to confirm:** anonymous "delete local data" now ends the provider session after
  clearing. F-5 says "for an anonymous session there is no sign-out", which is about the action
  offered to the owner rather than the internal provider call, and without it the deletion is not
  effective across a restart. If the owner reads that sentence as forbidding the internal call too,
  the alternative is to leave the anonymous session in place and accept that a recreated holder
  returns to `ANONYMOUS` with empty data. The abandoned identity becomes an orphan handled by the
  existing `E3-11` cleanup; no new server operation was introduced.
- A local clear that fails after a successful remote step leaves the account deleted remotely while
  local data survives. That state is observable as `UNKNOWN` with the persistence error, the request
  is retained, and a retry repeats only the clear; but nothing forces the owner to retry within the
  session, and a process death at that point loses the retained request. Local data would then
  remain for an account that no longer exists remotely.
- `E1-14` and `E1-17` remain the documented pre-existing flakes that make a red `shared-tests` or
  `ios-simulator-build` job ambiguous.
