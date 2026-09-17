# ADR-0178 / D-177 - Bound Virtual Time Advancement in Graph-Backed Tests

## Status

Accepted

The owner selected this option on 2026-09-16, while reviewing the `E3-03` correction rounds.

## Context

`kotlinx.coroutines.test.advanceUntilIdle()` runs the test scheduler until no scheduled task
remains. That contract makes it **non-terminating** when a coroutine keeps rescheduling itself in
virtual time, and the failure mode is worse than a slow test:

- The run never returns, so the test task is killed at the step timeout with **no test result and no
  test name**. `shared-tests` failed exactly that way repeatedly during `E3-03`.
- Because `advanceUntilIdle()` never yields the thread back to the scheduler, neither `runTest`'s own
  timeout nor a virtual `withTimeoutOrNull` wrapped around it can end the run. Reproduced: a scope
  whose coroutine delays and re-arms itself makes both of those expire without effect.

The production code contains that shape. `DefaultSyncController.scheduleAdoptionRetry`
(`core/sync/src/commonMain/.../SyncEngine.kt:604-613`) delays `retryDelayMillis(...)` and then calls
`requestSync(SyncTrigger.Periodic)`, so a cycle whose adoption keeps failing re-arms the loop
indefinitely. It is the only self-re-arming `delay` loop in production `commonMain`, and it runs on
`graphScope`, the same scope a graph test confines to its scheduler.

Three shared tests mount a real `AppGraph` and then call `advanceUntilIdle()`:
`FuelEntryStateHolderTest.unsupportedLocaleCurrencyFallsBackToEur`,
`AccountConversionAppGraphTest.malformedRemoteDocumentDoesNotEscapeResumePendingOnGraphScope` and
`AppGraphCloseTest.kotlinGraphCanCloseImmediatelyWhileSettingsBootstrapStartsWithoutAConsumer`.

**Reachability today.** The re-arm needs a *persistently failing* adoption, which needs a
non-sentinel owner whose `adoptRows` fails. No graph test can produce that: `DefaultAppGraph`
constructs `LocalOwnerAdoption(dependencies, databaseHandle.database)` without `injectedAdoptRows`,
the only failure injection lives in `LocalOwnerAdoptionFailureTest`, and that class never calls
`buildAppGraph`. The hazard is therefore **latent, not live**: one future test that injects a failing
adoption into a graph, or one future production re-arming loop on `graphScope`, makes every later
test in that task non-terminating. An earlier note in `docs/handoff-E3-03.md` attributed the observed
stall to the first of those three tests; that attribution was wrong and is withdrawn there.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Replace `advanceUntilIdle()` with a bounded `advanceTimeBy(span)` in every graph-backed test | Terminates by construction regardless of what re-arms. A mis-scheduled test fails by name and fast instead of killing the task. Test-only, so no product behaviour and no gated path changes. | The span is a judgement call: too small and a legitimate schedule is truncated, turning a scheduling bug into a false failure. It also does not remove the production loop, so the hazard stays latent for non-graph callers. |
| B. Leave the tests and only document the hazard | No test churn. | The documented failure mode is the one that costs a review round: an unattributable killed task. A note is not a guard, and the very next graph test can trip it. |
| C. Bound `scheduleAdoptionRetry` in production | Removes the loop at its root, so no caller can spin. | Changes product behaviour that `§9` requires, on the gated `core/sync/**` path, for a hazard no current caller reaches. It is a production story, not test hardening. |

## Decision

Option A. A shared helper `TestScope.advanceGraphWork(span)` advances virtual time by a bounded span
and then runs what became due, and every graph-backed test uses it instead of `advanceUntilIdle()`.
The span is 60 seconds of virtual time - larger than any backoff or retry schedule the shared suite
exercises, so legitimate scheduling still completes, and finite, so the helper always returns.

Option C is explicitly **not** taken here: bounding the adoption retry is required production
behaviour under `§9`, and it belongs to a production story with its own review gate. Option B is
rejected because the hazard's failure mode is unattributable, which is the exact cost this repository
already paid.

## Consequences

### Positive

- A graph-backed test can no longer kill the `shared-tests` task by advancing time.
- A test whose expected effect never arrives fails with its own assertion message and a bounded time,
  instead of a silent step timeout with no test name.
- The rule is executable by a regression test rather than carried in prose.

### Negative

- The span is a magic number in test scaffolding. If a future legitimate schedule exceeds it, the
  affected test fails and the span must be raised in a reviewed change, which is the intended
  pressure rather than a silent truncation.
- The production re-arming loop remains. This change bounds the *observation* of it in tests, not the
  loop itself.

### Constraints Introduced

- Graph-backed shared tests MUST advance virtual time with `advanceGraphWork`, not
  `advanceUntilIdle`. `advanceUntilIdle` stays permitted for tests that do not mount an `AppGraph`,
  where no self-re-arming production loop shares the scheduler.
- The span MUST stay above the largest schedule the suite exercises; raising it is a reviewed change.
- This decision MUST NOT be read as discharging the production hazard. Bounding
  `scheduleAdoptionRetry` remains a separate production story.

## Verification

- `GraphTestDependenciesTest.boundedAdvanceReturnsWhileWorkKeepsRearmingItselfInVirtualTime` runs a
  coroutine that re-arms itself every second in virtual time and asserts the helper returns and stops
  at its span. With `advanceUntilIdle()` the same test never returns.
- The three graph-backed tests that previously called `advanceUntilIdle()` pass on the helper, and
  `:shared` `testAndroidHostTest` and `iosSimulatorArm64Test` pass.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-177`)
- `docs/handoff-E3-03.md` (rounds 17-19, including the withdrawn attribution)
- `docs/adr/0176-fix-the-silent-shared-test-stall-inside-e3-03.md` (`D-175`)
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/GraphTestDependencies.kt`
