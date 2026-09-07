# ADR-0142 / D-141 - Use Server-Issued Orphan Cleanup Tickets

## Status

Accepted

## Context

The confirmed account-linking collision flow must delete the abandoned anonymous identity after
the client has switched to the existing permanent account. The operation is resumable, so its
authorization must survive Firebase ID token expiry without allowing one account to select
another account for deletion.

D-140 attempted to reuse an expired Firebase ID token by verifying its RS256 signature manually.
Security review found that the implementation omitted `aud` and `iss` validation and preferred an
attacker-controlled top-level `uid` custom claim over `sub`. A foreign-project token signed by the
shared Google signer could therefore authorize deletion of an arbitrary UID. Correcting those
checks in place would still leave a destructive operation dependent on a hand-written expired JWT
verifier, public-certificate fetching and key rotation.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **Server-issued cleanup ticket while the anonymous session is active (accepted)** | Uses Firebase's normal verified callable context at issuance; binds authorization to exactly one server-selected UID; survives account switching and token expiry; removes all custom JWT verification. | Adds one callable invocation, one durable client marker and one internal Firestore record with retention policy. |
| Correct D-140 in place with `aud`, `iss` and canonical `sub` validation | Smallest wire change and no new collection. | Retains a bespoke verifier for expired bearer credentials, certificate-network dependency and key-rotation failure modes on a destructive endpoint. |
| Delete the anonymous Auth user before switching accounts | Needs no delayed capability. | Irreversibly destroys the active session before permanent sign-in and snapshot replacement succeed, breaking cancellation and failure recovery. |

## Decision

Before account switching, the authenticated anonymous client calls the 2nd gen callable
`issueOrphanCleanupTicket`. The callable accepts no client-selected UID, requires
`firebase.sign_in_provider == "anonymous"`, generates 32 cryptographically random bytes and
returns their 43-character base64url encoding as `cleanupTicket`.

The server stores only the ticket's SHA-256 digest as the document ID in
`orphanCleanupTickets/{ticketHash}`. The record contains the verified anonymous UID,
`expiresAt = issuedAt + 30 days`, and status `PENDING`. Mobile clients have no Firestore access to
this collection. Its `expiresAt` field has TTL enabled and no single-field indexes. The raw ticket
exists only in the callable response and the client's durable collision marker.

After switching accounts and replacing the snapshot, the authenticated permanent client calls
`deleteOrphanedAnonymousAccount` with `{cleanupTicket}`. The callable requires a non-anonymous
caller, hashes the ticket, resolves the server-bound UID, rejects absent or expired tickets and
rejects a bound UID equal to the caller UID. It then deletes the anonymous Auth user, treating
`auth/user-not-found` as idempotent success, invokes `deleteUserData`, and marks the ticket
`COMPLETED` only after both deletion stages succeed. A completed, unexpired ticket returns the
same success response without repeating deletion. There is no ID-token or JWT fallback.

Both callables use `europe-west1`, `maxInstances: 2`, `memory: "256MiB"` and
`timeoutSeconds: 60`. D-132 keeps Functions App Check unenforced for the ticket issuer and both
deletion callables; Firebase Authentication, the unguessable single-purpose ticket, server-side
UID binding and D-66 cost controls provide the accepted boundary. Changing that posture requires
a new owner decision covering all three callables.

## Consequences

### Positive

- A client never supplies the UID stored in the cleanup authorization.
- Authorization remains valid across account switching and normal ID-token expiry without custom
  JWT parsing, public-certificate retrieval or foreign-project ambiguity.
- Completion-last state preserves safe retries after Auth deletion, remote-data deletion or a lost
  callable response.
- The 30-day lifetime bounds bearer exposure while matching the collision flow's durable recovery
  horizon; TTL removes expired and completed records asynchronously.

### Negative

- The collision flow gains a required online issuance step before switching accounts.
- Firestore retains a hashed capability record and anonymous UID until TTL cleanup. D-143
  (ADR-0144) amends this consequence: account deletion now purges every authorization bound to
  the deleted UID before Auth deletion, so the TTL is only a bounded fallback for abandoned or
  completed authorizations, never the normal account-deletion retention path.
- A stolen raw ticket remains a bearer capability until it expires or is completed, although it
  cannot select a UID and still requires an authenticated permanent caller.
- The ticket binds the UID's account state at issuance only. D-142 (ADR-0143) amends this threat
  model: the bound account may cease to be anonymous between issuance and consumption, so
  consumption revalidates D-134 eligibility before any destructive stage.

### Constraints Introduced

- The issuer MUST accept no client-selected UID and MUST derive the bound UID only from verified
  callable authentication context.
- Tickets MUST contain at least 256 random bits, MUST be validated in their canonical 43-character
  base64url form and MUST be stored only as SHA-256 digests on the server.
- `orphanCleanupTickets` MUST remain inaccessible to mobile Firestore clients.
- The TTL field override in `firestore/firestore.indexes.json` MUST remain enabled and unindexed.
- The delete callable MUST mark completion last and MUST NOT restore an ID-token verification
  fallback.
- Logs MUST NOT contain UIDs, raw tickets, ticket hashes, request payloads or raw provider errors.

## Verification

- `orphanedAnonymousAccount.test.mjs` pins issuance authentication, server-derived UID binding,
  ticket shape, digest-only persistence, expiry, permanent-caller checks, completion-last retries,
  idempotency, error mapping and redacted logs.
- `orphanCleanupTicketPolicy.test.mjs` rejects the superseded token verifier and pins the TTL field
  override.
- `orphanedAnonymousAccountEmulator.test.mjs` proves the real Admin Firestore authorization and
  deletion lifecycle without trigger delivery.
- `firestore.rules.test.mjs` proves mobile clients cannot read, create, update or delete internal
  authorization records.
- Function metadata tests pin the two callables' region, generation and runtime bounds.

## References

- `docs/CONTRACTS.md §11.3`, `§11.5`, `§16`
- `docs/adr/0141-verify-expired-anonymous-token-cryptographically-for-resumable-cleanup.md`
- `docs/BACKLOG.md` (`E2-04`, `E3-11`)
- `docs/SECURITY.md`
