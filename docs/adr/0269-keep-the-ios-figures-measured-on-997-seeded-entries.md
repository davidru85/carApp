# ADR-0269 / D-268 - Keep the iOS Figures Measured on 997 Seeded Entries

## Status

Accepted

The owner selected option A on 2026-11-05, after O18 had measured the iOS cold-start and smoothness
gates on a history that held 997 fuel entries instead of 1,000. On 2026-11-04 the owner had already
chosen to finish O18 on those entries.

## Context

`D-254` (ADR-0255) seeds one vehicle with 1,000 fuel entries, and `docs/versions-matrix.md` measures
list smoothness on that history. In O18, on the iPhone 13 mini (iOS 27.0.1), the iOS seeding test passed
and reported `benchmark_seed_done` with the value 1,000. After the adoption step, a read-only copy of the
app's database held one vehicle and **997** fuel entries.

The iOS seeder reports the planned count, not a count read back from the database.
`BenchmarkSeeding.createFuelEntry` drives one `FuelEntryFormViewModel` per entry and treats the save
callback as success, so three entries were lost without the seeding noticing. The shared seeder that
Android uses writes through `FuelRepository.createFuelEntry` and stops at the first write that returns an
error. It reported `seed=ok vehicles=1 fuelEntries=1000` on the emulator of `D-267`, but it does not
read the count back either.

On the 997 entries, the iOS cold-start gate median was 0.518455 s against 2 s. Each of the five fling
runs recorded 0 hitches.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Keep the O18 figures, record the deviation, and fix the seeder in a post-MVP story | No new device session; the 0.3 % shortfall cannot change a 0-hitch fling or a cold start four times under its gate | The iOS figures are not on exactly 1,000 entries |
| B. Fix the seeder inside `E4-03` with TDD, then repeat I4 to I8 on the iPhone | Exact 1,000-entry figures | One more TDD cycle and an iPhone session of about two hours, for figures that cannot change the verdict |
| C. Keep the figures and leave the seeder as it is | No work | A tool that reports a count it did not check stays in the repository |

## Decision

The selected option is **A**.

- The O18 figures stand. The `E4-03` measurement table records them as measured on 997 fuel entries.
- The post-MVP story `E5-06` (iOS benchmark seeder loses entries) finds and fixes the lost entries. It
  makes the seeding report a count read back from the database, and it fails the seeding when that
  count differs from the plan.
- `E5-06` does not re-measure. A later iOS measurement uses the fixed seeder.

## Consequences

### Positive

- `E4-03` closes without another iPhone session.
- The defect is recorded with its symptom and an owner, so no later measurement trusts the reported
  count.

### Negative

- The iOS smoothness figure covers 997 entries rather than 1,000.
- Until `E5-06` lands, the iOS seeding report can claim 1,000 entries that the database does not hold.

### Constraints Introduced

- The iOS figures of `E4-03` are recorded with the 997-entry history beside them.
- `E5-06` MUST make the iOS seeding compare the database count with the plan before it reports success,
  and MUST check whether the shared seeder needs the same read-back.

## Verification

- `docs/handoffs/E4-B2.md` O18 records the 997-entry read-back and the figures.
- `docs/BACKLOG.md` §`E5-06` carries the acceptance criteria.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-268`); `D-254` (ADR-0255); `D-266` (ADR-0267); `D-267`
  (ADR-0268)
- `docs/runbooks/performance-measurement.md` §3 I4 and I5
