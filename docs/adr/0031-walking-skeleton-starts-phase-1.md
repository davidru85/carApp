# ADR-0031 / D-30 - `E0-07` Walking Skeleton Moves to the Start of Phase 1

## Status

Accepted

Accepted by the owner on 2026-08-21.

## Context

`E0-07` builds a screen crossing native UI, a shared state holder, **Room**, Firestore and real anonymous auth, and its decision gate is about Room KMP on iOS.

Room lives in `:core:database`, which is `E1-01`. The Phase 0 preamble forbids creating `:core:database`, and `E0-04` now implements a rule that fails the build if it appears. `E0-07` therefore cannot use Room without breaking the rule that guards Phase 0.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Move `E0-07` to the start of Phase 1, immediately after `E1-01` | No contradiction: `:core:database` exists by then and Room is legitimately available. Phase 0 closes on tooling and contracts, which is what it actually delivers. | The riskiest integration is proven one story later, and the `E0-07` human gate moves with it. |
| Allow `E0-07` to create `:core:database` | Keeps the walking skeleton inside Phase 0. | Punches an exception through the Phase 0 module set that `E0-04` enforces, and the exception has to be written into both the preamble and the rule. |
| Reduce `E0-07` to exclude Room, with in-memory local state | No rule bent. | Stops being a walking skeleton. Its headline criterion — a value restored on a clean second device — is a persistence claim, and Room-on-iOS is the largest technical risk in the plan. |

## Decision

`E0-07` becomes the second story of Phase 1, immediately after `E1-01`. Phase 0 closes with `E0-02` through `E0-06` and `E0-08`. The execution order and the phase gate move with it; `E0-07` keeps its human review gate and its Room-to-SQLDelight decision gate.

## Consequences

### Positive

- The Phase 0 module set stays absolute, with no exception for any story.
- `E0-07` gets a real Room implementation to exercise rather than a stub.

### Negative

- The end-to-end integration risk is carried one story longer. `E1-01` is a self-contained persistence story, so the exposure is one story, not a phase.

### Constraints Introduced

- Phase 1 starts with `E1-01`, then `E0-07` as its gate; no other Phase 1 story starts until that gate passes.

## Verification

- `docs/BACKLOG.md` execution order lists `E1-01` then `E0-07` before the rest of Phase 1.

## References

- `docs/DECISION_BOARD.md` (`D-30`)
- `docs/BACKLOG.md`
- `docs/PHASE0_OPEN_DECISIONS.md` (deleted once absorbed)
