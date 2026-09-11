# Agent Handoff - E1-17

## Story

`E1-17 - iOS Onboarding UI Test Flake in ios-simulator-build - S`

## Ready Check

- Backlog story: `E1-17` is explicit in `docs/BACKLOG.md` and confines the fix to
  `iosApp/UITests`.
- Acceptance criteria reviewed: retry any onboarding affordance that remains visible and hittable;
  diagnose the unreached step; justify the network-bearing deadline from measured CI evidence; keep
  production code, shared state, contracts, ABI and decisions unchanged; and record repeated CI
  passes of the affected test.
- Dependencies checked: E1-09 delivered the iOS Vehicle/Fuel UI test target, E2-03 delivered the F-1
  onboarding route used by the helper, and E1-14 merged through pull request #66 before this branch
  was created. E1-15 and E1-16 are independent product-UI follow-ups and are not prerequisites.
- Decisions checked: D-74, D-84, D-102 and D-105 are Accepted. The only Pending decisions are D-149
  and D-150, needed by E3-15 and E3-16 respectively; E1-17 does not depend on them. No new library,
  service, identifier, scope or technical-stack choice is required.
- Normative sections reviewed: `docs/SPECIFICATION.md` sections 7 F-1, 11 and 12;
  `docs/CONTRACTS.md` sections 18 and 20.10; `docs/TECHNICAL_PLAN.md` sections 2, 4 and 12;
  `AGENTS.md` Definition of Ready, Story Intake, Continuous Progress Documentation, Definition of
  Done and Human Review Gates; `docs/CONTRIBUTING.md` branch, commit and pull-request rules.
- Expected verification: focused `carAppUITests` RED/GREEN execution on an iOS simulator; repeated
  focused execution; the complete iOS unit/UI suite; the complete non-instrumented command from
  `AGENTS.md`; `git diff --check`; and repeated `ios-simulator-build` CI executions on the final
  commit.
- Human review gates identified before work: `AGENTS.md` does not list E1-17, `iosApp/UITests` or
  test-only timing as a gated story, path or topic. The normal pull-request review and ten required
  checks still apply; the agent will not merge the pull request.
- Rule 0 acknowledged: owner conversation is Spanish (Spain); every repository artifact, branch,
  commit and pull-request description is technical English.

## In-Progress Checkpoint

- Date: 2026-09-11.
- Branch and base: `story/E1-17-ios-onboarding-ui-test-flake`, based on synchronized `main` at
  `4207b05` after pull request #66 merged.
- Current phase and latest commit: GREEN complete. RED is `7b7ff63`; the GREEN commit is the next
  action.
- Push and pull-request status: branch is local; nothing pushed; no pull request exists.
- Completed since the previous checkpoint: Ready Check completed. Four consecutive successful CI
  executions of the affected test on the pre-fix helper were measured at 27.021, 33.219, 21.143 and
  25.836 seconds for the entire test. The slowest whole-test duration, 33.219 seconds, is an upper
  bound for its onboarding segment and establishes the baseline for the new network-bearing bound.
  A test-only `OnboardingWaitState` now drives the helper policy so its defect can be exercised
  without relying on a tap being lost by chance. GREEN removed the one-shot latches: every visible,
  hittable onboarding affordance is retried at 0.5-second observation intervals. Entering the guest
  step starts a 60-second deadline; entering vehicle creation starts a separate 10-second deadline;
  the failure message is owned by that step. The helper prints elapsed time and tap-attempt counts so
  the final CI runs provide direct measurements.
- Verification evidence and known failures: the focused RED run compiled and executed three tests;
  all three failed on the intended assertions. A second available simulator reported retry action
  `wait` instead of `tapGuest`, the generic timeout instead of the guest-session diagnostic, and the
  same generic timeout instead of the vehicle-creation diagnostic. An intervening attempt on the
  first simulator failed before test execution with `Application failed preflight checks` / `Busy`;
  it is recorded as simulator infrastructure noise and is not RED evidence. In GREEN, all three
  focused tests passed. The first isolated end-to-end run reached vehicle creation in 1.245 seconds
  but then failed at the unrelated swipe-delete assertion because a fresh simulator took the
  mandatory first-vehicle route, whose save opens detail instead of returning to a list row. Repeating
  with the test's normal non-empty-list precondition reached vehicle creation in 3.248 seconds after
  one `welcome_guest` and one `add_vehicle` tap, then passed the complete swipe-delete test.
- Open decisions or blockers: none.
- Exact next step: commit GREEN, then refactor the policy and tests for clarity, run repeated local
  and complete verification, and finalize the handoff and project records.

## Scope Completed

- In progress.

## Acceptance Evidence

- Pending.

## Out of Scope / Not Done

- Product code, shared state holders, contracts, Swift-facing ABI, schema, migrations and decision
  records are outside this test-infrastructure story.

## Files Changed

- Pending.

## Decisions Made

- No owner decision or new project-level technical decision is required at intake.

## Verification Run

- Pending.

## Contract Impact

- No contract changes.

## Decision Board Impact

- No decision changes.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [ ] Entry appended

## Risks or Follow-ups

- The final CI repetition count cannot be claimed until the final branch head has run on GitHub.

## Human Review Gate

- Not applicable beyond the normal pull-request review and required checks. The agent will not merge
  the pull request.
