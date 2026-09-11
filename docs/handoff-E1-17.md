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
- Current phase and latest commit: REFACTOR complete. RED is `7b7ff63`; GREEN is `7d40b9e`; the
  REFACTOR commit is the next action.
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
- REFACTOR evidence: the retry fixture now covers both `welcome_guest` and `add_vehicle`; diagnostic
  tests assert the deadline value as well as its message; the timeout message is a property; and CI
  measurements use a stable three-decimal format. Ten consecutive local runs of the affected
  `testVehicleSwipeDeleteShowsConfirmationDialog` passed. Onboarding took 2.773 to 3.415 seconds,
  with one guest and one add-vehicle tap in every run. The complete non-instrumented repository
  command passed: 638 actionable tasks, 39 executed and 599 up-to-date; `contractCheck` reports 168
  aligned decisions and two unrelated Pending decisions.
- Open decisions or blockers: none.
- Exact next step: commit REFACTOR, push the three TDD commits, create the pull request, observe and
  repeat the final `ios-simulator-build` CI job, then record publication and CI evidence.

## Scope Completed

- Replaced one-shot onboarding latches with a test-only state machine that keeps requesting any
  visible and hittable `welcome_guest` or `add_vehicle` affordance until the UI advances.
- Split the old 30-second aggregate deadline into a 10-second initial-affordance bound, a 60-second
  guest-session bound and a 10-second vehicle-form bound. A transition starts the destination
  step's complete budget rather than consuming whatever remains of the previous step.
- Added step-specific failures for an absent affordance, a guest session that never reaches the
  vehicle list and vehicle creation that never opens.
- Added test-only elapsed-time and tap-attempt output for direct CI measurement.
- Added deterministic policy tests for both retry branches and both named timeout branches.

## Acceptance Evidence

- A still-available `welcome_guest` returns `tapGuest` on consecutive observations; a still-available
  `add_vehicle` returns `tapAddVehicle` on consecutive observations. The RED fixture returned `wait`
  on the second guest observation before GREEN.
- Guest-session and vehicle-creation fixtures assert different exact messages and their 60-second and
  10-second bounds. Both returned the old generic message in RED.
- Four pre-fix green CI runs measured the complete affected test at 27.021, 33.219, 21.143 and
  25.836 seconds. The slowest whole-test result, 33.219 seconds, necessarily exceeds its onboarding
  segment; 60 seconds therefore gives at least 26.781 seconds / 80.6% margin over that conservative
  CI upper bound. Final direct onboarding measurements are pending on this branch's CI run.
- Ten consecutive local affected-test runs passed after REFACTOR, with directly logged onboarding
  times of 2.773 to 3.415 seconds.
- All changes remain under `iosApp/UITests` plus documentation; production code, shared state,
  contracts, Swift-facing ABI, schema, migrations and decisions are unchanged.
- Repeated final-head CI evidence is pending publication.

## Out of Scope / Not Done

- Product code, shared state holders, contracts, Swift-facing ABI, schema, migrations and decision
  records are outside this test-infrastructure story.
- An unrelated local failure at the existing partial-refuel badge assertion is not widened into
  E1-17. The helper reached vehicle creation successfully before that assertion, and the changed
  lines do not touch the fuel flow.

## Files Changed

- `iosApp/UITests/VehicleAndFuelFlowUITests.swift`: retry policy, per-step bounds, diagnostics,
  measurement output and deterministic regression fixtures.
- `docs/handoff-E1-17.md`: live intake, TDD and verification evidence.

## Decisions Made

- No owner decision and no new project-level technical decision are required. The story explicitly
  delegates its test-local timeout mechanism to the implementation and requires its measurement and
  justification here.
- Kept the real Firebase anonymous sign-in path rather than adding a product seam. This preserves the
  end-to-end value of `ios-simulator-build` and stays inside the mandatory `iosApp/UITests` scope.
- Used a 60-second guest-session bound because the slowest of four successful pre-fix CI executions
  took 33.219 seconds for the entire affected test. The 80.6% margin is conservative because vehicle
  editing and deletion are also included in that measured time. Initial affordance and local form
  presentation retain focused 10-second bounds so a UI failure remains fast and diagnostic.
- Retry cadence is 0.5 seconds: it allows recovery from a swallowed tap without a tight polling loop.

## Verification Run

- RED:
  `xcodebuild -project iosApp/carApp.xcodeproj -scheme carApp -sdk iphonesimulator
  -destination 'id=56F1AD0C-42E0-499C-9469-DC91CDD8AD21' test` with the three E1-17 policy tests
  selected. Result: 3 tests executed, 3 intended assertion failures, `TEST FAILED`.
- GREEN: the same three focused tests after implementation. Result: 3 executed, 0 failures,
  `TEST SUCCEEDED`.
- Affected test after GREEN: one passing run in 20.016 seconds; onboarding reached the form in 3.248
  seconds after one guest and one add-vehicle tap.
- REFACTOR focused tests: 3 executed, 0 failures, `TEST SUCCEEDED`.
- REFACTOR stability: ten consecutive focused executions of
  `testVehicleSwipeDeleteShowsConfirmationDialog`; 10 passed, 0 failed, onboarding 2.773-3.415
  seconds.
- Complete non-instrumented command from `AGENTS.md`: `BUILD SUCCESSFUL` in 6 seconds, 638 actionable
  tasks (39 executed, 599 up-to-date), all contract, architecture, lint, coverage, Android build,
  Android-host and eligible Native checks passed.
- Complete local iOS suite: 42 unit tests passed. All three E1-17 helper calls reached vehicle
  creation in 3.279-3.381 seconds. One of six pre-existing product-flow UI tests then failed at the
  unchanged partial-refuel badge assertion (line 198 after this story's added test code), and the
  isolated rerun reproduced it after E1-17 completed in 3.510 seconds. Two separate attempts also
  failed before test execution with simulator `Application failed preflight checks` / `Busy`.
  Final clean-environment adjudication belongs to the required `ios-simulator-build` CI job.
- `git diff --check`: pending final documentation.

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
- The local partial-refuel badge failure is outside the changed helper and outside E1-17 scope. If it
  reproduces on CI, it requires classification rather than being absorbed into this story.
- Local CoreSimulator intermittently refused to start the XCUITest runner as `Busy`; rebooting the
  targeted simulator cleared the infrastructure state for the focused E1-17 runs.

## Human Review Gate

- Not applicable beyond the normal pull-request review and required checks. The agent will not merge
  the pull request.
