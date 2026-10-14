# ADR-0205 - Wait for graph sync effects under StateFlow conflation

## Status

Accepted

## Context

E3-09 CI run 38489375710 failed one Native vehicle-payload fixture among 248 shared tests; JVM and
Analytics tests passed. The isolated Native fixture passed locally. CI supplied no assertion message
or test-report artifact, so the precise remote assertion cannot be recovered from that run.

Inspection identified a demonstrable race in the fixture helper: `awaitSyncCycleSettled` reads the
remote effect only when SyncStatus emits. If the collector initially sees Idle, a fast cycle can
publish Syncing then Idle before it runs again. StateFlow conflates both to the last-observed Idle,
so it produces no new emission and the helper never reevaluates the now-completed remote effect.
A deterministic regression reproduces this without CPU load, sleeps or a real database.

## Options Considered

- Keep the status-only collector: the demonstrated race remains.
- Combine status and an explicit remote-effect flow: requires changing every callback-based fixture.
- Reevaluate both conditions through the existing bounded `awaitCondition`: keeps the test API,
  deadline, cancellation and every assertion.
- Increase the deadline or retry the failed job: neither repairs the missed emission.

## Decision

Use the existing bounded polling helper to reevaluate remote completion and terminal status
independently, with a one-millisecond real scheduling pulse between checks on Dispatchers.Default.
A bare yield loop keeps runCurrent busy when a fixture deliberately blocks the cycle; the real
pulse suspends outside the virtual scheduler, letting it become idle until the next check. This is an implementation repair of the assigned story's validation, within the
existing D-190 test-scheduling policy. It adds no product behavior, owner choice or technology.
The owner's 1A, 2A and 3B choices remain D-201, D-202 and D-203.

## Consequences and Constraints

The helper still requires a remote effect and non-Syncing aggregate before checking persistence.
The existing 45-second deadline is unchanged; the scheduling pulse is not a new deadline. No existing assertion is weakened, skipped or deleted;
the engine, public graph API and device lifetime rules are unchanged. Tests must demonstrate the
conflated cycle, a remote effect while still Syncing, and rejection of missing persisted state.

## Verification

Commit and push the failing regression before changing the helper. Run the regression on JVM and
Native, the whole vehicle suite, the full local command, provider-free route and all required CI
checks. The regression proves the helper race, not the unrecovered CI assertion text.
