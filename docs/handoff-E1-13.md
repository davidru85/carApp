# Agent Handoff

## Story

`E1-13 - Executable iOS Locale-Provider Behavior Coverage`

## Ready Check

- Backlog story: `E1-13 - Executable iOS Locale-Provider Behavior Coverage` (`S`, open).
- Acceptance criteria reviewed: execute the production `IosLocaleProvider` behavior, or one
  production-delegated implementation with no duplicated currency rule, on an iOS simulator;
  prove a supported two-decimal currency through `NSNumberFormatter.maximumFractionDigits`;
  prove fallback to `EUR` when the runtime fraction digits are not two; assert Foundation language
  tag and region extraction behaviorally; make the exact complete non-instrumented command execute
  the test outside the excluded `:composition:ios:iosSimulatorArm64Test` route; preserve D-108
  ownership, explicit production injection and D-75's single Firebase Apple dependency authority;
  replace the E1-10, ADR-0109 and ADR-0110 coverage-gap records with executable evidence.
- Dependencies checked: E1-10 is complete; D-75, D-108 and D-109 are `Accepted`; the E1-12 shared
  graph-close race is complete and introduces no blocker for this host-focused story.
- Decisions checked: `docs/DECISION_BOARD.md` contains no `Proposed` or `Pending` decision. D-75
  keeps the graph-derived standalone Native-test exclusion; D-108 keeps the adapter at the iOS
  host composition boundary; D-109 requires an executable canonical route. The exact host-test
  topology must be recorded as a new decision because it is a build-model choice; no owner-only
  identifier, Firebase topology, library, service or MVP-scope decision is required.
- Normative sections reviewed: `docs/SPECIFICATION.md` section 11 (TDD and CI),
  `docs/CONTRACTS.md` sections 11.6, 13, 18 and 20.0.1, `docs/TECHNICAL_PLAN.md` sections 4 and 5,
  ADR-0076 / D-75, ADR-0109 / D-108 and ADR-0110 / D-109.
- Expected verification: a focused iOS-simulator execution proving every locale-provider behavior;
  the exact complete non-instrumented command in `AGENTS.md`; `git diff --check`; inspection of
  `contractCheck` output for decision/ADR parity, unresolved decisions and pending assertions; and
  the iOS host build/test route if it is not already covered by the focused command.
- Human review gates identified before work: gated paths `docs/SPECIFICATION.md`,
  `docs/DECISION_BOARD.md` and `docs/adr/**`; gated topics Swift-facing API surface if changed and
  module boundaries if changed. E1-13 also requires human review because it changes canonical
  verification. The agent will not merge the pull request.
- Rule 0 acknowledged: owner conversation is Spanish (Spain); every repository artifact,
  including branches, commits, code, tests, documentation and the pull request, is technical
  English.

## In-Progress Checkpoint

- Date: 2026-09-03.
- Branch and base: `story/E1-13-ios-locale-coverage`, based on up-to-date `main`.
- Current phase and latest commit: GREEN complete and not yet committed; RED is committed at
  `4860163`.
- Push and pull-request status: branch and RED commit are local only; no pull request exists.
- Completed since the previous checkpoint: selected the in-scope extension of D-109: compile the
  composition-owned provider source into `:shared` `iosTest` without a project dependency, module
  move, second framework or second Firebase route. Added one focused build-logic guard that requires
  this source reuse, the behavior-test file and the existing canonical root Native test task.
  GREEN moved the internal adapter into a dedicated composition-owned package, added an internal
  `NSLocale` factory with the unchanged `NSLocale.currentLocale` production default, reused that
  exact source only in `:shared` `iosTest`, and added four behavior-specific Foundation tests.
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
- Open decisions or blockers: no owner-only decision or implementation blocker. D-109 will be
  refined in REFACTOR documentation with the exact route and its rejected alternatives.
- Exact next step: commit GREEN, then complete the D-109 decision mirrors, E1-10 gap closure,
  story completion records and full verification in REFACTOR.

## Scope Completed

- In progress.

## Acceptance Evidence

- `IosLocaleProviderTest.supportedTwoDecimalLocaleResolvesItsCurrencyCode` constructs Foundation
  locale `en_US`; the production adapter's `NSNumberFormatter.maximumFractionDigits` path accepts
  its two digits and returns supported code `USD`.
- `IosLocaleProviderTest.currencyWithoutTwoFractionDigitsFallsBackToEur` constructs Foundation
  locale `ja_JP`; its runtime `JPY` zero-fraction-digits path returns `EUR`.
- `languageTagComesFromTheFoundationLocaleIdentifier` and
  `regionComesFromTheFoundationCountryCode` execute the production extraction against concrete
  `NSLocale` values and assert `es-ES` and `US` respectively.
- The focused `:shared:iosSimulatorArm64Test` result records four tests, zero failures, zero errors
  and zero skips in the generated XML report.

## Out of Scope / Not Done

- No Firebase Apple dependency route, product behavior, locale support set or module boundary has
  been changed.

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

## Verification Run

- RED: `./gradlew :build-logic:convention:test --tests
  com.ruizurraca.carapp.buildlogic.IosCompositionContractTest.iosHostLocaleProviderTestsRunInCanonicalVerification
  --rerun-tasks` — failed as intended: one executed test, one failure at the missing source-reuse
  assertion.
- GREEN: `./gradlew :shared:iosSimulatorArm64Test :build-logic:convention:test --tests
  com.ruizurraca.carapp.buildlogic.IosCompositionContractTest.iosHostLocaleProviderTestsRunInCanonicalVerification
  :shared:ktlintCheck :composition:ios:ktlintCheck
  :composition:ios:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` — passed, 117 executed tasks;
  four provider behavior tests passed and the production simulator framework linked.

## Contract Impact

- No contract changes expected.

## Decision Board Impact

- D-109 will be refined in REFACTOR with the exact E1-13 test-only source-reuse topology. D-75 and
  D-108 remain unchanged.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [ ] Entry appended

## Risks or Follow-ups

- The test must exercise Foundation behavior and cannot be replaced by source-text assertions or a
  framework-link-only check.

## Human Review Gate

Applies: E1-13 changes canonical verification and must update gated decision documentation. Any
Swift-facing API or module-boundary change would add its corresponding gated topic.
