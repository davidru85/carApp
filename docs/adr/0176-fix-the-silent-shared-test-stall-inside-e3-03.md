# ADR-0176 / D-175 - Fix the Silent Shared-Test Stall Inside E3-03

## Status

Accepted

The owner chose this on 2026-09-16 during the `E3-03` review, in the same exchange that decided to
re-run CI and merge if it is green.

## Context

`shared-tests` is a required check for `main` (`D-31`, `D-34`). Several `E3-03` executions failed it
by **step timeout**, not by an assertion: the last started task was a `:shared` test task, no test
result was ever produced, and the runner killed the step at its 8-10 minute limit. Executions
`35022299380`, `35025942928`, `35030493064`, `35069800864`, `35068401575`, `35027677842` and
`35079145091` share that shape. Every stall was on `:shared:iosSimulatorArm64Test` or
`:shared:testAndroidHostTest`; no `:core:*` or `:feature:*` task ever stalled.

Re-running the previously green commit `8b76f6aa` (`35020526800`) stalled too, and in that same run
`shared-tests` passed while `provider-decoupling` hung on the same `:shared:testAndroidHostTest`
task. Same commit, same runner image, same suite, opposite results. That established the defect as
non-deterministic rather than a regression, and independent of the CI-tooling commits, which touch no
`:shared` test source.

Two constructs made a bounded assertion incapable of reporting its own failure:

- `FlowExpectation.awaitState` awaited its collector with an unbounded `emission.cancelAndJoin()`.
  Its own documentation stated the consequence: "Extreme CPU starvation or non-cooperative cleanup
  can therefore delay the assertion beyond `runTest`'s timeout."
- Eight bare `while (condition) yield()` polls in `AccountConversionAppGraphTest`,
  `LocalOwnerAdoptionFailureTest` and `LocalOwnerAdoptionTriggerTest`. A busy-wait never suspends for
  real, so `runTest`'s timeout cannot fire and the poll spins a core until the step is killed.

Both were reproduced locally: the first as a 300-second silent hang, the second pinned by a thread
dump inside `runTest`'s own `drain`, which is why no result was produced. The defect class is the one
`E1-14` already closed for the expectations that existed then.

The owner had to choose where the correction belongs, because `E3-03`'s REFACTOR was already closed
and the affected code is test code the story itself added.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. A separate test-infrastructure story, sibling to `E1-14` | Keeps `E3-03` closed, and the fix benefits every story rather than one. This is the `E1-14` precedent. | `E3-03` and everything downstream of it (`E3-04`, `E3-05`, the rest of Phase 3) wait for that story to finish. |
| B. (selected) A seventeenth correction round inside `E3-03` | One pull request to close, and the defect never reaches `main`. The fix is verifiable against the same required check it unblocks. | Extends a review that has already run sixteen rounds, and mixes product work with test scaffolding. Because the stall is not locally reproducible, each attempt costs a CI round trip. |
| C. Relax the CI check (raise the step timeout, split it, or remove it) | Unblocks immediately without touching tests. | `D-31` / `D-34` fix the check names and limits, so it needs its own decision review, and it discards the signal that detects a genuinely stalled test. |

## Decision

**Option B.** The correction is delivered inside `E3-03` as round 17, in test code only.

The owner separately decided to re-run CI and merge pull request #69 if it is green, rather than hold
the merge until the stall is provably eliminated.

## Consequences

### Positive

- The required check reports a real result again instead of being killed with no diagnosis.
- A future non-cooperative collector or unbounded poll fails by name, with the expectation's
  description, instead of taking down the whole test task.
- The fix is scoped to shared test scaffolding, so no product behaviour, timing, state holder,
  `AppGraph` or database contract changes.

### Negative

- `E3-03`'s review extends by one round, and the handoff and project log grow accordingly.
- The residual risk of merging on a green run is accepted: the fix's evidence is a green CI run and
  a reproduced RED, not a proof that the stall can never recur under a differently-shaped starvation.

### Constraints Introduced

- The correction MUST stay in test code; a production cause would stop the round and escalate.
- `E3-17` / `D-172` remains the separate production hazard around `AppGraph.close()` and is not
  addressed here.
- The permanent regression MUST fail against the unfixed helper; a test that passes either way is not
  evidence.

## Verification

- RED `cd41ed7`: `FlowExpectationTest.aCollectorThatIgnoresCancellationCannotOutliveTheExpectation`
  hangs for 120 seconds with no test result against the unbounded helper, reproducing the CI shape.
- GREEN `9c7c51f`: the same test fails in ~8 s, naming the expectation; `awaitCondition` replaces the
  eight polls.
- REFACTOR `583f077`: the poll's bound is a monotonic real-time deadline rather than an iteration
  count, because these conditions wait on asynchronous SQLite work that needs real CPU, and the poll
  stays on the caller's dispatcher because a virtual-time timeout would expire before that work is
  scheduled.
- Local: `:shared:testAndroidHostTest` (173 tests) and `:shared:iosSimulatorArm64Test` pass; the
  provider-free host suite passes; 6/6 repeated combined executions pass; the exact `shared-tests`
  steps pass; `ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
  koverVerify --rerun-tasks` passes with 397 tasks and 30 `[PASS]` assertions; the complete
  non-instrumented command passes.
- CI: `35123138250` on `9c7c51f` passed 9 of 10 jobs, including the previously stalling
  `provider-decoupling`; `shared-tests` failed in 40 s with a runner DNS resolution error rather than
  a stall. `35125459030` on `2daa531` stalled again on `:shared:testAndroidHostTest`, so the two
  bounded waits were not the whole cause. `35127303504` on `a900571` is **fully green**, all ten
  required checks passing.

## Residual Risk

A third silent-hang mechanism is proven and **not** fixed by this decision:
`advanceUntilIdle()` never terminates when work keeps re-arming itself in virtual time, and
`DefaultSyncController.scheduleAdoptionRetry` (`core/sync/src/commonMain/.../SyncEngine.kt:604-613`)
is exactly that shape (delay, then `requestSync(SyncTrigger.Periodic)`). It is reachable in
`FuelEntryStateHolderTest.unsupportedLocaleCurrencyFallsBackToEur`, the one shared test that both
confines a real `AppGraph` to the test scheduler and calls `advanceUntilIdle()`. Bounding the retry
changes production behaviour, so it belongs to a production story; `a900571` adds per-test log lines
so the culprit is named on the next stall. This is recorded rather than silently carried.

## References

- `docs/BACKLOG.md` (`E3-03`, `E1-14`)
- `docs/handoff-E3-03.md`
- `docs/PROJECT_LOG.md` (2026-09-16 entry)
- `.github/workflows/ci.yml` (`shared-tests`, `provider-decoupling`)
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/FlowExpectation.kt`
