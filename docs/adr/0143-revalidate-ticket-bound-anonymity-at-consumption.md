# ADR-0143 / D-142 - Revalidate Ticket-Bound Anonymity at Consumption

## Status

Accepted

## Context

D-141 (ADR-0142) replaced the superseded expired-token verifiers with a server-issued cleanup
ticket. The ticket cannot select a UID: it is bound at issuance to the verified anonymous caller
and only its SHA-256 digest is stored. Security review of PR #60 nevertheless found a reachable
data-loss sequence that the D-141 threat model does not cover:

1. Anonymous UID A obtains a cleanup ticket (`docs/CONTRACTS.md` §11.3 step 1).
2. Step 2 (`signInWithCredential`) fails or is abandoned; the durable client operation marker
   retains the raw ticket for its full 30-day lifetime.
3. The user later links UID A to a permanent credential without a collision, so A keeps its UID
   and becomes a permanent account.
4. Still later the user signs in to a different permanent account B and a stale marker retry
   invokes `deleteOrphanedAnonymousAccount`.

The existing `orphanUid === callerUid` guard does not fire, because the caller is B. Without an
additional check the callable deletes permanent account A and its Firestore data.

D-134 defines anonymous eligibility as an empty `providerData` list, but before this decision that
predicate was enforced only in `onAnonymousUserDeleted` (the automatic trigger path), never on the
ticket-authorized destructive path.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **Revalidate eligibility at ticket consumption (accepted)** | Closes the data-loss sequence with one additional Admin `getUser` call; reuses the single D-134 predicate; preserves missing-account convergence for completion-last retries. | Adds one Auth read to every first destructive attempt; a bound account that legitimately remains anonymous is looked up twice (issuance context, consumption). |
| Re-check only the `sign_in_provider` claim of the bound account | Cheaper conceptual change. | `providerData` is the D-134 definition; a second predicate would fork the eligibility rule the trigger already enforces. |
| Accept issuance-time anonymity for the ticket lifetime | No code change. | Leaves the documented data-loss sequence open for up to 30 days per ticket. |
| Purge the ticket when its bound account links to a credential | No consumption-time lookup. | Requires a second Auth trigger, expands the 1st gen surface TD-01 keeps closed, and races with link operations that do not fire triggers on all platforms. |

## Decision

`deleteOrphanedAnonymousAccount` extends its Auth gateway with
`getUser(uid): Promise<AuthUserProviderSnapshot | null>`, implemented through the Firebase Admin
SDK. Before any destructive stage, after resolving the ticket and rejecting caller/bound-UID
equality, the callable:

- resolves the bound UID's current Auth record;
- rejects the request with `failed-precondition` when the account exists and its `providerData`
  list is not empty (the account ceased to be anonymous between issuance and consumption);
- treats `auth/user-not-found` as idempotent success, preserving the existing
  missing-anonymous-Auth-user convergence and completion-last retry semantics;
- maps any other lookup failure to `internal` with the existing redacted `AUTH_USER` stage log.

The eligibility predicate is the single shared D-134 definition (`isAnonymousAuthUser` in
`functions/src/auth/anonymousUserEligibility.ts`), also used by `onAnonymousUserDeleted`; it is
extracted into a shared module rather than duplicated, so D-134 has exactly one definition.

## Consequences

### Positive

- A ticket issued for an anonymous account can never authorize deletion of an account that has
  since become permanent, for the full 30-day ticket lifetime.
- D-134 eligibility has one executable definition shared by the trigger and the callable.
- Missing bound accounts still converge: direct remote deletion and completion-last retries are
  unchanged.
- The error taxonomy stays closed: the rejection is the existing `failed-precondition` code.

### Negative

- Every first destructive attempt performs one additional Admin Auth read.
- A stale ticket whose bound account became permanent fails permanently with
  `failed-precondition` instead of expiring silently; the client must surface the typed error.

### Constraints Introduced

- The ticket-authorized path MUST re-verify D-134 eligibility before any destructive stage.
- The eligibility predicate MUST remain the single shared D-134 definition; duplicating it in the
  callable is forbidden.
- `auth/user-not-found` on the bound-account lookup MUST remain idempotent success.
- A non-`auth/user-not-found` lookup failure MUST map to `internal` with the redacted `AUTH_USER`
  stage log and MUST NOT leak the raw provider failure.

## Verification

- `functions/test/orphanedAnonymousAccount.test.mjs` covers: a ticket bound to a UID that now has
  a federated provider entry is rejected before any deletion; a phone-only bound account is
  rejected; a still-anonymous bound account is deleted as before; a missing bound account still
  converges; an unexpected Auth lookup failure maps to `internal` with redacted logs.
- The RED run of those tests failed before the fix exactly on the now-permanent and phone-only
  cases, proving the defect was reachable.

## References

- `docs/CONTRACTS.md` §11.3 (step 5), §11.5 (callable description, error taxonomy)
- `docs/adr/0142-use-server-issued-orphan-cleanup-tickets.md` (amended threat model)
- `docs/adr/0135-deleted-user-eligibility-for-the-anonymous-cleanup-trigger.md` (D-134)