# ADR-0079 / D-78 - Apply Structural-First Consumption Invalidation Precedence

## Status

Accepted

## Context

`CalculateConsumption` returns one singular `ConsumptionInvalidReason` for an invalid full-tank
segment, but several invalidation conditions can apply to the same segment. The existing contract
defines the reason set without defining which reason wins. An implementation-local order would
make UI explanations unstable and could contradict the unconditional `docs/CONTRACTS.md §2`
guarantee that a segment with `distanceKm <= 0` returns `NonPositiveDistance` and never reaches
division.

Calculation order is `odometerKm, date, id`, so the previous full-tank anchor `P` always has
`P.odometerKm <= E.odometerKm`. Distance is therefore never negative and is zero exactly when the
two full-tank odometers are equal. Zero distance does not imply `odometerInconsistent`: R-1 uses
chronological order `date, createdAt, id`, and the additional `createdAt` tie-breaker can give `E`
a different chronological predecessor. A zero-distance segment can consequently have no user or
derived flag at all, which makes the §2 guarantee independently necessary.

Returning multiple reasons would also change `SegmentResult.Invalid.reason`, the singular
`FuelEntryListItemUi.invalidReason` projection and the Swift-facing surface rather than completing
the existing E1-05 contract.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Structural-first precedence | Preserves the unconditional arithmetic guard; gives duplicate structure priority over data-quality flags; keeps every canonical type unchanged. | Only one applicable explanation is exposed. |
| User-flag-first precedence | Surfaces missed-entry and inconsistent-odometer flags first. | Can hide zero distance and contradict the unconditional `NonPositiveDistance` contract. |
| Return every applicable reason | Preserves every explanation for presentation. | Changes core models, list projections and the Swift-facing contract; expands the story substantially. |

## Decision

When multiple reasons apply, `CalculateConsumption` uses this precedence:

```text
NoPreviousFullTank > NonPositiveDistance > DuplicateOdometerInSegment >
MissedEntriesInSegment > InconsistentOdometerInSegment
```

`EndEntryNotFullTank` is excluded because it belongs only to the list projection and is never a
`SegmentResult.Invalid` reason.

## Consequences

### Positive

- Non-positive distance always prevents division and always produces its normative reason.
- Duplicate-odometer structure is reported before flags on entries inside the segment.
- Consumption result and Swift projection shapes remain unchanged.
- The reason selected for the same input is deterministic across platforms.

### Negative

- Lower-priority applicable causes are not exposed to presentation.
- Tests must cover overlapping causes so later refactors cannot reorder the checks accidentally.

### Constraints Introduced

- A duplicate-odometer acceptance test uses an intermediate entry sharing `P.odometerKm`; using
  `E` would create zero distance and correctly select `NonPositiveDistance` first.
- Tests cover every pair of reasons that can coexist, including zero distance with no flags.

## Verification

- Common tests execute the same precedence matrix on Android host and `iosSimulatorArm64`.
- `docs/CONTRACTS.md §4` records the normative precedence.

## References

- `docs/SPECIFICATION.md §6` R-1 and R-3
- `docs/CONTRACTS.md §2`, `§4`, `§20.6`
- `docs/BACKLOG.md` (`E1-05`)
