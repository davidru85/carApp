# ADR-0005 / D-4 - Store Vehicle Fuel Type From Day One

## Status

Accepted

## Context

Fuel type is not required in the MVP UI, but it is likely to matter for later reporting and additional expense types. Adding synchronized schema fields after real users exist is more expensive than storing a default field from the start.

The MVP records only combustion or fuel-like vehicle labels. Electric and hybrid support needs a separate energy model, because it affects kWh input, mixed energy units, validation, consumption display and remote schema compatibility.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Store MVP `fuelType` on `Vehicle` from day one | Future-proof schema for combustion/fuel-like labels, simple default, no later remote migration for this field. | Unused field in MVP UI; electric/hybrid still require a future enum and schema compatibility change. |
| Add `fuelType` later | Smaller initial model. | Remote schema evolution and migration after users exist. |
| Store `fuelType` per fuel entry | Captures mixed fuel cases. | Unnecessary complexity for MVP and likely wrong ownership. |
| Include `ELECTRIC` and `HYBRID` in MVP enum | Avoids a future enum change. | Pretends the MVP supports vehicles whose energy input and consumption units are deliberately out of scope. |

## Decision

Store `fuelType` on `Vehicle` from day one with default `GASOLINE`, but limit the MVP enum to `GASOLINE`, `DIESEL`, `LPG`, `CNG` and `OTHER`. Do not expose a selector in MVP UI. `ELECTRIC` and `HYBRID` are deferred to a future energy-model scope change.

## Consequences

### Positive

- Easier future expansion for fuel-type reporting.
- Avoids synchronized schema churn later for combustion/fuel-like values.

### Negative

- Domain model contains a field that the MVP UI does not edit.
- Future electric/hybrid support still needs an explicit enum/schema expansion and migration story.

### Constraints Introduced

- `fuelType` belongs to `Vehicle`, not `FuelEntry`.
- MVP `FuelType` values are exactly `GASOLINE`, `DIESEL`, `LPG`, `CNG` and `OTHER`.
- `ELECTRIC` and `HYBRID` are roadmap values only and MUST NOT be introduced without a future energy-model story or ADR.
- UI must not introduce a fuel type selector unless the specification changes.

## Verification

- Vehicle domain tests verify default value.
- Contract tests verify the exact MVP enum values.
- UI review ensures no MVP selector is added.
