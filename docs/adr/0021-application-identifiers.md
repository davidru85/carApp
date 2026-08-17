# ADR-0021 / D-22 - Application Identifiers Are Owner-Decided

## Status

Accepted

Accepted by the owner on 2026-08-17.

## Context

`E0-01` creates the Android and iOS host applications, which requires an `applicationId`, a bundle identifier, a package namespace and a display name. None of these existed in the documentation, so an agent would have had to invent them. Two of them — the `applicationId` and the bundle identifier — are immutable once the app is published to a store, and the Firebase configuration is generated from them.

This is the same class of problem as the Firestore location: a one-way door being left to whoever happens to run the story first.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Fix identifiers in a dedicated owner-decided document | The value is explicit, reviewable and stable. | Requires an owner decision before Phase 0 starts. |
| Let the bootstrap story choose | Unblocks immediately. | Effectively irreversible after publication, and the choice is invisible in review. |
| Use a placeholder and rename later | Unblocks immediately. | Renaming touches Firebase apps, signing configuration and every generated file; it is remembered only if it is written down. |

## Decision

Fix all application identifiers in `docs/identifiers.md`, together with the development Firebase project ID and the Firestore location. `E0-01` reads that file and MUST NOT invent or alter any value in it.

The accepted values are `carApp` for the product name, `com.ruizurraca.carapp` for the Android `applicationId`, the namespace and the iOS bundle identifier, `Shared` for the iOS framework name, and `carapp-dev` for the development Firebase project ID. Debug builds use a `.debug` suffix so debug and release builds can coexist on one device.

Production Firebase topology and production project identifiers are deferred by `D-14` until release preparation. Agents MUST NOT invent them.

## Consequences

### Positive

- The irreversible choice is made deliberately and is visible in one file.
- Firebase app registration and signing configuration have a stable basis.

### Negative

- Production Firebase identifiers remain undecided until release preparation.

### Constraints Introduced

- Agents MUST NOT invent an identifier, project name, region or display name. If one is missing, they stop and escalate.
- Changing any value after `E0-07` requires a written migration plan and a human review gate.

## Verification

- `E0-01` acceptance criterion asserts the identifiers match `docs/identifiers.md` exactly.
- `E4-04` cannot complete until production Firebase identifiers are decided.
- `docs/identifiers.md` is a gated path in `AGENTS.md`.

## References

- `docs/DECISION_BOARD.md` (`D-22`)
- `docs/identifiers.md`
