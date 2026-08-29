# Agent Handoff - E1-05

## Story

`E1-05 - Consumption Calculation R-3 - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — implement the pure `CalculateConsumption` use case for R-3.
- [x] Acceptance criteria reviewed — full-to-full happy path, partial-entry participation without
  row segments, every invalid reason and D-78 overlap precedence, deterministic calculation order,
  canonical segment and weighted-average arithmetic, totality, no use-case filtering and the
  1,000-entry performance workload.
- [x] Dependencies checked — E1-01 through E1-04 and E0-07 are complete; `:core:model` already owns
  `FuelEntry`, `ConsumptionReport`, `SegmentResult`, invalid reasons and canonical arithmetic.
  D-79 leaves the production repository filter with E1-06.
- [x] Decisions checked — D-18, D-26, D-37, D-75 and D-78 through D-80 apply and are `Accepted`.
  No `Proposed` or `Pending` decision blocks E1-05.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §3`, `§5.2`, `§6` R-3 and `§11`;
  `docs/CONTRACTS.md §2`, `§3`, `§4`, `§13`, `§20.4` and `§20.6`;
  `docs/TECHNICAL_PLAN.md §4`, `§10` and `§12`; and ADR-0079 through ADR-0081.
- [x] Expected verification identified — focused Android-host and `iosSimulatorArm64` tests;
  feature lint, detekt and Kover; standalone uninstrumented JVM benchmark; optimized
  `iosArm64` test-binary link; architecture and contract checks; the complete repository command
  from `AGENTS.md`; and `git diff --check`.
- [x] Human review gates identified before work — E1-05 is a gated story; this change also touches
  gated `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/versions-matrix.md` and `docs/adr/**`.
  The pull request requires owner review and MUST NOT be merged by the agent.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Added the canonical `CalculateConsumption` contract and its default pure implementation.
- Implemented full-to-full segment selection, partial-refuel accumulation, D-78 invalidation
  precedence, canonical segment arithmetic and distance-weighted average calculation.
- Added the shared functional, golden, ordering, totality, no-filter and complete overlap test
  suites on Android host and `iosSimulatorArm64`.
- Added the D-80 standalone uninstrumented JVM performance gate and optimized `iosArm64` test
  binary without adding a dependency.
- Added the protected Linux CI performance step and macOS optimized device-binary link step.

## Acceptance Evidence

- Two full tanks produce one `NoPreviousFullTank` anchor and one valid segment; one valid segment
  has a non-null average and is not reliable, while two valid segments set `isReliable = true`.
- Partial entries produce no `SegmentResult`, never produce `EndEntryNotFullTank` in the use case,
  and contribute litres to the next full-to-full segment.
- An internal segment-facts test proves an intermediate row sharing `P.odometerKm` contributes its
  litres and raises `DuplicateOdometerInSegment`; the public duplicate tests never use `E` as the
  duplicate acceptance fixture.
- Missed-entry and inconsistent-odometer flags invalidate only containing segments. D-78 tests
  prove every coexisting reason pair and the zero-distance/no-flag case in canonical precedence.
- Calculation sorts by `odometerKm, date, id`; a back-dated partial entry does not change its
  odometer segment.
- Feature tests exercise all three segment golden values and the weighted `774` average versus the
  incorrect `776` arithmetic mean. The canonical `:core:model` golden suite remains green.
- Empty, partial-only and extreme constructed input lists return without throwing.
- A foreign-vehicle row with non-null `deletedAt` participates when supplied directly, proving the
  D-79 use case performs no filtering.
- The first uninstrumented 1,000-entry run reported a 3,392,708 ns median with five warm-ups,
  twenty measurements, `javaAgents=0` and 96.6% headroom. The enabled gate later passed at
  3,568,521 ns.
- `linkReleaseTestIosArm64` produces the optimized device-test binary. The required manual result
  is deliberately not claimed:

| iOS performance evidence | Status | Device | Date |
|--------------------------|--------|--------|------|
| Optimized real-device median | Pending (E4-03, D-80) | — | — |

## Out of Scope / Not Done

- E1-06 production repository filtering and persistence. Its
  `FuelEntryRepositoryConsumptionFilterTest` must prove the moved criterion against the real
  `observeConsumption` implementation.
- The real-iPhone performance result is unavailable and remains explicit under D-80; neither the
  simulator nor the linked release binary is reported as a substitute measurement.

## Files Changed

- `feature/fuel/src/commonMain/**` — pure calculation contract, implementation and segment facts.
- `feature/fuel/src/commonTest/**` — functional, precedence, golden, no-filter and benchmark data.
- `feature/fuel/src/androidHostTest/**` — standalone uninstrumented JVM benchmark entry point.
- `feature/fuel/src/iosArm64Test/**` — optimized real-device benchmark test entry point.
- `feature/fuel/build.gradle.kts` and `.github/workflows/ci.yml` — isolated JVM task, optimized
  Native test binary and protected CI steps.
- D-78 through D-80 records — three ADRs and all four decision mirrors.
- `docs/CONTRACTS.md §4`, `docs/BACKLOG.md`, `docs/versions-matrix.md` and current-state records.

## Decisions Made

- D-78 fixes singular consumption invalidation precedence.
- D-79 moves production repository-filter evidence to E1-06 and makes E1-05 prove no filtering.
- D-80 isolates performance measurement from coverage and keeps real-device evidence explicit.
- The owner approved the three decisions before RED, so they were recorded as `Accepted` without
  creating a Definition-of-Ready contradiction.
- No TDD exemption was used. RED, GREEN and REFACTOR are separate commits followed by one push.

## Verification Run

- RED `./gradlew :feature:fuel:testAndroidHostTest` — failed as required: 61 tests executed, the 21
  new behavior tests failed against the empty implementation.
- RED `./gradlew :feature:fuel:iosSimulatorArm64Test` — failed as required: 58 tests executed, the
  same 21 common behavior tests failed.
- GREEN `./gradlew :feature:fuel:testAndroidHostTest :feature:fuel:iosSimulatorArm64Test` —
  successful with the RED tests unchanged.
- `./gradlew :feature:fuel:consumptionBenchmark :feature:fuel:linkReleaseTestIosArm64` — successful;
  first real JVM median 3,392,708 ns, no Java agent, and optimized device binary linked.
- `./gradlew :feature:fuel:testAndroidHostTest :feature:fuel:iosSimulatorArm64Test
  :feature:fuel:consumptionBenchmark :feature:fuel:linkReleaseTestIosArm64
  :feature:fuel:ktlintCheck :feature:fuel:detekt :feature:fuel:koverVerify architectureCheck
  contractCheck` — successful after REFACTOR; gate median 3,568,521 ns, feature coverage passed,
  16 architecture rules passed and 81 decision/ADR mirrors passed.
- Complete repository verification command — passed (`BUILD SUCCESSFUL` in 8 seconds; 602 actionable tasks: 56 executed, 1 from cache and 545 up-to-date).
- `git diff --check` — passed with no whitespace errors.

## Contract Impact

- Updated `docs/CONTRACTS.md §4` with D-78 precedence.

## Decision Board Impact

- Added D-78, D-79 and D-80 with ADR-0079, ADR-0080 and ADR-0081.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [x] Entry appended.

## Risks or Follow-ups

- E1-06 must prove that production `observeConsumption` supplies only non-deleted entries for the
  requested vehicle to `CalculateConsumption` through
  `FuelEntryRepositoryConsumptionFilterTest`.
- E4-03 must run and record the optimized benchmark on a real iPhone if it remains unavailable
  during E1-05.

## Human Review Gate

- Applies: E1-05 gated story and gated contract, decision, ADR and versions-matrix paths. Owner
  review is required before merge.
