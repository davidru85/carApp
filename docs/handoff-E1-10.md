# Agent Handoff - E1-10

## Story

`E1-10 - Settings Persistence - S` (`docs/BACKLOG.md`).

## Ready Check

- Backlog story: explicit. Implement the existing local `user_settings` schema surface,
  `SettingsRepository` and `UpdateSettingsCommand`, then replace the temporary D-94 Fuel Entry
  creation currency with persisted settings.
- Acceptance criteria reviewed:
  - First repository access creates exactly one row with a supported locale-derived two-decimal
    currency or `EUR`, fixed `KM` / `LITER` units and `analyticsEnabled = false`.
  - Explicit updates accept only supported two-decimal currencies.
  - Native Android and iOS currency APIs validate locale-derived currency minor units; a runtime
    factor other than `100` resolves to `EUR`.
  - Currency changes do not rewrite existing Fuel Entries.
  - Settings remain device-local, create no outbox row and have no remote document.
  - A command with both fields `null` returns `ValidationError.NoOp` and mutates nothing.
  - Destructive local-data deletion removes settings; the next repository access recreates the
    defaults with analytics disabled.
- Dependencies checked: E1-01 provides the schema-v1 `user_settings` table; E1-06 provides Fuel
  Entry persistence; E1-08 and E1-09 provide the shared form holder and both native hosts. All are
  merged into `main`. E1-11 and E1-12 do not own `:core:database`, and no pull request is open.
- Decisions checked: D-20, D-36, D-38, D-55, D-58, D-59, D-75, D-77, D-89 and D-94 are `Accepted`.
  `docs/DECISION_BOARD.md` contains no `Proposed` or `Pending` decision.
- Normative sections reviewed: `docs/SPECIFICATION.md` §3.1, §3.2, §5.2, §5.3, §11 and §12;
  `docs/CONTRACTS.md` §2, §3, §5, §6, §11.5, §12, §13, §15, §16, §16.1, §18, §20.0.1,
  §20.2, §20.3, §20.4 and §20.5; `docs/TECHNICAL_PLAN.md` §3, §4, §6, §7, §10 and §12;
  ADR-0095 / D-94.
- Expected verification: focused `:core:common`, `:core:database`, `:feature:session`, `:shared`,
  Android host and iOS simulator tests; Android and iOS host builds; provider decoupling; the
  Objective-C header golden; and the complete non-instrumented command from `AGENTS.md`.
- Human review gates identified before work: applies because `core/database/**` is a gated path.
  The agent MUST NOT merge the pull request.
- Rule 0 acknowledged: owner conversation is Spanish (Spain); every repository artifact, branch,
  commit and pull-request field is technical English.
- TDD workflow: the owner explicitly requested one local RED commit, one local GREEN commit and
  one local REFACTOR commit followed by a single push, superseding the default push-after-each-phase
  workflow for this story. The deviation will be retained under Decisions Made.

## Scope Completed

- Pending E1-10 implementation.

## Acceptance Evidence

- Pending E1-10 verification.

## Out of Scope / Not Done

- Settings UI remains owned by E4-01.
- Firebase Analytics integration remains owned by E3-09.
- Sign-out and account-deletion orchestration remain owned by E2-05 and E3-10.

## Files Changed

- Pending E1-10 implementation.

## Decisions Made

- Owner workflow instruction: use separate local RED, GREEN and REFACTOR commits, then perform one
  push before opening the pull request. This explicitly exempts E1-10 from the default per-phase
  push requirement in `docs/SPECIFICATION.md` §11; test-first ordering and commit separation remain
  mandatory.

## Verification Run

- Pending E1-10 verification.

## Contract Impact

- Pending final review; no normative contract change is currently expected.

## Decision Board Impact

- Pending final review of implementation decisions.

## Shared-Write Modules Touched

- `:core:database`. No concurrent story or open pull request owns this module at intake.

## Project Log Entry

- [ ] Entry appended

## Risks or Follow-ups

- Pending E1-10 implementation.

## Human Review Gate

- Applies: `core/database/**` is a gated path under `AGENTS.md`. The agent MUST NOT merge the pull
  request.
