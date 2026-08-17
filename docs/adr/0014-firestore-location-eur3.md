# ADR-0014 / D-13 - Firestore Location `eur3`

## Status

Proposed

Requires owner confirmation in `E0-00`, before `E0-07` creates the database.

## Context

A Cloud Firestore database has a location that is chosen at creation time and **cannot be changed afterwards**. Moving data later means creating a new database and migrating every document and every client configuration.

The initial user base is Spanish, the project owner is in Spain, and the data is personal (vehicle usage, cost and location-adjacent patterns), so GDPR considerations favour keeping it inside the European Union.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| `eur3` European multi-region | Data stays in the EU. Multi-region durability. Lower latency for the target users. | Slightly higher cost than a single region. |
| `europe-west1` single region | Cheapest EU option. | Lower availability guarantees than a multi-region. |
| `nam5` United States multi-region | Default in many tutorials. | Data leaves the EU, which creates avoidable GDPR exposure for no benefit. |

## Decision

Create the Firestore database in `eur3`, in Native mode, in both the development and production projects.

## Consequences

### Positive

- User data stays in the EU.
- Lower read and write latency for the target user base.

### Negative

- Marginally higher storage and operation cost than a single region.

### Constraints Introduced

- The location MUST be verified before `E0-07` writes the first document, because it is immutable.
- Both `carapp-dev` and `carapp-prod` use the same location, so behaviour is comparable between them.

## Verification

- `E0-07` acceptance criterion asserts the database exists in the location fixed here.
- `docs/identifiers.md` records the final value.

## References

- `docs/DECISION_BOARD.md` (`D-13`)
- `docs/identifiers.md`
- `docs/SPECIFICATION.md`
