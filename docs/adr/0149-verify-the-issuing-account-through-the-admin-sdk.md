# ADR-0149 / D-148 - Verify the Issuing Account Through the Admin SDK

## Status

Accepted

## Context

`D-141` (ADR-0142) made `issueOrphanCleanupTicket` the only way to obtain an orphan-cleanup
authorization, and required "authenticated callable context with
`firebase.sign_in_provider == anonymous`". `D-142` (ADR-0143) later established, for the consumption
callable, that a claim is only evidence of what was true when the token was minted, and that the
bound account's *current* Admin record must be re-verified before any destructive stage.

The issuer never received the same treatment. It read `request.auth.uid` and the
`sign_in_provider` claim and wrote the authorization, with no Admin call at all. Callable token
verification does not perform a revocation or current-user check by default, so an ID token minted
while an account was anonymous stays valid for the remainder of its lifetime after that account is
linked to a permanent credential, disabled, or deleted. A holder of such a token could therefore
mint a cleanup ticket for an identity that is no longer eligible for one, including a UID whose
authorizations `deleteAccount` had already purged under `D-143`.

The asymmetry was the defect: the consumption side re-verified, the issuing side did not, and the
issuing side is where the authorization is created.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **Resolve the caller's Admin record before writing anything (accepted)** | The authorization is created only against the account's current state; reuses the single `D-134` anonymity definition already shared by the trigger and the consumption callable; symmetric with `D-142`. | One Admin read on every issuance, and one more failure mode to classify and redact. |
| Require a recently issued token (`auth_time` freshness) | No Admin call. | Freshness is not eligibility: a token minted seconds ago is still anonymous-by-claim for an account linked immediately afterwards, and the MVP has no re-authentication step here to make freshness meaningful. |
| Rely on consumption-time revalidation alone (`D-142`) | No change at all. | The authorization record is created regardless, so `anonymousUid` is persisted for an ineligible identity; that is precisely the state `D-143` exists to prevent. |

## Decision

`issueOrphanCleanupTicket` resolves the caller's current Firebase Auth record through the Admin SDK
before it generates or persists anything, and issues a ticket only when
`canIssueOrphanCleanupTicket` holds: the record exists, is **not disabled**, and still satisfies the
shared `D-134` empty-`providerData` predicate. A linked, disabled or missing record is rejected with
`failed-precondition` and creates no authorization.

Disabled records are rejected as well as linked and missing ones, because disabling is how an
account is taken out of service without deleting it, and a stale ID token outlives that change
exactly as it outlives linking.

An Admin lookup failure is not an eligibility answer: it maps to `internal` with the redacted
`AUTH_USER` stage log, and it too creates no authorization. Neither path attaches the UID, the
token, the request payload or the raw provider failure to a log or to the returned error.

The predicate is a single shared function so the trigger (`D-134`), the consumption callable
(`D-142`) and the issuer cannot drift apart on what "anonymous" means.

## Consequences

### Positive

- A stale anonymous token cannot mint an authorization for a linked, disabled or deleted identity.
- The issuing and consuming sides of the ticket now apply the same current-state rule.
- The rejection is typed and distinguishable from an infrastructure failure by its callable code.

### Negative

- Every issuance costs one Admin read, on a path that previously touched only Firestore.
- An Admin outage now blocks issuance, where it previously would not have. That is the intended
  trade: without the record, eligibility is unknown, and issuing anyway is what this decision
  forbids.

### Constraints Introduced

- `issueOrphanCleanupTicket` MUST NOT create an authorization before the Admin record is resolved
  and found eligible.
- Eligibility MUST use the single shared `D-134` predicate; a second definition of "anonymous" is a
  contract failure.
- An eligibility rejection MUST be `failed-precondition` and an Admin lookup failure MUST be
  `internal` with the redacted `AUTH_USER` stage; neither may leak the UID, token, payload or raw
  failure.

## Verification

- `functions/test/orphanedAnonymousAccount.test.mjs` pins the three stale-claim rejections (linked,
  disabled, deleted), each asserting that the calls list holds the lookup alone so no authorization
  was created; the `internal` classification of an Admin lookup failure with its redacted stage
  log; the redaction of the rejection itself; and the ordering of the lookup before the write on the
  happy path.
- `functions/test/orphanedAnonymousAccountEmulator.test.mjs` exercises the real Admin gateway.

## References

- `docs/CONTRACTS.md` §11.5
- `docs/adr/0142-use-server-issued-orphan-cleanup-tickets.md` (`D-141`)
- `docs/adr/0143-revalidate-ticket-bound-anonymity-at-consumption.md` (`D-142`)
- `docs/adr/0135-deleted-user-eligibility-for-the-anonymous-cleanup-trigger.md` (`D-134`)
- `docs/adr/0150-close-the-ticket-issuance-and-account-deletion-race.md` (`D-149`, the remaining
  interleaving this decision deliberately does not close)
