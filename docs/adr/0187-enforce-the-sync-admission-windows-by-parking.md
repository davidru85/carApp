# ADR-0187 - Enforce the `§9.8` admission windows by parking, not refusing

## Status

Accepted

The ADR status MUST equal the status of the same decision ID in `docs/DECISION_BOARD.md`.

## Context

`docs/CONTRACTS.md §9.8` declares two automatic-cycle windows and says nothing about what happens to a
trigger that arrives while one of them is open. `E3-04` had to decide, because before it the windows
were declarative constants no code consumed: `DefaultSyncController.requestSync` started a cycle
immediately, so the 2 s post-write debounce and the 30 s minimum interval had no effect at all. Making
them effective means answering four questions that the contract does not settle, and each of them
shapes observable behaviour:

1. What happens to a trigger that arrives inside a window?
2. From which moment is the 30 s interval measured?
3. Does the single pending follow-up of `§9.1` obey the windows?
4. Does a pull-to-refresh cycle arm the interval for the automatic cycles that follow it?

The answers are a change to the synchronization algorithm's admission behaviour, which `AGENTS.md`
lists as a gated topic. Recording them in code comments and in a story handoff does not count, so this
ADR fixes them as a decision.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Option A (selected) for (a): park the trigger and serve it at the window boundary | No trigger is lost, and a burst of writes collapses into one cycle at the boundary, which is the coalescing `§9.8` exists to produce. The parked batch is claimed as a whole, so ten writes 200 ms apart become one cycle instead of ten | A trigger's cycle is delayed by up to the window. That is the intended meaning of a debounce, and the alternative does not remove the delay, it removes the cycle |
| Option B for (a): refuse the trigger and return | Simple, and the caller returns immediately | The write that caused the trigger stays outstanding until some later, unrelated trigger happens to arrive. With a debounce that fires on every write, "later" may be never, which is silent data-loss-shaped behaviour in a backup feature |
| Option A (selected) for (b): anchor the 30 s floor to the moment a cycle reaches its remote steps, after the connectivity, `LOCAL_OWNER` and adoption gates | The interval bounds remote backup traffic, which is its purpose. A cycle refused for offline or `LOCAL_OWNER`, or one that failed before any remote call, performed no work to space out, so spacing it out would only delay the retry that follows a failure that is not about the network | The interval is not a wall-clock guarantee between triggers; it is a bound on remote work. A reader expecting a simple timer must read the anchoring comment |
| Option B for (b): anchor at admission | Trivially simple: arm when the request is accepted | It spaces out cycles that performed no remote work at all. Being offline for an hour and then reconnecting would make the first real cycle wait out a floor armed by cycles that only observed the offline state |
| Option C for (b): anchor at cycle completion | Also simple, and it measures something real | It pushes the next cycle further out the slower the previous one was, so a slow network delays the following cycle by its own duration on top of the interval. The interval would grow with the very condition it should not depend on |
| Option A (selected) for (c): exempt the single follow-up from both windows | The follow-up exists because work is *already due*: it is created when a trigger arrives during an active cycle, so throttling it would delay work the engine has already decided to run. Exempting it is what keeps `§9.1`'s exactly-one-active-and-one-pending invariant meaningful | The follow-up runs outside the floor, so two cycles can run closer together than 30 s. That is deliberate: the pair is triggered by work, not by the automatic cadence |
| Option B for (c): apply the floor to the follow-up | Uniform treatment of every cycle | It delays work already known to be due, which is the opposite of what a follow-up is for, and it makes the debounce a source of latency after the first cycle rather than a coalescer before it |
| Option A (selected) for (d): a `PullToRefresh` cycle re-arms the floor for the automatic cycles that follow it | The floor stays armed after manual activity. `§9.8` exempts pull-to-refresh as a *requester* — it must never be delayed by the window — and that exemption says nothing about whether it counts as activity for the next automatic cycle. Repeated manual refreshes therefore cannot leave the automatic floor permanently unarmed | A manual refresh does more than it appears to: it also holds back the next automatic cycle. That is the conservative direction for remote traffic, which is what the floor bounds |
| Option B for (d): arm the floor only for automatic cycles | The floor is exactly "automatic cycles only", which reads literally from the constant's name | Repeated manual refreshes leave the automatic floor unarmed, so an automatic cycle can follow a manual one immediately. In a backup feature the floor's purpose is to bound remote writes, and manual refreshes produce those writes just the same |

## Decision

The selected options are: **Option A for (a), (b), (c) and (d)**.

`DefaultSyncController` implements them as follows.

- **(a) Parking.** `admit` unconditionally appends the request to `parkedRequests` before reading any
  window, then returns without a cycle if a window is open. The request is served by the timer that
  opens the window it is waiting on: `claim()` takes the entire parked batch, so every parker is
  answered by one cycle rather than only the last arrival. Parking unconditionally is also what makes
  the answer the same for every admission path.
- **(b) Anchoring.** `executeCycle` arms the floor with `armFloorWindowLocked()` at the point where the
  cycle has passed the connectivity, `LOCAL_OWNER` and adoption gates and is about to reach the remote
  steps. A cycle that returns early never arms it.
- **(c) Follow-up exemption.** The only path that reads a window is `admit`'s `parkedRequests` branch.
  A trigger that arrives while a cycle is active takes the earlier `pendingFollowUp` branch and returns
  before any window is consulted, so the follow-up obeys neither window.
- **(d) Pull-to-refresh activity.** `PullToRefresh` skips the window *check* but still runs the arm
  step in `executeCycle`, so a manual refresh re-arms the floor for the automatic cycles that follow it.
  It never waits on a window itself, which is the `§9.8` exemption.

Both window writes and reads happen under `cycleMutex`. The floor in particular is armed from
`executeCycle`, which runs on the graph scope's multi-threaded dispatcher holding no lock, so the arming
itself takes the mutex (`armFloorWindowLocked`). Without that, a superseded timer's final `pending = false` could reopen a
floor a newer arming had just set.

## Consequences

### Positive

- The two `§9.8` constants are enforced rather than declared, which is the substance of the `E3-04`
  acceptance criterion that names them.
- Parking makes coalescing exact: a burst of writes becomes one cycle at the window boundary, and no
  trigger is dropped, so the outbox cannot be left with an outstanding row that no later trigger
  happens to revisit.
- Anchoring the interval to the remote steps keeps it a bound on remote traffic rather than a wall
  clock, so an offline or `LOCAL_OWNER` period does not manufacture a floor out of cycles that did no
  work.
- The follow-up exemption preserves `§9.1`'s exactly-one-active-and-one-pending shape without letting
  the floor delay work that is already due.

### Negative

- A trigger's cycle is delayed by up to the window length, which is the intended meaning of the
  debounce and the interval but is a real latency the user can observe after a write.
- The floor is not a simple timer. Its anchoring point is a semantic choice that a reader must find in
  the code, and this ADR is where that choice is recorded.
- A pull-to-refresh holds back the next automatic cycle, which is more than its visible effect. The
  trade is deliberate: the floor bounds remote writes and a manual refresh produces them.
- Exempting the follow-up means two cycles can start closer together than the interval. The interval
  is therefore a bound on the automatic cadence, not on cycle starts in general.

### Constraints Introduced

- A trigger that arrives inside an open `§9.8` window MUST be parked and served at the window boundary,
  never refused.
- `SYNC_MIN_AUTOMATIC_INTERVAL_MS` MUST be anchored to the moment a cycle reaches its remote steps,
  after the connectivity, `LOCAL_OWNER` and adoption gates: not to admission and not to completion.
- A cycle that returns early from those gates MUST NOT arm the floor.
- The single pending follow-up of `§9.1` MUST be exempt from both windows.
- `PullToRefresh` MUST bypass the window check and MUST still re-arm the floor for subsequent automatic
  cycles.
- Every `AdmissionWindow` write and read MUST happen under `cycleMutex`, including the arming
  performed from `executeCycle`.

## Verification

`core/sync/src/commonTest/kotlin/com/ruizurraca/carapp/core/sync/SyncAdmissionPolicyTest.kt` covers each
choice against the declared constants: a trigger inside an open debounce window is parked and served
once the window opens, a burst of writes becomes one cycle, no automatic cycle starts inside
`SYNC_MIN_AUTOMATIC_INTERVAL_MS` of a previous one that reached its remote steps, a cycle refused for
offline or `LOCAL_OWNER` does not arm the floor, the follow-up is not throttled, `PullToRefresh` is
served without waiting while still re-arming the floor, and a resume of exactly the foreground
threshold is not a trigger. The regression guard for the concurrency fixes that accompany this decision
is the same suite plus `DefaultSyncControllerTest`, which step 11.1 runs.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-186`)
- `docs/SPECIFICATION.md`
- `docs/CONTRACTS.md §9.1`, `§9.8`
- `docs/TECHNICAL_PLAN.md`
- `docs/BACKLOG.md` (`E3-04`)
