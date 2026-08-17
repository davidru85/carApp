# ADR-0015 / D-14 - Firebase Project Topology

## Status

Proposed

Requires owner confirmation in `E0-00`, before `E0-07`.

## Context

The project needs somewhere to run Firestore rules and authentication during development without polluting the data that real users will eventually depend on, and CI needs to run rule tests on every pull request without holding production credentials.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Two projects, `carapp-dev` and `carapp-prod`, plus the local emulator | Clean separation, rules can be validated in `dev` before production, CI needs no real credentials. | Two sets of configuration files and registered apps. |
| Single project | Simplest setup. | Test data and real data share a database; a bad rules deploy affects users immediately. |
| Three projects with a staging tier | Closest to a production service. | Unnecessary overhead for a single-developer MVP. |

## Decision

Use two Firebase projects, `carapp-dev` and `carapp-prod`, each with Android debug, Android release, iOS debug and iOS release apps registered. CI runs exclusively against the Firestore emulator.

Rules are always deployed to `carapp-dev` and validated there before being deployed to `carapp-prod`.

## Consequences

### Positive

- A rules mistake cannot reach users without passing through `dev` first.
- CI is credential-free for Firebase, which removes a whole class of secret-leak risk.

### Negative

- Two sets of `google-services.json` and `GoogleService-Info.plist` to maintain.

### Constraints Introduced

- Debug builds MUST NOT point at `carapp-prod`.
- CI MUST NOT hold production Firebase credentials.
- Both projects use the Firestore location fixed by ADR-0014.

## Verification

- `E3-01` runs rule tests on the emulator.
- `E0-05` asserts CI contains no production Firebase credentials.
- `docs/identifiers.md` records the final project IDs.

## References

- `docs/DECISION_BOARD.md` (`D-14`)
- `docs/identifiers.md`
- `docs/SECURITY.md`
