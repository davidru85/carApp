# Agent Handoff - E3-06

## Story

`E3-06 - Provider Decoupling Proof - S` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — `E3-06`, pulled forward before `E3-01` by the owner so the
  required provider-decoupling check is executable before the first Firebase integration module.
- [x] Acceptance criteria reviewed — excluding `:integration:*` and `:wiring:firebase` from
  Gradle settings leaves `:core:*`, `:feature:*` and `:shared` compiling and testing with local
  fakes; CI runs the proof under the existing required name `provider-decoupling`.
- [x] Dependencies checked — Phase 0 and `E1-01` are merged; the check can be implemented before
  provider modules exist because the canonical provider registry includes only directories that
  exist, and functional fixtures create representative provider directories.
- [x] Decisions checked — `D-0`, `D-3`, `D-5`, `D-6`, `D-14`, `D-16`, `D-31` and `D-34` are
  `Accepted`; on 2026-08-24 the owner also accepted the E0-07 slice, prerequisite ordering,
  development certificate, Gradle-property exclusion, explicit provider registry and macOS
  JVM/Kotlin-Native verification decisions recorded by this pull request as `D-39` through
  `D-45`. No `Proposed` or `Pending` decision blocks the story.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §8.5`, `docs/CONTRACTS.md §11.6`,
  `§15.2` and `§18`, `docs/TECHNICAL_PLAN.md §4`, `§5` and `§12`, `docs/BACKLOG.md E3-06`,
  `docs/CONTRIBUTING.md`, and `AGENTS.md`.
- [x] Expected verification identified — focused build-logic functional tests; excluded provider
  graph compilation and tests on Android host and `iosSimulatorArm64`; architecture and contract
  checks; the complete local CI command; GitHub's nine required checks.
- [x] Human review gates identified before work — gated decision and ADR paths, module-boundary
  policy, CI provider-decoupling proof, and the E3-06 sequencing decision. Owner review is
  required before merge.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Added the closed Firebase provider path registry to canonical Gradle settings and made inclusion
  conditional on both directory existence and `carapp.excludeFirebaseProviders`.
- Added Gradle TestKit coverage for normal provider inclusion, missing paths and complete provider
  exclusion.
- Replaced the placeholder CI step with a macOS provider-free proof covering Android host and
  `iosSimulatorArm64` while preserving the required `provider-decoupling` check name.
- Recorded the prerequisite, walking-skeleton, Firebase key, build-model and target-coverage
  decisions raised before implementation.

## Acceptance Evidence

- `ProviderSettingsFunctionalTest.providerRegistryIncludesExactlyExistingModules` proves the
  registry includes exactly the existing registered provider paths and creates no projects for
  missing directories.
- `ProviderSettingsFunctionalTest.providerExclusionOmitsEveryExistingProviderModule` proves
  `carapp.excludeFirebaseProviders=true` removes every existing registered provider.
- The provider-free `testAndroidHostTest iosSimulatorArm64Test` invocation compiles and tests the
  included `:core:*` and `:shared` graph without providers; no `:feature:*` module exists yet.
- `.github/workflows/ci.yml` runs that same provider-free invocation on `macos-latest` under the
  unchanged required job name `provider-decoupling`.

## Out of Scope / Not Done

- `E3-01` Firestore rules and `E0-07` walking skeleton remain separate later pull requests.

## Files Changed

- Build model and tests: `settings.gradle.kts`, `build-logic/convention/build.gradle.kts`, and
  `ProviderSettingsFunctionalTest.kt`.
- CI: `.github/workflows/ci.yml`.
- Decision and security records: `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md`,
  `docs/TECHNICAL_PLAN.md`, `docs/SECURITY.md`, `docs/identifiers.md`, `docs/adr/README.md`, and
  ADR-0040 through ADR-0046.
- Story and status records: `docs/BACKLOG.md`, `docs/PROJECT_LOG.md`, `AGENTS.md`, `README.md`,
  `docs/DEFINITION.md`, and this handoff.

## Decisions Made

- `D-39`: E0-07 uses a minimal contract-valid `Vehicle` slice and edits the vehicle name.
- `D-40`: E3-01 Firestore rules precede E0-07.
- `D-41`: the development Firebase key uses the owner's current local debug certificate
  restriction.
- `D-42`: the prerequisite order is E3-06, E3-01, then E0-07.
- `D-43`: provider exclusion uses `carapp.excludeFirebaseProviders=true` in canonical settings.
- `D-44`: provider modules use a closed explicit registry and missing directories are not included.
- `D-45`: one macOS `provider-decoupling` job tests Android host and `iosSimulatorArm64`.
- No `SHOULD` rule was deviated from. Rule 0 held for the whole story.

## Verification Run

- RED: the registry functional test failed on its inclusion assertion before the registry existed.
- GREEN: the registry functional test passed after the explicit conditional registry was added.
- RED: the exclusion functional test failed on its exclusion assertion before the property was
  consumed.
- GREEN: both `ProviderSettingsFunctionalTest` tests passed after property support was added.
- `./gradlew -Pcarapp.excludeFirebaseProviders=true testAndroidHostTest iosSimulatorArm64Test
  --stacktrace` — passed.
- `./gradlew ktlintCheck detekt architectureCheck contractCheck
  :build-logic:convention:test` — passed; `contractCheck` reported 46 decisions and 46 matching
  ADRs, with only the three expected later-story assertions pending.
- `./gradlew ktlintCheck detekt architectureCheck contractCheck
  :build-logic:convention:test koverVerify :androidApp:assembleDebug testAndroidHostTest
  iosSimulatorArm64Test` — passed.

## Contract Impact

- No contract changes. The implementation makes the existing `docs/CONTRACTS.md §18`
  `provider-decoupling` requirement executable.

## Decision Board Impact

- Added accepted decisions `D-39` through `D-45`, ADR-0040 through ADR-0046, and identical mirror
  rows in `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`.

## Shared-Write Modules Touched

- None. `:core:database` is compiled and tested but not modified.

## Project Log Entry

- [x] One story entry and one entry for each accepted decision were appended.

## Risks or Follow-ups

- `E3-01` follows this story, then `E0-07`, per the owner-approved prerequisite order.
- No provider directory exists yet, so TestKit fixtures prove the conditional provider behavior
  now; the first provider module will exercise the same registry against production project files.
- Each future provider module must be added explicitly to the closed registry. Automatic provider
  directory discovery remains forbidden.

## Human Review Gate

- Applies: `docs/SPECIFICATION.md`, `docs/DECISION_BOARD.md`, `docs/adr/**`, module-boundary
  policy and the provider-decoupling CI proof. The owner must review and merge this pull request.
