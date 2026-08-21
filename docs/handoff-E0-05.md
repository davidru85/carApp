# Agent Handoff - E0-05

## Story

`E0-05 - Quality Tooling and CI - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — `E0-05`.
- [x] Acceptance criteria reviewed — the eight criteria listed under `E0-05`.
- [x] Dependencies checked — stacked on `E0-02`, `E0-03`, `E0-08` and `E0-04`.
- [x] Required decisions are not `Proposed` or `Pending` — none pending; applies `D-16` and `D-18`.
- [x] Normative sections reviewed — `docs/CONTRACTS.md §18`, `docs/versions-matrix.md` "Coverage thresholds".
- [x] Expected verification identified — the whole CI task set run locally.
- [x] Human review gates identified before work — none for the code; branch protection is an owner action.
- [x] Rule 0 acknowledged — chat replies in Spanish (es-ES), every artifact in technical English.

## Scope Completed

- `.editorconfig` with `ktlint_code_style = ktlint_official`, and `detekt.yml`, both committed at the root and shared by every module. **No baseline file anywhere**, and CI fails if one appears.
- `carapp.quality` (ktlint + detekt) and `carapp.coverage` (Kover) convention plugins, applied by `carapp.kmp.library` and `carapp.android.application`, so a new module cannot opt out.
- Kover thresholds from `D-18`: `:core:model` and `:core:common` at 90%, `:core:sync` at 80%, feature modules at 85%. `koverVerify` passes for both Phase 0 modules that have one.
- `contractCheck`, implementing the assertions of `§18` that can run today.
- `.github/workflows/ci.yml` with the nine check names fixed by `§18`, unchanged and in order.

## Acceptance Evidence

| Criterion | Evidence |
|-----------|----------|
| `.editorconfig` sets `ktlint_official`; `detekt.yml` committed; no baseline files | Both files exist; the `ktlint` job fails the build if a baseline file is committed. |
| Style violations, failing tests and coverage below threshold fail CI | `ktlintCheck`, `detekt` and `koverVerify` all run with `ignoreFailures = false`. `koverVerify` was observed failing at 82.6% against the 90% bound before the guard tests were added. |
| Android and iOS simulator / shared framework verification run on macOS CI | `shared-tests` and `ios-simulator-build` run on `macos-latest`; the latter links the framework and builds the Xcode project. |
| CI check names match `§18` exactly | The nine job names are `android-assemble`, `shared-tests`, `ios-simulator-build`, `ktlint`, `detekt`, `architecture-check`, `provider-decoupling`, `contract-check`, `objc-header-golden-check`. |
| `contract-check` implements the `§18` assertions and fails when any is violated | 10 assertions PASS, 3 report PENDING with the story that unblocks them. It found two real defects while being written: six interfaces named in the contract appeared in no backlog story, and the `:core:testing` platform-API row parsed to nothing. |
| The first invocation uses the Phase 0 type set | Assertion 1 runs over every `kotlin` fence in `docs/CONTRACTS.md`, which is a superset of the Phase 0 type set. |
| The external identifier allowlist is applied | The allowlist of `§18` is implemented verbatim, including Room-generated types and coroutine types. |
| The `Instant` package is read from the matrix | `E0-06` pinned it; assertion 15 fails if a `TBD` returns, and `PinnedInstantPackageTest` fails the build if the package moves. |
| Branch protection requires those checks | **Owner action, not done — see below.** |

## Out of Scope / Not Done

- **Branch protection for `main` is not configured.** It needs repository admin rights, cannot be set from a PR, and the checks must run at least once before GitHub will offer them by name. It is the owner's action after this merges. **`DEC-6`.**
- **Three `§18` assertions report `PENDING`, not `PASS`:** 7 (the Objective-C golden header, produced by `E0-07`), 11 (`CLIENT_MAX_SCHEMA_VERSION` against `firestore/rules/main.rules`, created by `E3-01`) and 13 (`testAppGraphDependencies` parity, blocked by `DEC-2`). They are reported rather than skipped: a check that silently skips reads as coverage that is not there.
- Assertions 9, 10, 14, 16 and 17 are not implemented as separate checks. 10 is a rule about how assertion 2 compares (implemented inside it); 16 is implemented in `architectureCheck`; 9, 14 and 17 need `docs/SPECIFICATION.md §8.3` parsing and the `§20.10` Swift surface, which arrives with `E0-07`.
- `objc-header-golden-check` and `provider-decoupling` exist as jobs but have nothing to verify yet. They emit a GitHub warning and pass, and both fail loudly if the thing they guard appears without the check being implemented.
- CI has never actually run. Everything below was verified locally.

## Files Changed

- `.editorconfig`, `detekt.yml`, `.github/workflows/ci.yml` (new).
- `build-logic/convention/src/main/kotlin/.../QualityConventionPlugin.kt`, `CoverageConventionPlugin.kt`, `contract/ContractCheck.kt`, `contract/ContractCheckPlugin.kt` (new).
- `build-logic/convention/build.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`.
- `docs/BACKLOG.md` — `E0-03`, `E1-03` and `E1-06` now name the interfaces they implement, closing the gap assertion 5 found.
- Lint fixes across `core/**` and `shared/**`, plus `core/model/.../ArithmeticGuardsTest.kt` (new).

## Decisions Made

- **No `SHOULD` deviated from**; no new decision ID, so no ADR from this story.
- **`MagicNumber` is suppressed in the two arithmetic files, with the reason in the file.** The literals there are not magic: they are the canonical formula of `§2`, which that section says MUST be implemented literally. Replacing `500_000` with a named constant would hide the one thing a reviewer has to check — that the expression matches the document character for character. The same argument applies to `CountBucket.ofCount`, whose bounds `§20.9` states as exact literals.
- **`contract-check` reports `PENDING` instead of skipping.** Three assertions cannot run yet. Passing them silently would report coverage that does not exist.
- **Assertion 1 accepts a declaration anywhere in `docs/CONTRACTS.md`, not only in §20**, and reports the deviation separately. Implemented literally it fails today, because `Logger` is declared in `§17`, `AnalyticsTracker` in `§16.1`, `RemoteSyncSource` in `§10`, `AppGraphDependencies` in `§11.6`, the repositories in `§12` and the use cases in `§13` — while `§20` opens with "Every type referenced by a signature in this document is declared here". Nothing is ambiguous for an implementer, so this is a contract tidiness question for the owner: **`DEC-4`**.
- **The Xcode pin fails CI on a major-version drift and warns on a patch drift.** Failing on any drift would block every PR the day GitHub moves its image; ignoring drift would make "pinned on the macOS CI runner too" untrue.

## Verification Run

- [x] Relevant tests pass
- [x] Lint passes (ktlint, detekt)
- [x] Coverage thresholds hold
- [x] Architecture checks pass
- [x] Contract check passes
- [x] Relevant builds pass
- [x] Documentation updated

```text
./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test \
          koverVerify :androidApp:assembleDebug testAndroidHostTest iosSimulatorArm64Test
```

`BUILD SUCCESSFUL in 23s` locally. This is the first story in which every quality box can honestly be ticked.

## Contract Impact

- [x] No contract changes. `DEC-4` proposes one.

## Decision Board Impact

- [x] No decision changes.

## Shared-Write Modules Touched

- [x] None.

## Project Log Entry

- [x] Entry appended to `docs/PROJECT_LOG.md`.

## Risks or Follow-ups

- CI has never run. The first PR to merge this will be the first real execution, and runner-image differences are the likely source of surprises.
- Branch protection (`DEC-6`) is what turns all of this from advisory into enforced. Until it is set, a PR can merge red.
- detekt 1.23.8 is built against an older Kotlin compiler than the pinned 2.4.10. It runs and reports correctly here, but type-resolution rules are not enabled, so its analysis is syntactic.
- `koverVerify` measures the Android host variant. Coverage of Kotlin/Native-only paths is not counted.

## Human Review Gate

- [x] Not applicable for the code. Branch protection is an owner action.
