# ADR-0125 / D-124 - Gate the Automatic Anonymous Retry on an Explicit Local Start

## Status

Accepted

Taken while implementing `E2-06` on 2026-09-06 and revised the same day, before merge, in the
owner's review of pull request #55. The first draft gated the retry on the presence of `LOCAL_OWNER`
rows alone; the owner rejected that gate as an inaccurate proxy for the owner's decision and
identified a case it strands. This record describes the revised, implemented behaviour. No
superseding decision was created, because `D-124` has never been merged.

## Context

`docs/CONTRACTS.md §11.2` says that when first launch cannot reach Firebase Auth, the app runs under
the `LOCAL_OWNER` sentinel and "anonymous UID acquisition is retried in the background when
connectivity returns; on success, local-owner adoption runs". `E2-06` requires that adoption be
"triggered automatically when connectivity returns, not only from a UI action".

Taken literally, "retry when connectivity returns" would also fire for a device that has never left
the welcome screen. That device is signed out and its owner is the sentinel, exactly like the device
the rule is written for, but it has made no choice. Creating a Firebase anonymous account for it
would be the app deciding on the owner's behalf, and `E2-03` deliberately made the welcome screen
name every sign-in affordance rather than start one implicitly.

So the retry needs to know that the owner asked for a local session. Two things can carry that:

- the choice itself, which is made in `SessionStateHolder.startAnonymousSignIn()` and falls back to
  `SessionPhase.LOCAL` when the acquisition fails; and
- rows written under the sentinel, which only exist because the owner was using the app locally.

Neither is sufficient alone. The choice lives in memory and does not survive a process restart. The
rows are durable but lag the choice: an owner who starts locally and has not created a vehicle yet
has made the decision and left no trace of it.

The rows-only gate also strands a device. If connectivity is available at the moment the owner starts
locally, the connectivity signal has already fired and finds nothing to adopt. There is no later
edge to react to while the network stays up, so the device keeps the sentinel indefinitely even
though it is online and has data.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| The explicit choice, plus rows as durable evidence, plus a re-evaluation on the first local write (Selected) | Represents the owner's actual decision; survives a restart through the rows; the write trigger closes the stranded case that no connectivity edge would ever reopen; still needs no persisted field and no migration | Two signals and two triggers instead of one; the in-memory half is lost on restart and is covered by the rows rather than by itself |
| The presence of `LOCAL_OWNER` rows alone | One condition, no in-memory state | Rows are not the decision: an owner who chose the local start and wrote nothing is invisible. Strands a device that was online when it started locally, because the only connectivity edge fired before any row existed |
| The in-memory `SessionPhase.LOCAL` alone | Reads the owner's actual choice | Lost on process death, which is the common case for a device that was offline for hours; the retry would never fire until the owner reopened the welcome screen |
| A persisted device-local "local start accepted" flag | One durable signal | A schema change to `user_settings` or a new table, its migration and its migration tests, for one boolean, when the rows already carry the same fact durably; a second source of truth about the session that can disagree with the data |

## Decision

`LocalOwnerAdoption.acquireAnonymousUidIfWaiting()` performs the `§11.2` retry when **all** of the
following hold:

1. the current owner is the `LOCAL_OWNER` sentinel;
2. the auth state is `SignedOut`;
3. no acquisition is already in flight; and
4. **either** this process still remembers that the owner explicitly chose "continue without an
   account" and that choice fell back to a local session, **or** at least one row is still owned by
   the sentinel.

`SessionStateHolder` records the choice by calling `LocalOwnerAdoption.onLocalStartAccepted()` on the
branch of `startAnonymousSignIn()` that falls back to `SessionPhase.LOCAL`. That is the only place
the product turns "continue without an account" into a local session.

Two triggers call the function:

- **Connectivity becoming available.** The `§11.2` trigger.
- **A write committing under the sentinel.** The vehicle repository calls
  `LocalOwnerAdoption.onLocalOwnerWriteCommitted()` after a successful write. A vehicle is
  necessarily the first row a local session creates, because a fuel entry needs one, so this is the
  moment a device that started locally first gains something to adopt. It exists for the device that
  was already online when it started locally and therefore has no later connectivity edge. The
  re-evaluation is launched, not awaited: a local write MUST NOT wait on a network round trip to
  report that it succeeded.

Concurrency is resolved by dropping, not queueing. The function takes a dedicated lock with
`tryLock`; a trigger that arrives while an acquisition is in flight returns without attempting, so
concurrent triggers produce one attempt rather than two.

No persisted field and no migration were added. The durable half of the signal is the product data
itself, which is already stored, already migrated and cannot disagree with the rows the retry exists
to adopt.

## Consequences

### Positive

- Returning connectivity never creates an account for a device whose owner has not started using the
  app, which keeps `E2-03`'s "no sign-in without naming it" property intact.
- An owner who chose the local start is retried even before they have written anything.
- A device that was online throughout is not stranded under the sentinel waiting for a connectivity
  edge that will never come.
- No storage, no migration and no second source of truth.

### Negative

- The signal has two halves with different lifetimes, and the ADR has to say which covers which case.
- The write trigger runs on every successful vehicle write under the sentinel, not only the first.
  The guards make repeats cheap, and a successful acquisition ends them by changing the owner.
- The launched re-evaluation is not awaited by the write, so a test that wants to observe it must
  await its effect rather than the write's return.

### Constraints Introduced

- The retry MUST NOT run for an owner that is not the sentinel, and MUST NOT run while the auth state
  is `Unknown`; only `SignedOut` is a retryable state.
- The retry MUST NOT create an account for a device that has neither the explicit choice nor rows.
- Concurrent triggers MUST produce at most one acquisition attempt.
- A future trigger that wants an account without either signal needs its own decision.

## Verification

`LocalOwnerAdoptionTest` in `:shared`:

- no explicit choice and no rows, connectivity returns: no account is created;
- the explicit choice was made, the first attempt failed, connectivity returns before any row exists:
  exactly one retry;
- rows only, with no in-memory signal, as a process restart leaves it: the retry runs;
- an already authenticated session: no retry;
- a trigger arriving while an acquisition is in flight: one attempt, not two;
- through `DefaultAppGraph`, with the device online throughout, the first vehicle write triggers the
  acquisition that the single early connectivity emission could not;
- through `DefaultAppGraph`, a failed "continue without an account" is retried when the network
  returns, with no row in the database, which also proves the `SessionStateHolder` wiring.

## References

- `docs/DECISION_BOARD.md` (`D-124`)
- `docs/CONTRACTS.md` sections 11.2 and 11.4
- `docs/SPECIFICATION.md` section 7 F-1
- [ADR-0115](0115-native-to-shared-sign-in-handoff.md) (`D-114`)
- [ADR-0126](0126-hold-vehicle-reads-until-adoption-has-run.md) (`D-125`)
