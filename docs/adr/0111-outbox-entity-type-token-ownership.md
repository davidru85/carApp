# ADR-0111 / D-110 - Outbox Entity-Type Token Ownership

## Status

Accepted

Selected during the E1-11 owner review on 2026-09-02.

## Context

`docs/CONTRACTS.md §8` mandates that every outbox payload includes an `entityType` field whose
wire values are `"VEHICLE"` and `"FUEL_ENTRY"`. `docs/CONTRACTS.md §20` defines the authoritative
`EntityType` enum in `:core:sync`. The outbox column `entityType`, the SQL `CHECK` constraints in
`schema.sq`, the embedded SQL literals in `database.sq`, the `DatabaseMutations` coalescing calls,
the feature outbox mappers and the tests all carry these tokens independently.

E1-11 restored `entityType` compliance in `VehicleOutboxMapper`. The remaining question is whether
to centralize the token strings now or leave them as explicit contract literals.

Two rejected alternatives were considered:

1. **Extract shared constants now** — a Kotlin constant could unify the mapper and
   `DatabaseMutations` call sites. This does not unify the SQL `CHECK` constraints or the embedded
   SQL literals, so it is a partial fix that introduces a new constant without a single source of
   truth.

2. **Derive all values from the `:core:sync` `EntityType`** — the feature mappers already depend on
   `:core:sync`, so this is free for `:feature:vehicle` and `:feature:fuel`. But `docs/TECHNICAL_PLAN.md §4`
   forbids `:core:database -> :core:sync`, and `:core:sync` already depends on `:core:database`, so
   the edge would be a cycle. Unifying `DatabaseMutations` would require relocating `EntityType`
   (likely to `:core:model`), which touches gated paths (`core/sync/**`, `core/model/**`) and the
   gated topic "module boundaries and dependency rules". Any centralization must also resolve the
   SQL representation (`CHECK` constraints, embedded SQL literals) and independent contract test
   assertions.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Keep explicit contract tokens in E1-11; add a bounded test-only anchor pinning the `EntityType` enum names to the wire values | No production code change, no module-boundary change, no gate; the enum-to-contract anchor makes a future enum rename fail the build; repository tests already read outbox rows written by `DatabaseMutations` using `EntityType.*.name`, so divergence there fails the build | The `DatabaseMutations` literals, the `:feature:fuel` literals, the `:shared` literals and the SQL `CHECK`/embedded literals remain independent until a future story centralizes them |
| Extract shared constants now | Unifies some Kotlin call sites | Does not unify SQL; introduces a new constant without a single source of truth; partial fix |
| Derive all values from the `:core:sync` `EntityType` | Single source of truth in Kotlin | Impossible for `:core:database` without a gated module-boundary change (forbidden edge + cycle); does not resolve SQL representation; requires relocating `EntityType` |

## Decision

E1-11 keeps the explicit contract tokens (`"VEHICLE"`, `"FUEL_ENTRY"`) in production code. The
`:feature:vehicle` commonTest files derive their outbox lookup keys and seeds from
`EntityType.*.name` (the enum is already on their classpath at zero new module cost), while the
payload value assertions stay as exact string literals — the contract wire-value anchor. One new
test, `entityTypeEnumNamesMatchTheOutboxWireValues`, pins the `EntityType` enum names to the wire
values mandated by `docs/CONTRACTS.md §8` and `§20`, so an enum rename fails the build instead of
silently changing what would be persisted and pushed.

No production code, no contract change and no module-boundary change are introduced by this
decision. The PR touches gated paths (`AGENTS.md`, `docs/SPECIFICATION.md`,
`docs/DECISION_BOARD.md`, `docs/adr/**`) via the D-110 mirrors and ADR-0111; the human review
gate is the owner's review and merge of PR #45. Any future unification is a separate, explicit,
Ready Phase 3 story that must jointly resolve where `EntityType` lives, module dependencies, SQL
representation and contract assertions.

## Consequences

### Positive

- The `EntityType` enum names are pinned to the outbox wire values by a single test anchor.
- An enum rename fails the build instead of silently changing persisted and pushed payloads.
- The PR touches gated paths (`AGENTS.md`, `docs/SPECIFICATION.md`, `docs/DECISION_BOARD.md`,
  `docs/adr/**`) via the D-110 mirrors and ADR-0111; the human review gate is the owner's review
  and merge of PR #45.
- The deliberate asymmetry is safe: repository tests read outbox rows written by
  `DatabaseMutations` using `EntityType.*.name`, so divergence between the enum and the
  `DatabaseMutations` literals fails the build.

### Negative

- `DatabaseMutations` (`core/database/.../DatabaseMutations.kt`), the `:feature:fuel` and `:shared`
  literals, and the `.sq` `CHECK`/SQL literals remain independent string literals until a future
  centralization story.

### Constraints Introduced

- Any future unification of the `entityType` token MUST be a separate Ready story with its own
  `D-` id, ADR and `docs/SPECIFICATION.md §12` / `docs/TECHNICAL_PLAN.md §2` mirrors.
- That story MUST jointly resolve: where `EntityType` lives (`:core:sync` today; `:core:model` would
  make it reachable from `:core:database` and every feature), module dependencies, SQL representation
  (`CHECK` constraints, embedded SQL literals) and contract assertions.
- That story is naturally taken by the sync-engine story that first consumes `entityType` — not
  automatically assigned to E3-03.
- The `entityTypeEnumNamesMatchTheOutboxWireValues` test is a characterization pin of an existing
  invariant; it has no RED phase.

## Verification

- `:feature:vehicle:testAndroidHostTest` passes with the enum-derived lookup keys and the literal
  value assertions.
- `entityTypeEnumNamesMatchTheOutboxWireValues` asserts `EntityType.VEHICLE.name == "VEHICLE"` and
  `EntityType.FUEL_ENTRY.name == "FUEL_ENTRY"`.
- `contractCheck` proves D-110 and ADR status parity across all four mirrors.

## References

- `docs/CONTRACTS.md §8` (Outbox Contract)
- `docs/CONTRACTS.md §20` (EntityType enum)
- `docs/TECHNICAL_PLAN.md §4` (dependency rules)
- ADR-0110 / D-109
