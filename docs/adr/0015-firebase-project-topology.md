# ADR-0015 / D-14 - Firebase Project Topology

## Status

Accepted

Accepted by the owner on 2026-08-17.

## Context

The project needs somewhere to run Firestore rules and authentication during development. Firestore is a backup and recovery replica, not the source of truth; the Room database remains the product's primary database and the only UI source.

During development there are no production users. CI still needs to run rule tests on every pull request without holding credentials for any real Firebase project.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| One development project now, a separate production project before release, plus the local emulator | Simplest development setup. CI still uses no real credentials. Enough while Firestore is backup-only and there are no production users. Keeps production isolated before any public release. | Production project creation and its project ID must be completed during release preparation. Development data must never be treated as production data. |
| Two projects from day one, `dev` and `prod`, plus the local emulator | Clean separation, rules can be validated in `dev` before production, CI needs no real credentials. | Two sets of configuration files and registered apps before production exists. |
| Single project through production | Simplest setup. | Test data and real data share a database; a bad rules deploy affects users immediately. Rejected for public release. |
| Three projects with a staging tier | Closest to a production service. | Unnecessary overhead for a single-developer MVP. |

## Decision

Use one development Firebase project during development, with Android debug and iOS debug apps registered. CI runs exclusively against the Firestore emulator.

Do not create or configure a production Firebase project during development. Before `E4-04` release preparation completes, create a separate production Firebase project and decide its project identifier. Public release builds point only at that production project.

## Consequences

### Positive

- Development setup stays small.
- CI is credential-free for Firebase, which removes a whole class of secret-leak risk.
- Production stays isolated before any public release.

### Negative

- There is no production Firebase isolation during development.
- Before public release, the owner must create the separate production project and decide its project identifier.

### Constraints Introduced

- Debug builds point at the development Firebase project.
- CI MUST NOT hold Firebase project credentials and MUST NOT write to a real Firebase project.
- No public release build may point at the development Firebase project.
- Any real Firebase project created for this app uses the Firestore location fixed by ADR-0014.
- The production Firebase project ID is a release-preparation owner decision, not an agent decision.

## Verification

- `E3-01` runs rule tests on the emulator.
- `E0-05` asserts CI contains no Firebase project credentials.
- `E0-07` proves the walking skeleton against the development Firebase project.
- `E4-04` cannot complete until the separate production Firebase project exists and the owner decides its project identifier.

## References

- `docs/DECISION_BOARD.md` (`D-14`)
- `docs/identifiers.md`
- `docs/SECURITY.md`
