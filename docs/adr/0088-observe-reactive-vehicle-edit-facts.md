# ADR-0088 / D-87 - Observe Reactive Vehicle Edit Facts

## Status

Accepted

Accepted by the owner on 2026-08-30.

## Context

The initial odometer becomes immutable after the Vehicle has an active Fuel Entry. A form opened
before a background recovery inserts an entry can otherwise continue to show an editable field.
The repository write transaction is authoritative, but presentation also needs a live projection
so its editability state converges while the form remains open.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Observe `VehicleEditFacts` reactively from Vehicle and Fuel Entry queries | Re-emits after local or pulled Fuel Entry changes; keeps UI advisory and write validation aligned. | Adds one repository projection and combines two database observations. |
| Load editability once with a suspend query | Simple API and implementation. | Becomes stale while the form is open and misses background recovery changes. |
| Keep the field optimistic and reject only on save | Smallest read surface; transaction remains safe. | The UI advertises an edit that is known to be forbidden and discovers it only after submission. |

## Decision

Add `VehicleEditFacts(vehicle, canEditInitialOdometer)` and
`VehicleRepository.observeVehicleEditFacts(id)`. Absence emits `Outcome.Ok(null)`. The projection
observes both the Vehicle row and active Fuel Entry count and re-emits when either changes.
`VehicleFormStateHolder` maps the fact to its state. The existing transactional
`ValidationError.EditNotAllowed("initialOdometerKm")` remains authoritative when saving.

## Consequences

### Positive

- Open forms react to background recovery and local Fuel Entry changes.
- Presentation does not duplicate the database fact query.
- A stale UI flag cannot bypass transactional validation.

### Negative

- Editing a Vehicle maintains two database observations.
- The repository contract gains a Kotlin-only projection type.

### Constraints Introduced

- The projection MUST re-emit when active Fuel Entries for the Vehicle change.
- `VehicleEditFacts` MUST remain absent from the Swift-facing surface.
- Save-time validation MUST reject a stale optimistic edit.

## Verification

- Repository tests add and tombstone a Fuel Entry while collecting the projection.
- State-holder tests map editability and prove a stale `true` cannot make a forbidden save pass.

## References

- ADR-0077 / D-76
- `docs/CONTRACTS.md §6`, `§12`, `§20`
- `docs/BACKLOG.md` E1-07
