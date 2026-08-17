# ADR-0014 / D-13 - Firestore Location `europe-west1`

## Status

Accepted

Accepted by the owner on 2026-08-17.

## Context

A Cloud Firestore database has a location that is chosen at creation time and **cannot be changed afterwards**. Moving data later means creating a new database and migrating every document and every client configuration.

The initial user base is Spanish, the project owner is in Spain, and the data is personal (vehicle usage, cost and location-adjacent patterns), so GDPR considerations favour keeping it inside the European Union.

Firestore is not the primary database for product behaviour. The app is local-first: Room is the internal source of truth, all UI reads come from Room, and Firestore exists only as a backup and synchronization replica for cross-device recovery.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| `europe-west1` single region | Data stays in the EU. Lower cost than a multi-region. Suitable latency for backup and synchronization use. | Lower availability guarantees than a multi-region. |
| `eur3` European multi-region | Data stays in the EU. Multi-region durability. Lower latency for the target users. | Slightly higher cost than a single region. |
| `nam5` United States multi-region | Default in many tutorials. | Data leaves the EU, which creates avoidable GDPR exposure for no benefit. |

## Decision

Create every real Firestore database for this app in `europe-west1`, in Native mode. During development this applies to the single development Firebase project; any future production Firebase project uses the same location.

## Consequences

### Positive

- User data stays in the EU.
- Cost is lower than the `eur3` multi-region option.
- Latency and availability remain suitable for a backup and synchronization replica, because Room is the user-facing source of truth.

### Negative

- Availability guarantees are lower than a multi-region Firestore deployment.

### Constraints Introduced

- The location MUST be verified before `E0-07` writes the first document, because it is immutable.
- Every real Firebase project created for this app uses this location, so development and any future production project remain comparable.

## Verification

- `E0-07` acceptance criterion asserts the database exists in the location fixed here.
- `docs/identifiers.md` records the final value.

## References

- `docs/DECISION_BOARD.md` (`D-13`)
- `docs/identifiers.md`
- `docs/SPECIFICATION.md`
