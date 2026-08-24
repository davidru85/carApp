# ADR-0043 - Provider Decoupling Precedes the First Integration

## Status

Accepted

## Context

The required `provider-decoupling` CI job deliberately fails once an `integration/` directory
exists until `E3-06` replaces its placeholder with a real proof. `E3-01` needs the first Firebase
client configuration, so introducing it first would make the protected branch check fail.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Complete E3-06 before E3-01 | Preserves one story per pull request and keeps CI effective. | Adds another prerequisite pull request. |
| Fold E3-06 into E3-01 | Fewer pull requests. | Mixes two stories and weakens review isolation. |
| Relax the placeholder until later | Lets E3-01 start immediately. | Removes the guard precisely when provider code appears. |

## Decision

The selected option is to complete `E3-06` before `E3-01`, which remains before `E0-07` under
`D-40`.

## Consequences

### Positive

- The first provider module lands only after decoupling is executable.
- Every pull request retains one owning backlog story.
- The required CI check never needs a weakened transition state.

### Negative

- E3-01 and E0-07 each move one pull request later.

### Constraints Introduced

- The order is E3-06, E3-01, then E0-07.
- A provider integration MUST NOT be introduced before the E3-06 proof is merged.

## Verification

- The backlog execution order and story statuses expose this prerequisite chain.
- The E3-06 pull request replaces the placeholder CI job before `integration/` exists.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-42`)
- `docs/SPECIFICATION.md §8.5`
- `docs/TECHNICAL_PLAN.md §5`
- `docs/BACKLOG.md` (`E3-06`, `E3-01`, `E0-07`)
