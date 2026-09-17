# ADR-0177 / D-176 - Size the CI Job Hang Guard Above the Slowest Healthy Run

## Status

Accepted

## Context

The `E3-03` CI-tooling follow-up gave every protected job a flat `timeout-minutes: 20` and made that
policy executable as `docs/CONTRACTS.md §18` assertions 27-29, with
`WorkflowTimeoutContract.MAX_JOB_MINUTES` as the single ceiling. The intent was a hang guard: a job
that stops making progress is killed instead of occupying a runner.

The ceiling was set to the same 20 minutes that `docs/BACKLOG.md` and the workflow header already
use as a **monitored objective** for whole-run duration (`E0-05`). Those are different quantities. An
objective is a target nobody is failed for missing; this ceiling is a hard kill. Coupling them made
the guard fire on healthy work:

- `ios-simulator-build` was cancelled on run `35133388068` at 20m50s with
  `The job has exceeded the maximum execution time of 20m0s`. Nothing was hung: the run's other nine
  checks, including `shared-tests`, had already passed.
- The job is genuinely long and genuinely variable. Over 58 sampled runs its 44 successes ranged
  from 12.1 to 23.9 minutes, with a median of 15.3 and a p90 of 18.3 — nearly a factor of two
  between the fastest and slowest healthy execution. The job builds an optimized Kotlin/Native
  device-test binary, links the debug simulator framework and runs `xcodebuild test`; its duration
  tracks macOS runner speed.
- That spread means a ceiling near the fast end is reachable by a healthy run. 18.6 minutes, the
  figure first noticed, is 93% of a 20-minute ceiling, and slower successes went higher still. On a
  slow runner every step inflates together — the cancelled run's link step took 4m26s against a
  2m34s baseline — so the ceiling fired without any defect in the work under test.
- The failure mode is the worst available one. A cancelled required check reports no test result, so
  it is indistinguishable from a real regression, and it blocks a pull request whose actual subject
  passed. `D-175` was accepted precisely because that ambiguity cost a review round.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Keep 20 minutes and make the slow job fit | The guard is tight and the whole-run objective is unchanged. | Requires cutting several minutes from a job whose duration is dominated by toolchain and runner speed, and `D-35` forbids merging it into `shared-tests`. An unrealistically tight guard keeps cancelling healthy work, which is the defect being fixed. |
| B. One ceiling well above the slowest healthy run, applied uniformly | One constant, one rule, no per-job policy table. Headroom is expressed in the contract text rather than inferred. | A genuinely hung Linux job burns 40 minutes instead of 20, up to 20 wasted runner-minutes per job. |
| C. Per-job ceilings proportional to each measured worst case | Tightest possible guard per job; a hung fast job is caught sooner. | Turns one constant into a maintained measurement table that must be re-derived whenever a job changes, and a stale row is a silent cancellation waiting to happen. |

## Decision

Option B. `WorkflowTimeoutContract.MAX_JOB_MINUTES` and every job's `timeout-minutes` are raised to
40, and `docs/CONTRACTS.md §18` assertion 28 states the ceiling as 40 minutes together with its
purpose: the limit kills a hung job and MUST retain headroom over the measured distribution rather
than sit near its worst observed success. Forty is 1.67x the slowest of the 44 sampled successes and
2.6x their median, which is the margin a distribution this wide needs. The `E0-05` whole-run
objective of 20 minutes is unchanged and remains monitored rather than gating.

The `shared-tests` and `provider-decoupling` step-level limits (10, 10, 4 and 8, 8 minutes) are
unchanged; they stay strictly below the ceiling, which is what assertion 29 requires.

## Consequences

### Positive

- A slow but healthy job can no longer be cancelled by a limit whose only job is to catch hangs.
- `ios-simulator-build`'s 23.9-minute worst observed success sits at 60% of the ceiling, so runner
  variance is absorbed instead of becoming a red required check.
- The guard still exists and still bounds a hang, so the original intent is preserved.

### Negative

- A hung Linux job is detected at 40 minutes rather than 20, so a bad run may waste up to 20
  additional runner-minutes. `cancel-in-progress` bounds the total, and a hang is rare relative to
  the cost of cancelling healthy work.

### Constraints Introduced

- The ceiling MUST stay above the slowest measured successful run of any job, with margin over the
  spread rather than a hair above the maximum, and the contract text MUST state that rule so a future
  tightening is a reviewed decision rather than a one-line edit.
- The `E0-05` 20-minute target MUST NOT be reused as a hard limit again; the two quantities are
  different and conflating them is what caused the cancellation.

## Verification

- `contractCheck` asserts 27-30: every protected job declares a limit, none exceeds 40 minutes, the
  `shared-tests` steps keep stricter limits, and all ten protected names remain present.
- `:build-logic:convention:test` keeps the firing fixtures: omitting a job timeout fails assertion
  27, and declaring 41 minutes fails assertion 28.
- The pull request head re-runs all ten required checks, and `ios-simulator-build` completing
  normally is the direct evidence that the guard no longer fires on healthy work.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-176`)
- `docs/CONTRACTS.md §18` (assertions 27-30)
- `docs/BACKLOG.md` (`E0-05`, the 20-minute monitored objective)
- `docs/adr/0036-ci-keeps-shared-tests-and-ios-build-separate.md` (`D-35`)
- `docs/adr/0176-fix-the-silent-shared-test-stall-inside-e3-03.md` (`D-175`)
