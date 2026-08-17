# ADR-0024 / D-23 - Account Deletion Uses a Firebase Admin Server Operation

## Status

Accepted

Accepted by the owner on 2026-08-17.

## Context

Store compliance requires in-app account deletion. The app also has a strict Firestore security posture: synchronized deletes are tombstone updates and mobile clients never receive hard-delete permission. Those two requirements conflict if account deletion is implemented by client-side Firestore batch deletes.

The deletion path must remove remote user data before deleting the Firebase Auth account. Deleting the auth account first can leave unreachable orphan documents.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Firebase Admin server operation | Preserves strict client Firestore rules; supports real remote purge for account deletion; centralizes destructive ordering and logging. | Adds a backend operation and server-side tests. |
| Client hard-delete exception | Simpler client-only implementation. | Expands mobile client privileges and weakens the tombstone-only Firestore rule model. |
| Tombstone-only account deletion | Reuses sync deletion semantics. | Does not provide a physical remote purge and is weaker for store/account-deletion expectations. |
| Manual support deletion | Avoids backend implementation in MVP. | Poor user experience and not acceptable for normal in-app account deletion. |

## Decision

Use a Firebase Admin server operation for account deletion.

The app re-authenticates if required, then calls the authenticated server operation with the current Firebase user. The server verifies that the caller UID equals the target UID, deletes remote documents under `users/{uid}` in this order: `fuelEntries`, then `vehicles`, and deletes the Firebase Auth user only after remote document deletion succeeds. The app clears local data only after the server operation returns success.

Client Firestore rules keep `allow delete: if false`; account deletion is not implemented as a client Firestore hard-delete exception.

## Consequences

### Positive

- Store-required account deletion can physically purge remote data.
- The mobile client keeps the least-privilege Firestore model.
- Destructive ordering is centralized in one backend operation.

### Negative

- The MVP needs a server-side Firebase component and tests.
- Failure handling must cover partial remote deletion without falsely reporting success.

### Constraints Introduced

- The server operation MUST verify the authenticated caller UID before deleting anything.
- The server operation MUST NOT delete documents outside `users/{uid}`.
- Remote document deletion order is `fuelEntries`, then `vehicles`, then Firebase Auth user deletion.
- Partial document deletion progress MUST NOT be returned as success.
- The operation MUST be idempotent for already-deleted documents and missing auth users.
- Server operation failures are surfaced to the app as `AuthError.AccountDeletionRemoteFailed`.
- Logs MUST be redacted according to `docs/CONTRACTS.md §17` and `docs/SECURITY.md`.
- Mobile client hard deletes remain forbidden by Firestore rules.

## Verification

- `E3-10` implements and tests the Firebase Admin account deletion operation.
- Firestore emulator tests continue to prove client hard deletes are rejected.
- Server-side tests prove caller UID verification, remote deletion ordering, idempotency and failure-before-auth-deletion behavior.

## References

- `docs/DECISION_BOARD.md` (`D-23`)
- `docs/SPECIFICATION.md` §7
- `docs/CONTRACTS.md` §6, §11.1, §11.5 and §16
- `docs/BACKLOG.md` `E3-10`
