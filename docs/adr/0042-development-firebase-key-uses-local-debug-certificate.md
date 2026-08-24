# ADR-0042 - Development Firebase Key Uses the Local Debug Certificate

## Status

Accepted

## Context

The repository is public. Before the Android Firebase client configuration is committed, its API
key must be restricted by the debug application ID and a signing-certificate fingerprint. The MVP
has one maintainer and no release Firebase project yet.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Current local debug certificate | No new keystore secret; immediately usable by the owner. | A new development machine needs another explicitly registered fingerprint. |
| Dedicated development keystore | Stable identity across machines. | Creates another private key and storage/configuration obligation. |
| Defer the restriction | No setup work now. | Violates the public-repository security precondition. |

## Decision

The selected option is to restrict the development Android Firebase API key to
`com.ruizurraca.carapp.debug` and the owner's current local debug signing certificate. The iOS key
is restricted to `com.ruizurraca.carapp.debug` as an iOS bundle identifier.

## Consequences

### Positive

- No additional private signing material is created or shared.
- The committed client configuration is unusable by an Android binary signed with another key.

### Negative

- Moving development to another machine requires explicitly adding that machine's debug
  certificate fingerprint before Firebase calls work there.

### Constraints Introduced

- Debug keystores and signing credentials remain outside the repository.
- The API-key value and certificate fingerprint MUST NOT be written to project documentation.
- A new fingerprint is an explicit security configuration change, not an automatic widening.

## Verification

- E0-07 records the Google Cloud restriction state in its handoff before committing either
  Firebase configuration file.
- The Android signing report supplies the registered certificate fingerprint without committing
  the keystore.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-41`)
- `docs/SECURITY.md`
- `docs/identifiers.md`
- `docs/BACKLOG.md` (`E0-07`)
