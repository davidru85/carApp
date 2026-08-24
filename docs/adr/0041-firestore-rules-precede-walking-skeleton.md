# ADR-0041 - Firestore Rules Precede the Walking Skeleton

## Status

Accepted

## Context

`E0-07` needs a real authenticated Firestore round trip. The development database cannot safely
accept mobile writes through temporary or permissive rules, and the repository contract requires
the closed schemas and emulator evidence owned by `E3-01`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Complete E3-01 before E0-07 | Establishes secure, reproducible rules before real client traffic. | Adds a prerequisite pull request. |
| Fold E3-01 into E0-07 | Keeps the original next-story label. | Mixes two gated stories and creates an oversized review. |
| Deploy temporary cloud-only rules | Fastest path to a manual round trip. | Diverges from the contract, is not reproducible and creates a security gap. |

## Decision

The selected option is to complete `E3-01` before `E0-07`.

## Consequences

### Positive

- The first real mobile write is governed by the complete reviewed rule set.
- Firestore emulator evidence is isolated in its owning story.
- The public repository never relies on an undocumented permissive backend state.

### Negative

- The walking skeleton moves one pull request later.

### Constraints Introduced

- Temporary or cloud-only walking-skeleton rules are forbidden.
- `E0-07` starts only after the `E3-01` pull request is merged.

## Verification

- The backlog execution order places E3-01 before E0-07.
- E3-01 runs every emulator test required by `docs/CONTRACTS.md §16`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-40`)
- `docs/CONTRACTS.md §16`
- `docs/TECHNICAL_PLAN.md §12`
- `docs/BACKLOG.md` (`E3-01`, `E0-07`)
