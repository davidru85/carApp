# ADR-0029 / D-28 - Implement the Feature-Layer Package Rules in `E1-07`

## Status

Accepted

Accepted by the owner on 2026-08-21.

## Context

`docs/TECHNICAL_PLAN.md §4` has 17 rows. `E0-04` enforces the 14 that govern module edges and library capabilities. The remaining three — feature `domain`, `data` and `presentation` — govern packages inside a single Gradle module, because a feature is one module and layer separation is by package (`§3`).

`D-16` assigns package-level rules to Konsist. Konsist rules are tests and need a module to live in, and no `:feature:*` module exists. Konsist is pinned at 0.17.3 by `E0-06` and currently unused.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Implement them in `E1-07`, the first feature module | The rules land with the code they guard. Nothing is unguarded meanwhile, because no feature exists that could break them. | `E0-04` closes with three criteria deferred. |
| Create a dedicated architecture-test module now | The rules would exist earlier and have a single home. | That module is not in the canonical inventory of `docs/CONTRACTS.md §1.1`, so it would fail `contract-check` assertion 8 — a rule breaking a rule. |
| Extend the custom Gradle checker to do package analysis and drop Konsist | One tool, already working and already covered by fixtures. | Contradicts `D-16` and would need a superseding ADR plus package analysis the checker does not do. |

## Decision

The three feature-layer rows are implemented with Konsist in `E1-07`, the first story that creates a `:feature:*` module. `E0-04` keeps the 14 module-level rows.

## Consequences

### Positive

- Konsist is introduced when it has something to check, rather than as an unused dependency.
- `E0-04` stays a Phase 0 story about the module graph.

### Negative

- Between now and `E1-07` there is no automated guard on feature layering. The exposure is nil in practice, because there is no feature module to mis-layer.

### Constraints Introduced

- `E1-07` MUST add the three rules with a failing fixture each, matching the `E0-04` standard.

## Verification

- `E1-07` ships Konsist rules for feature `domain`, `data` and `presentation`, each with a fixture proving it fires.

## References

- `docs/DECISION_BOARD.md` (`D-28`)
- `docs/TECHNICAL_PLAN.md §4`
- `docs/handoff-E0-04.md`
