# ADR-0155 / D-154 - Keep the Collision Credential in Memory Only

## Status

Accepted

## Context

The collision flow of `docs/CONTRACTS.md §11.3` begins when `linkCredential` fails with
`AuthError.CredentialAlreadyInUse`. The native provider credential that caused the collision is
needed once more, at step 2, to sign into the permanent account that owns it via
`signInWithCredential(credential, allowUidChange = true)`.

Between the collision and that sign-in the app shows a destructive confirmation and waits for the
owner. The question is what happens to the credential across that wait, and specifically whether it
survives a process death.

The rest of the operation is deliberately durable: `D-151` persists the captured snapshot, the
cleanup ticket and the resume phase precisely so the destructive replacement cannot be left half
finished. Making the credential durable too would make the whole flow resumable with no owner
interaction at all. It would also write a bearer secret to the device-local database, which is not
encrypted, and `docs/CONTRACTS.md §17` forbids storing or logging credential material.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. In memory only, beside the anonymous UID it belongs to | No credential material is ever written to disk; the retained value dies with the process; the window it covers ends before anything destructive has happened | A restart between the collision and the confirmed sign-in loses it, so the owner must reacquire it from the provider |
| B. Persist it in the durable marker | The flow resumes with no owner interaction from any point | Writes a bearer secret into an unencrypted local database, against `docs/CONTRACTS.md §17`; the credential outlives the operation if the marker is orphaned; the value is stolen by any attacker with file access, for no gain after the session has already switched |
| C. Persist it in platform secure storage | Keeps the secret out of the plain database while remaining durable | Introduces Keychain and Keystore access, a new platform abstraction and a new owner-level dependency decision, for a resume window measured in seconds; still retains a bearer secret past the moment it is needed |

## Decision

The selected option is **A**. The colliding credential is held in memory only, together with the
anonymous UID it was acquired for, and is dropped whenever the collision stops being actionable: on
confirmation, on cancellation via `clearMessage()`, on a native sign-in failure, on starting another
sign-in or conversion, and on `close()`. A collision whose anonymous session is no longer current
yields nothing to confirm.

## Consequences

### Positive

- No credential material reaches disk, in any form, at any point in the flow.
- The retention window is the shortest one that still works, and it closes before the session
  switch, so nothing destructive has yet happened when it lapses.
- Cancellation is total: dropping the in-memory value is all that is required to leave the anonymous
  account and both data sets untouched.

### Negative

- A restart between the collision confirmation prompt and the confirmed sign-in requires the owner
  to reacquire the credential from the native provider and start the conversion again.

### Constraints Introduced

- The credential MUST NOT be written to the durable marker, the outbox, analytics, logs or crash
  reports.
- Resumption after the step 2 session switch MUST NOT need the credential: every remaining step is
  authorized by the persisted cleanup ticket and the permanent session.
- A confirmation MUST be refused unless the anonymous identity it was raised for is still the
  current session.

## Verification

- `SessionStateHolderTest` covers the typed confirmation, cancellation leaving the anonymous session
  untouched, and the discarding of a collision that is no longer actionable.
- `AccountConversionCoordinatorTest` resumes the operation from every post-confirmation boundary
  using only the persisted marker, proving the credential is not needed after the switch.
- `AccountConversionDatabaseAccessTest` pins the durable marker's columns, none of which holds
  credential material.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-154`)
- `docs/SPECIFICATION.md §7 F-4`
- `docs/CONTRACTS.md §11.1`, `§11.3`, `§17`
- `docs/TECHNICAL_PLAN.md §2`
- `docs/adr/0152-store-the-conversion-marker-in-normalized-tables.md`
- `docs/handoff-E2-04.md`
