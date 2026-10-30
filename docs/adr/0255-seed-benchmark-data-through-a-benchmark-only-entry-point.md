# ADR-0255 / D-254 - Seed Benchmark Data Through a Benchmark-Only Entry Point

## Status

Accepted

The owner selected option A on 2026-10-24. The `staging`-based execution (R-1/R-2/R-3) was confirmed on
2026-10-30.

## Context

The smoothness and returning-user measurements need one vehicle with 1,000 fuel entries on the device.
No seeding path exists. Writes must go through `DatabaseMutations` so the stored rows are exactly what
the product would store, and nothing may be enqueued for remote backup while seeding.
`SwiftDebugOnlyHookRule` (`E4-04`) recognised only reads of the launch environment, so a launch-argument
hook would not have been checked at all.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. A benchmark-only entry point writing through the product repositories while `LOCAL_OWNER`, offline | Real write path, real read models, nothing enqueued | Code that must provably stay out of shipping builds |
| B. A prebuilt SQLite fixture copied into the app container | Fast | Bypasses validation and read-model maintenance; breaks on every schema change |
| C. UI-driven entry | No new code | About an hour of UI automation per run |

## Decision

The selected option is **A**.

- `:shared` declares `seedBenchmarkFuelHistory(graph: AppGraph, fuelEntryCount: Int)` and the pure plan
  `benchmarkFuelEntrySeed(index: Int)`, all `@HiddenFromObjC`. The function writes one vehicle and the
  planned entries through the graph's own decorated repositories, so `DatabaseMutations`, validation,
  read models and adoption hooks run exactly as in the product. It refuses, writing nothing, unless the
  device is offline and the current owner is `LOCAL_OWNER`, and re-checks both before every write.
- The plan: vehicle "Benchmark vehicle", initial odometer 10,000 km; entry `i` (from 0) dated
  2020-01-01T00:00:00Z plus `i` days, odometer `10,000 + (i + 1) × 500` km, 40.000 L at 1.500 per
  litre, a partial fill for every `i` with `i % 10 == 9`.
- Android: `BenchmarkSeedActivity` exists only in `androidApp/src/benchmark`, is exported only in the
  benchmark manifest (the shell cannot start a non-exported activity) and logs one result line with
  the tag `CarAppBenchmark`.
- iOS: the launch argument `-CARAPP_BENCHMARK_SEED` is read only inside `#if CARAPP_BENCHMARK`, compiled
  only in the `Benchmark` configuration. Because the Swift ABI is closed, the iOS entry point drives the
  existing `VehicleFormViewModel` and `FuelEntryFormViewModel`, which reach the same repositories; its
  plan constants are pinned equal to the Kotlin plan by a build-logic guard.
- R-3: `SwiftDebugOnlyHookRule` also treats `processInfo.arguments` as a launch read. A launch-argument
  read is accepted only in the active branch of `#if DEBUG` or `#if CARAPP_BENCHMARK` (alone or conjoined
  with `&&`); a disjunction, an `#else` or an `#elseif` branch is rejected, as for the environment.

## Consequences

### Positive

- Seeded data is indistinguishable from data the owner entered.
- Every launch-argument hook of the application target is now checked.

### Negative

- The `:shared` seeding function ships, unreachable, in the Android release artifact (R8 is off,
  `D-245`).

### Constraints Introduced

- No `main`, `debug`, `release` or `staging` source set and no Swift compiled outside `CARAPP_BENCHMARK`
  references a seeding entry point; a build-logic guard enforces it.
- `CARAPP_BENCHMARK` is defined only in the `Benchmark` settings block of `iosApp/project.yml`.

## Verification

- `BenchmarkSeedingTest`: the plan values, exactly N entries, nothing enqueued, refusal online and for a
  non-sentinel owner, identical histories on two empty databases.
- `PerformanceHarnessContractTest`: the entry points' placement, the condition's placement and the
  cross-host plan parity.
- `SwiftDebugOnlyHookContractTest`: the launch-argument fixtures.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-254`)
- `docs/SPECIFICATION.md §9.1`; `docs/CONTRACTS.md §11.2`-`§11.4`; `D-38`, `D-124`; `D-251`, `D-259`
