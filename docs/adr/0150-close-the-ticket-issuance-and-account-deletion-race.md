# ADR-0150 / D-149 - Close the Ticket Issuance and Account Deletion Race

## Status

Proposed

This decision is the owner's. `E3-15` MUST NOT start until it is `Accepted`.

## Context

`D-148` (ADR-0149) stops a stale anonymous token from minting a cleanup ticket, but it cannot
guarantee the `D-143` erasure invariant on its own: *no UID-bound authorization record exists once
`deleteAccount` has returned success*.

The normative deletion order of `docs/CONTRACTS.md §11.5` is:

1. delete remote data under `users/{uid}`;
2. purge every `orphanCleanupTickets` record bound to the UID;
3. delete the Firebase Auth user;
4. return success.

The issuer, after `D-148`, is:

1. resolve the caller's Admin record and check eligibility;
2. write the authorization;
3. return the ticket.

The surviving interleaving is `I1, D1, D2, I2, D3, D4`: the issuer's eligibility check passes while
the account is still perfectly valid, the purge runs and finds nothing, the issuer's write lands,
and the Auth user is then deleted and success is returned. A UID-bound authorization outlives a
successful deletion, and its record contains `anonymousUid`.

No check inside the issuer closes this window, because at the moment of the write the account
legitimately still exists and is still anonymous. Adding a post-write re-verification helps only
after the Auth user is gone, and in this interleaving it is not gone yet. The window is therefore
closed only by changing something outside the issuer, which is why this is an owner decision rather
than an implementation detail.

A related sub-case is bounded by the same choice: the issuer must never return a ticket whose
authorization the purge removed between the write and the response, because that ticket is already
unusable. Every option below pairs with a post-write read-back in the issuer, which is cheap and
uncontroversial once the mechanism is chosen.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **A. Second purge pass after Auth deletion (recommended)** | No new collection, no new retention rule and no user-visible behaviour change; the purge is already idempotent, so the second pass is a repeat of existing code; the extra query is normally empty and costs one Firestore read per deletion. | `§11.5` gains a stage, so the normative order and its tests change; the issuer still needs a post-write re-verification for a write that lands after the second pass; two passes are a convergence argument rather than a serialization point, and must be reasoned about carefully. |
| B. Disable the Auth user as the first deletion stage | A disabled record is already rejected by `D-148`, so most racing issuances are refused rather than compensated; the marker is the account's own state, not a new store. | Adds an Admin write to every deletion, and `§11.5` gains a stage before data deletion; if deletion then fails midway the owner is left locked out of a still-existing account until a retry succeeds, which is a user-visible consequence of an internal repair. |
| C. Internal `deletedUids` marker collection read inside the issuance transaction | The only option with a real serialization point rather than a convergence argument; the issuer can refuse deterministically. | Introduces a new internal server-only collection, which needs a `§16` internal-registry entry, the parity test, and a retention rule the project does not currently have; markers must outlive any token lifetime, so they accumulate; the heaviest option by some distance. |

## Decision

Not yet taken. The recommendation is **option A**, because it satisfies the invariant with the
smallest contract change, introduces no new collection and no retention rule, and has no
user-visible consequence. Option B is defensible but trades an internal repair for a user-visible
lockout window on a failed deletion. Option C is the most rigorous and the most expensive; it is
worth choosing only if the owner wants a serialization point rather than a convergence argument.

Whichever option is accepted, `E3-15` MUST also:

- add a post-write read-back so the issuer never returns a ticket whose authorization was
  concurrently removed;
- prove every interleaving against the real Firestore emulator rather than against fakes;
- keep consumption, completion-last semantics and idempotent retries intact;
- introduce no client-selected UID, no JWT verification fallback and no weaker authorization path.

## Consequences

### Positive

- The `D-143` erasure guarantee becomes unconditional rather than "unconditional outside one
  interleaving".

### Negative

- Every option changes the normative deletion order of `§11.5`, so `E3-10` and `E3-11` tests that
  pin that order change with it.

### Constraints Introduced

- Until this decision is accepted and `E3-15` ships, the residual risk stands and MUST be recorded
  as an accepted residual risk in `docs/SECURITY.md` if the owner chooses to defer.

## Verification

- To be defined by the accepted option. In every case `E3-15` MUST prove the interleavings against
  the Firestore emulator, and MUST show zero UID-bound authorization records once deletion returns
  success.

## References

- `docs/CONTRACTS.md` §11.5
- `docs/adr/0149-verify-the-issuing-account-through-the-admin-sdk.md` (`D-148`)
- `docs/adr/0144-erase-orphan-cleanup-authorizations-on-account-deletion.md` (`D-143`)
- `docs/BACKLOG.md` (`E3-15`)
