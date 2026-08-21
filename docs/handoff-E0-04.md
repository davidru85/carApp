# Agent Handoff - E0-04

## Story

`E0-04 - Architecture Guards - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — `E0-04`.
- [x] Acceptance criteria reviewed — the 21 criteria listed under `E0-04`.
- [x] Dependencies checked — stacked on `E0-02` (convention plugins) and `E0-03`/`E0-08` (the modules to check).
- [x] Required decisions are not `Proposed` or `Pending` — none pending; applies `D-2` and `D-16`.
- [x] Normative sections reviewed — `docs/TECHNICAL_PLAN.md §4`, `docs/CONTRACTS.md §2`, `§3.1`, `§11.6`, `§17`, `§20.3.2`, `§20.6`.
- [x] Expected verification identified — `architectureCheck` green on the real graph, plus a failing fixture per rule.
- [x] Human review gates identified before work — none.
- [x] Rule 0 acknowledged — chat replies in Spanish (es-ES), every artifact in technical English.

## Scope Completed

`carapp.architecture` registers an `architectureCheck` task on the root project. Its rules are **generated from `docs/TECHNICAL_PLAN.md §4`**: the task parses that table at execution time, so editing the table changes the check, and a table the parser cannot understand fails rather than being skipped.

The rules themselves are pure functions over plain data (`ModuleUnderCheck`), which is what makes every one of them testable.

Module-graph rules, generated from the table: forbidden module edges, undeclared module edges, and forbidden library capabilities (Firebase, GitLive, Room, Koin, Ktor) resolved to real coordinates. Source and graph rules written against the contract: the Phase 0 module set, SKIE outside `:shared`, feature-to-feature edges, `expect`/`actual` in `:core:crash`, `AppDatabase`/`DatabaseFactory` leaking out of `:core:database`, `Float`/`Double` in `:core:*`, `:feature:*` and `:shared`, free-text `Logger` field values, logging from `:core:database` outside `PERSISTENCE.*`, `outbox.lastError` reads in sync logic, writes to `currentOdometerKm`/`odometerInconsistent` outside `:core:database`, `ConsumptionInvalidReason`/`SegmentResult` declared outside `:core:model`, `createAppGraph` referenced from `:integration:*`, and image-loading dependencies without a story reference.

## Acceptance Evidence

- `./gradlew architectureCheck` reports `14 rules from docs/TECHNICAL_PLAN.md §4, 8 modules` and passes on the real graph.
- `./gradlew :build-logic:convention:test` runs **23 fixtures, 0 failures**. Each asserts both directions: the offending shape is rejected with the expected rule name, and the legal shape beside it is accepted.
- Two defects were found by the fixtures and fixed, which is the evidence that the fixtures are not decorative:
  - the glob matcher used `Regex.escape`, which wraps the pattern in `\Q…\E`, so `*` was never substituted and `:core:*` matched nothing — every `:integration:*` and `:core:*` rule would have passed everything silently;
  - the capability parser matched table tokens exactly, so `:core:testing`'s "platform APIs in `commonMain` public API (…)" parsed to no rule at all.

## Out of Scope / Not Done

- **The three feature-layer rows of `§4` are not enforced** — feature `domain`, feature `data` and feature `presentation`. They are package-level rules inside a single Gradle module, which `D-16` assigns to Konsist, and Konsist rules need a module to live in. No `:feature:*` module exists yet. The rows are parsed and the module-level parts are ready; the package-level analysis belongs with the first feature module (`E1-07`). **This is `DEC-3`.**
- **Konsist is not applied at all**, for the same reason. It is pinned at 0.17.3 by `E0-06` and unused.
- `contract-check` assertion 1 and its Room-generated-type allowance are `E0-05`, not this story.
- "The check runs on every PR" needs CI, which `E0-05` creates.
- The `:wiring:firebase` "product logic" rule of `§4` — every top-level declaration must be a Koin `Module`, a factory returning an abstraction or a platform initialiser — is not implemented. It needs the module to exist and a Kotlin declaration parser rather than a line scanner. It belongs with `E3-08`.

## Files Changed

- `build-logic/convention/src/main/kotlin/.../architecture/` — `ArchitectureRule.kt`, `DependencyRuleTableParser.kt`, `ArchitectureChecker.kt`, `ArchitectureCheckPlugin.kt` (new).
- `build-logic/convention/src/test/kotlin/.../architecture/ArchitectureCheckerTest.kt` (new).
- `build-logic/convention/build.gradle.kts`, `build.gradle.kts`.

## Decisions Made

- **No `SHOULD` deviated from**; no new decision ID, so no ADR from this story.
- **The rules are pure functions and the fixtures feed them fabricated modules.** Most rules protect `:core:sync`, `:core:auth`, `:core:database`, `:integration:*` and `:feature:*`, and the Phase 0 module set forbids creating any of them — `E0-04` itself requires a rule that fails the build if they appear. A fixture that had to create the offending module could never exist for those rules. Fabricated data proves each rule fires today and keeps proving it when the real modules arrive.
- **Test sources are excluded from the source scan.** A test may legitimately name a forbidden type in order to assert that it is forbidden.
- **Comments are stripped before scanning.** Otherwise the KDoc explaining a ban trips the ban.

## Verification Run

- [x] Relevant tests pass
- [ ] Lint passes (ktlint, detekt) — `E0-05`
- [ ] Coverage thresholds hold — `E0-05`
- [x] Architecture checks pass
- [ ] Contract check passes — `E0-05`
- [x] Relevant builds pass
- [x] Documentation updated

```text
./gradlew architectureCheck :build-logic:convention:test
./gradlew :core:model:testAndroidHostTest :core:common:testAndroidHostTest :core:analytics:testAndroidHostTest \
          :core:crash:testAndroidHostTest :core:testing:testAndroidHostTest :shared:testAndroidHostTest \
          :androidApp:assembleDebug
```

## Contract Impact

- [x] No contract changes.

## Decision Board Impact

- [x] No decision changes.

## Shared-Write Modules Touched

- [x] None.

## Project Log Entry

- [x] Entry appended to `docs/PROJECT_LOG.md`.

## Risks or Follow-ups

- `DEC-3`: the feature-layer package rules and Konsist are unimplemented and need an owner decision on where they land.
- The source scan is line-based, not a Kotlin parser. It is deliberately conservative and can be defeated by unusual formatting; it catches the realistic mistake, not a determined workaround.
- The check is not wired into `check` or CI yet; until `E0-05`, it runs only when invoked.

## Human Review Gate

- [x] Not applicable.
