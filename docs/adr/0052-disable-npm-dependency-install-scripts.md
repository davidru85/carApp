# ADR-0052 - Disable npm Dependency Install Scripts

## Status

Accepted

## Context

A clean E3-01 install found lifecycle scripts in four transitive test dependencies:
`@firebase/util`, `fsevents`, `protobufjs` and `re2`. npm 12 blocks unreviewed dependency scripts,
but older npm versions run them by default. The complete Firestore emulator suite passes with all
four scripts blocked, so none is required by the repository's test path.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Set repository-wide `ignore-scripts=true` | Deterministic across npm versions and executes no unnecessary transitive lifecycle code. | A future dependency that requires an install step needs an explicit reviewed policy change. |
| Approve pinned scripts and pin npm 12 | Allows reviewed lifecycle scripts to run. | Adds another tool pin and permits native or post-install execution that E3-01 does not need. |
| Keep npm's version-dependent default | Adds no configuration. | Older and newer npm versions execute different code for the same lockfile. |

## Decision

The repository `.npmrc` sets `ignore-scripts=true`. Local and CI `npm ci` commands therefore skip
all dependency lifecycle scripts. A future exception requires a superseding decision after the
exact package, version and script have been reviewed.

## Consequences

### Positive

- Clean npm installs have the same lifecycle policy across supported environments.
- The emulator harness has a smaller dependency-execution attack surface.
- No npm runtime version pin is needed solely to obtain install-script approval behavior.

### Negative

- A dependency that genuinely needs an install script will fail at runtime until reviewed.

### Constraints Introduced

- `.npmrc` MUST retain `ignore-scripts=true` while D-51 is accepted.
- CI MUST use the repository configuration rather than enabling scripts through command-line flags.
- Dependency updates MUST rerun the complete emulator suite after a clean `npm ci`.

## Verification

- `npm ci` reports the blocked scripts under npm 12.
- `npm run test:firestore-rules` passes after that clean installation.
- The protected `contract-check` job installs dependencies without an override.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-51`)
- `docs/SECURITY.md`
- `docs/BACKLOG.md` (`E3-01`)
