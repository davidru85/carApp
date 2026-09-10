# ADR-0165 / D-164 - Defer Departure Integrity Hardening to Post-MVP

## Status

Accepted

## Context

The fourth owner review of pull request #65 identified three low-probability concurrency and
lifecycle gaps in the E2-05 departure flow:

1. Provider operations and retained retries are not atomically bound to the owner that created the
   request. A session switch in the remaining check-to-use window can make later work act on a
   different active owner.
2. `NonCancellable` preserves the mandatory post-destructive coroutine tail, but an Android or iOS
   graph close can dispose the auth client and database while that tail still needs them.
3. An auth transition observed while the outbox count owns presentation state can be consumed
   without being reconciled afterwards, leaving stale phase/provider information or `isBusy` set.

All three require deterministic concurrency coverage and lifecycle design beyond a documentation
correction. None is exercised by the normal single-owner, foreground departure path.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Accept the three gaps for the MVP and track separate post-MVP stories | Keeps PR #65 bounded, records the actual guarantees, and gives each independent concern an explicit owner | The low-probability windows remain until `E5-02`, `E5-03`, and `E5-04` run |
| B. Fix all three in E2-05 | Closes the findings before merge | Broadens an already large authentication pull request with identity serialization, graph-lifecycle ownership, and presentation reconciliation work |
| C. Merge without recording them | No additional work in PR #65 | Leaves stronger claims than the implementation can prove and no discoverable follow-up |

## Decision

The selected option is **A**.

The MVP accepts the three gaps as documented residual risks. `E5-02` owns owner-bound departure
operations, `E5-03` owns mandatory-tail completion during graph close, and `E5-04` owns auth-state
reconciliation after asynchronous departure evaluation.

## Consequences

### Positive

- PR #65 remains focused on the normal E2-05 flow.
- The contract and security documentation no longer overstate the implemented guarantees.
- Each independent improvement has explicit acceptance criteria and a backlog owner.

### Negative

- A rare session switch can make retained or delayed departure work target the wrong active owner.
- A graph close can interrupt the effective completion of a tail whose coroutine remains alive.
- A session switch during the outbox count can leave stale session presentation state.

### Constraints Introduced

- No document MAY claim that E2-05 atomically binds every departure step to its captured owner.
- No document MAY claim that `NonCancellable` keeps graph-owned dependencies alive during graph
  closure.
- No document MAY claim that every auth transition suppressed during departure evaluation is later
  reconciled.
- `E5-02`, `E5-03`, and `E5-04` are deferred post-MVP stories and do not block PR #65 or MVP
  completion.

## Verification

- `docs/BACKLOG.md` contains the three post-MVP stories and their acceptance criteria.
- `docs/CONTRACTS.md` states the current limits next to the affected guarantees.
- `docs/SECURITY.md` records the accepted residual risks.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-164`)
- `docs/SPECIFICATION.md §7 F-5`, `§12`
- `docs/CONTRACTS.md §11.5`, `§20.10`
- `docs/TECHNICAL_PLAN.md §2`
- `docs/BACKLOG.md` (`E5-02`, `E5-03`, `E5-04`)
- `docs/handoff-E2-05.md`
