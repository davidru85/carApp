# ADR-0166 / D-165 - Close the Departure Process-Death Window in E2-05

## Status

Accepted

Supersedes [ADR-0164](0164-accept-the-departure-process-death-window.md) (`D-163`).

## Context

`D-163` accepted a residual risk: the F-5 departure retained its unfinished work in memory only, so
a process death between a successful remote step and a completed local clear left local data for an
account that no longer existed remotely, and lost the retry with the process. It recorded the risk
in `docs/SECURITY.md` and created `E2-09` to close it with a durable marker after `E2-05` merged.

The owner then chose to close the window before merging instead. That is a change to an accepted
decision, so it is recorded here rather than applied silently.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Close the window inside `E2-05` and supersede `D-163` | One merged story leaves no accepted risk behind, and the marker is reviewed together with the flow it protects | Adds a schema version, a migration and launch-time resumption to a pull request that had already gone green after four review rounds |
| B. Keep `D-163` and deliver `E2-09` after `E2-05` merges | Keeps the pull request bounded, exactly as `D-163` intended | `E2-05` merges carrying a known risk, and the follow-up depends on a merge that had not happened |
| C. Branch `E2-09` from the unmerged `E2-05` branch as a stacked pull request | Starts the work immediately while keeping the stories separate | Two chained pull requests, and a rebase for every further change to `E2-05` |

## Decision

The selected option is **A**. `D-163` is `Superseded`, `E2-09` is removed from `docs/BACKLOG.md`
because its acceptance criteria are delivered here, and the durable marker of `D-166` with the
resumption of `D-167` closes the window.

The window is **narrowed, not eliminated**. The marker is written before the first destructive step,
so what remains is the instant between the `D-23` operation returning success and that success being
recorded in the local database. Firebase Auth and SQLite share no transaction, so that gap cannot be
closed by this mechanism, and it is the same class of limit as `D-150`.

## Consequences

### Positive

- `E2-05` merges with no accepted process-death risk of its own beyond the narrowed instant.
- The marker is reviewed in the same gated review as the flow that writes it.

### Negative

- The pull request gained a schema version, a committed migration and launch-time resumption after
  it had already been reviewed four times.
- A residual instant remains and is still recorded in `docs/SECURITY.md`.

### Constraints Introduced

- No document MAY describe the departure as fully recoverable across process death. The recovery
  covers every step the marker records; it does not cover a death between the server call returning
  and its success being written.
- `docs/SECURITY.md` keeps a residual-risk entry scoped to that instant.
- `E2-09` MUST NOT be reintroduced as a separate story for work delivered here.

## Verification

- `AccountDepartureRecoveryTest` builds a fresh coordinator over the same database, exactly as the
  app graph does at launch, and proves every interrupted kind is finished without repeating a step.
- `AccountDepartureDatabaseAccessTest` and the populated v3-to-v4 migration test cover the marker.
- `docs/CONTRACTS.md §11.5` states the recovery and the remaining instant.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-165`)
- `docs/SPECIFICATION.md §7 F-5`, `§12`
- `docs/CONTRACTS.md §11.5`
- `docs/TECHNICAL_PLAN.md §2`, `§6`
- `docs/adr/0164-accept-the-departure-process-death-window.md`
- `docs/handoff-E2-05.md`
