# Agent Handoff - E1-01

## Story

`E1-01 - :core:database - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — `E1-01`, the next story after Phase 0.
- [x] Acceptance criteria reviewed — multiplatform persistence; schema v1 and every required table;
  synchronized control-column constraints; nullable `serverUpdatedAt`; shared mutation sequence;
  exact outbox DDL and coalescing; database-owned odometer invariants; committed SQL schema and
  migration verification; observable `Flow` queries and suspending one-shot operations.
- [x] Dependencies checked — Phase 0 is complete; `E0-01` through `E0-06` and `E0-08` are merged;
  `E0-07` depends on this story and therefore has not started.
- [x] Decisions checked — `D-36` was accepted by the owner on 2026-08-22 and supersedes `D-1`;
  `D-37` was accepted on 2026-08-23 and limits Kotlin/Native iOS targets to ARM64;
  `D-38` was accepted on 2026-08-24 and selects the transaction facade for entity mutations;
  no `Proposed` or `Pending` decision remains.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §2`, `§3.1`, `§6`, `§8`, `§9`, `§11`;
  `docs/CONTRACTS.md §2`–`§5`, `§7`–`§9`, `§15.1`, `§18`, `§20.3.2`;
  `docs/DECISION_BOARD.md` (`D-1`, `D-36`, `D-37`, `D-38`);
  `docs/TECHNICAL_PLAN.md §3`, `§4`, `§6`, `§12`; `docs/versions-matrix.md`; ADR-0037 through
  ADR-0039.
- [x] Expected verification identified — focused `:core:database` Android host and
  `iosSimulatorArm64` tests; SQLDelight migration verification; architecture fixtures; and the
  complete repository command from `AGENTS.md`.
- [x] Human review gates identified before work — gated paths `docs/SPECIFICATION.md`,
  `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/adr/**`, `docs/versions-matrix.md` and
  `core/database/**`; gated topics technical stack, pinned versions and module boundaries.
- [x] Rule 0 acknowledged — owner conversation remains Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Added `:core:database` with SQLDelight 2.3.2, the SQLite 3.24 dialect, asynchronous generated
  operations and AndroidX bundled SQLite 2.7.0 through `sqldelight-androidx-driver` 0.2.1.
- Committed schema v1 and typed operations for all seven local tables, the shared mutation
  sequence, outbox coalescing, observable reads and suspending one-shot reads.
- Added `DatabaseMutations` as the atomic write boundary for fuel-entry create, update, single
  tombstone and vehicle-cascade tombstone recomputation.
- Executed the same database behavior and file-backed persistence tests on Android host and
  `iosSimulatorArm64`.
- Removed the unsupported `iosX64` target and added the D-38 architecture rule that rejects direct
  generated entity mutations outside `:core:database`.

## Acceptance Evidence

- `AndroidFileBackedSchemaTest` and `IosFileBackedSchemaTest` create a file-backed database, close
  it, reopen it and recover the inserted row using the bundled driver.
- `SchemaV1Test` proves the seven-table inventory; both synchronized entities reject `deleted`
  outside `0`/`1`, reject both invalid `deleted`/`deletedAt` combinations and round-trip nullable
  `serverUpdatedAt`; it also proves the absence of the fuel-entry foreign key and vehicle-name
  unique index and checks `idx_outbox_due` column order.
- `LocalSequenceTest` proves monotonic allocation shared across entity types and proves writes with
  supplied sequences do not consume the counter.
- `OutboxCoalescingTest` proves snapshot replacement resets retry state while preserving the
  original outbox `seq`.
- RED/GREEN commits prove each product behavior independently: sequence `00be415`/`03738ee`,
  outbox `3f16481`/`6787a19`, reads `30b32e0`/`ceb690a`, create recomputation
  `2ff424f`/`ab0b74c`, update recomputation `1ffde49`/`7c97585`, single tombstone
  `117f214`/`c8d2517`, cascade tombstones `3e838ad`/`34e1a6f`, and the mutation boundary
  `a1c1783`/`d927904`.
- Recompute tests use deliberately stale non-target rows and cover create, chronological move,
  odometer-only update, coincident successors, notes/currency no-op, the pre-delete successor and
  the three-row cascade exclusion set.
- `SqlDelightConventionPlugin` keeps `.sq` files as the schema source, enables
  `verifyMigrations`, disables system-SQLite linking and contains no destructive recreation path.
- `DatabaseReadQueriesTest` uses Turbine to prove `Flow` invalidation and verifies a suspending
  one-shot lookup.
- `ArchitectureCheckerTest.generatedEntityMutationsAreCalledOnlyByCoreDatabase` covers every
  generated synchronized-entity mutation function and its permitted facade path.

## Out of Scope / Not Done

- `E0-07` and the remaining Phase 1 stories are not part of this pull request.

## Files Changed

- `core/database/**` — schema, typed queries, transaction facade and multiplatform tests.
- `build-logic/convention/**` — SQLDelight convention plugin, dependency capability updates and
  the D-38 source guard.
- `gradle/libs.versions.toml`, root/settings/shared Gradle files — accepted dependency set, module
  registration and ARM64 iOS targets.
- `docs/adr/0037-*`, `0038-*`, `0039-*` and the four decision mirrors — D-36 through D-38.
- `AGENTS.md`, `README.md`, `docs/DEFINITION.md`, `docs/BACKLOG.md`, this handoff and
  `docs/PROJECT_LOG.md` — live repository state and completion evidence.

## Decisions Made

- `D-36` supersedes `D-1`; see [ADR-0037](adr/0037-local-database-sqldelight-androidx-sqlite.md).
- `D-37` removes the already-unlinked `iosX64` simulator target because the complete accepted
  bundled-SQLite dependency set publishes only ARM64 iOS variants; see
  [ADR-0038](adr/0038-supported-ios-targets-are-arm64.md).
- `D-38` routes synchronized entity writes through `DatabaseMutations` and rejects direct
  generated entity-mutation calls outside `:core:database`; see
  [ADR-0039](adr/0039-database-mutations-use-transaction-facade.md).
- The TDD order exemption for SQLDelight schemas and migrations was used as permitted by
  `docs/SPECIFICATION.md §11`; product behaviours around sequence allocation, coalescing and
  read-model recomputation used RED/GREEN commits.
- No refactoring phase was needed after any GREEN increment; the explicit optional refactoring
  commit was therefore skipped as permitted by `docs/SPECIFICATION.md §11`.
- Rule 0 held throughout: owner conversation was Spanish (Spain), and repository artifacts,
  branches, commits and pull-request content are technical English.

## Verification Run

- `./gradlew :core:database:testAndroidHostTest :core:database:iosSimulatorArm64Test
  :core:database:ktlintCheck :core:database:detekt` — successful after every GREEN recomputation
  increment.
- `./gradlew :build-logic:convention:test architectureCheck` — successful; 14 dependency-table
  rules across 9 modules, including the D-38 failing fixture.
- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
  koverVerify :androidApp:assembleDebug testAndroidHostTest iosSimulatorArm64Test` — `BUILD
  SUCCESSFUL` in 23 seconds, 273 actionable tasks; 39 decisions match, with the three expected
  future-story contract assertions reported as `PENDING`.
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — `BUILD SUCCESSFUL`.
- `xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator -configuration Debug
  ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build` from `iosApp/` — `BUILD SUCCEEDED`.

## Contract Impact

- Updated `docs/CONTRACTS.md` terminology and database-type ownership for `D-36`; product data and
  behavioural contracts remain unchanged.

## Decision Board Impact

- Updated `docs/DECISION_BOARD.md`: `D-1` is `Superseded`; `D-36`, `D-37` and `D-38` are
  `Accepted` with ADR-0037, ADR-0038 and ADR-0039 respectively.

## Shared-Write Modules Touched

- `:core:database`; no other story is modifying it.

## Project Log Entry

- [x] Entry appended at story completion.

## Risks or Follow-ups

- `E0-07` must exercise the accepted database stack through the real Android and iOS application
  composition paths; E1-01 proves the driver and persistence directly but does not build the app
  graph owned by that story.
- Every new synchronized-entity mutation must extend `DatabaseMutations` and the closed
  architecture fixture in the same story.
- Schema v1 has no predecessor and therefore no `.sqm` file. Every future version must add a
  committed migration and a populated previous-version migration test.
- `iosX64` is unsupported under D-37. Reintroduction requires a complete compatible database
  dependency set and a superseding owner decision.

## Human Review Gate

- Applies: `core/database/**`, `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`,
  `docs/DECISION_BOARD.md`, `docs/adr/**` and `docs/versions-matrix.md`; technical stack, pinned
  versions and module boundaries. The owner must review and merge this pull request.
