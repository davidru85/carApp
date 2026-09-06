# ADR-0125 / D-124 - Gate the Automatic Anonymous Retry on There Being Something to Adopt

## Status

Accepted

Taken while implementing `E2-06` on 2026-09-06.

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

The state that distinguishes the two devices is "the owner started using the app offline". The
question is where that state is read from.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| The presence of rows still owned by the sentinel (Selected) | No new persisted state and no new migration; survives process death because it is the product data itself; it is precisely the condition adoption exists for, so the retry never runs when it would have nothing to do | A device whose owner chose the local start but has created nothing yet acquires its UID only at the first write, not at the moment connectivity returns |
| The in-memory `SessionPhase.LOCAL` of `SessionStateHolder` | Reads the owner's actual choice | Lost on process death, which is the common case for a device that was offline for hours; the retry would then never fire until the owner reopened the welcome screen |
| A persisted device-local "local start accepted" flag | Reads the choice and survives restarts | A schema change to `user_settings` or a new table, its migration and its migration tests, for one boolean; a second source of truth about the session that can disagree with the data |

## Decision

`LocalOwnerAdoption.acquireAnonymousUidIfWaiting()` runs the `§11.2` retry only when all three hold:
the current owner is the sentinel, the auth state is `SignedOut`, and at least one row is still owned
by the sentinel. The connectivity trigger calls exactly this function.

The deferred case is bounded and self-correcting: a device whose owner chose the local start and has
written nothing has nothing to adopt, and the first write it makes puts it back in scope of the
retry. Nothing is lost by waiting, because there is nothing to lose yet.

## Consequences

### Positive

- Returning connectivity never creates an account for a device whose owner has not started using the
  app, which keeps `E2-03`'s "no sign-in without naming it" property intact.
- The condition needs no storage, no migration and no second source of truth.
- The retry is skipped exactly when it would be a no-op, so it costs one indexed count.

### Negative

- The moment of acquisition is the first write after connectivity, not the moment of connectivity,
  for a device that started locally and wrote nothing.

### Constraints Introduced

- The retry MUST NOT run for an owner that is not the sentinel, and MUST NOT run while the auth state
  is `Unknown`; only `SignedOut` is a retryable state.
- The retry MUST stay gated on adoptable data. A future trigger that wants an account without one
  needs its own decision.

## Verification

- `LocalOwnerAdoptionTest` in `:shared` covers all three gates: waiting data acquires exactly one
  UID, an empty local database acquires none, and an already authenticated session acquires none.
- The end-to-end path is covered through `DefaultAppGraph`, where the only action taken is the
  authentication itself.

## References

- `docs/DECISION_BOARD.md` (`D-124`)
- `docs/CONTRACTS.md` sections 11.2 and 11.4
- `docs/SPECIFICATION.md` section 7 F-1
- [ADR-0115](0115-native-to-shared-sign-in-handoff.md) (`D-114`)
