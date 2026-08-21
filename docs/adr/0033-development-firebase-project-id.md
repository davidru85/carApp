# ADR-0033 / D-32 - Development Firebase Project ID is `davidruiz-carapp-dev`

## Status

Accepted

Accepted by the owner on 2026-08-21.

## Context

`D-22` fixed the development Firebase project ID as `carapp-dev`, recorded in `docs/identifiers.md`. When the project was finally created, that ID turned out to be unavailable: Google Cloud project IDs are globally unique across all customers, and `carapp-dev` is already held by an account that is not the owner's.

The evidence is unambiguous. `POST cloudresourcemanager.googleapis.com/v1/projects` returns `409 ALREADY_EXISTS`, and `POST firebase.googleapis.com/v1beta1/projects/carapp-dev:addFirebase` returns `403 PERMISSION_DENIED`, which together mean the project exists and the owner has no rights over it.

`docs/identifiers.md` forbids an agent inventing, guessing or temporarily changing any identifier, so the replacement had to be an owner decision.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| `davidruiz-carapp-dev` | Available, keeps the `carapp-dev` suffix so the purpose is still legible, and the owner-name prefix makes a future collision unlikely. | Longer, and inconsistent with the `ruizurraca` prefix chosen for the application identifiers in `D-22`. |
| Keep trying short variants | Might stay closer to the original. | Every attempt is a guess against a global namespace, and a project ID is permanent once created. |

## Decision

The development Firebase project ID is `davidruiz-carapp-dev`. The project was created with that ID and the display name `carApp Dev`. `D-22` is unchanged in every other respect: the `applicationId`, bundle identifier, namespace and shared package root all stay `com.ruizurraca.carapp`.

The production project ID remains deferred by `D-14` and MUST be decided before `E4-04`. It is now known that the deferred decision carries availability risk, not only a naming choice.

## Consequences

### Positive

- The project exists, so `E0-07` has a backend to target.
- The Firestore location decision of `D-13` can be applied to a real database.

### Negative

- The Firebase project ID no longer matches the `ruizurraca` prefix used by the application identifiers. That is cosmetic: the project ID never appears in a store listing or a package name.

### Constraints Introduced

- `docs/identifiers.md` records `davidruiz-carapp-dev`, and agents MUST NOT use `carapp-dev` anywhere.
- The production project ID MUST be checked for availability before it is recorded as decided.

## Verification

- `firebase projects:list` shows `davidruiz-carapp-dev`.
- No occurrence of `carapp-dev` remains outside this ADR and the project log.

## References

- `docs/DECISION_BOARD.md` (`D-32`)
- `docs/identifiers.md`
- [ADR-0021](0021-application-identifiers.md) (`D-22`), [ADR-0015](0015-firebase-project-topology.md) (`D-14`)
