# Agent Handoff - E1-17

## Story

`E1-17 - iOS Onboarding UI Test Flake in ios-simulator-build - S`

## Ready Check

- Backlog story: `E1-17` is explicit in `docs/BACKLOG.md` and confines the fix to
  `iosApp/UITests`.
- Acceptance criteria reviewed: retry any onboarding affordance that remains present and hittable;
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
  test-only timing as a gated story, path or topic. A Debug-only launch-environment seam to remove
  the network round trip **would** touch production code and is a gated topic; it is escalated
  below and MUST NOT be implemented without an owner decision in `docs/DECISION_BOARD.md`. The
  normal pull-request review and ten required checks still apply; the agent will not merge.
- Rule 0 acknowledged: owner conversation is Spanish (Spain); every repository artifact, branch,
  commit and pull-request description is technical English.

## In-Progress Checkpoint

- Date: 2026-09-11.
- Branch and base: `story/E1-17-ios-onboarding-ui-test-flake`, based on synchronized `main` at
  `4207b05` after pull request #66 merged.
- Current phase and latest commit: the CI-failure correction is complete. The prior
  RED/GREEN/REFACTOR sequence is at `7b7ff63`, `7d40b9e` and `984fbba`; the earlier stale-element
  cycle is `2f80204` and `66f4552`; the CI-failure correction is `20ceff6` (RED), `4ee4f1b`,
  `12d9308`, `bd481c5` and `32fcca4` (GREEN/REFACTOR/docs); and the follow-up hardening from the
  first corrected-head rerun is `f3e362e`.
- Push and pull-request status: pull request #67 is open. The implementation commits through
  `f3e362e` are pushed; the documentation update that records the 180-second cap and the hardened
  snapshot resolution is being pushed with this checkpoint.
- Completed since the previous checkpoint: corrected the three defects the owner identified in the
  CI failure on run `34641152156`, then hardened two more that the first corrected-head reruns
  exposed. The retry gate now requires `isEnabled`; each retry action is bound to its accessibility
  identifier; the wait budget is a single absolute cap that does not reset on step changes; each
  affordance is resolved through one guarded snapshot so a vanished element cannot crash the test;
  one shared helper drives all three UITest files; and the helper records the per-step distribution
  rather than an aggregate.
- Verification evidence and known failures: nine policy tests pass on a booted iOS 26.5 simulator;
  the affected end-to-end tests pass locally; the full non-instrumented command passes. Run
  `34641152156` is the evidence that the previous version was **not** stable: it failed
  `ios-simulator-build` with three tests from one root cause. A same-head rerun of the first
  corrected version then (i) exceeded a 120-second cap on the guest step on a rate-limited runner and
  (ii) exposed the element-resolution race; both were fixed and the cap is now 180 seconds.
- Open decisions or blockers: option (b), a Debug-only launch-environment seam to remove the real
  Firebase anonymous sign-in from the UI tests, requires an owner decision and is recorded under
  "Owner Decision Required" below. No other blocker.
- Exact next step: push this documentation update, then record consecutive green `ios-simulator-build`
  runs on the final head. The pull request is not merged.

## Owner Decision Required (escalated, not taken)

The evidence below shows the UI onboarding test cannot be made fully deterministic inside
`iosApp/UITests` alone, because the dominant cost and variance is the real Firebase anonymous
sign-in round trip. Two options exist and the story's acceptance criteria forbid the agent from
taking the second unilaterally:

- **(a) Keep the real network path and raise the bound — implemented now.** The absolute cap is 180
  seconds. The pre-fix guest step was measured at 62.090 seconds in run `34641152156`, and a
  same-head rerun of the corrected helper exceeded a 120-second cap on a rate-limited runner, so 180
  is the observed successful worst case plus a stated margin. This is in scope, keeps the end-to-end
  value of `ios-simulator-build`, and makes the flake rarer. It does not eliminate the network
  dependency: a slow enough provider response can still exceed 180 seconds, and a single UI test can
  now legitimately exceed one minute.
- **(b) Remove the network round trip with a Debug-only launch-environment seam — recommended, not
  implemented.** Following the `CARAPP_UI_TEST_FORCE_FIRST_VEHICLE` precedent
  (`OnboardingFlowUITests.swift`), a Debug-only seam could make the anonymous session resolve
  synchronously under UI test. This would remove the variance entirely and let the bound drop to a
  few seconds. It touches production code, which the E1-17 acceptance criteria explicitly route to
  the owner, so it MUST NOT be added without a new decision ID in `docs/DECISION_BOARD.md`, its ADR
  and the four mirroring documents.

Recommendation: adopt (b) after the owner records the decision. Until then, (a) is in force and the
180-second cap is justified by the measurements below.

## Scope Completed

- Replaced the one-shot onboarding latches with a shared retry policy that keeps requesting any
  affordance that is present, **enabled** and hittable until the UI advances.
- Extracted one shared `waitForOnboarding` helper in `iosApp/UITests/OnboardingWait.swift` and
  routed `VehicleAndFuelFlowUITests.swift` and `CarAppKeychainPersistenceUITests.swift` through it;
  `OnboardingFlowUITests.swift` also calls it. The original one-shot latches in
  `CarAppKeychainPersistenceUITests.swift` are gone.
- Bound the whole wait with a single absolute cap (`OnboardingWaitBudget.absoluteLimit`) that does
  not restart on a step change, so a reappearing affordance cannot extend total runtime.
- Kept the step monotonic (forward only) and made the tap action carry its target, so a retry is
  bound to an accessibility identifier rather than to whichever element happens to be first.
- Added a pure availability policy (`exists && isEnabled && isHittable`) and a pure retry policy,
  both exercised without driving the UI.
- Recorded per-step onboarding timings (`waitingForAffordance`, `startingGuestSession`,
  `openingVehicleCreation`) and tap counts for direct CI measurement.

## Acceptance Evidence

- **The pre-fix 60-second justification is retracted.** It was derived from whole-test durations
  (27.021 / 33.219 / 21.143 / 25.836 s) measured on runs where anonymous sign-in happened to be
  fast. An aggregate test duration is not an upper bound for the onboarding segment whose variance
  is the subject of this story, and the stated 80.6% margin was computed against the wrong quantity.
  CI run `34641152156` observed the **guest step alone** at 62.090 seconds, so that margin is
  negative in reality. The previous text claimed the margin was conservative; it was not.
- **Re-diagnosis of the two backlog candidate causes.** The pre-fix helper retried `welcome_guest`
  50 times across 62.090 seconds and still only reached the vehicle list afterwards. That is
  candidate (2), the Firebase anonymous sign-in round trip, dominating: retrying a tap cannot
  shorten a network wait. Candidate (1), the one-shot latch, was real — it is why an ineffective tap
  was unrecoverable — but it was not the binding constraint once retries existed. All 50 taps were
  also no-ops because `welcome_guest` reports `isHittable == true` while disabled, so
  `guestTapAttempts` was inflated and did not measure real tap opportunities.
- **Measured per-step distribution (CI):**

| Run | Destination | Total (s) | waitingForAffordance (s) | startingGuestSession (s) | openingVehicleCreation (s) | Guest attempts | Add attempts |
|---|---|---|---|---|---|---|---|
| 34639310063 | vehicle creation | 7.443 | — | — | — | 1 | 1 |
| 34639310063 | vehicle creation | 6.575 | — | — | — | 1 | 1 |
| 34641152156 (fail) | vehicle list (never reached) | 62.090 | — | — | — | 50 (no-op) | 1 |
| 34641152156 | vehicle creation | 5.204 | — | — | — | 1 | 1 |

  The pre-fix runs only recorded a total and tap counts, not per-step values; the per-step columns
  become available from this correction onward. The 62.090-second failure is the pre-fix CI
  observation of the guest step in isolation. A same-head rerun of the corrected helper then
  exceeded a 120-second cap on a rate-limited runner, which is why the implemented cap is 180
  seconds rather than 120.
- **Snapshot race hardened.** The corrected helper first resolved an affordance by reading `exists`,
  `isEnabled`, `isHittable` and `frame` one at a time. A rerun proved that a SwiftUI transition can
  remove the element between reads, making XCTest fail with "Failed to get matching snapshot". The
  helper now resolves each affordance through one throwing `snapshot()` inside a guarded scope, so a
  vanished element yields a waiting state instead of crashing the test.
- **Policy tests (RED then GREEN).** `testOnboardingAvailabilityExcludesDisabledAffordances`,
  `testOnboardingTapTargetsMapToTheirAccessibilityIdentifiers`,
  `testOnboardingStepNeverMovesBackwardsWhenAGuestReappears` and
  `testOnboardingAbsoluteCapDoesNotResetOnStepChange` each failed before the fix and pass after it,
  alongside five more policy tests. Nine tests, zero failures.
- **Local end-to-end.** Seven affected tests pass locally; onboarding reached vehicle creation in
  0.957–3.348 seconds with one guest and one add-vehicle tap attempt.
- All changes remain under `iosApp/UITests` plus the regenerated Xcode project and documentation;
  production code, shared state, contracts, Swift-facing ABI, schema, migrations and decisions are
  unchanged.
- Consecutive green `ios-simulator-build` runs on the final implementation head: 3 of 3 (run
  `34682336801` first execution plus two same-head reruns), all completing the full iOS suite with
  zero failures. Documentation commits after that head change no iOS source.

## Out of Scope / Not Done

- Product code, shared state holders, contracts, Swift-facing ABI, schema, migrations and decision
  records are outside this test-infrastructure story. Option (b) is therefore **not implemented**;
  it is escalated.
- The unrelated local partial-refuel badge assertion is not widened into E1-17.

## Files Changed

- `iosApp/UITests/OnboardingWait.swift` (new): shared availability/retry policy, absolute budget,
  snapshotted tap positions, per-step measurement and the `waitForOnboarding` function.
- `iosApp/UITests/OnboardingWaitPolicyTests.swift` (new): deterministic policy and wiring tests.
- `iosApp/UITests/VehicleAndFuelFlowUITests.swift`: routed through the shared helper; local policy
  types removed.
- `iosApp/UITests/OnboardingFlowUITests.swift`: first-vehicle launch routed through the helper.
- `iosApp/UITests/CarAppKeychainPersistenceUITests.swift`: one-shot latches replaced by the helper.
- `iosApp/carApp.xcodeproj/project.pbxproj`: regenerated by `generate-project.sh` for the two new
  files.
- `AGENTS.md`, `docs/BACKLOG.md`, `docs/PROJECT_LOG.md` and `docs/handoff-E1-17.md`: delivery status
  and evidence.

## Decisions Made

- No owner decision and no new project-level technical decision are required for option (a). Option
  (b) is escalated and not taken.
- Kept the real Firebase anonymous sign-in path rather than adding a product seam. This preserves
  the end-to-end value of `ios-simulator-build` and stays inside the mandatory `iosApp/UITests`
  scope; the cost is a network-dependent test whose cap must rest on CI measurement.
- The absolute cap is 180 seconds. The CI-observed guest-step worst case before the fix was
  62.090 s, and a same-head rerun of the corrected helper exceeded 120 s on a rate-limited runner;
  180 s is that observed worst case plus a stated margin. It replaces the retracted 60-second
  whole-test justification. The cap is deliberately a single interval that does not reset on step
  changes.
- The step never moves backwards, and a tap action carries its target, so a reappearing affordance
  cannot reset the budget or be wired to the wrong control.
- Retry cadence remains 0.5 seconds.

## Verification Run

- Policy RED: `xcodebuild ... -only-testing:carAppUITests/OnboardingWaitPolicyTests test` — four
  intended failures (`isEnabled`, identifier mapping, step monotonicity, absolute cap).
- Policy GREEN: the same command — 9 executed, 0 failures, `TEST SUCCEEDED`.
- Affected end-to-end GREEN (local): `-only-testing:carAppUITests/OnboardingFlowUITests
  -only-testing:carAppUITests/VehicleAndFuelFlowUITests test` — 6 executed, 0 failures, with
  onboarding measured per step.
- Same-head CI reruns of the first corrected version (`34649174041`): one run green with per-step
  values (for example `startingGuestSession=3.784`), one rerun failed on a pre-existing
  swipe-to-delete assertion at `VehicleAndFuelFlowUITests.swift:42` after onboarding succeeded, and a
  later rerun failed when the guest step exceeded 120 seconds and when element resolution raced a
  transition. The cap was raised to 180 seconds and resolution was hardened accordingly.
- Complete non-instrumented command from `AGENTS.md`: `BUILD SUCCESSFUL`, 638 actionable tasks, all
  contract, architecture, lint, coverage, Android build, Android-host and eligible Native checks
  passed.
- `git diff --check`: clean.
- Consecutive green `ios-simulator-build` runs on the final implementation head `4ac8e85`:
  **3 of 3**. Run `34682336801` passed on its first execution and on two further same-head reruns,
  all three completing the full iOS unit and UI suite with zero failures. The per-step values on the
  first execution were `startingGuestSession=1.675–4.327` seconds across the five onboarding waits,
  and the affected swipe-delete test reached vehicle creation in `3.466` seconds. The count is on the
  implementation head; the documentation commits after it change no iOS source, so the iOS binary is
  identical.

## Contract Impact

- No contract changes.

## Decision Board Impact

- No decision changes. Option (b) would require one if the owner adopts it; that is recorded under
  "Owner Decision Required".

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [x] Entry appended

## Risks or Follow-ups

- **The flake is made rarer, not eliminated.** The real Firebase anonymous sign-in is still in the
  path, so a provider response slower than the 180-second cap can still fail the job. Option (b)
  removes that risk and is recommended to the owner.
- The local partial-refuel badge assertion is outside E1-17 scope. If it reproduces on CI it needs
  classification rather than absorption.
- Local CoreSimulator intermittently refused to start the XCUITest runner as `Busy`; restarting the
  targeted simulator cleared it.

## Human Review Gate

- Applies to the escalated option (b) only: a Debug-only production seam is a gated topic and needs
  an owner decision before implementation. Option (a) is test-only and remains under normal
  pull-request review. The agent will not merge the pull request.
