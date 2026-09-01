# Agent Handoff - E1-10

## Story

`E1-10 - Settings Persistence - S` (`docs/BACKLOG.md`).

## Ready Check

- Backlog story: explicit. Implement the existing local `user_settings` schema surface,
  `SettingsRepository` and `UpdateSettingsCommand`, then replace the temporary D-94 Fuel Entry
  creation currency with persisted settings.
- Acceptance criteria reviewed:
  - First repository access creates exactly one row with a supported locale-derived two-decimal
    currency or `EUR`, fixed `KM` / `LITER` units and `analyticsEnabled = false`.
  - Explicit updates accept only supported two-decimal currencies.
  - Native Android and iOS currency APIs validate locale-derived currency minor units; a runtime
    factor other than `100` resolves to `EUR`.
  - Currency changes do not rewrite existing Fuel Entries.
  - Settings remain device-local, create no outbox row and have no remote document.
  - A command with both fields `null` returns `ValidationError.NoOp` and mutates nothing.
  - Destructive local-data deletion removes settings; the next repository access recreates the
    defaults with analytics disabled.
- Dependencies checked: E1-01 provides the schema-v1 `user_settings` table; E1-06 provides Fuel
  Entry persistence; E1-08 and E1-09 provide the shared form holder and both native hosts. All are
  merged into `main`. E1-11 and E1-12 do not own `:core:database`, and no pull request is open.
- Decisions checked: D-20, D-36, D-38, D-55, D-58, D-59, D-75, D-77, D-89 and D-94 are `Accepted`.
  `docs/DECISION_BOARD.md` contains no `Proposed` or `Pending` decision.
- Normative sections reviewed: `docs/SPECIFICATION.md` §3.1, §3.2, §5.2, §5.3, §11 and §12;
  `docs/CONTRACTS.md` §2, §3, §5, §6, §11.5, §12, §13, §15, §16, §16.1, §18, §20.0.1,
  §20.2, §20.3, §20.4 and §20.5; `docs/TECHNICAL_PLAN.md` §3, §4, §6, §7, §10 and §12;
  ADR-0095 / D-94.
- Expected verification: focused `:core:common`, `:core:database`, `:feature:session`, `:shared`,
  Android host and iOS simulator tests; Android and iOS host builds; provider decoupling; the
  Objective-C header golden; and the complete non-instrumented command from `AGENTS.md`.
- Human review gates identified before work: applies because `core/database/**` is a gated path.
  The agent MUST NOT merge the pull request.
- Rule 0 acknowledged: owner conversation is Spanish (Spain); every repository artifact, branch,
  commit and pull-request field is technical English.
- TDD workflow: the owner explicitly requested one local RED commit, one local GREEN commit and
  one local REFACTOR commit followed by a single push, superseding the default push-after-each-phase
  workflow for this story. The deviation will be retained under Decisions Made.

## In-Progress Checkpoint

- Date: 2026-09-01.
- Branch: `story/E1-10-settings-persistence`, based on merged PR #43 at `df37ec7`.
- RED is complete at `a399395` and GREEN is complete at `4a56593`.
- REFACTOR was published at `d9a893e`; PR #44 is open and MUST NOT be merged by the agent. A second
  owner review reopened REFACTOR with four required fixes and five cleanups. The same REFACTOR
  commit will be amended, then the existing remote branch will be updated with force-with-lease.
- Code corrections now staged locally: the graph closure flag is volatile; the missing-row gate
  runs before locale resolution; repository constructors are collapsed and documented; bootstrap
  and runtime-factor call-site comments record their ordering and validation limits.
- Verification topology now staged locally: `:androidApp:testDebugUnitTest` is mandatory in the
  canonical command and CI, D-109 / ADR-0110 records the change, and E1-13 owns executable
  `IosLocaleProvider` behavior because D-75 excludes the composition Native test binary.
- Documentation corrections now staged locally: D-106 retains its residual check-then-act window,
  acceptance evidence claims only the gate and idempotent close ordering, and the iOS behavior gap
  is explicit. `SettingsDatabaseAccess.deleteSettings()` remains intentionally staged without a
  production caller for E2-05 / E3-10.
- Known external verification: the initial PR `shared-tests` attempt reproduced the E1-12 / issue
  #42 Kotlin/Native `SIGSEGV`; its isolated retry and every other required CI check passed.
- Corrected focused verification passed with forced task execution for
  `:androidApp:testDebugUnitTest`, `:feature:session:testAndroidHostTest`,
  `:shared:testAndroidHostTest` and `:build-logic:convention:test`. The expanded exact command from
  `AGENTS.md` then passed with 627 actionable tasks; `contractCheck` reports 110 aligned decisions
  and ADRs with none unresolved.
- `CLAUDE.md` does not exist and was not created. The mandatory replacement-agent rule remains in
  `AGENTS.md`, `docs/CONTRIBUTING.md`, the handoff template and the pull-request template.
- Exact next step at commit creation: amend `d9a893e`, update the existing PR #44 with
  force-with-lease, update its live checkpoint with the resulting commit and CI state, and leave it
  open for the mandatory human review.

## Owner Decision Package

The owner selected option A for D-106, D-107 and D-108 on 2026-09-01. The second review required
D-109 Android verification and allowed either executable iOS evidence now or an explicit gap with
a registered follow-up; E1-10 selected the latter to preserve D-75 and D-108.

### D-106 - Missing-settings bootstrap ownership

Problem: E1-10 requires locale defaults when the single settings row does not exist, including
after destructive local-data flows. The implementation needs one authority that performs the
idempotent write without coupling the database module to platform locale APIs.

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **Selected: A — Repository self-healing plus eager one-shot AppGraph bootstrap** | Meets literal first-launch creation and repository-access recreation; one cross-platform implementation; keeps locale injection outside `:core:database` | Adds a graph-owned coroutine scope whose cancellation must precede database close; interacts with D-89 lifecycle tests |
| B — Explicit suspending AppGraph initializer called by both hosts | Startup timing and failures are explicit; no hidden eager work | Changes common and Swift-facing startup orchestration; both hosts need loading/error paths and future hosts can omit the call |
| C — Database factory creates the row | Row exists before any graph consumer and requires no background coroutine | Pushes locale-derived product defaults into `:core:database`, widens factory/test contracts and weakens the existing dependency boundary |

Selected constraints: the repository remains the self-healing functional authority. The graph
performs only a terminating first-successful settings read in its own
`CoroutineScope(SupervisorJob() + dispatchers.io)`. Closing the graph cancels that scope before the
database handle, the bootstrap re-checks graph closure immediately before a missing-row write, and
bootstrap failures are silently absorbed. An Android-host regression constructs and immediately
closes a graph without consumers. The general graph-close race remains owned by E1-12 / D-89.

### D-107 - Persisted currency delivery to a new Fuel Entry form

Problem: a new form needs the persisted currency without changing existing Fuel Entries or
overwriting an explicit edit made while asynchronous settings loading completes.

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **Selected: A — Holder-owned first-successful currency observation with locale fallback** | Uses the existing holder scope; avoids a graph-global presentation state; applies the persisted default once and protects explicit edits | The form can briefly hold the locale fallback while the database emits; each new form starts one short-lived collector |
| B — Graph-wide shared settings `StateFlow` | One database collector; reusable by future settings and analytics consumers; updates are immediately available after bootstrap | Adds graph state/lifecycle complexity; needs an initial error/fallback value and still needs an edit-protection rule |
| C — Suspending one-shot read before form construction | The form starts with one exact snapshot and needs no collector | Makes the synchronous AppGraph/Swift holder factory asynchronous, expands ABI and does not naturally react to later settings changes |

Selected constraints: a new form consumes only the first persisted currency and then terminates
the collector. The locale fallback remains the initial value, an explicit edit made before the
value arrives is protected, `resolveLiveMoney()` still runs when it is applied, later settings
changes do not mutate the open form, and edit forms never consume settings currency.

### D-108 - Native locale-provider ownership

Problem: Android and iOS must use their native currency APIs while `LocaleProvider` remains an
explicit injected common port and Firebase types stay inside integration modules.

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **Selected: A — Native host adapters injected into Firebase wiring (current GREEN implementation)** | Keeps native APIs at platform composition edges; preserves provider-free common code and explicit injection; easy platform-focused tests | Maintains two small adapters and adds a locale parameter to the production provider factory |
| B — Android/iOS source-set implementations inside `:wiring:firebase` | Keeps host call sites smaller and preserves a one-argument production factory | Makes Firebase wiring own an unrelated platform service and complicates provider-decoupling/source-set responsibilities |
| C — Hosts pass a one-time `LocaleInfo` snapshot instead of a provider | Minimal adapter surface and no platform provider classes | Stops modelling locale as an injectable runtime boundary, duplicates resolution at call sites and cannot observe a later system-locale change |

Selected constraint: the production `firebaseAppProviders(databaseFilePath, localeProvider)`
overload keeps `localeProvider` explicit with no default. Any default remains confined to the
internal staged/test overload.

### D-109 - Native locale-provider verification topology

Problem: Android's native provider test was not part of standard verification, while the iOS
provider's Foundation fraction-digits path has no executable test and its composition Native test
binary is excluded by D-75.

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **Selected: A — Canonical Android unit task plus explicit E1-13 iOS follow-up** | Enforces Android behavior immediately; preserves D-108 host ownership and D-75's single Firebase Apple dependency route | iOS behavior remains a declared gap until E1-13 |
| B — Move iOS adapter behavior into a standard-tested non-host module | Could execute the behavior through the existing Native task set | Moves Foundation logic away from its D-108 owner solely for test topology and risks duplication or API leakage |
| C — Add another Firebase Apple dependency route for composition tests | Could execute the excluded composition test binary | Reopens D-75, adds a second version authority and uses dependency machinery rejected by the owner |

Selected constraints: `:androidApp:testDebugUnitTest` is present in all current canonical-command
mirrors and the CI unit-test job. Android behavior is executable; source inspection is not
evidence. E1-13 must execute the production iOS provider behavior while preserving D-75 and D-108.

## Scope Completed

- Added the schema-v1 fixed-row SQLDelight settings queries and typed database access without a
  migration, remote document or outbox mutation.
- Added the settings domain contract, `UpdateSettingsCommand`, local data source and self-healing
  SQLDelight repository with stable validation and persistence errors.
- Added the shared two-decimal locale-currency resolver and native Android/Foundation providers,
  injected explicitly through production host composition.
- Replaced the temporary form-only currency source with the first persisted settings currency for
  new Fuel Entry forms while retaining the locale fallback and existing-entry isolation.
- Added the one-shot best-effort AppGraph bootstrap, ordered graph-scope cancellation and
  immediate-close regression required by D-106.
- Removed the duplicate D-94 currency rule from `DefaultAppGraph` in favor of
  `resolveLocaleCurrency`.
- Formalised continuous in-flight documentation under D-105 and completed all repository status,
  ADR, decision-mirror, handoff and project-log updates.

## Acceptance Evidence

- `graphBootstrapCreatesSettingsWithoutAConsumer`,
  `sqlDelightObservationCreatesLocaleDefaults` and
  `firstObservationCreatesLocaleDefaultsWithAnalyticsDisabled` prove first-launch and
  access-time default creation.
- `upsertMaintainsExactlyOneFixedMetricSettingsRow` proves the fixed ID, `KM` / `LITER` values and
  one-row invariant.
- `supportedCurrencyWithDifferentRuntimeFactorFallsBackToEur`, the other
  `LocaleCurrencyTest` cases and the executed Android provider tests prove supported runtime factor
  `100` and `EUR` fallback behavior on Android. E1-13 owns executable iOS provider behavior.
- `unsupportedExplicitCurrencyReturnsInvalidUnitAndMutatesNothing` and
  `noOpReturnsTypedErrorAndMutatesNothing` prove explicit validation and no-op semantics.
- `settingsWritesDoNotTouchFuelEntriesOrOutbox`, the absence of any remote settings schema and the
  unchanged integration modules prove device-local-only persistence.
- `persistedCurrencyReplacesLocaleFallbackOnANewForm`,
  `explicitCurrencyEditBeforePersistedValueArrivesIsNotOverwritten`,
  `laterSettingsChangesDoNotMutateAnOpenCreationForm` and
  `existingEntryCurrencyIsNeverReplacedBySettings` prove the D-107 form boundary.
- `deleteRemovesTheDeviceLocalRow` and `repositoryAccessAfterDeletionRecreatesLocaleDefaults`
  prove destructive deletion and self-healing with analytics disabled.
- `kotlinGraphCanCloseImmediatelyWhileSettingsBootstrapStartsWithoutAConsumer` and
  `closedGraphGatePreventsBootstrapDefaultWrite` prove the creation gate and idempotent close
  ordering on Android host. They do not prove absence of a write in the residual check-then-act
  window documented by D-106 and ADR-0107.

## Out of Scope / Not Done

- Settings UI remains owned by E4-01.
- Firebase Analytics integration remains owned by E3-09.
- Sign-out and account-deletion orchestration remain owned by E2-05 and E3-10.
- Executable `IosLocaleProvider` Foundation behavior remains owned by E1-13 under D-109.

## Files Changed

- Settings implementation and tests: `core/common/**/LocaleCurrency*`,
  `core/database/**/SettingsDatabaseAccess*`, `core/database/**/database.sq`,
  `feature/session/**`, and the Fuel Entry/shared graph state-holder sources and tests.
- Native composition: `AndroidLocaleProvider.kt`, `MainActivity.kt`, its Android test,
  `IosLocaleProvider.kt`, `CreateSwiftAppGraph.kt`, Firebase provider wiring and platform contract
  tests.
- Decisions and continuity: `AGENTS.md`, `README.md`, `docs/BACKLOG.md`,
  `docs/CONTRIBUTING.md`, all four decision mirrors, ADR-0106 through ADR-0110, both templates,
  `docs/handoff-E1-10.md` and `docs/PROJECT_LOG.md`.

## Decisions Made

- Owner workflow instruction: use separate local RED, GREEN and REFACTOR commits, then perform one
  push before opening the pull request. This explicitly exempts E1-10 from the default per-phase
  push requirement in `docs/SPECIFICATION.md` §11; test-first ordering and commit separation remain
  mandatory.
- The SQLDelight query definitions and native/Firebase provider integration use the explicit TDD
  order exemptions in `docs/SPECIFICATION.md` §11. Their behavior is still covered by database,
  resolver, Android provider, graph and iOS framework verification.
- D-105 selects live, versioned story checkpoints for reliable replacement-agent continuation.
- D-106 selects repository self-healing plus a terminating, best-effort AppGraph bootstrap with
  cancellation before database close and a pre-write closure check.
- D-107 selects a holder-owned first persisted currency only, with locale fallback, explicit-edit
  precedence, live-money recomputation and edit-mode isolation.
- D-108 selects native host adapters injected through an explicit production provider-factory
  parameter; the internal staged/test overload alone retains a default.
- D-109 makes Android application unit tests part of canonical local and CI verification and
  registers E1-13 for the D-75-blocked iOS provider behavior gap.
- `SettingsDatabaseAccess.deleteSettings()` has no production caller in E1-10. It is intentionally
  staged for the destructive local-data flows owned by E2-05 and E3-10, rather than speculative
  reusable infrastructure.
- The stale `PlatformHostContractTest` assertion that `WalkingSkeletonModel` owned graph closure
  was corrected to the E1-09 `carAppApp` ownership. This is executable-documentation cleanup, not
  a new product or architecture decision.

## Verification Run

- RED for the owner decision package: `./gradlew :feature:fuel:testAndroidHostTest
  :shared:testAndroidHostTest` failed only the expected graph-bootstrap and later-settings-change
  tests before implementation.
- Focused Android host: `./gradlew :feature:fuel:testAndroidHostTest
  :feature:session:testAndroidHostTest :shared:testAndroidHostTest` — passed.
- Second-review focused rerun: `./gradlew :androidApp:testDebugUnitTest
  :feature:session:testAndroidHostTest :shared:testAndroidHostTest
  :build-logic:convention:test --rerun-tasks` — passed; `AndroidLocaleProviderTest` executed two
  tests with zero failures.
- Focused iOS and style: `./gradlew :feature:fuel:ktlintCheck :feature:session:ktlintCheck
  :shared:ktlintCheck :feature:fuel:iosSimulatorArm64Test
  :feature:session:iosSimulatorArm64Test` and `./gradlew :shared:iosSimulatorArm64Test` — passed.
- Exact expanded complete non-instrumented command from `AGENTS.md`, including
  `:androidApp:testDebugUnitTest` — passed, 627 actionable tasks. This
  includes `ktlintCheck`, `detekt`, `architectureCheck`, `contractCheck`, convention-plugin tests,
  `koverVerify`, Android debug assembly, Android-host tests and all required Kotlin/Native tests.
- `contractCheck` reported 110 decisions and ADRs aligned, no unresolved decisions, an unchanged
  Objective-C golden header and the exact D-75 Native exception set.

## Contract Impact

- No `docs/CONTRACTS.md` change. Existing settings, database, presentation and provider contracts
  were implemented without changing public repository/use-case signatures or the Swift ABI.

## Decision Board Impact

- D-105 through D-109 are `Accepted`, with ADR-0106 through ADR-0110 and identical status rows in
  every required mirror. No decision remains `Proposed` or `Pending`.

## Shared-Write Modules Touched

- `:core:database`. No concurrent story or open pull request owns this module at intake.

## Project Log Entry

- [x] Entry appended

## Risks or Follow-ups

- E1-12 / issue #42 remains open and can abort `:shared:iosSimulatorArm64Test` when a test closes an
  app graph before its `backgroundScope` collectors are cancelled. E1-10 does not change that
  test-infrastructure ownership or the deferred D-89 production-safety question.
- E1-13 remains open and owns executable Foundation behavior coverage for `IosLocaleProvider`;
  current framework-link and source-contract evidence is not behavioral coverage.

## Human Review Gate

- Applies: `core/database/**` is a gated path under `AGENTS.md`. The agent MUST NOT merge the pull
  request.
