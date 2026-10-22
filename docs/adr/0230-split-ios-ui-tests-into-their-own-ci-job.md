# ADR-0230 / D-229 - Split the iOS UI Tests into Their Own CI Job

## Status

Accepted

The owner selected option A on 2026-10-17.

The single `ios-ui-tests` job and its 60-minute `JOB_CEILING_MINUTES` entry are superseded by `D-230`
(ADR-0231, `E4-B1`, 2026-10-22): run 39087272433 cancelled that job at its 60-minute ceiling with
five test classes not started, and the suite now runs as five protected shard checks inside the
40-minute default. The rest of this decision stands: `ios-simulator-build` keeps the `carAppTests`
unit target and the Gradle links at 40 minutes, the UI suite stays out of it, and a job exceeds the
default only through a named `JOB_CEILING_MINUTES` entry.

## Context

`docs/CONTRACTS.md §18` assertion 28 encoded a single 40-minute job ceiling, introduced by `D-176`
(ADR-0177) as a hang guard sized above the slowest healthy run. That sizing was derived from a
distribution measured before `E4-02`: over 58 sampled runs, the 44 successful `ios-simulator-build`
executions ranged from 12.1 to 23.9 minutes.

`E4-02` added nine `AccessibilityAuditUITests` audits to the same job. Run 39038842049 on the macOS
runner with Xcode 27.0 measured the phase breakdown below, and the job was cancelled at 14:40:03
while the UI suite was still running:

| Phase | Duration |
|-------|----------|
| checkout, setup and the pinned-Xcode check | 0:24 |
| `:feature:fuel:linkReleaseTestIosArm64` | 5:28 |
| `:composition:ios:linkDebugFrameworkIosSimulatorArm64` | ~6:44 |
| `xcodebuild` compiling the test app | ~5:41 |
| `carAppTests` (144 unit tests) | 0:08 |
| `carAppUITests`, the nine accessibility audits included | ~21:48 and still running |

The audits are genuinely expensive on a CI runner rather than slow by accident: the
`odometer-warning` audit alone spends 126 s inside a single `XCUIElement` query and 326 s in total.
The overrun is therefore not recoverable by trimming waits, and the unit target costs eight seconds
of a job whose remaining twenty-plus minutes are the UI suite.

The failure mode is the worst available one. A cancelled required check reports no test result, so it
is indistinguishable from a real regression and blocks a pull request whose actual subject passed —
the same ambiguity that cost a review round in `D-175`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Split the UI tests into their own protected job with its own 60-minute ceiling, and keep `ios-simulator-build` at 40 for the unit target and the Gradle links | Each check reports its own result, so a unit failure and an audit failure stop being one signal; the 40-minute guard keeps its `D-176` meaning for every job that does not need more; the two jobs run in parallel, so the split costs no wall-clock beyond the second runner. | One more required check name, so `main`'s branch protection must be updated by the owner; `ios-simulator-build` no longer fails when only a UI test fails, so a green build check is not evidence about the UI suite. |
| B. Raise the single job to 60 minutes | No new check name and no branch-protection change; one job keeps running everything. | A hung unit test now burns 60 minutes; the guard stops being sized over a measured distribution and becomes a number chosen to fit one job's worst case; a UI failure and a unit failure still arrive as one signal. |
| C. Move the accessibility audits out of the required checks | The required suite returns to the measured 12.1–23.9 minute distribution. | The audits are the executable form of the accessibility acceptance criteria of `docs/SPECIFICATION.md §11`; making them non-blocking lets a measured WCAG and AX regressions land. Rejected. |

## Decision

The selected option is **A**. `.github/workflows/ci.yml` gains a job `ios-ui-tests` that repeats the
`ios-simulator-build` checkout, `setup-java`, `setup-gradle` and pinned-Xcode-check steps, links
`:composition:ios:linkDebugFrameworkIosSimulatorArm64`, and runs
`xcodebuild … test -only-testing:carAppUITests` on `macos-latest` with `timeout-minutes: 60`.
`ios-simulator-build` keeps `timeout-minutes: 40` and its final step becomes
`xcodebuild … test -only-testing:carAppTests`; its `:feature:fuel:linkReleaseTestIosArm64` step stays,
because that binary serves `E4-03`'s device test (`D-80`) and has nothing to do with the UI suite.

`WorkflowTimeoutContract` keeps 40 minutes as `DEFAULT_JOB_CEILING_MINUTES` and names the exceptions
explicitly in `JOB_CEILING_MINUTES`, whose only entry is `ios-ui-tests` to 60, so a job cannot borrow a
taller ceiling it did not declare. `ios-ui-tests` joins `PROTECTED_CHECK_NAMES` and the required-check
list of `docs/CONTRACTS.md §18`, and assertion 28 is restated as a per-job ceiling.

## Consequences

### Positive

- `ios-ui-tests` reports its own result, so a UI or audit failure and a unit failure are two distinct
  signals instead of one cancelled job.
- The 40-minute guard keeps its `D-176` purpose for every job that has no measured reason to exceed
  it, and the exception is a named, reviewed entry rather than a raised default.
- `ios-simulator-build` now finishes in roughly the time of its two Gradle links plus the eight-second
  unit target, so its own guard has wide headroom over its measured duration.

### Negative

- `main`'s branch protection must gain the `ios-ui-tests` context, and until the owner adds it the new
  check is not required; a UI-only failure will therefore not block a merge in that window.
- The macOS runner minutes per pull request grow, because the framework link and the test-app compile
  now happen in both jobs rather than once.

### Constraints Introduced

- `ios-ui-tests` MUST NOT be renamed while branch protection requires it by name; renaming it and
  updating protection are one change (`docs/CONTRIBUTING.md`).
- A job MAY exceed the default ceiling only by being named in `WorkflowTimeoutContract`
  `JOB_CEILING_MINUTES` with a decision that records its measurement. The default MUST NOT be raised to
  fit one job.
- The `ios-simulator-build` unit step MUST stay `-only-testing:carAppTests` and the `ios-ui-tests` step
  MUST stay `-only-testing:carAppUITests`, so the two checks keep answering different questions.
- The `:feature:fuel:linkReleaseTestIosArm64` step stays in `ios-simulator-build`; it is `E4-03`'s
  device-test binary (`D-80`) and moving it is a separate decision.

## Verification

- `./gradlew contractCheck` reports assertions 27 to 30 `[PASS]` with no `[PENDING]`.
- `:build-logic:convention:test` keeps the firing fixtures: 41 minutes on a default-ceiling job fails
  assertion 28, 61 minutes on `ios-ui-tests` fails assertion 28, a default-ceiling job at 60 minutes
  fails assertion 28, and removing the `ios-ui-tests` protected name fails assertion 30.
- The workflow parses and both jobs appear with their `-only-testing` targets.
- The owner re-runs CI on the pull request head and confirms `ios-ui-tests` completes inside 60
  minutes and `ios-simulator-build` inside 40.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-229`)
- `docs/CONTRACTS.md §18` (required checks, assertions 27 to 30)
- `docs/adr/0177-size-the-ci-job-hang-guard-above-the-slowest-healthy-run.md` (`D-176`)
- `docs/adr/0103-ios-unit-and-ui-test-targets-in-ci.md` (`D-102`)
- `docs/handoffs/E4-B1.md`, `docs/handoffs/E4-02.md`
- `docs/BACKLOG.md` (`E4-02`, `E4-03`)
