# ADR-0031 / D-30 - `E0-07` Walking Skeleton Moves to the Start of Phase 1

## Status

Accepted

Accepted by the owner on 2026-08-21.

Amended by `D-36` (SQLDelight replaces Room), `D-42` (E3-06 and E3-01 become prerequisites) and
`D-64` (the cross-device proof moves to E3-12 and uses permanent authentication). The original
sequencing context below is retained, but E0-07's current acceptance contract is the local/remote
Vehicle path under the same retained anonymous session.

## Context

`E0-07` builds a screen crossing native UI, a shared state holder, **Room**, Firestore and real anonymous auth, and its decision gate is about Room KMP on iOS.

Room lives in `:core:database`, which is `E1-01`. The Phase 0 preamble forbids creating `:core:database`, and `E0-04` now implements a rule that fails the build if it appears. `E0-07` therefore cannot use Room without breaking the rule that guards Phase 0.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Move `E0-07` to the start of Phase 1, immediately after `E1-01` | No contradiction: `:core:database` exists by then and Room is legitimately available. Phase 0 closes on tooling and contracts, which is what it actually delivers. | The riskiest integration is proven one story later, and the `E0-07` human gate moves with it. |
| Allow `E0-07` to create `:core:database` | Keeps the walking skeleton inside Phase 0. | Punches an exception through the Phase 0 module set that `E0-04` enforces, and the exception has to be written into both the preamble and the rule. |
| Reduce `E0-07` to exclude persistent local state | No rule bent. | Stops being a walking skeleton. Its persistence claim requires the real database on both native paths. |

## Decision

`E0-07` moves to Phase 1 after `E1-01`. Phase 0 closes with `E0-02` through `E0-06` and `E0-08`.
The execution order and phase gate move with it; later prerequisite decisions refine its exact
position and database implementation.

## Consequences

### Positive

- The Phase 0 module set stays absolute, with no exception for any story.
- `E0-07` gets a real persistent database implementation to exercise rather than a stub.

### Negative

- The end-to-end integration risk is carried one story longer. `E1-01` is a self-contained persistence story, so the exposure is one story, not a phase.

### Constraints Introduced

- Phase 1 starts with `E1-01`; E0-07 follows its accepted E3-06 and E3-01 prerequisites and remains
  the opening gate before other Phase 1 feature stories.

## Verification

- `docs/BACKLOG.md` execution order lists `E1-01` then `E0-07` before the rest of Phase 1.

## References

- `docs/DECISION_BOARD.md` (`D-30`)
- `docs/BACKLOG.md`
- `docs/PHASE0_OPEN_DECISIONS.md` (deleted once absorbed)
