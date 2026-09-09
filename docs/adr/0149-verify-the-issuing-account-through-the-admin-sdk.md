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
`canIssueOrphanCleanupTicket` holds: the record exists, is **known to be enabled** — an explicit
`disabled === false` state — and still satisfies the shared `D-134` empty-`providerData` predicate.
A linked, disabled, missing or state-unknown record is rejected with `failed-precondition` and
creates no authorization.

**Exact scope of this decision.** `canIssueOrphanCleanupTicket` decides a fact about the snapshot
the Admin lookup returned. This decision therefore guarantees exactly this much:

- the Admin record observed by the lookup **existed**, was **explicitly enabled**
  (`disabled === false`) and satisfied the shared `D-134` anonymity predicate;
- a token whose identity was **already** linked, disabled, deleted or state-unknown when that lookup
  resolved is rejected, and no authorization is created — the useful, fail-closed guarantee;
- it does **not** prove that the identity remains eligible until the subsequent Firestore write
  commits.

Firebase Auth and Firestore share no atomic transaction, so `auth.getUser`, the predicate and
`authorizations.issue` are three separate operations and the account can move between them. That
residual window — linking, disabling or deletion after an eligible snapshot has been observed and
before the authorization write commits — is **not** closed here. It is `D-150` (ADR-0151), an
unresolved owner decision, with `E3-16` as its story. No wording in this repository may describe
`D-148` as making a stale token unable to mint an authorization for a linked, disabled or deleted
identity without that qualification.

Disabled records are rejected as well as linked, missing and state-unknown ones, because disabling
is how an account is taken out of service without deleting it, and a stale ID token outlives that
change exactly as it outlives linking. The enabled state is required explicitly rather than inferred
from the absence of a `disabled` value: eligibility is a positive fact about the current record, and
a snapshot that does not carry the value is not known to be enabled, so the predicate fails closed
rather than treating an unknown state as enabled. The production
`FirebaseAdminAuthDeletionGateway.getUser` returns the real `UserRecord.disabled` value, so the
concrete gateway always supplies the explicit state.

An Admin lookup failure is not an eligibility answer: it maps to `internal` with the redacted
`AUTH_USER` stage log, and it too creates no authorization. Neither path attaches the UID, the
token, the request payload or the raw provider failure to a log or to the returned error.

The predicate is a single shared function so the trigger (`D-134`), the consumption callable
(`D-142`) and the issuer cannot drift apart on what "anonymous" means.

## Consequences

### Positive

- A stale anonymous token cannot mint an authorization for an identity that was **already** linked,
  disabled, deleted or state-unknown when the Admin lookup resolved. That is a real narrowing of the
  original defect, in which no Admin call happened at all. It is **not** a guarantee about the
  instant the authorization write commits; see `D-150`.
- The issuing and consuming sides of the ticket now apply the same current-state rule.
- The rejection is typed and distinguishable from an infrastructure failure by its callable code.

### Negative

- **The lookup-to-write window remains open.** An account linked, disabled or deleted after the
  lookup resolves and before the write commits still receives a UID-bound authorization. `D-142`
  covers only part of the consequence: it revalidates the bound account at consumption and so
  prevents destructive cleanup of an account that has become linked, but it does not prevent the
  authorization from being created and retained, and it does not independently reject an account
  that stays anonymous and becomes **disabled** after issuance eligibility was observed. This is
  `D-150` / `E3-16`.
- Every issuance costs one Admin read, on a path that previously touched only Firestore.
- An Admin outage now blocks issuance, where it previously would not have. That is the intended
  trade: without the record, eligibility is unknown, and issuing anyway is what this decision
  forbids.

### Constraints Introduced

- `issueOrphanCleanupTicket` MUST NOT create an authorization before the Admin record is resolved
  and found eligible.
- No document MAY state that `D-148` prevents an authorization from being created for a linked,
  disabled or deleted identity without scoping the claim to the state observed when the Admin lookup
  resolved, and MUST name `D-150` / `E3-16` as the owner of the lookup-to-write window.
- Eligibility MUST use the single shared `D-134` predicate; a second definition of "anonymous" is a
  contract failure.
- The record MUST be known to be enabled: an explicit `disabled === false` state. A snapshot whose
  enabled state is unavailable MUST reject, never issue.
- An eligibility rejection MUST be `failed-precondition` and an Admin lookup failure MUST be
  `internal` with the redacted `AUTH_USER` stage; neither may leak the UID, token, payload or raw
  failure.

## Verification

- `functions/test/orphanedAnonymousAccount.test.mjs` pins the four stale-claim rejections (linked,
  disabled, deleted, and unknown enabled state), each asserting that the calls list holds the lookup
  alone so no authorization was created, and re-asserts that fail-closed behaviour over all four
  ineligible snapshots in `D-148 still fails closed when the account is already ineligible at lookup
  time`. Three further tests, named for `D-150`, pin the **limitation** rather than a guarantee: an
  account linked, disabled or deleted between the lookup and the write still receives an
  authorization, and the test asserts the post-transition record no longer satisfies the predicate.
  They exist so that no document can claim `D-148` covers that window; the `internal` classification of an Admin lookup failure
  with its redacted stage log; the redaction of the rejection itself; and the ordering of the lookup
  before the write on the happy path. These are handler tests against injected fake gateways.
- `functions/test/firebaseAdminDeletionGateways.test.mjs` pins the concrete
  `FirebaseAdminAuthDeletionGateway.getUser`: it forwards the record's `disabled` and `providerData`
  values unchanged and maps only `auth/user-not-found` to `null`, rethrowing any other provider
  failure. This is unit coverage of the production gateway against a stub `Auth` client, not a live
  Admin Auth round trip.
- `functions/test/orphanedAnonymousAccountEmulator.test.mjs` runs against the real Firestore
  emulator and exercises the real Firestore gateways (the authorization gateway and the data
  deletion gateway) end to end. It does **not** exercise the real Admin Auth gateway: the suite
  starts only the Firestore emulator, so the `D-148` eligibility lookup is stubbed there with an
  eligible anonymous record. The live Admin Auth path is exercised by the ten protected checks'
  deployable build and by the dry-run deploy, not by an emulator round trip.

## References

- `docs/CONTRACTS.md` §11.5
- `docs/adr/0142-use-server-issued-orphan-cleanup-tickets.md` (`D-141`)
- `docs/adr/0143-revalidate-ticket-bound-anonymity-at-consumption.md` (`D-142`)
- `docs/adr/0135-deleted-user-eligibility-for-the-anonymous-cleanup-trigger.md` (`D-134`)
- `docs/adr/0150-close-the-ticket-issuance-and-account-deletion-race.md` (`D-149`, the
  account-deletion interleaving this decision deliberately does not close)
- `docs/adr/0151-close-the-issuance-lookup-to-write-window.md` (`D-150`, the linking/disabling
  lookup-to-write window this decision deliberately does not close)
