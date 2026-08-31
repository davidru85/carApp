# Agent Handoff - E1-08

## Story

`E1-08 - Android UI: Fuel Entries - L` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — implement the Android Fuel Entry list, create/edit form,
  segment and average consumption presentation, two-step odometer confirmation and the production
  Fuel Entry presentation holders in `:feature:fuel`.
- [x] Acceptance criteria reviewed — F-3 defaults, live R-2 derivation through `MoneyInput`,
  warning then confirmation, accessible no-consumption reasons, independent missed-entry and
  inconsistent-odometer indicators on every row, the empty-consumption state and byte-exact Swift
  ABI preservation.
- [x] Dependencies checked — E1-04 through E1-06 provide Fuel Entry domain, consumption and local
  data; E1-07 provides Compose Navigation, instrumented Android UI tests, the Kotlin/Swift graph
  split and keyed holder release. E1-10 and E3-03 remain later stories and are not implemented or
  partially absorbed here.
- [x] Decisions checked — D-8, D-20, D-55, D-77 through D-88 and D-90 through D-97 apply and are
  `Accepted`. D-92 through D-97 record the owner-approved E1-08 projection, money-resolution,
  defaults, staged sync, calendar-day and iOS export choices. No `Proposed` or `Pending` decision
  blocks E1-08.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §3`, `§5.2`, `§6` R-1 through R-3,
  `§7` F-3, `§8.3`, `§8.4`, `§11`; `docs/CONTRACTS.md §2` through `§6`, `§12` through `§15`,
  `§20.4` through `§20.6` and `§20.10`; `docs/TECHNICAL_PLAN.md §3`, `§4`, `§10`, `§12`; the
  Android design references indexed by `docs/DESIGN.md §4`; and ADR-0093 through ADR-0098.
- [x] Expected verification identified — focused `:core:model`, `:feature:fuel` and `:shared`
  Android-host and iOS simulator tests; Android compilation and API 36 instrumented tests; feature
  lint, detekt and Kover; architecture fixtures and checks; contract checks; Shared framework
  linking with an exact empty golden-header diff; the complete repository command from
  `AGENTS.md`; and `git diff --check`.
- [x] Human review gates identified before work — gated `docs/SPECIFICATION.md`,
  `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/adr/**`, module boundaries and the
  Swift-facing ABI. Owner review is required before merge; the agent MUST NOT merge the PR.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Pending RED, GREEN and REFACTOR implementation.

## Acceptance Evidence

- Pending implementation and verification.

## Out of Scope / Not Done

- E1-10 settings persistence and E3-03 synchronization engine behavior remain out of scope.

## Files Changed

- Pending implementation.

## Decisions Made

- D-92 through D-97 record the six owner-approved E1-08 technical choices.
- Native Compose host code uses the TDD-order exemption in `docs/SPECIFICATION.md §11`; it still
  requires instrumented UI tests. Shared presentation, domain and graph behavior remain test-first.
- The owner explicitly requires three ordered commits — RED, GREEN and REFACTOR — followed by one
  push and pull-request creation.

## Verification Run

- Baseline `./gradlew architectureCheck contractCheck` — successful before RED: 16 architecture
  rules and 92 decision/ADR mirrors passed with no unresolved decision.
- RED `./gradlew :feature:fuel:testAndroidHostTest :shared:testAndroidHostTest` — the test sources
  compiled and executed; the projection test failed because the row flags were dropped and six
  shared graph tests failed against the deterministic Fuel Entry shells. The one pre-existing
  fallback assertion passed. This is the expected behavioral RED state.
- RED `./gradlew ktlintCheck detekt architectureCheck contractCheck` — architecture and contracts
  passed with 98 decision/ADR mirrors; the first run identified only formatting in the new shared
  test, which was corrected before the RED commit.
- GREEN and REFACTOR evidence pending.

## Contract Impact

- Pending updates to Fuel Entry projection, presentation, platform-day and iOS export contracts.

## Decision Board Impact

- Added accepted D-92 through D-97 with ADR-0093 through ADR-0098 and identical mirrors.

## Shared-Write Modules Touched

- None. `:core:database` is not modified by E1-08.

## Project Log Entry

- [ ] Entry appended

## Risks or Follow-ups

- E1-10 must replace the locale-derived creation currency at the AppGraph composition point with
  the persisted settings source.
- E3-03 must replace D-95 constant `Idle` with the single final `SyncController.status` source.
- E1-09 must apply D-96's identical device-local calendar-day conversion on iOS.

## Human Review Gate

- Applies: gated normative documents, module boundaries and the Swift-facing ABI. The agent MUST
  NOT merge the pull request.
