# ADR-0091 / D-90 - Release Swift Holders and Separate Save Completion

## Status

Accepted

Accepted for the E1-07 owner code-review correction on 2026-08-31.

## Context

`SwiftAppGraph` cached every state holder until the complete graph closed. In particular, the
creation form used the `null` cache key, then changed its own `vehicleId` to the created ID after a
successful save. A second creation request returned that mutated holder, retained the first
Vehicle's inputs and invoked the update path. Every visited keyed screen also retained a child
scope and its subscriptions for the lifetime of the graph.

The Swift facade needs bounded, destination-owned holder lifetimes. Creation completion must not
change the identity of a holder that was constructed for creation.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Add keyed release functions, separate `savedVehicleId` from `vehicleId` and reset creation inputs after success | Keeps cache identity stable, makes navigation completion explicit, prevents create-to-update corruption and cancels one destination scope on exit. | Adds Swift ABI members and requires each native destination to release its holder. |
| Rekey the creation holder as an editor and begin observing edit facts after save | Reuses the same holder for the created Vehicle. | Couples creation navigation to cache mutation, requires subscription replacement and makes a second creation request ambiguous. |
| Stop caching keyed holders | Always returns fresh inputs. | Loses factory idempotence and still leaks scopes unless every caller separately tracks and closes every returned holder. |

## Decision

`VehicleFormUiState` exposes `savedVehicleId` separately from the route identity in `vehicleId`.
Saving clears the completion signal; successful creation resets the form to creation defaults and
then publishes the new ID without changing `vehicleId`.

`SwiftAppGraph` adds keyed, idempotent release functions for Vehicle forms, Fuel Entry lists and
Fuel Entry forms. Each function removes the matching cached holder, closes it and cancels its child
scope. A later request for that key creates a fresh holder.

## Consequences

### Positive

- A retained creation holder cannot silently update the Vehicle it just created.
- Consecutive creation flows start empty and create distinct Vehicles.
- Swift destinations can release subscriptions without closing the complete application graph.

### Negative

- `VehicleFormUiState` and `SwiftAppGraph` gain new Swift-facing members.
- E1-09 must call the matching release function when a destination leaves its back stack.

### Constraints Introduced

- `vehicleId` remains the immutable route identity for the holder's lifetime.
- `savedVehicleId` is a completion signal, not an edit identity.
- Each keyed Swift destination MUST release its holder when that destination ends.
- Release functions MUST be idempotent and MUST cancel the holder's child scope.

## Verification

- Feature common tests prove two saves through a retained creation holder issue two create commands
  from reset inputs and never issue an update.
- Shared tests prove caching before release, scope cancellation, a fresh instance after release and
  two distinct persisted Vehicles.
- The generated Objective-C header golden records the intentional ABI additions.

## References

- ADR-0087 / D-86
- `docs/CONTRACTS.md §15.3`, `§20.10`
- `docs/BACKLOG.md` E1-07
