# ADR-0164 / D-163 - Accept the Departure Process-Death Window

## Status

Superseded by [ADR-0166](0166-close-the-departure-process-death-window-in-e2-05.md) (`D-165`).

The owner chose to close the window inside `E2-05` with a durable recovery marker rather than
accept it and defer the marker to a separate story. The analysis below stands as the record of
why the window existed and what it cost; the acceptance it recommended no longer holds, and the
`E2-09` story it created has been removed from the backlog because its criteria are delivered
in `E2-05`.

## Context

A departure retains its unfinished work in memory so a retry repeats only that part. If the process
dies between a successful remote step and a completed local clear, the retained request dies with it:
local data survives for an account that no longer exists remotely, and the owner is offered no retry.
The documentation described the flow as resumable, which no test demonstrated across a restart.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Accept the risk, record it, and track the fix separately | Keeps E2-05 within scope and stops the documentation claiming a property the code lacks; the gap gets an owner | The window stays open until the follow-up story runs |
| B. Add a durable recovery marker now | Closes the window and makes resumability demonstrable | A new schema version, a committed migration and a populated migration test in the shared-write `:core:database` module, plus resumption at launch — a story's worth of work inside an already large pull request |
| C. Accept it without a follow-up | Shortest | A known gap that leaves data from a deleted account, with nobody assigned to close it |

## Decision

The selected option is **A**.

The residual risk is recorded in `docs/SECURITY.md`, every claim of resumability across process death
is removed, and the durable recovery marker is tracked as `E2-09`.

## Consequences

### Positive

- The contract and the handoff describe what the code actually guarantees.
- The remaining gap is visible and assigned instead of implied.

### Negative

- Until `E2-09`, a process death in that window leaves local data for a deleted account.

### Constraints Introduced

- No document MAY describe the departure as recoverable across process death until an executable
  recovery proves it.
- `E2-09` owns the durable marker and its migration.

## Verification

- The `docs/SECURITY.md` residual-risk entry and the `E2-09` backlog story.
- `docs/CONTRACTS.md §11.5` states the limit explicitly rather than implying resumability.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-163`)
- `docs/SPECIFICATION.md §7 F-5`, `§12`
- `docs/CONTRACTS.md §11.5`, `§20.2`, `§20.10`
- `docs/TECHNICAL_PLAN.md §2`
- `docs/handoff-E2-05.md`
