# ADR-0005 / D-4 - Store Vehicle Fuel Type From Day One

## Status

Accepted

## Context

Fuel type is not required in the MVP UI, but it is likely to matter for later reporting and additional expense types. Adding synchronized schema fields after real users exist is more expensive than storing a default field from the start.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Store `fuelType` on `Vehicle` from day one | Future-proof schema, simple default, no later remote migration. | Unused field in MVP UI. |
| Add `fuelType` later | Smaller initial model. | Remote schema evolution and migration after users exist. |
| Store `fuelType` per fuel entry | Captures mixed fuel cases. | Unnecessary complexity for MVP and likely wrong ownership. |

## Decision

Store `fuelType` on `Vehicle` from day one with default `GASOLINE`, but do not expose a selector in MVP UI.

## Consequences

### Positive

- Easier future expansion.
- Avoids synchronized schema churn later.

### Negative

- Domain model contains a field that the MVP UI does not edit.

### Constraints Introduced

- `fuelType` belongs to `Vehicle`, not `FuelEntry`.
- UI must not introduce a fuel type selector unless the specification changes.

## Verification

- Vehicle domain tests verify default value.
- UI review ensures no MVP selector is added.
