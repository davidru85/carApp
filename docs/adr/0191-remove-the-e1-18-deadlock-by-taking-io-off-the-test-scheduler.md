# ADR-0191 / D-190 - Remove the `E1-18` Deadlock by Taking `io` Off the Test Scheduler

## Status

Accepted

Owner decision taken on 2026-09-24: implement Option B of the `E1-18` analysis, replacing the
temporary `D-189` measure it supersedes.

## Context

`shared-tests` and `provider-decoupling` failed intermittently by **step timeout**, never by a failed
assertion. `D-189` raised the two stalling step limits as an explicit stopgap and recorded the
predicted consequence: because the hang produces no output and never self-heals, a longer limit only
delays the red check. Measurement confirmed it — the step was killed at the raised 15-minute limit
after 7 minutes of complete silence.

The deadlock itself, registered as `E1-18`:

- `DefaultAppGraph` runs graph work on `dependencies.dispatchers.io`.
- `AppGraph.close()` launches its database-release waiter on that same dispatcher, and
  `AndroidxDriverConnectionPool.close()` reaches its writer lock through a **`runBlocking`**.
- Under `TestDispatcherProvider`, `main`, `default` and `io` were the *same*
  `StandardTestDispatcher(testScheduler)`, so that blocking close seized the only thread that could
  resume the transaction holding the lock. Self-deadlock, no timeout inside it, no test result.

`D-189` also recorded that the fix needed its own decision because it supersedes `E1-14`'s
confinement decision, which `GraphTestDependenciesTest` pins as a contract.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Keep re-running until green | No code change. | The owner measured a high error rate on repeated re-runs, so the check stops being a usable signal. |
| B. (selected) Take `io` off the scheduler | Removes the deadlock at its cause. | Supersedes `E1-14`'s confinement decision, so `GraphTestDependenciesTest` MUST be rewritten to pin the new intent. |
| C. Close off the scheduler in the harness only | Narrower. | Leaves the production `AppGraph.close()` path able to deadlock under any test that closes a graph on a scheduler. |
| D. Raise the step limits (`D-189`) | Unblocks immediately. | Does not fix anything: measured as a later, equally red failure. Superseded by this decision. |

## Decision

**Option B, implemented as three coordinated changes.** The first two were forced by measurement, not
chosen up front; each was reached by testing a variant and observing what it broke.

1. **`io` becomes a real dispatcher in graph fixtures.** `TestDispatcherProvider` gains a second
   constructor parameter, defaulting to the confined dispatcher so non-graph call sites are
   unchanged; `confinedGraphDependencies` passes `Dispatchers.IO`. The driver's blocking close now
   runs on a thread the scheduler does not need.
2. **`graphScope` moves from `io` to `default`.** This is the step that makes the fix work rather than
   merely relocate the problem. The sync engine schedules its `delay()` calls on the graph scope, so
   moving the engine to a real `io` turned the 2 s post-write debounce and the adoption backoff into
   wall-clock time. Measured, that produced an **80% assertion-failure rate** — a worse signal than
   the deadlock it replaced. `default` stays scheduler-confined, so virtual time is preserved, while
   `io` remains the one blocking-capable dispatcher. In production both are `Dispatchers.Default`, so
   this split is invisible outside tests.
3. **A second, independent form of the same deadlock was fixed too.** Taking `io` off the scheduler
   removed the `AppGraph.close()` form, but a live `jstack` capture showed the suite still stalled at
   a different site: `LocalOwnerAdoptionTest.tearDown` -> `SqlDriverDatabaseHandle.close` ->
   `AndroidxDriverConnectionPool.close` -> `runBlocking`, parked on the test-scheduler thread. Tests
   that close the handle **directly** never went through the graph at all, so the dispatcher split
   could not reach them. `TrackedDatabaseHandles.close()` now queues the release on a worker scope and
   **does not await it**: the caller returns to its scheduler, the suspended transaction drains and
   releases the writer, and the close completes on the worker. Awaiting it, even from a worker, would
   re-block the caller and restore the deadlock. The three adoption `tearDown`s stop closing the
   handle themselves, because the factory already owns it.
4. **The vehicle list's local observation moves to `default`.** With `io` real, its
   `flowOn(dispatchers.io)` took the arrival of restored rows off the confined scheduler and made the
   recovery window a real-time race. Production cannot observe the difference; the test suite stops
   racing.

The window-closing republish is also hardened: when an outstanding-recovery window closes over a
*resolved-empty* listing, that listing is stale by construction — the window described the recovery
that was going to deliver this owner's rows — so the holder re-reads instead of publishing a
confirmed empty list to `SPECIFICATION.md` F-1. A count that was already zero is not a closing window
and still publishes directly.

`D-189`'s timeout raise is **reverted**: the two stalling steps return to their original 10- and
8-minute limits.

## Consequences

### Positive

- The deadlock is removed at its cause. Measured over 10 consecutive runs of the exact
  `provider-decoupling` Android-host command: **0 hangs**, against 1-in-10 before the fix.
- The two checks report a real result again, so a red `shared-tests` means what it says.
- The temporary measure is withdrawn, so no caveat about a meaningless red check remains.
- The scheduling contract is now executable in the direction that matters:
  `assertQueuedGraphWork` asserts that `io` is **not** the scheduler, so a future edit that re-confines
  it fails by name.

### Negative

- `E1-14`'s confinement decision is superseded rather than extended: confinement is now scoped to
  `main` and `default`, and `io` is deliberately outside it.
- The graph now uses two dispatchers where it used one, which is one more thing to reason about in
  graph tests.

### Constraints Introduced

- `io` MUST NOT be confined to the test scheduler in any fixture that mounts an `AppGraph`.
- `graphScope` MUST run on a scheduler-confined dispatcher, because the sync engine's timers depend on
  virtual time.
- The `D-176` job ceiling and every job-level `timeout-minutes` are unchanged; the reverted step
  limits stay strictly below `MAX_JOB_MINUTES`.

## Verification

- `assertQueuedGraphWork` asserts `main`/`default` wait for `runCurrent()` while `io` is a different
  dispatcher, and `GraphTestDependenciesTest` exercises it on both the default and customized paths.
- 10 consecutive runs of `-Pcarapp.excludeFirebaseProviders=true :shared:testAndroidHostTest
  --rerun-tasks`: 0 hangs.
- 16 consecutive runs of the exact `shared-tests` step command
  (`:androidApp:testDebugUnitTest testAndroidHostTest --rerun-tasks`): 0 hangs. The second figure is
  the one that matters for CI, because that step runs a wider task set than the
  `provider-decoupling` route the first measurement covers.
- `:shared:testAndroidHostTest`, `:feature:vehicle:testAndroidHostTest`, `:core:sync`,
  `:core:auth`, `:feature:fuel` and the complete non-instrumented `AGENTS.md` command pass.
- `contractCheck` reports no `PENDING` assertion with the step limits restored.

## References

- `docs/BACKLOG.md` (`E1-18`)
- `docs/adr/0190` (`D-189`, the superseded temporary measure)
- `docs/handoff-E1-14.md` (the confinement decision this supersedes)
- `docs/handoff-E3-12.md` (the diagnosis, the two captured stacks and the measured rates)
