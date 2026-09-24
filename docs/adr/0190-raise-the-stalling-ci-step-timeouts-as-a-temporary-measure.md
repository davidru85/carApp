# ADR-0190 / D-189 - Raise the Stalling CI Step Timeouts as a Temporary Measure

## Status

Accepted

Owner decision taken on 2026-09-24 during the `E3-12` review, to unblock pull request #73.

## Context

`shared-tests` and `provider-decoupling` fail intermittently by **step timeout**, never by a failed
assertion. The cause is the deadlock registered as `E1-18`: `AndroidxDriverConnectionPool.close()`
calls `runBlocking` to take the writer lock, seizing the very test-scheduler thread that would have to
resume the transaction holding that lock. It is pre-existing (`main` hangs at the same seam) and
independent of the branch.

The owner re-ran the checks repeatedly and observed a high error rate, so treating the failure as a
rarity to retry was rejected as a way forward (`D-175` had accepted exactly that, but on a lower
observed rate). The owner's decision is therefore two-part:

- the permanent fix is **Option B** of the `E3-12` analysis: split `io` away from the test scheduler
  so the driver's blocking close runs on a thread nobody needs. That work belongs to `E1-18`; it
  supersedes `E1-14`'s confinement decision and needs its own decision and ADR.
- the temporary measure is **Option D**: raise the stalling step timeouts so pull request #73 can
  progress while `E1-18` is delivered.

## Measured evidence

| Observation | Value |
|---|---|
| `Run Android application and KMP host tests` — limit before | 10 minutes |
| Same step, healthy completion | 2 m 13 s |
| Same step, hung | killed at 10 m 13 s |
| Progress in the hung run | last `STARTED` at 10:15:42, then **no output until the kill at 10:22:52** |
| `Run provider-free Android host tests` — limit before | 8 minutes |
| Same step, healthy completion | ~52 s |

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Re-run until green | No change, and `D-175`'s precedent. | The owner measured a high error rate on repeated re-runs, so the check stops being a usable signal at this rate. |
| B. Split `io` away from the scheduler | Removes the deadlock at its cause. | Test-only, but supersedes `E1-14`'s confinement decision, which `GraphTestDependenciesTest` pins; needs its own decision and ADR, so it cannot land inside a gated story's review. |
| C. Close off the scheduler in the harness | Narrower than B. | Treats the harness symptom and leaves the production `AppGraph.close()` path able to deadlock. |
| D. (selected, temporary) Raise the stalling step timeouts | Unblocks immediately; no product or test code changes; the job ceiling of `D-176` is untouched. | **It does not fix the deadlock.** The measured silence proves the hang never self-heals, so a longer limit still ends in a red check — it only buys more attempts and delays the failure. |
| E. Reduce the deadlock window in test code | No dispatcher change. | Every graph-backed test must cooperate; fragile and easy to reintroduce. |

## Decision

**Option D, explicitly temporary, with Option B recorded as the real fix in `E1-18`.**

The two stalling steps rise from 10 to 15 minutes in `shared-tests` and from 8 to 15 minutes in
`provider-decoupling`. The `D-176` job ceiling (`MAX_JOB_MINUTES = 40`) and every job-level
`timeout-minutes` are unchanged, and the contract assertions of `docs/CONTRACTS.md §18` still hold
because every step limit stays below that ceiling.

## Consequences

### Positive

- Pull request #73 is not blocked on a second, unrelated defect.
- The step limits stay a hang guard rather than becoming a speed budget: 15 minutes is still far below
  the 40-minute job ceiling.

### Negative

- **The temporary measure does not make the red checks green.** The hung run produced no output for
  7 m 10 s; no timeout value converts that into a pass. The expected effect is a later, equally red
  failure plus more re-run attempts per green CI run.
- The signal that `shared-tests` reports is weakened for as long as `E1-18` is open, which is exactly
  the trade `AGENTS.md` already warns about.

## Constraints Introduced

- This is a **stopgap**. `E1-18` MUST implement Option B and MUST remove this measure when it lands.
- No job-level `timeout-minutes` may change, and `MAX_JOB_MINUTES` stays 40 (`D-176`).
- The step limits MUST stay strictly below `MAX_JOB_MINUTES`, or contract assertion 29 fails.
- `E1-18` MUST NOT be treated as closed by this decision.

## Verification

- `.github/workflows/ci.yml` raises exactly the two stalling steps to 15 minutes.
- `./gradlew contractCheck` passes with no `PENDING` assertion, which is what proves assertions 27-29
  still hold against the new values.
- The mirroring rows in `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`,
  `docs/DECISION_BOARD.md` and `docs/adr/README.md` match this decision.

## References

- `docs/BACKLOG.md` (`E1-18`, Option B)
- `docs/adr/0176-fix-the-silent-shared-test-stall-inside-e3-03.md` (`D-175`)
- `docs/adr/0177-size-the-ci-job-hang-guard-above-the-slowest-healthy-run.md` (`D-176`)
- `docs/handoff-E3-12.md` (the deadlock diagnosis, thread stacks and measured rates)
