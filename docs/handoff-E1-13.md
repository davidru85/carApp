# Agent Handoff

## Story

`E1-13 - Executable iOS Locale-Provider Behavior Coverage`

## Ready Check

- Backlog story: `E1-13 - Executable iOS Locale-Provider Behavior Coverage` (`S`, open).
- Acceptance criteria reviewed: execute the production `IosLocaleProvider` behavior, or one
  production-delegated implementation with no duplicated currency rule, on an iOS simulator;
  resolve a real supported two-decimal locale currency; anchor Foundation's two/non-two-decimal
  premise directly; assert reachable fallback, language-tag and nullable-region behavior; make the
  exact complete non-instrumented command execute the tests outside the excluded
  `:composition:ios:iosSimulatorArm64Test` route; preserve D-108 ownership, explicit production
  injection and D-75's single Firebase Apple dependency authority; replace the E1-10, ADR-0109 and
  ADR-0110 coverage-gap records with precise executable evidence and a named residual limitation.
- Dependencies checked: E1-10 is complete; D-75, D-108 and D-109 are `Accepted`; the E1-12 shared
  graph-close race is complete and introduces no blocker for this host-focused story.
- Decisions checked: `docs/DECISION_BOARD.md` contains no `Proposed` or `Pending` decision. D-75
  keeps the graph-derived standalone Native-test exclusion; D-108 keeps the adapter at the iOS
  host composition boundary; D-109 requires an executable canonical route. The exact host-test
  topology must refine D-109 because it is the build-model choice that decision left to E1-13; no
  owner-only identifier, Firebase topology, library, service or MVP-scope decision is required.
- Normative sections reviewed: `docs/SPECIFICATION.md` section 11 (TDD and CI),
  `docs/CONTRACTS.md` sections 11.6, 13, 18 and 20.0.1, `docs/TECHNICAL_PLAN.md` sections 4 and 5,
  ADR-0076 / D-75, ADR-0109 / D-108 and ADR-0110 / D-109.
- Expected verification: a focused iOS-simulator execution proving the reachable provider paths and
  direct Foundation premise; the exact complete non-instrumented command in `AGENTS.md`; `git diff
  --check`; inspection of `contractCheck` output for decision/ADR parity, unresolved decisions and
  pending assertions; and the iOS host build/test route if it is not already covered by the focused
  command.
- Human review gates identified before work: gated paths `docs/SPECIFICATION.md`,
  `docs/DECISION_BOARD.md` and `docs/adr/**`; gated topics Swift-facing API surface if changed and
  module boundaries if changed. E1-13 also requires human review because it changes canonical
  verification. The agent will not merge the pull request.
- Rule 0 acknowledged: owner conversation is Spanish (Spain); every repository artifact,
  including branches, commits, code, tests, documentation and the pull request, is technical
  English.

## In-Progress Checkpoint

### Review round 1 — 2026-09-03

- Branch and pull request: `story/E1-13-ios-locale-coverage`, PR #50; the agent will not merge it.
- Review scope accepted: correct the non-discriminating JPY test and overclaimed evidence, enforce
  the exact D-109 reused source and directory contents, and cover Foundation's nullable region and
  currency-code branches without changing production behavior.
- Constraints rechecked: D-75, D-108, the closed Swift ABI and the single Firebase Apple dependency
  authority remain unchanged. No owner-only decision, new dependency or scope change is required.
- Current phase: RED complete. The focused convention test compiled and executed, then failed at
  the new D-109 assertion because `localeCurrencyOutsideTheMvpSetFallsBackToEur` does not exist yet.
  The preceding exact-source-file and single-Kotlin-file directory assertions passed, isolating the
  failure to the missing review coverage rather than test setup.
- GREEN complete: renamed and aligned the unsupported-currency test with Android, added the direct
  Foundation fraction-digit premise anchor, and added the language-only locale case. The focused
  command passed 117 executed tasks; `IosLocaleProviderTest` now reports six tests, zero failures,
  zero errors and zero skips. The production provider and its testability seam remain unchanged.
- REFACTOR complete: corrected ADR-0109, ADR-0110, D-109 mirrors, E1-10's dated closure update and
  this handoff; recorded the unreachable supported-code/non-two-digits branch as a residual
  limitation. The focused refactor command passed 97 executed tasks, including six Native tests,
  affected lint, the full convention suite and `contractCheck` with 111 aligned decisions/ADRs,
  none unresolved and no pending assertion.
- Final local verification complete: the exact contract/convention command passed; the exact
  complete non-instrumented command passed with 629 actionable tasks (123 executed, 55 from cache,
  451 up-to-date); forced provider decoupling passed 229 executed tasks and re-executed the six
  Native tests. The append-only project log now corrects the original completion entry.
- Review commits: RED `6bf31fb`, GREEN `278d0e3`, REFACTOR `afbf54a`; production behavior, D-75,
  D-108, the Swift ABI and Firebase Apple dependency authority are unchanged.
- Exact next step: commit this final evidence checkpoint, push the review round to the existing
  branch, replace the overclaimed PR description, wait for every required check and leave PR #50
  open for owner review.
- Required verification: the focused RED and GREEN routes, the exact complete non-instrumented
  command from `AGENTS.md`, `./gradlew contractCheck :build-logic:convention:test`, forced
  provider-decoupling, `git diff --check` and all PR checks.
- Verification note: an auxiliary attempt to run
  `:build-logic:convention:ktlintCheck` failed at task selection because that included-build project
  exposes no such task. It tested no artifact; the required root `ktlintCheck` remains part of the
  final exact command.

### Original delivery checkpoint

- Date: 2026-09-03.
- Branch and base: `story/E1-13-ios-locale-coverage`, based on up-to-date `main`.
- Current phase and latest commit: TDD delivery complete — RED `4860163`, GREEN `77737b2` and
  REFACTOR `c8ee7ad`; post-delivery checkpoint `41182b7` is published, and required CI run
  `33693596513` is green.
- Push and pull-request status: all three TDD phase commits are published. The remote branch was
  observed at RED `4860163` after the local RED commit, recorded by Git's remote-ref log as an
  update by push even though the agent issued no push command; the agent explicitly pushed GREEN
  and REFACTOR together after final verification. All commits through `41182b7` are published,
  and PR #50 is open for mandatory owner review.
- Completed since the previous checkpoint: selected the in-scope extension of D-109: compile the
  composition-owned provider source into `:shared` `iosTest` without a project dependency, module
  move, second framework or second Firebase route. Added one focused build-logic guard that requires
  this source reuse, the behavior-test file and the existing canonical root Native test task.
  GREEN moved the internal adapter into a dedicated composition-owned package, added an internal
  `NSLocale` factory with the unchanged `NSLocale.currentLocale` production default, reused that
  exact source only in `:shared` `iosTest`, and added an initial four-test Foundation suite that the
  review round later made discriminating and extended to six tests.
- Verification evidence and known failures: the focused RED command
  `./gradlew :build-logic:convention:test --tests
  com.ruizurraca.carapp.buildlogic.IosCompositionContractTest.iosHostLocaleProviderTestsRunInCanonicalVerification
  --rerun-tasks` compiled and executed one test, which failed at the source-reuse assertion because
  `shared/build.gradle.kts` does not yet include the composition-owned adapter source. This is the
  intended missing E1-13 behavior.
  The first GREEN style/framework check linked the production `Shared` simulator framework but
  found one ktlint import-order violation in `IosLocaleProviderTest.kt`; the local test owns the
  failure and it was corrected before commit.
  The corrected focused GREEN command passed 117 executed tasks. The `Shared` simulator framework
  linked, both affected ktlint tasks passed, the canonical-route guard passed, and
  `IosLocaleProviderTest` executed four tests with zero failures, errors or skips.
  REFACTOR decision and state documentation passes `contractCheck` and the complete build-logic
  convention test suite. `contractCheck` reports 111 aligned decisions and ADRs, none unresolved,
  no `PENDING` assertions, the unchanged four-module D-75 set and the unchanged Swift allowlist.
  The exact complete non-instrumented command passed with 629 actionable tasks. The forced
  provider-decoupling command then passed 229 executed tasks and re-executed all four iOS locale
  tests without including the Firebase provider or composition projects. GitHub Actions run
  `33693596513` then passed all ten required checks, including shared Native tests, provider
  decoupling, the Objective-C header golden check, Android instrumentation and the iOS app/XCUITest
  job.
- Open decisions or blockers: no owner-only decision or implementation blocker. The D-109 route is
  documented with three options and remains subject to the mandatory human review of this pull
  request.
- Exact next step: owner review of PR #50, including confirmation of the selected D-109 option;
  address any review finding and leave merge approval to the owner.

## Scope Completed

- Added deterministic executable iOS-simulator coverage for the reachable E1-13 Foundation
  behavior and a direct platform-premise anchor for fraction digits.
- Kept the adapter internal and composition-owned while reusing its exact source in the existing
  standard-command `:shared` Native test binary.
- Added an executable build-logic guard for source ownership, behavior-test presence and canonical
  command reachability.
- Refined D-109, closed the E1-10/ADR gap records, marked Phase 1 complete and recorded the story
  completion evidence.

## Acceptance Evidence

- `IosLocaleProviderTest.supportedTwoDecimalLocaleResolvesItsCurrencyCode` constructs Foundation
  locale `en_US` and proves that the production adapter resolves real supported code `USD`.
- `IosLocaleProviderTest.localeCurrencyOutsideTheMvpSetFallsBackToEur` constructs Foundation locale
  `ja_JP` and proves `ja-JP`, `JP` and `EUR`; the fallback is attributable to `JPY` being outside
  `SUPPORTED_CURRENCY_CODES`, not independently to its fraction digits.
- `foundationCurrencyFractionDigitsMatchTheMvpPremise` directly proves that Foundation reports two
  fraction digits for `USD` and a value other than two for `JPY`.
- `languageTagComesFromTheFoundationLocaleIdentifier` and
  `regionComesFromTheFoundationCountryCode` execute the production extraction against concrete
  `NSLocale` values and assert `es-ES` and `US` respectively.
- `languageOnlyLocaleProvidesNullRegionAndFallsBackToEur` proves that `NSLocale("es")` produces
  language tag `es`, null region and `EUR` when Foundation supplies no currency code.
- The reviewed focused `:shared:iosSimulatorArm64Test` result records six tests, zero failures, zero
  errors and zero skips in the generated XML report.
- Residual limitation: all 21 MVP-supported currency codes are two-decimal by contract, so no real
  Foundation locale can reach "supported code with non-two fraction digits". The direct premise
  anchor protects against platform/toolchain drift; no fake supported currency or wider production
  seam was introduced.

## Out of Scope / Not Done

- No Firebase Apple dependency route, product behavior, locale support set or module boundary has
  been changed.
- No Swift-facing declaration, Objective-C golden header, schema, migration or remote behavior was
  changed.

## Files Changed

- `build-logic/convention/src/test/kotlin/com/ruizurraca/carapp/buildlogic/IosCompositionContractTest.kt`
  (RED canonical-route guard).
- `composition/ios/src/iosMain/kotlin/com/ruizurraca/carapp/CreateSwiftAppGraph.kt` and the moved
  `composition/ios/src/iosMain/kotlin/com/ruizurraca/carapp/locale/IosLocaleProvider.kt` (unchanged
  production injection plus internal deterministic locale factory).
- `docs/handoff-E1-13.md` (story intake and live checkpoint).
- `shared/build.gradle.kts` and
  `shared/src/iosTest/kotlin/com/ruizurraca/carapp/locale/IosLocaleProviderTest.kt` (test-only exact
  source reuse and executable simulator behavior tests).
- `AGENTS.md`, `README.md`, `docs/BACKLOG.md` and `docs/TECHNICAL_PLAN.md` (Phase 1 completion and
  canonical verification state).
- `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md`, ADR-0109 and ADR-0110 (D-109 route and exact
  executable evidence).
- `docs/handoff-E1-10.md` (dated closure update preserving the historical E1-10 record).
- `docs/PROJECT_LOG.md` (append-only E1-13 completion entry).

## Decisions Made

- The owner explicitly requested one local commit for each RED, GREEN and REFACTOR phase, followed
  by one push and pull-request creation. This exempts E1-13 from the default push-after-each-phase
  sequence in `docs/SPECIFICATION.md` section 11; test-first ordering and separate phase commits
  remain mandatory.
- The owner's term `NETWORK Phase` is interpreted as the TDD `RED phase`, matching the repository's
  closed three-phase workflow and the requested subsequent GREEN and REFACTOR phases.
- The D-109 route will reuse the exact composition-owned `IosLocaleProvider` source in `:shared`
  `iosTest`. This is test-only source reuse rather than a production dependency: it preserves the
  D-108 host owner, the D-75 exclusion set, the one-framework runtime and the closed Swift ABI.
- The internal `NSLocale` factory is a testability seam with `NSLocale.currentLocale` as its
  default; production construction and behavior remain unchanged. Native host/provider integration
  code is explicitly exempt from TDD order under `docs/SPECIFICATION.md` section 11. The executable
  canonical-route guard was still committed RED before this seam and the behavior tests.
- Review round 1 retains the production seam and behavior. Its RED commit makes the guard fail on
  absent reviewed tests; GREEN adds only tests. The optional Android language-only case is not added
  because E1-13 is the iOS host coverage story and Android null-locale behavior was not a required
  review finding.

## Verification Run

### Review round 1

- RED `6bf31fb`: `./gradlew :build-logic:convention:test --tests
  com.ruizurraca.carapp.buildlogic.IosCompositionContractTest.iosHostLocaleProviderTestsRunInCanonicalVerification
  --rerun-tasks` — failed as intended after compiling and executing one test. The exact-source and
  single-source-directory assertions passed first; the failure named the missing
  `localeCurrencyOutsideTheMvpSetFallsBackToEur` declaration.
- GREEN `278d0e3`: `./gradlew :shared:iosSimulatorArm64Test
  :build-logic:convention:test --tests
  com.ruizurraca.carapp.buildlogic.IosCompositionContractTest.iosHostLocaleProviderTestsRunInCanonicalVerification
  :shared:ktlintCheck :composition:ios:ktlintCheck
  :composition:ios:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` — passed, 117 executed tasks;
  six Native provider/premise tests passed and the production simulator framework linked.
- REFACTOR `afbf54a`: `./gradlew :shared:iosSimulatorArm64Test :shared:ktlintCheck
  :composition:ios:ktlintCheck contractCheck :build-logic:convention:test --rerun-tasks` — passed,
  97 executed tasks; six Native tests, affected lint, every convention test and contract parity
  passed.
- `./gradlew contractCheck :build-logic:convention:test` — passed, 8 actionable tasks (1 executed,
  7 up-to-date); `contractCheck` reported 111 aligned decisions/ADRs, none unresolved, no pending
  assertion, the exact D-75 exclusion set and unchanged Swift allowlist.
- Exact complete non-instrumented command from `AGENTS.md` — passed, 629 actionable tasks (123
  executed, 55 from cache, 451 up-to-date), including lint, detekt, architecture, contracts,
  convention tests, coverage, Android assembly/unit tests and required Android-host/Native tests.
- `./gradlew -Pcarapp.excludeFirebaseProviders=true testAndroidHostTest iosSimulatorArm64Test
  --rerun-tasks` — passed, 229 executed tasks; the provider-free graph re-executed all six
  `IosLocaleProviderTest` cases with zero failures, errors or skips.

### Original delivery

- RED: `./gradlew :build-logic:convention:test --tests
  com.ruizurraca.carapp.buildlogic.IosCompositionContractTest.iosHostLocaleProviderTestsRunInCanonicalVerification
  --rerun-tasks` — failed as intended: one executed test, one failure at the missing source-reuse
  assertion.
- GREEN: `./gradlew :shared:iosSimulatorArm64Test :build-logic:convention:test --tests
  com.ruizurraca.carapp.buildlogic.IosCompositionContractTest.iosHostLocaleProviderTestsRunInCanonicalVerification
  :shared:ktlintCheck :composition:ios:ktlintCheck
  :composition:ios:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` — passed, 117 executed tasks;
  the original pre-review four-test suite passed and the production simulator framework linked.
- REFACTOR focused documentation: `./gradlew contractCheck :build-logic:convention:test
  --rerun-tasks` — passed; `contractCheck` reports 111 aligned decisions/ADRs, none unresolved, no
  pending assertion, the exact D-75 exclusion set and an unchanged Swift allowlist.
- Exact complete non-instrumented command from `AGENTS.md` — passed with 629 actionable tasks (40
  executed, 5 from cache, 584 up-to-date), including ktlint, detekt, architecture, contract,
  convention tests, Kover, Android assembly/unit tests and required Android-host/Native tests.
- `./gradlew -Pcarapp.excludeFirebaseProviders=true testAndroidHostTest iosSimulatorArm64Test
  --rerun-tasks` — passed, 229 executed tasks; the provider-free graph re-executed all four
  `IosLocaleProviderTest` cases with zero failures, errors or skips.
- `git diff --check` — passed.
- GitHub Actions run `33693596513` — passed all ten required checks on post-delivery checkpoint
  `41182b7`; only existing Node.js 20 and `setup-java@v4` deprecation annotations were reported.

## Contract Impact

- No contract changes.

## Decision Board Impact

- D-109 is refined in all four mirrors and ADR-0110 with the exact E1-13 test-only source-reuse
  topology. D-75 and D-108 remain unchanged; ADR-0109 now cites the executable evidence.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [x] Entry appended

## Risks or Follow-ups

- The explicit test-only source path is intentional coupling; `IosCompositionContractTest` now
  requires the exact provider file, exactly one Kotlin file in its recursively compiled directory,
  the reviewed test declarations and canonical command reachability.
- The defensive supported-code/non-two-digits branch remains undiscriminated because real platform
  data cannot reach it under the two-decimal MVP supported set. The Foundation premise anchor is the
  executable protection for this limitation.

## Human Review Gate

Applies: gated paths `AGENTS.md`, `docs/SPECIFICATION.md`, `docs/DECISION_BOARD.md` and
`docs/adr/**`, plus E1-13's explicit canonical-verification review requirement. The implementation
does not change the gated Swift-facing API or module-boundary topics. The agent MUST NOT merge the
pull request.
