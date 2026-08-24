# ADR-0047 - Firestore Rules Use the Official Node Test Stack

## Status

Accepted

## Context

E3-01 must prove authentication, authorization, exact payload validation, server timestamps and
delta queries against the Firestore emulator. The repository has no Node build yet, and a direct
REST harness would need to reproduce authentication and emulator lifecycle behavior itself.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Official Firebase rules unit library with built-in `node:test` | Supported auth mocking, emulator-only safety and no third-party test runner. | Adds an exact npm toolchain. |
| Direct REST requests with built-in `node:test` | Fewer npm packages. | Custom authentication, server-timestamp, cleanup and query plumbing. |
| Kotlin/GitLive integration tests | Exercises the future application client. | Pulls provider/native linking work from E0-07 and E3-02 into the rules story. |

## Decision

Use Node `22.22.3`, Firebase CLI `15.28.1`, Firebase JS SDK `12.18.0` and
`@firebase/rules-unit-testing` `5.0.1`, with the built-in `node:test` runner. All npm dependencies
are exact and committed through `package.json` plus `package-lock.json`.

## Consequences

### Positive

- Tests can create authenticated and unauthenticated contexts without production credentials.
- The harness follows the official Firestore emulator testing path.
- Jest, Mocha and Vitest are unnecessary.

### Negative

- Contributors and CI need the pinned Node runtime and an npm install before emulator tests.

### Constraints Introduced

- Node-only dependency versions are canonical in `package.json` and `package-lock.json`; Gradle
  dependency versions remain canonical in `gradle/libs.versions.toml`.
- The exact versions are recorded in `docs/versions-matrix.md` and MUST NOT be repeated in CI.
- Emulator tests use the built-in `node:test` runner.

## Verification

- `npm ci` resolves the committed lockfile without mutation.
- The Firestore rules test script runs through the pinned local Firebase CLI dependency.
- CI installs Node `22.22.3` and executes the same npm script.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-46`)
- `docs/CONTRACTS.md §16`
- `docs/BACKLOG.md` (`E3-01`)
- Firebase documentation: Firestore security rules emulator testing
