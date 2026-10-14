# ADR-0207 / D-206 - Own the Entity Type Wire Value in `:core:common`

## Status

Accepted

The owner selected option A on 2026-10-14.

## Context

`E3-13` centralizes the outbox `entityType` token. The tokens `"VEHICLE"` and `"FUEL_ENTRY"` are
repeated as independent string literals across production Kotlin (`DatabaseMutations`,
`SyncDatabaseAccess`, `AccountConversionDatabaseAccess`, `VehicleOutboxMapper` and
`FuelEntryOutboxMapper`), across the SQL of `database.sq`, and in the `CHECK` constraints of
`schema.sq` and the `2.sqm` migration.

`EntityType` is declared in `:core:sync`. `docs/TECHNICAL_PLAN.md §4` forbids `:core:database ->
:core:sync`, and the edge would also be a cycle because `:core:sync` already depends on
`:core:database`. `:core:database` therefore cannot reference the enum, which is why its own
literals exist.

SQLite cannot read a Kotlin constant. Binding `entityType` as a parameter in every statement would
change dozens of generated query signatures and would still leave the `CHECK` constraints as
literals, and replacing the text column with a lookup table is a schema migration with table
rebuilds. The repository already handles the same shape for `CONNECTIVITY_ERROR_CODES`: the constant
lives in `:core:common`, the SQL repeats it, and `ConnectivityCodeParityTest` fails the build when the
two diverge.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Move `EntityType` to `:core:common`; SQL literals verified against it by an executable parity check | Reachable from `:core:database`, `:core:sync`, feature `data` and the integrations. Follows the existing placement of the shared sync vocabulary (`SyncStatus`, `SyncTrigger`, `CONNECTIVITY_ERROR_CODES`) and the `ConnectivityCodeParityTest` pattern. No schema migration. | `:core:common` is exported to the Swift framework, so the enum needs `@HiddenFromObjC` to keep the Objective-C header unchanged. Feature `domain` and `presentation` can also see it. |
| B. Move `EntityType` to `:core:model`, with the same SQL parity check | Same reach; `:core:model` is not exported to Swift. | `docs/TECHNICAL_PLAN.md §4` defines `:core:model` as the domain vocabulary; the enum carries the Firestore collection name, a transport detail. |
| C. Keep `EntityType` in `:core:sync` | No move. | `:core:database` still cannot reference it, so its literals remain and the `E3-13` acceptance criterion "no independent string literal remains in production code" cannot be met. |

## Decision

The selected option is **A**.

- `EntityType`, with its `collection` property and its two entries, moves unchanged from
  `com.ruizurraca.carapp.core.sync` to `com.ruizurraca.carapp.core.common` and is annotated
  `@HiddenFromObjC`. Its entry `name` is the outbox `entityType` wire value.
- Production Kotlin emits and matches the wire value only through `EntityType`; no production source
  outside its declaration carries the `"VEHICLE"` or `"FUEL_ENTRY"` literal.
- The SQL keeps its literals. A `build-logic` parity test parses the enum from its declaration and
  fails when a `CHECK (entityType IN (...))` list in `schema.sq` or any `.sqm` migration differs from
  the enum entries, or when an `entityType` literal in `database.sq` is not an enum entry.
- The column type and the schema version are unchanged.

## Consequences

### Positive

- One Kotlin source for the wire value; a new entity type cannot be added to the enum without the
  build pointing at every SQL list that must change.
- No migration and no change to the Swift-facing surface.

### Negative

- The SQL literals remain textual duplicates, kept honest only by the parity test, exactly as the
  connectivity codes are.
- Every import of `com.ruizurraca.carapp.core.sync.EntityType` changes.

### Constraints Introduced

- `docs/CONTRACTS.md §20` declares `EntityType` in the `:core:common` block.
- A future entity type changes the enum, the `CHECK` lists through a migration, and the SQL
  statements the parity test names, in the same change.

## Verification

- `EntityTypeWireValueParityTest` in `build-logic` checks the enum against every `CHECK` list and
  every `entityType` literal in the SQL, with failing fixtures for a divergent list and a foreign
  literal.
- The same test rejects an `"VEHICLE"` or `"FUEL_ENTRY"` string literal in any production Kotlin
  source outside the enum declaration, with a failing fixture.
- `entityTypeEnumNamesMatchTheOutboxWireValues` keeps passing.
- The `objc-header-golden-check` job shows no header change.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-206`)
- `docs/CONTRACTS.md §8`, `§20`
- `docs/TECHNICAL_PLAN.md §4`
- `docs/BACKLOG.md` (`E3-13`)
- `D-110` (`docs/handoffs/E1-11.md`)
