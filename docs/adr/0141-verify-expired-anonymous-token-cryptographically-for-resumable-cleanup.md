# ADR-0141 / D-140 - Verify Expired Anonymous Token Cryptographically for Resumable Cleanup

## Status

Accepted

## Context

During the confirmed account-linking collision flow (`docs/CONTRACTS.md §11.3`), the client captures
the anonymous ID token in step 1, while steps 2–4 are resumable. If the flow is interrupted or delayed
(e.g., app termination, lost network connectivity, or delayed user adoption) until the anonymous ID token's
1-hour TTL expires, the client calls `deleteOrphanedAnonymousAccount` in step 5 for the first time while
the anonymous Auth user still exists. Similarly, if an initial callable attempt failed prior to Auth deletion,
a delayed retry also encounters an expired token with an active Auth user.

Because the client has already signed into the permanent account in step 2/3a, the client cannot refresh the
abandoned anonymous token. Under D-139, the callable only permitted an expired token if the user was already
deleted from Firebase Auth (`auth/user-not-found`); if the user still existed, it returned `invalid-argument`.
This resulted in permanent non-convergence, violating the normative requirement that retry after any
interruption MUST converge.

D-139 rejected active Auth deletion on expired tokens because decoding unverified JWT payloads would allow an
attacker to forge expired claims and delete active anonymous accounts. However, verifying the token's
cryptographic signature against Google's public x509 certificates proves authenticity without relying on unverified claims.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **Option A (Accepted, D-140): Cryptographic RS256 signature verification against Google public certs with an `iat` window** | Retains the exact `{ anonymousIdToken }` wire contract and F-4 step sequence; guarantees complete convergence whether Auth was deleted or not; mathematically proves Google issued the token for this anonymous account without trusting unverified claims. | Requires fetching and caching Google public certificates; tokens delayed beyond key rotation/`iat` bounds (30 days) require fallback. |
| Option B: Server-side cleanup authorization ticket / lease in step 1 | Cryptographically authorized by the active session before account switch. | Adds network dependency and complexity to step 1 (E2-04); requires new callable/collection. |
| Option C: Reorder F-4 lifecycle (delete Auth in step 1, remote data in step 5) | Relies on existing D-139 `auth/user-not-found` path. | Leaves user with a destroyed Auth account if permanent sign-in fails or is cancelled in step 2; changes client protocol contract. |

## Decision

Superseding D-139, when `deleteOrphanedAnonymousAccount` verifies `anonymousIdToken`:
1. If `auth.verifyIdToken(token)` throws `auth/id-token-expired`:
   - Cryptographically verify the token's RS256 signature against Google's public certificates (`https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com`).
   - Validate header (`alg === "RS256"`, non-empty `kid`) and payload claims (`sub`, `firebase.sign_in_provider === "anonymous"`).
   - Enforce an `iat` (issued-at) bound within 30 days (`MAX_EXPIRED_TOKEN_AGE_SECONDS = 30 * 24 * 3600`) and clock skew tolerance (300s).
   - If signature verification fails, `kid` is unknown, `iat` is out of bounds, or claims are invalid, reject with `invalid-argument`.
   - Once verified, query Admin Auth via `auth.getUser(orphanUid)`:
     - If the user is present: proceed to delete Auth user (`auth.deleteUser`), then delete remote data (`deleteUserData`), converging on complete cleanup.
     - If `getUser` throws `auth/user-not-found`: skip Auth deletion and complete remote data deletion.
     - If `getUser` or certificate fetching throws an infrastructure failure, log stage `"AUTH_USER"` in redacted form and return `internal`.
2. Permanent caller verification (`callerUid !== orphanUid` and caller provider !== "anonymous") is strictly enforced.
3. `OrphanCleanupAuthGateway.getUser` is made mandatory (no optional seams).

## Consequences

### Positive

- Complete self-healing convergence under `docs/CONTRACTS.md §11.3` after any interruption in steps 2–4 or delayed retries.
- Cryptographic proof guarantees authenticity; no unverified claims are trusted.
- No protocol changes in client flow (E2-04) or extra Firestore round trips.
- Eliminated unexercised seam `getUser?` on `OrphanCleanupAuthGateway`.

### Negative

- Functions runtime must fetch and cache Google's public certificates.

### Constraints Introduced

- Public certificates are cached respecting HTTP `Cache-Control` header `max-age` (defaulting to 6 hours).
- Maximum allowable expired token age is capped at 30 days.

## Verification

- `functions/test/orphanedAnonymousAccount.test.mjs`:
  - Interruption during steps 2–4 with token expiry converges on deleting existing anonymous Auth user and Firestore collections.
  - Delayed retry after earlier failure before Auth deletion converges even after token expiry.
  - Expired token retry after Auth deletion converges by skipping Auth deletion.
  - Expired token with invalid cryptographic signature is rejected with `invalid-argument`.
  - Expired token with unknown `kid` is rejected with `invalid-argument`.
  - Expired token with `iat` older than 30 days is rejected with `invalid-argument`.
  - Transient failure during certificate retrieval maps to `internal` with stage `AUTH_USER`.
- `functions/test/orphanedAnonymousAccountEmulator.test.mjs`:
  - Passes against real Firestore emulator.

## References

- `docs/CONTRACTS.md §11.3`, `§11.5`
- `docs/adr/0140-permit-expired-anonymous-token-on-retry-when-auth-user-deleted.md` (superseded D-139)
- `docs/BACKLOG.md` (`E3-11`)
