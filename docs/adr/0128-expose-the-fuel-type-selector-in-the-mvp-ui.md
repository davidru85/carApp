# ADR-0128 / D-127 - Expose the Fuel Type Selector in the MVP UI

## Status

Accepted

Selected by the owner on 2026-09-06, after `E1-16` was filed and the conflict with `D-4` was
escalated. Numbering note: `D-123` to `D-126` and `ADR-0124` to `ADR-0127` are reserved by pull
request #55 (`E2-06`), which is in owner review and not merged. This record deliberately starts at
`D-127` so both pull requests can merge in either order without renumbering.

## Context

`D-4` decided to store `fuelType` on `Vehicle` from day one, with a default of `GASOLINE` and the
MVP enum limited to `GASOLINE`, `DIESEL`, `LPG`, `CNG` and `OTHER`. Its stated constraint was: "Do
not expose a selector in MVP UI." That clause is mirrored in `docs/DECISION_BOARD.md`,
`docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `ADR-0005`.

The reasoning was about schema cost, not about product value: adding a synchronized field after real
users exist is expensive, so the field was stored early and left unedited. Nothing in `D-4` argues
that the owner should be unable to record what their vehicle runs on.

`E1-16` was then filed to expose the selector. Its own text acknowledges the origin - "Under
decision `D-4`, the selector was originally omitted from the MVP UI" - but no decision record was
created, and `docs/BACKLOG.md` is a derived document. Under `AGENTS.md` a rule recorded only in a
backlog entry does not bind the next agent, and a derived document that contradicts a normative one
is void and MUST be escalated. So `E1-16` was not Ready: an agent starting it would find an
`Accepted` decision forbidding exactly what the story asked for.

The gap is small and entirely additive. Every one of the five values is already modelled in
`:core:model`, persisted by `:core:database`, accepted by the Firestore rules and settable through
`VehicleFormStateHolder.setFuelType(...)`. What is missing is only the control.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A new decision superseding only the UI clause of `D-4` (Selected) | `D-4`'s storage decision, which is implemented and correct, stays intact and keeps its original reasoning; the change to the UI is recorded where the next agent is bound by it; `E1-16` becomes Ready | Two records to read instead of one; `D-4`'s guardrail has to name its own partial supersession |
| Rewrite `D-4` in place | One record | `D-4` was decided and implemented long ago. Rewriting it erases why the field was stored early, and the repository already distinguishes revising an unmerged decision from superseding a merged one |
| Keep `D-4` and drop `E1-16` | No normative change; MVP scope stays exactly as closed | `fuelType` stays a field the product writes but never lets the owner set, which is the negative consequence `D-4` itself recorded and accepted only because nothing depended on it |

## Decision

The MVP UI exposes a `FuelType` selector in the Vehicle creation and edit forms on both platforms,
defaulting to `GASOLINE`, over the five MVP values.

The "Do not expose a selector in MVP UI" clause of `D-4` is superseded by this decision. Everything
else in `D-4` stands unchanged and keeps status `Accepted`: `fuelType` is stored on `Vehicle`, not
on `FuelEntry`; the default is `GASOLINE`; and `ELECTRIC` and `HYBRID` remain out of the MVP enum,
deferred to the `E5-01` energy model. This is a partial supersession, so `D-4` is not marked
`Superseded`: the decision it made about storage is alive and implemented.

`E1-16` is the story that implements it and is Ready once this record exists.

## Consequences

### Positive

- The owner can record what their vehicle runs on, and the field the product has been persisting
  since `E1-03` stops being write-only.
- `E1-16` has a normative basis, so the next agent is bound by it rather than by a backlog sentence.

### Negative

- The MVP UI surface grows by one control on two platforms, with its localized strings.
- `D-4` now has to be read together with this record.

### Constraints Introduced

- The selector MUST offer exactly the five MVP values. Adding `ELECTRIC` or `HYBRID` remains an
  energy-model scope change owned by `E5-01`, and this decision does not authorise it.
- The default for a new vehicle MUST stay `GASOLINE`, which is what `D-4` decided and what existing
  rows contain.
- No schema, migration or Firestore rule change is authorised by this decision. The stored values
  and the remote contract are unchanged.

## Verification

- `contractCheck` asserts that `D-127` appears with an identical status in all four mirroring
  documents and that this ADR's status matches.
- `E1-16` carries the executable acceptance criteria: selecting a non-default value persists on
  creation and on edit, on both platforms.

## References

- `docs/DECISION_BOARD.md` (`D-127`)
- `docs/BACKLOG.md` (`E1-16`, `E5-01`)
- [ADR-0005](0005-vehicle-fuel-type-from-day-one.md) (`D-4`)
