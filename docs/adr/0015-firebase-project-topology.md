# ADR-0015 / D-14 - Firebase Project Topology

## Status

Accepted

Accepted by the owner on 2026-08-17.

## Context

The project needs somewhere to run Firestore rules and authentication during development. Firestore is a backup and synchronization replica, not the source of truth; the Room database remains the product's primary database and the only UI source.

During development there are no production users. CI still needs to run rule tests on every pull request without holding credentials for any real Firebase project.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| One development project plus the local emulator | Simplest development setup. CI still uses no real credentials. Enough while Firestore is backup-only and there are no production users. | Production topology must be revisited before release. Development data must never be treated as production data. |
| Two projects from day one, `dev` and `prod`, plus the local emulator | Clean separation, rules can be validated in `dev` before production, CI needs no real credentials. | Two sets of configuration files and registered apps before production exists. |
| Three projects with a staging tier | Closest to a production service. | Unnecessary overhead for a single-developer MVP. |

## Decision

Use one development Firebase project during development, with Android debug and iOS debug apps registered. CI runs exclusively against the Firestore emulator.

Do not create or configure a production Firebase project during development. Production Firebase topology and project identifiers are decided by the owner before `E4-04` release preparation, and before any public release build points at Firebase.

## Consequences

### Positive

- Development setup stays small.
- CI is credential-free for Firebase, which removes a whole class of secret-leak risk.
- The production topology decision is made later, with better information from the MVP.

### Negative

- There is no production Firebase isolation during development.
- Before public release, the owner must decide whether to add a separate production project, rename the development project role, or choose another topology.

### Constraints Introduced

- Debug builds point at the development Firebase project.
- CI MUST NOT hold Firebase project credentials and MUST NOT write to a real Firebase project.
- No public release build may point at the development Firebase project.
- Any real Firebase project created for this app uses the Firestore location fixed by ADR-0014.
- Production Firebase topology is a release-preparation owner decision, not an agent decision.

## Verification

- `E3-01` runs rule tests on the emulator.
- `E0-05` asserts CI contains no Firebase project credentials.
- `E0-07` proves the walking skeleton against the development Firebase project.
- `E4-04` cannot complete until the owner decides production Firebase topology and project identifiers.

## References

- `docs/DECISION_BOARD.md` (`D-14`)
- `docs/identifiers.md`
- `docs/SECURITY.md`
