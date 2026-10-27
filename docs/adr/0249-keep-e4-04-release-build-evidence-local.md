# ADR-0249 / D-248 - Keep E4-04 Release Build Evidence Local

## Status

Accepted

The owner selected option A on 2026-10-24.

## Context

No CI job builds a release artifact. Changing the `docs/CONTRACTS.md §18` required-check list is gated
and changes branch protection.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Local release-build evidence in `E4-04`; the ten required checks unchanged | No branch-protection change. | A release-only regression is caught only by the next local or owner build. |
| B. A non-required CI release build job | Continuous signal. | CI time and secrets-free signing setup; scope for a follow-up. |

## Decision

The selected option is **A**. `E4-04` records `assembleRelease`, `bundleRelease` and an unsigned iOS
Release build in its handoff; B is offered as a follow-up story.

## Consequences

### Positive

- §18 and branch protection are unchanged.

### Negative

- Release-only breakage is not caught by CI.

### Constraints Introduced

- Every release uses the local evidence commands of `docs/handoffs/E4-04.md`.

## Verification

- `docs/handoffs/E4-04.md` "Release installability evidence".

## References

- `docs/DECISION_BOARD.md` (decision ID `D-248`); `docs/CONTRACTS.md §18`
