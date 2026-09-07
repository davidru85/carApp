# ADR-0140 / D-139 - Permit Expired Anonymous Token on Retry When Auth User Is Deleted

## Status

Superseded by ADR-0141 (D-140)

## Context

During the confirmed account-linking collision flow (`docs/CONTRACTS.md §11.3`), the client switches
to the permanent account in step 2 and calls `deleteOrphanedAnonymousAccount` in step 5 with the
captured anonymous ID token.

If `deleteOrphanedAnonymousAccount` successfully deletes the Firebase Auth user through the Admin
SDK but fails during remote data deletion (`deleteUserData`), the callable returns `internal`. If a
subsequent retry occurs after 1 hour (e.g. following a network interruption or app restart), the
captured anonymous token has expired (`auth/id-token-expired`). Because the client is signed in as
the permanent user and the anonymous account was already deleted from Firebase Auth, the client
cannot refresh the token. Under standard token verification, the retry permanently fails with
`invalid-argument`, stranding the orphan Firestore data and violating the §11.3 retry convergence
invariant.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **Option A (Accepted): Permit well-formed expired anonymous token on retry if and only if Auth user is already deleted (`auth/user-not-found`)** | Retains the exact wire contract `{ anonymousIdToken }`; requires no new Firestore collections, documents or extra writes; convergence is self-healing. | Requires extracting payload claims from the expired token and confirming Auth account deletion status via `getUser`. |
| Option B: Server-side Firestore cleanup intent / lease document | Persisted state in Firestore before deleting Auth user. | Adds a new internal collection, extra read/write operations and cost on every cleanup execution, and orphan lease management. |
| Option C: Reorder deletion (remote data first, Auth user last) | Data deleted before Auth account. | Does not solve 1-hour expiry if data deletion fails initially and the retry occurs > 1 hour later, because the client cannot refresh the token after account switch. |

## Decision

When `deleteOrphanedAnonymousAccount` verifies `anonymousIdToken`:
1. If `auth.verifyIdToken(token)` throws `auth/id-token-expired`:
   - Parse the unverified token payload to extract `uid` and verify standard claims (`firebase.sign_in_provider === "anonymous"` and `orphanUid !== callerUid`).
   - Query Admin Auth via `auth.getUser(orphanUid)`.
   - If `getUser` throws `auth/user-not-found`, the anonymous user was already deleted on a prior attempt. The callable skips Auth deletion and completes `deleteUserData({ firestore, uid: orphanUid })`.
   - If the user still exists in Auth, or if claims are invalid, the expired token is rejected with `invalid-argument`.
2. Admin SDK transient/infrastructure failures during verification or user lookup map to `internal` with stage `"AUTH_USER"` logged in redacted form.

## Consequences

### Positive

- Retries after any interruption converge on complete cleanup even if > 1 hour has elapsed.
- No changes to the callable request payload or response contract (`D-133`).
- Zero added database reads/writes during normal executions.
- Active Auth accounts cannot be deleted with expired tokens.

### Negative

- Token verifier must handle `auth/id-token-expired` by inspecting decoded payload and checking Auth user existence.

### Constraints Introduced

- `OrphanCleanupAuthGateway` exposes `getUser(uid: string): Promise<{uid: string}>` implemented by `admin.auth().getUser(uid)`.
- An expired token is accepted only when `getUser` returns `auth/user-not-found` and the payload's `sign_in_provider` is `"anonymous"`.

## Verification

- `functions/test/orphanedAnonymousAccount.test.mjs` verifies:
  - An initial failure during remote data deletion followed by a retry with an expired anonymous token converges on complete remote data deletion.
  - An expired token for an Auth user that still exists is rejected with `invalid-argument`.
  - An expired token with a non-anonymous provider is rejected with `invalid-argument` or `failed-precondition`.
  - Transient Auth verification errors map to `internal`.

## References

- `docs/CONTRACTS.md §11.3`, `§11.5`
- `docs/adr/0134-fix-the-orphan-cleanup-callable-wire-contract.md` (`D-133`)
- `docs/BACKLOG.md` (`E3-11`)
