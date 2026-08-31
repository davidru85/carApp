# ADR-0098 / D-97 - Export Fuel Presentation with Stable ABI

## Status

Accepted

Accepted by the owner for E1-08 on 2026-08-31.

## Context

E1-08 moves Fuel Entry holders and UI types from their D-55 shells in `:shared` to the owning
feature presentation package. `:composition:ios` does not currently export `:feature:fuel`, and
exporting the complete module without refinement would expose repositories, commands, validators,
data implementations and domain helpers forbidden by the Swift allowlist.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Export `:feature:fuel`, hide every non-allowlisted declaration and pin existing presentation names exactly | Satisfies feature ownership and preserves one framework and the existing ABI. | Requires systematic Objective-C refinement across Fuel production sources. |
| Keep exported wrapper types in `:shared` | Avoids exporting the feature module. | Contradicts E1-08 presentation ownership and maintains duplicate facades. |
| Export `:feature:fuel` without hiding domain and data declarations | Requires fewer annotations. | Violates the explicit Swift allowlist and leaks repositories and business types. |

## Decision

`:composition:ios` adds `api(project(":feature:fuel"))` and exports the module from its sole
`Shared` framework. All non-allowlisted Fuel Entry domain, repository, validation and data
declarations are hidden from Objective-C. Moved presentation declarations use exact
`SharedFuelEntry...` Objective-C names and their existing `FuelEntry...` Swift names. The expected
generated-header diff is empty.

## Consequences

### Positive

- Fuel presentation obeys the feature boundary without creating a second framework runtime.
- Existing Swift consumers see byte-identical symbols and signatures.

### Negative

- New public Fuel declarations require an explicit export-visibility review.
- `:composition:ios` gains one more export-only feature dependency.

### Constraints Introduced

- `FuelEntryRepository`, commands, `MoneyInput`, validators, domain helpers and
  `SqlDelightFuelEntryRepository` MUST remain absent from the Objective-C header.
- `SharedFuelEntryListItemUi` keeps its existing two Boolean indicator fields and exact signature.
- Any non-empty golden-header diff is a contract failure for E1-08.

## Verification

- Framework linking succeeds with the added export.
- Exact generated-versus-golden comparison produces no output.
- Contract tests reject forbidden symbols and module-derived presentation renames.

## References

- ADR-0086 / D-85
- `docs/CONTRACTS.md §11.6`, `§15.3`, `§20.10`
- `docs/BACKLOG.md` E1-08
