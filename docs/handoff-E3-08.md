# Agent Handoff - E3-08

## Story

`E3-08 - App Graph and Firebase Wiring - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — `E3-08` completes the Kotlin-facing `AppGraph`, the
  Swift-facing `SwiftAppGraph` and `:wiring:firebase` in place, on top of the provider-free
  `buildAppGraph` and the single `createSwiftAppGraph(isDebugBuild)` declaration that `E0-07`
  already owns.
- [x] Acceptance criteria reviewed — the seven criteria of the story: surface parity with
  `docs/CONTRACTS.md §11.6` and `§20.10`; `:shared:testing` factory parity; `:wiring:firebase` as
  the only Firebase-constructing module; the checkable "product logic" definition of
  `docs/TECHNICAL_PLAN.md §4`; Koin-free graph construction from tests; a Swift facade that
  exposes a sync state holder rather than `SyncController` and owns its scopes; and cached,
  idempotent, close-guarded `SwiftAppGraph` holder factories.
- [x] Dependencies checked — `E0-07` owns the provider-free `buildAppGraph` and the framework
  topology (`D-58`/`D-59`); `E3-03` merged on 2026-09-17 through pull request #69 and delivered
  `SyncController`, the `AppGraph.syncStateHolder(scope)` member and the real sync wiring;
  `E3-17` merged on 2026-09-17 through pull request #70 and delivered the `D-172` close contract.
  `E3-06`, `E3-01`, `E3-02` and `E3-17` are complete. No `Proposed` or `Pending` decision gates
  this story: `D-149`, `D-150` and `D-173` gate `E3-15`, `E3-16` and `E3-18`, none of which this
  story touches.
- [x] Decisions checked — `D-3` (Koin is wiring-only), `D-16` (module rules are a custom Gradle
  check with a failing fixture per rule), `D-27`/`D-56` (the test factory lives in
  `:shared:testing`, consumed from `commonTest` only), `D-38`, `D-43`/`D-44` (explicit provider
  registry), `D-45`, `D-58`/`D-59` (composition root and provider port), `D-86`/`D-89` (Swift
  facade scope ownership and the single `DatabaseHandle`), `D-108`/`D-126` (native providers
  injected at the host boundary), and `D-172` (close releases the handle after graph work). All
  are `Accepted`.
- [x] Normative sections reviewed — `docs/CONTRACTS.md §11.6`, `§15`, `§18` (assertions 7, 13 and
  14, plus the new 34), `§20.3`, `§20.10`; `docs/TECHNICAL_PLAN.md §3`, `§4` and `§4.1`;
  `docs/SPECIFICATION.md §8.2`, `§8.5` and `§11`; `docs/SECURITY.md`; and the `E0-04`, `E0-07`,
  `E3-03`, `E3-06` and `E3-17` handoffs.
- [x] Expected verification identified — focused RED/GREEN fixtures for each new rule;
  `:build-logic:convention:test`, `architectureCheck`, `contractCheck`, `:shared` Android-host and
  `iosSimulatorArm64` tests, the provider-free graph proof, the Objective-C golden-header diff and
  the canonical full CI command.
- [x] Human review gates identified before work — `docs/CONTRACTS.md` and `docs/adr/**` are gated
  paths, and the Swift-facing API surface plus the module dependency rules are gated topics, so
  this story requires owner review before merge. `E3-08` is not one of the gated stories of
  `AGENTS.md`.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## In-Progress Checkpoint

- Date: 2026-09-20
- Branch and base: `story/E3-08-app-graph-and-firebase-wiring`, based on `origin/main` at `588ad00`
  (the `E3-17` merge).
- Current phase and latest commit: review round 9 addressed. Story RED `4c51b55`, GREEN `03f5f3f`,
  REFACTOR `5b48c4b`, records `1a19448`; review fixes round 1 `4857ee9`; review fixes round 2
  `b7ebd87` (declaration classified before the Koin exemption), `9b27f80` (assertion 35, the arrow
  split and the two emptiness fixtures), `41da711` (`§20.10` and the ADRs), `3a6a5d7` (the round-2
  record); review fixes round 3 `03d8d7f` (the depth-zero body brace, the per-class emptiness guard
  and the round-3 record); review fixes round 4 `b9b0d0e`, `a90bef1` (the annotation scanner),
  `6b338bb`, `d7187a2` (the scope detected by declared type), `8860e48`, `95066e2` (the missing
  `AppGraph` block and the one-sided parse messages), each a red/green pair; review fixes round 5
  `316cb52`, `ee925ba` (the member kind, declared type and exported-visibility comparison), the
  round-5 record; review fixes round 6 `ac35860`, `0971edc` (the interleaved declaration order), the
  round-6 record; review fixes round 7 `f8b5c26` (the ten regressions, RED) and `63b0d47` (the eight
  fixes, GREEN); review fixes round 8 `c322c6e` (the six regressions plus one rule-level fixture,
  RED) and `84d376a` (the six fixes, GREEN); review fixes round 9 `d1df795` (the two regressions,
  RED) and `bd92724` (the two fixes, GREEN).
- Review round 1: five findings, four of them defects in the checks this story added, all fixed
  after a failing fixture each. Finding 1 was false recorded evidence: neither assertion could see a
  Kotlin default on the Kotlin-facing `AppGraph`, because assertion 14 never read
  `Member.defaulted` for the interface and assertion 34 stripped the default before comparing.
  Assertion 34 now compares the default as part of the parameter shape and assertion 14 reports the
  interface's own defaults. Finding 2: assertion 14 false-positived on a private `SwiftAppGraph`
  member, which is not exported and therefore not constrained by `§18`; private members are filtered
  out and a fixture proves it. Finding 3: `isKoinModuleDeclaration` admitted any type whose name
  begins with `Module` and any line mentioning `module {`; the `Module` type is now matched exactly
  and the initialiser must be the declaration's own. Finding 4: the violation put the declared name
  between the modifiers and the keyword (`declares enum  StrayMode class`); it is now in source
  order and the fixtures assert the message text. Finding 5: `SwiftSurfaceContractTest` gained one
  fixture per problem branch of both assertions and the dead `fixture: Boolean = true` parameter was
  removed; the fixtures fabricate `Inputs` and assert the exact problem text.
- Review round 2: six findings, all in the checks and records this story added, all fixed after a
  failing fixture each. Finding 1: `isKoinModuleDeclaration` read the text after the first colon
  before the keyword was known, so `class FirebaseWiring : Module`, `internal object
  FuelEntryMapper : Module` and `interface LocalGate : Module` were admitted as Koin bindings; the
  exemption now applies only to a `val`/`var`, proved by
  `aSupertypeNamedModuleAndAnAnnotatedDeclarationDoNotEscapeTheRule`. Finding 2: the same matcher
  could not cross `@`, so `@JvmField internal val leaked` and an annotated `class` parsed as no
  declaration at all; a leading-annotation prefix is stripped before matching, proved by the same
  fixture. Finding 3: assertion 34 guarded only the Kotlin-facing interface, leaving the
  Swift-facing `class SwiftAppGraph` block of `§20.10` unguarded because the golden header is
  regenerated with the change that alters the class; assertion 35 now compares it member by member,
  proved by six fixtures and by mutation C. Finding 4: `FuelEntryFormStateHolder.isLoading` and
  `observeSaveCompletions()` are public `@HiddenFromObjC` members absent from `§20.10`, the same
  blind spot `AppGraph.syncStateHolder(scope)` was; both are now declared carrying the annotation
  and `§11.6` states the convention. Finding 5: `splitTopLevel` decremented its depth on the `>` of
  `->`, so `callback: (Int) -> Unit` drove the depth negative and merged every later parameter,
  reporting the default under the wrong name; the arrow is now ignored, proved by
  `aDefaultAfterAFunctionTypedParameterIsReportedUnderItsOwnName`. Finding 6: ADR-0181 claims every
  problem branch has a fixture asserting the exact text, and the two assertion-14 emptiness branches
  had none; `anUnparsedKotlinFacingInterfaceIsReportedByAssertion14` and
  `anUnparsedSwiftFacingClassIsReportedByAssertion14` now cover them. The `FUN` regex also gained a
  leading word boundary, so an identifier ending in `fun` followed by `(` no longer parses as a
  member.
- Review round 3: one finding, a false negative in assertion 14. `bodyOf` took the first `{` after a
  declaration, and `class SessionStateHolder internal constructor(… onLocalStartAccepted: () -> Unit
  = {}, …)` puts a lambda default before the class body, so `matchingBrace` closed that lambda
  immediately and the body parsed as empty. All 17 non-private members of `SessionStateHolder` left
  assertion 14's coverage, and nothing reported it because the per-source guard only fires when a
  file yields no class at all. The same defect applied to `contractBlock` for `§20.10`. Fixed by
  `bodyBrace`, which returns the first brace at parenthesis depth zero, used by `bodyOf` and by
  `contractBlock`; and by a per-class emptiness guard that reports `declares no parsed member`.
  Proven by mutation 16, which exited 0 before the fix and now fails with
  `class SessionStateHolder.dismissAnonymousReminder defaults force: Boolean = false`, by mutation 17
  (`class SessionStateHolder declares no parsed member`) and by the control mutation on
  `VehicleListStateHolder`, which still fails unchanged. Two permanent fixtures added (119 -> 121
  build-logic tests). The four mirror D-180 rows named only assertion 34 although the shipped check
  registers three; all four now name 34 and 35. `§20.10` reordered
  `@HiddenFromObjC fun observeSaveCompletions()` to its real position between `setNotes` and `save`.
- Review round 4: three fail-open defects, all in this story's checks and all fixed after a failing
  fixture each, with no decision changed. (1) `LEADING_ANNOTATIONS` could not balance parentheses
  and could not see a use-site target, so `@Deprecated("x", ReplaceWith("y")) internal class
  StrayMapper` and `@get:JvmName("leak") internal val leaked = …` left the `@` on the line and
  parsed as no declaration; a scanner replaces the regular expression and `isKoinModuleDeclaration`
  reads the stripped text too. (2) `Member.scopeParameter` matched the parameter name `scope`
  instead of the declared type, so `syncStateHolder(coroutineScope: CoroutineScope)` passed
  assertions 14, 34 and 35 together; `Parameter.isCoroutineScope` now tests the type and
  `const val SCOPE` became `const val COROUTINE_SCOPE`. (3) `appGraphMembersMatch` threw
  `IllegalStateException` on a `§20.10` without `interface AppGraph`, aborting the whole report and
  suppressing every other assertion; it now returns that result the way assertion 35 did, and both
  functions name the side that failed to parse. Five fixtures added (124 -> 129 build-logic tests),
  three mutation rows re-run, and the `§11.6` `@HiddenFromObjC` deferral recorded in `docs/BACKLOG.md`
  under both `E3-08` and `E3-05`.
- Review round 5: three gaps in the two graph-surface comparisons, all of the "reported `PASS`
  while covering less than it claimed" class, all fixed after a failing fixture each, no decision
  changed. (1) `Member.signature` was `name(parameters)` and dropped the return type, so a factory
  changed to return another holder left assertions 34 and 35 green; the signature now carries the
  declaration kind and the declared type, and `balancedParameters` returns the parameter text with
  its closing index so the type is read in the same scan. (2) `FUN` matched functions only, so an
  exported `val`/`var` member was invisible to both; `propertyMembers` now parses direct properties
  at brace depth zero — a local variable in a function or lambda is not collected — requires an
  explicit declared type, and keeps the keyword, because a `var` is write access `§11.6` does not
  expose. (3) Only `private` was filtered, so an `internal` or `protected` helper was compared
  against `§20.10` and held to the scope and default rules although it never reaches Swift;
  visibility is now four-way and the exported filters keep only `public`. Twelve fixtures added
  (129 -> 141 build-logic tests), five mutation rows re-run including two controls that prove the
  fix did not trade one silent gap for another.
- Review round 6: one finding, the same class as rounds 3 to 5 — a comparison reporting `PASS`
  while covering less than it claimed. `members(...)` concatenated every function before every
  property, so a contract declaring `fun A`, `val B`, `fun C` and an implementation declaring
  `fun A`, `fun C`, `val B` normalized to the same order and both assertions passed. `Member` now
  carries a `sourceOffset` that both producers report in the same character space, and the combined
  list is sorted by it; the offset is excluded from `signature`, so what is compared is unchanged.
  Two fixtures added (141 -> 143 build-logic tests), and two mutation rows — one real-repository
  divergent order that fails with both orders quoted, one control at the same position that passes.
- Review round 7: eight defects reproduced by the review of `72392dd`, all in the two checks and all
  fixed after the ten-regression suite was observed RED (10 tests, 10 failures, no compilation
  error). F1 the Koin exemption overrode the expect/actual prohibition; F2 qualified annotations and
  literal parentheses hid wiring declarations; F3 an inner assignment or a string passed as a
  property's Koin initialiser; F4 annotation text set an exported function's visibility; F5 local
  functions and comments became exported members; F6 public properties with repeated modifiers or a
  qualified annotation were invisible; F7 escaped function names bypassed the default-argument
  check; F8 malformed contract braces aborted the whole `contract-check` report. The fixes share one
  offset-preserving lexical mask (`build-logic/.../source/KotlinSourceText.kt`) so the existing
  regexes keep working and diagnostics keep their original text. Ten permanent regressions were
  added; the three suites report 92 tests and zero failures.
- Review round 8: six defects, each a member or a declaration the two checks could not see, plus one
  false documentation claim. (A) `PROPERTY` enumerated its modifiers, so a public property carrying
  `abstract`, `inline`, `expect`, `actual` or `external` never reached assertions 34 and 35.
  (B) `STATE_HOLDER` enumerated seven modifiers, so `expect class VehicleFormStateHolder` dropped out
  of assertion 14 and removed a real default argument from its coverage. (C) `bodyOf` and
  `contractBlock` located a declaration with `indexOf`, so `class SessionStateHolderShim` shadowed
  `class SessionStateHolder` and the wrong body was parsed. (D) An accessor on the declaration line
  was part of the captured type, so `val isClosed: Boolean get()` could not equal any `§20.10`
  spelling. (E) `propertyMembers` tracked brace depth only, so a nested class's constructor `val`
  parameters were collected as public members of the enclosing declaration. (F) A leading
  `context(…)` clause put a `(` before the keyword, which `TOP_LEVEL_DECLARATION` cannot cross, so
  the declaration was skipped with no report. The recorded mitigation for the `STATE_HOLDER` modifier
  limit is false and is corrected in ADR-0181 and below: the guard does not bound it.
- Round 8 evidence: the six regressions and the rule-level context fixture were observed RED as seven
  failing tests with no compilation error, and GREEN afterwards. `:build-logic:convention:test`
  reports 160 tests and 0 failures (153 before this round plus 7). `ktlintCheck detekt` pass,
  `architectureCheck` prints `16 rules … 23 modules`, and `contractCheck` reports 14, 34 and 35
  `PASS` with no `PENDING` and no `FAIL`. No decision changed: `D-178`, `D-179` and `D-180` are
  unchanged and every item above is a defect in an implementation of an already-Accepted decision.
  The accessor regression was re-verified RED with its own fix reverted, so it proves the defect
  rather than passing incidentally.
- Review round 9: four findings, two executable and two documentary, none of which changed a
  decision. (1) `Member.signature` discarded `suspend`, so mutating `AppGraph.syncController()` to a
  suspending declaration with `§20.10` unchanged left `[14:PASS, 34:PASS, 35:PASS]`; `suspend` is now
  read from the masked header and compared. (2) `isKoinModuleDeclaration` read the initialiser from
  the declaration's own line, so the idiomatic wrapped form `val bindings =` followed by
  `module { … }` was reported as `wiring-product-logic` — a false positive against the exact shape
  `docs/TECHNICAL_PLAN.md §4` admits. (3) The `§20.10` `SyncStateHolder` block declared
  `refreshDebug()` before `clearMessage()` while the class declares the reverse; the contract was
  reordered, not the class, so the golden header is untouched. (4) The `E3-08` backlog section lacked
  the `Human review required.` line its index row claims.
- Round 9 evidence: two fixtures added to `SwiftSurfaceContractTest` and six asserted shapes to
  `ArchitectureCheckerTest`, observed RED first (3 failing tests, no compilation error) and GREEN
  afterwards. `:build-logic:convention:test` reports 162 tests and 0 failures.
  `architectureCheck` prints `16 rules … 23 modules`; `contractCheck` reports assertions 1, 5, 7, 14,
  34 and 35 `PASS` with no `PENDING`. Behavioural proofs: the `suspend` mutation fails assertion 34
  with `suspend syncController(): SyncController is declared but absent from §20.10; syncController():
  SyncController is declared in §20.10 but absent from the interface` and reverts to green; a wrapped
  `module { }` appended to `:wiring:firebase` passes `architectureCheck` while the same wrapper with
  `listOf(1)` fails it with `declares internal val wrappedProbe`.
- Push and pull-request status: pushed to `origin/story/E3-08-app-graph-and-firebase-wiring`; pull
  request #71 is open against `main` and awaiting the owner's gated review. Review round 9 was
  pushed as `bccd841..50919d8`. The ten required checks are green on run `35520555700`, which covers
  the head `50919d8` and passed on its **first attempt** with no re-run. Review round 7 was
  pushed as `72392dd..2c258c1`. The ten required checks are green on run `35512948905`, which covers
  the corrected head `2c258c1` and passed on its **first attempt** with no re-run: all ten,
  `shared-tests` and `provider-decoupling` included. That run belongs to the correction commit, not
  to the reviewed head: the reviewed head `72392dd` has its own green run `35508457783`, and it is
  green precisely while the eight defects were reproducing against it. Review round 5 was pushed as `faa9357..a99d7b7` and was green on
  run `35430529028`. Review round 4 was pushed as `cb34b35..faa9357` and was green on
  run `35381816775`. Run `35376856797` covered
  `f8b8406`: nine checks on their first attempt and `provider-decoupling` on its second, after the
  8-minute `Run provider-free Android host tests` step was killed with no test result — the same
  silent-stall class as `shared-tests`, at a new site. Run `35373955424` covered `a5989a1` and run
  `35366069672` covered `58311fa`; both needed one `shared-tests` re-run.
  Earlier green runs needed re-runs for `shared-tests` only: `35338122967` covered `c834699` (third
  attempt), `35336079709` covered `a28174f` (second attempt), `35333547781` covered `016a46b`
  (second attempt), `35332058609` covered `5d40994` and `35330477631` covered `0551c10`;
  `35324474324` covered `cb46b0b` (review round 1). Every later commit is record-only and re-runs
  the identical set, so `gh pr checks 71` is authoritative for the current head.
- `shared-tests` needed re-runs on runs `35333547781`, `35336079709`, `35338122967`, `35363599393`,
  `35366069672` and `35373955424` — every other required check was green on its first attempt
  throughout. The mechanisms are pre-existing and outside this round, whose Kotlin changes are
  confined to `build-logic`; the local suite is green on both targets, including
  `:shared:iosSimulatorArm64Test --rerun-tasks`.
  - **Host-step stall**: the `Run Android application and KMP host tests` step killed at its
    10-minute limit with no test result and no assertion failure, on runs `35333547781`,
    `35336079709` and `35366069672` (first attempt). This is the silent stall classified in
    `docs/PROJECT_LOG.md` (2026-09-17, "Intermittent `shared-tests` stall, classified not fixed")
    and left latent by `D-175`/`D-177`. The final `STARTED` line before each kill,
    `VehicleStateHoldersTest > anEmptyResultForOneOwnerDoesNotResolveTheNextOwnersList`, is a
    buffering artifact, not the culprit: the same line is the last in the successful run of that
    task, which completes in about 3 minutes there.
  - **Native-step stall**: the `Run Kotlin/Native simulator tests` step killed at the same limit
    after `> Task :shared:iosSimulatorArm64Test`, again with no test result, on runs `35338122967`
    (second attempt), `35363599393` (first), `35366069672` (second) and `35373955424` (first). Same
    silent-stall class, on the Native step instead of the host one.
  - **`E1-14` assertion failure**: a real
    `LocalOwnerAdoptionTriggerTest.aFuelEntryWriteTriggersAcquisitionAndAdoptionAfterAnEarlierAttemptFailed[iosSimulatorArm64]`
    failure (`188 tests completed, 1 failed`, `kotlin.AssertionError`) on run `35338122967` (first
    attempt), the Kotlin/Native flake on the test and target `docs/BACKLOG.md` (`E1-14`),
    `docs/handoff-E3-14.md` and `docs/PROJECT_LOG.md` already record.
  - **Environment, not regression.** The exact two steps of the job were re-run locally on this
    branch with `--rerun-tasks`: the host step and `iosSimulatorArm64Test` with the four `-x`
    exclusions complete in about 3 minutes and 36 seconds respectively, against a 10-minute CI cap.
    This round's Kotlin changes are confined to `build-logic/convention/` — two files, neither of
    which is compiled into any test task on either target — and every failed job passed on re-run.
    `E1-14` and `E1-17` remain open by design, which is why a red `shared-tests` is ambiguous.
- Completed since the previous checkpoint: the two architecture rules, the Swift surface contract
  (assertions 14, 34 and 35), the `§20.10` clarifications, `D-178` through `D-180` with their ADRs
  and the four mirror tables, the backlog and `AGENTS.md` reconciliation, and the story handoff.
- Verification evidence and known failures: the canonical CI command passes locally; mutation
  evidence for every new rule is recorded under "Acceptance Evidence" below. No known failure.
- Open decisions or blockers: none. `E3-08` introduced no open decision.
- Exact next step: none for the agent. The branch is pushed and pull request #71 is green on run
  `35520555700`; the story now waits for the owner's gated review of review round 9.

## Scope Completed

- Executed the `:wiring:firebase` "product logic" rule of `docs/TECHNICAL_PLAN.md §4` as a
  declaration-shape check (`D-178`), the rule `E0-04` recorded as unowned because it needs a
  Kotlin declaration parser and the module.
- Added the source rule that keeps `com.ruizurraca.carapp.integration.` out of every module other
  than `:wiring:firebase` and `:integration:*` (`D-179`).
- Implemented `docs/CONTRACTS.md §18` assertion 14 and added assertions 34 and 35, both in a new
  `SwiftSurfaceContract` registered on `contractCheck` (`D-180`).
- Closed the live divergence `E3-03` shipped: `docs/CONTRACTS.md §20.10` now declares
  `syncStateHolder(scope)` on the Kotlin-facing `AppGraph`, which the interface already had.
- Extended the `SwiftAppGraph` lifecycle coverage to the cache-key and close-guard criteria the
  story names, and the graph-construction coverage to the Koin-free criterion.
- Registered `D-178`, `D-179` and `D-180` with ADR-0179, ADR-0180 and ADR-0181 and the four
  mirror tables.
- Reconciled `docs/BACKLOG.md`, `AGENTS.md`, `docs/CONTRACTS.md` and this handoff; appended the
  project-log entry.

## Acceptance Evidence

Each criterion, and the evidence that proves it.

**1. The surfaces match `§11.6` and `§20.10`.** `contractCheck` assertions 14, 34 and 35 all report
`PASS` on the real repository. A read-only audit of every block of both sections against the real
declarations and the committed golden header found the surface otherwise conformant: the
Kotlin-facing `AppGraph`'s eight members, `createSwiftAppGraph`, all ten `SwiftAppGraph` members,
all six state-holder classes' contract-listed members, all eight `UiState` data classes and their
enums, and the whole `§11.6` block (`AppGraphDependencies`, `AppProviders`, `buildAppGraph`,
`testAppGraphDependencies`) match member for member, in order, with identical types and
nullability. The two extras that remain — `FuelEntryFormStateHolder.isLoading` and
`observeSaveCompletions()` — are `@HiddenFromObjC`, absent from the golden header, and are the
Kotlin-side seam the Android host consumes. Review round 2 found them undeclared in `§20.10`, which
is the same blind spot `AppGraph.syncStateHolder(scope)` was: a public member the generated header
cannot show, so the contract and the code diverge with nothing able to see it. Both are now declared
in `§20.10` carrying `@HiddenFromObjC`, and `§11.6` states the convention that a public
`@HiddenFromObjC` member of an exported state-holder class is still declared in `§20.10`.

**2. `:shared:testing` factory parity.** `contractCheck` assertion 13 reports `PASS` with
`16 parameters in canonical order; every parameter defaulted`.

**3. Only `:wiring:firebase` constructs Firebase implementations.** Mutation evidence: with
`:wiring:firebase`'s auth edge switched from `implementation` to `api` and `:composition:ios`
naming `FirebaseAuthClient` in its own source, the module compiles the reference but
`architectureCheck` fails with `:composition:ios: firebase-implementation-outside-wiring` while
`contractCheck` assertion 7 stays green — the golden header cannot see a type that is named but
never exported, which is the hole `D-179` closes. Restoring the edge and the source restores a
green build.

**4. Every top-level declaration in `:wiring:firebase` is a Koin module, an abstraction factory or
a platform initialiser.** Mutation evidence: appending
`internal data class StrayMapping(val id: String)` to `FirebaseAppProviders.kt` makes
`architectureCheck` fail with `:wiring:firebase: wiring-product-logic`; removing it restores a
green build. The fixture asserts twelve rejected shapes and eight accepted ones, including every
shape the real module uses.

**5. Tests build the graph without starting Koin.** Koin is not a dependency of any module
(`grep` over every `build.gradle.kts` finds none). `TestAppGraphDependenciesTest` now constructs
the graph through `buildAppGraph` and `testAppProviders(...)` and serves a vehicle-list, session
and sync state holder from it, which would throw at the first container access if graph
construction needed one. The previous assertion in that file only inspected a flag.

**6. The Swift facade exposes a sync state holder, not `SyncController`, and owns the scopes.**
`SwiftAppGraph.kt` contains no `SyncController` reference and asserts this at the source
(assertion 14). `SwiftAppGraphLifecycleTest.releaseCancelsTheCachedCreationFormAndTheNextFlowCreatesANewVehicle`
(from `E1-07`) already proves a released holder's scope is cancelled.

**7. Holders are cached per argument set and throw after `close()`.**
`SwiftAppGraphLifecycleTest.identicalArgumentsReuseTheSameCachedHolderAndDifferentOnesDoNot`
asserts identity for repeated calls and distinctness across keys for the list, session, sync and
per-vehicle form holders; `everyFactoryThrowsAfterCloseExceptCloseItself` asserts
`IllegalStateException` from all six factories after `close()` and that a second `close()` is
idempotent.

**Mutation evidence, re-run after review round 1.** Every row was applied to the real
repository, the named check run, and the mutated file restored. `git status` reported only the one
mutated path after every restore, and the tree is clean at the end of the run. The message column is
the observed output, not the intended one.

| # | Mutation | Task | Observed |
|---|----------|------|----------|
| 1 | `sessionStateHolder(scope: CoroutineScope = CoroutineScope(Job()))` on `AppGraph` | `contractCheck` | 14 FAIL `AppGraph.sessionStateHolder defaults scope: CoroutineScope = CoroutineScope(Job())`; 34 FAIL `sessionStateHolder(scope: CoroutineScope = CoroutineScope(Job())) is declared but absent from §20.10; sessionStateHolder(scope: CoroutineScope) is declared in §20.10 but absent from the interface` |
| 2 | `vehicleListStateHolder(scope: CoroutineScope = CoroutineScope(Job()))` on `AppGraph` | `contractCheck` | 14 FAIL `AppGraph.vehicleListStateHolder defaults scope: CoroutineScope = CoroutineScope(Job())`; 34 FAIL with both signatures |
| 3 | `vehicleListStateHolder(vehicleId: String = "")` on `SwiftAppGraph` | `contractCheck` | 14 FAIL `SwiftAppGraph.vehicleListStateHolder defaults vehicleId: String = ""`; 34 PASS |
| 4 | `syncStateHolder(scope: CoroutineScope)` on `SwiftAppGraph` | `contractCheck` | 14 FAIL `SwiftAppGraph.syncStateHolder takes scope: CoroutineScope`; 34 PASS |
| 5 | `SyncController` referenced inside `SwiftAppGraph` | `contractCheck` | 14 FAIL `SwiftAppGraph references SyncController`; 34 PASS |
| 6 | `selectVehicle(vehicleId: String? = null)` on `VehicleListStateHolder` | `contractCheck` | 14 FAIL `feature/vehicle/…/VehicleStateHolders.kt: class VehicleListStateHolder.selectVehicle defaults vehicleId: String? = null` |
| 7 | `requestDelete(entryId: String = "")` on `FuelEntryListStateHolder` | `contractCheck` | 14 FAIL `feature/fuel/…/FuelEntryStateHolders.kt: class FuelEntryListStateHolder.requestDelete defaults entryId: String = ""` |
| 8 | `syncStateHolder(scope)` removed from `§20.10` | `contractCheck` | 34 FAIL `syncStateHolder(scope: CoroutineScope) is declared but absent from §20.10` |
| 9 | `internal class StrayMapper` in `:wiring:firebase` | `architectureCheck` | FAIL `:wiring:firebase: wiring-product-logic` / `FirebaseAppProviders.kt:233 declares internal class StrayMapper` |
| 10 | `val moduleRegistry: ModuleRegistry = ModuleRegistry()` in `:wiring:firebase` | `architectureCheck` | FAIL `:wiring:firebase: wiring-product-logic` / `declares internal val moduleRegistry` |
| 11 | `val mentioned = stagedLogger() + module { }` in `:wiring:firebase` | `architectureCheck` | FAIL `:wiring:firebase: wiring-product-logic` / `declares internal val mentioned` |
| 12 | `implementation` -> `api` on the integration edge, plus `FirebaseAuthClient` named in `:composition:ios` | `architectureCheck` | FAIL `:composition:ios: firebase-implementation-outside-wiring` |
| 13 | `class FirebaseWiring : Module` appended to `:wiring:firebase` | `architectureCheck` | FAIL `:wiring:firebase: wiring-product-logic` / `FirebaseAppProviders.kt:247 declares class FirebaseWiring` |
| 14 | `@JvmField internal val leaked = mutableListOf<Any>()` appended to `:wiring:firebase` | `architectureCheck` | FAIL `:wiring:firebase: wiring-product-logic` / `FirebaseAppProviders.kt:247 declares internal val leaked` |
| 15 | `fun syncStateHolder(): SyncStateHolder` removed from the `class SwiftAppGraph` block of `§20.10` | `contractCheck` | 35 FAIL `syncStateHolder() is declared but absent from §20.10` |
| 16 | `dismissAnonymousReminder(force: Boolean = false)` on `SessionStateHolder` | `contractCheck` | 14 FAIL `shared/src/commonMain/kotlin/com/ruizurraca/carapp/StateHolders.kt: class SessionStateHolder.dismissAnonymousReminder defaults force: Boolean = false` |
| 17 | `class SessionStateHolder` reduced to a body with no `fun` | `contractCheck` | 14 FAIL `shared/src/commonMain/kotlin/com/ruizurraca/carapp/StateHolders.kt: class SessionStateHolder declares no parsed member` |
| 18 | `@Deprecated("x", ReplaceWith("y")) internal class StrayMapper` appended to `:wiring:firebase` | `architectureCheck` | FAIL `:wiring:firebase: wiring-product-logic` / `FirebaseAppProviders.kt:247 declares internal class StrayMapper` |
| 19 | `syncStateHolder()` -> `syncStateHolder(coroutineScope: CoroutineScope)` in `SwiftAppGraph.kt` and the matching `§20.10` line | `contractCheck` | 14 FAIL `SwiftAppGraph.syncStateHolder takes coroutineScope: CoroutineScope`; 34 and 35 PASS; all 31 assertion lines still printed |
| 20 | `interface AppGraph {` -> `interface KotlinAppGraphSurface {` in `§20.10` | `contractCheck` | 34 FAIL `§20.10 declares no interface AppGraph block`; all 31 assertion lines still printed, no stack trace and no `IllegalStateException` |
| 21 | `vehicleListStateHolder(scope)` return type changed to `SessionStateHolder` in `AppGraph.kt` | `contractCheck` | 34 FAIL `vehicleListStateHolder(scope: CoroutineScope): SessionStateHolder is declared but absent from §20.10; vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder is declared in §20.10 but absent from the interface` |
| 22 | `vehicleListStateHolder()` return type changed to `SessionStateHolder` in `SwiftAppGraph.kt` | `contractCheck` | 35 FAIL `vehicleListStateHolder(): SessionStateHolder is declared but absent from §20.10; vehicleListStateHolder(): VehicleListStateHolder is declared in §20.10 but absent from the class` |
| 23 | `val extra: String = ""` added to `SwiftAppGraph` | `contractCheck` | 35 FAIL `val extra: String is declared but absent from §20.10` |
| 24 | `private val unreviewed: String = ""` added to `SwiftAppGraph` (control) | `contractCheck` | PASS, no diagnostic: a non-exported property is not compared |
| 25 | `syncStateHolder(coroutineScope: CoroutineScope)` in `SwiftAppGraph` (control) | `contractCheck` | 14 FAIL `SwiftAppGraph.syncStateHolder takes coroutineScope: CoroutineScope`, unchanged by this round |
| 26 | `val extra: String` added to `SwiftAppGraph` **before** `fun close()`, while `§20.10` declares it **after** `fun close()` | `contractCheck` | 35 FAIL, quoting both real orders: `§20.10 declares [… close(), val extra: String], the class declares [… val extra: String, close()]` |
| 27 | The same declaration placed at the **same** position on both sides (control) | `contractCheck` | PASS: order agrees, so the member is a real match rather than a positional artefact |

Rows 2 and 10 are the two rows that previously passed while the change they were supposed to catch
was in place: a default on an `AppGraph` factory's `scope` (the old `signature` stripped the default
before comparing, so assertion 34 saw nothing) and a property whose type merely begins with
`Module`. Row 1 is the exact mutation review round 1 reported as not failing. Rows 10 and 11 were
confirmed to pass before the fix and to fail after it.

The two rows that the review found were not covered at all are rows 1 and 10 above: rows 2, 10 and
11 were confirmed to pass before the fix and to fail after it.

**Canonical verification.** The full CI command of `AGENTS.md` passes locally after the review
fixes: 642 actionable tasks, `BUILD SUCCESSFUL`. `:build-logic:convention:test` reports 109 tests,
0 failures, and `contractCheck` reports every assertion `PASS` with no `PENDING` line.

## Out of Scope / Not Done

- **The Konsist fixture `docs/CONTRACTS.md §20.10` requires** to ban `PostWriteDebounce`,
  `ConnectivityRecovered` and `Periodic` from any `iosMain` call site of
  `SyncStateHolder.requestSync`. `D-16` assigns package-level rules to Konsist, `E1-07` introduced
  it for the feature-layer rows, and Konsist is not yet a dependency of any module. No iOS call
  site of `requestSync` exists today (`iosApp/` and `iosMain` contain none), so no rule of the
  contract is contradicted; the fixture belongs with the story that first adds one. This is a
  SHOULD-level deferral, recorded here as its reason.
- **The `§11.6` rule that a public `@HiddenFromObjC` member of an exported state-holder class is
  declared in `§20.10` has no executable check.** Assertions 34 and 35 compare only the `class
  AppGraph` and `class SwiftAppGraph` blocks of `§20.10`; no assertion compares a state-holder
  block, and the generated Objective-C header cannot see a `@HiddenFromObjC` member at all. Exactly
  two such members exist today, both in `FuelEntryFormStateHolder`: `isLoading` and
  `observeSaveCompletions()`. Review round 2 declared both by hand, so no live divergence remains,
  but the rule stays prose and the next hidden member added to an exported holder can go undeclared
  with nothing failing. The two other `@HiddenFromObjC` declarations in the holder files are the
  top-level `createVehicleListStateHolder` / `createVehicleFormStateHolder` factories, which this
  rule does not cover because they are not class members.
  **Expected owner: `E3-05` (Backup Status UI)** — the earliest remaining Phase 3 story that renders
  a state-holder-backed surface (`SyncStateHolder`'s sync status), and therefore the first place a
  hidden member is plausibly added; if another story touches the holder surface first, this transfers
  to it. Making the rule executable also needs a `D-` decision on where it lives — `SwiftSurfaceContract`
  beside assertions 34 and 35, or the `D-16` Konsist rules — because `D-16` assigns package-level
  rules to Konsist while this is a document-block comparison.
- `E3-04` owns enforcing `SYNC_POST_WRITE_DEBOUNCE_MS` and `SYNC_MIN_AUTOMATIC_INTERVAL_MS`; this
  story does not touch the trigger constants.
- `E3-09` owns `:integration:firebase-analytics` and `E4-04` owns
  `:integration:firebase-crashlytics`; `:wiring:firebase` keeps the no-op analytics tracker and
  the `CrashReporter` no-op already bound by `E0-08`.
- `E3-12` owns the permanent-account cross-device recovery proof.
- **The `SwiftSurfaceContract` coverage limits are documented in ADR-0181, Negative.** What remains
  open after review rounds 5 to 8: `HOLDER_SOURCES` hardcodes three files, a property without an
  explicit declared type is not compared, and recognition stays textual. The
  no-parsed-class guard reports only a source that yields no holder at all; review round 8 proved it
  does not bound a single holder going missing, and closed the `STATE_HOLDER` modifier limit instead.

## Review Round 1

The owner's gated review of pull request #71 reproduced five findings against the real repository.
All were fixed on the same branch, each after a failing fixture, and each is recorded in the
mutation table above.

| Finding | Defect | Fix |
|---------|--------|-----|
| 1 | `docs/handoff-E3-08.md` and ADR-0181 both claimed a Kotlin default on `AppGraph` fails assertion 14; it did not. Assertion 14 never read the interface's defaults, and assertion 34 stripped the default before comparing | Assertion 34 compares the default as part of the parameter shape; assertion 14 reports the interface's own defaults. ADR-0181 and the table corrected, every row re-run |
| 2 | Assertion 14 false-positived on a private `SwiftAppGraph` member, which is not exported | Private members filtered out of the Swift-facing loop; a fixture proves a private helper with a `scope` and a default is accepted while a public one is rejected |
| 3 | `isKoinModuleDeclaration` admitted any type beginning with `Module` and any line mentioning `module {`; proved side by side with `moduleRegistry` vs `otherRegistry` | The `Module` type is matched exactly (`Module`, `Module?`, `org.koin.core.module.Module`) and the initialiser must be the declaration's own; four rejecting fixtures added |
| 4 | The violation put the declared name between the modifiers and the keyword: `declares enum  StrayMode class` | The description is rebuilt in source order; the fixtures assert the exact message text rather than only the rule id |
| 5 | `SwiftSurfaceContractTest` had one test and no failing fixture on any parser branch; `Inputs` and the `fixture` marker parameter had zero call sites | One fixture per problem branch of both assertions, asserting the exact problem text; the marker parameter removed |

**Additional request, recorded and addressed.** The coverage limits the review listed — the
hardcoded three-file `HOLDER_SOURCES`, the `STATE_HOLDER` modifier set, the string-literal-unaware
`matchingBrace`, and the generic/extension `FUN` shapes — are enumerated in ADR-0181 under Negative.
The chosen mitigation for the two that can fail silently is the no-parsed-class guard: a holder
source that yields no recognised class is now reported instead of dropping out of assertion 14. The
`FUN` regex was widened to match `fun <T> name(` and `fun Foo.name(` rather than documenting them as
unseen. The remaining two are documented, because closing them means the parser growing a scanner.

## Review Round 2

The owner's second gated review of pull request #71 reproduced seven findings against the real
repository. All were fixed on the same branch, each after a failing fixture, and no decision changed.
Finding 7 arrived in review round 3 and is recorded here with the same six; it is the same defect
class as finding 3 — a guard that reported `PASS` while covering less than it claimed.

| Finding | Defect | Fix |
|---------|--------|-----|
| 1 | `isKoinModuleDeclaration` read the text after the first `:` on every column-zero line, before the keyword was known, so `class FirebaseWiring : Module`, `internal object FuelEntryMapper : Module` and `interface LocalGate : Module` claimed the Koin exemption and were admitted | The declaration is classified first and the exemption applies only to a `val`/`var`; `aSupertypeNamedModuleAndAnAnnotatedDeclarationDoNotEscapeTheRule` rejects a `class`, an `object` and an `interface` inheriting `Module` |
| 2 | The same matcher could not see past `@`, so `@Suppress("unused") internal class StrayMapper` and `@JvmField internal val leaked = …` parsed as no declaration at all and escaped the rule | `LEADING_ANNOTATIONS` is stripped before matching, so the modifier group stays annotation-free; the same fixture rejects both shapes and accepts an annotated Koin binding and an annotated private factory. **Superseded by review round 4**, which replaced that regular expression with `stripLeadingAnnotations`: it could not balance parentheses and could not see a use-site target |
| 3 | Assertion 34 guarded only the Kotlin-facing interface. The Swift-facing `class SwiftAppGraph` block of `§20.10` had no member comparison, because the golden header is regenerated and committed with the change that alters the class and so can never report a stale block | `§18` assertion 35 compares the Swift-facing block with the real class, `private` members excluded; six fixtures and mutation C prove it |
| 4 | `FuelEntryFormStateHolder.isLoading` and `observeSaveCompletions()` are public `@HiddenFromObjC` members absent from `§20.10` — the same blind spot `AppGraph.syncStateHolder(scope)` was | Both are declared in `§20.10` carrying `@HiddenFromObjC`, and `§11.6` states the convention |
| 5 | `splitTopLevel` decremented its depth on the `>` of `->`, so `callback: (Int) -> Unit` drove the depth to -1 and every later comma stopped splitting, reporting the default under the wrong name | The `>` of an arrow closes nothing; `aDefaultAfterAFunctionTypedParameterIsReportedUnderItsOwnName` proves it. `FUN` also gained a leading word boundary, so an identifier ending in `fun` followed by `(` is not read as a member declaration |
| 6 | ADR-0181 states every problem branch has a fixture asserting its exact text, and the two assertion-14 emptiness branches had none; `bothSidesFailingToParseIsReported` asserted assertion 34 only | `anUnparsedKotlinFacingInterfaceIsReportedByAssertion14` and `anUnparsedSwiftFacingClassIsReportedByAssertion14` cover both branches, asserting the exact text |
| 7 | Assertion 14 silently covered **no member** of `SessionStateHolder`: `bodyOf` took the first `{` after the declaration, which is the lambda default of the internal primary constructor's `onLocalStartAccepted` parameter, so `matchingBrace` closed it immediately and the class body parsed as empty. All 17 exported members were outside the check, and nothing reported it because the per-source guard only fires when a file yields no class | `bodyBrace` selects the first brace at parenthesis depth zero, so the class body is found with or without a primary constructor, and a per-class emptiness guard reports `declares no parsed member`. Proven by mutation 16, which passed before the fix and fails after it, and by mutation 17; `aHolderWithALambdaDefaultInItsConstructorStillHasItsMembersChecked` and `aHolderClassWithNoParsedMemberIsReported` are the permanent fixtures |

## Review Round 4

The owner's fourth gated review of pull request #71 reproduced three fail-open defects, all in the
checks this story added, and all three fixed on the same branch after a failing fixture each. No
decision changed: `D-178`, `D-179` and `D-180` keep their wording and `Accepted` status.

| Finding | Defect | Fix |
|---------|--------|-----|
| 1 | `LEADING_ANNOTATIONS` was the regular expression `^(?:@\w+(?:\([^)]*\))?\s+)+`. It cannot balance parentheses, so `@Deprecated("x", ReplaceWith("y")) internal class StrayMapper` left the `@` on the line and `TOP_LEVEL_DECLARATION` parsed no declaration at all; `@get:JvmName("leak") internal val leaked = …` escaped the same way. Both shapes passed the rule silently | A scanner replaces the regular expression: `stripLeadingAnnotations`, `skipOneAnnotation`, `skipBalancedParentheses` and `Char.isAnnotationNameChar` (`@` names admit the `:` of a use-site target). `isKoinModuleDeclaration` also reads the stripped text, because a use-site target carries its own colon and the raw read took `JvmName(…)` for the declared type. Fixture: `anAnnotationWithNestedParenthesesOrAUseSiteTargetDoesNotHideTheDeclaration` |
| 2 | `Member.scopeParameter` matched the parameter **name** `scope`, not the declared type. `§11.6` constrains the type: `SwiftAppGraph.syncStateHolder(coroutineScope: CoroutineScope)` passed assertions 14, 34 and 35 together once `§20.10` was edited in the same change, because the member comparison agreed with itself | `Parameter.isCoroutineScope` tests `type.removeSuffix("?") == "CoroutineScope"` and `scopeParameter` selects by that; `const val SCOPE` is replaced by `const val COROUTINE_SCOPE`. Fixtures: `aSwiftFacingMemberTakingAScopeUnderAnotherNameIsRejected` and `aKotlinFacingFactoryWhoseScopeParameterIsNotACoroutineScopeIsRejected` |
| 3 | `appGraphMembersMatch` called `contractBlock()`, which `check`s for `interface AppGraph` and throws. A `§20.10` without that block aborted the whole `contract-check` run with `IllegalStateException`, suppressing every other assertion's result. The one-sided parse case also blamed "both sides" when only one had failed, and assertion 35's counterpart did too | `appGraphMembersMatch` returns the missing-block result the way assertion 35 already did, and both functions report which side failed. Fixtures: `aContractWithNoKotlinFacingBlockIsReported`, `aContractSideThatParsesToNothingIsNamedRatherThanBlamedOnBothSides`, `anInterfaceSideThatParsesToNothingIsNamedRatherThanBlamedOnBothSides` and their two Swift-facing counterparts |

**Mutation evidence for round 4** is rows 18 to 20 of the table under "Acceptance Evidence", which
is the canonical one. Each row was applied to the real repository, the named check run, and the
file restored with `git checkout --`; `git status --porcelain` printed nothing afterwards.

Rows 18 and 19 passed before this round's fixes and fail after them. Row 20 is the one that
previously aborted the report: it now degrades to a single failed assertion, which is what lets a
reviewer see the other thirty results.

**Documentation.** `docs/adr/0179-…` reworded its annotation limit and named the new fixture;
`docs/adr/0181-…` gained the two new coverage limits. The `§11.6` `@HiddenFromObjC` rule is
recorded as a deferral in `docs/BACKLOG.md` under both `E3-08` (with its reason) and `E3-05` (as an
inherited acceptance criterion), expected owner `E3-05`.

## Review Round 5

The owner's fifth gated review of pull request #71 reproduced three gaps in the two graph-surface
assertions, all of the same class: the comparison reported `PASS` while covering less than it
claimed. All three were fixed on the same branch after a failing fixture each, and no decision
changed — `D-180` keeps its wording and `Accepted` status.

| Finding | Defect | Fix |
|---------|--------|-----|
| 1 | `Member.signature` was `name(parameters)` and discarded the return type. A factory changed to return another holder left assertions 34 and 35 at `PASS`, because the name and the parameters still matched | The signature carries the declaration kind and the declared type; `balancedParameters` returns the parameter text with its closing index so the return type is read from the same scan. Fixtures: `aKotlinFacingReturnTypeChangeIsRejected`, `aSwiftFacingReturnTypeChangeIsRejected` |
| 2 | `FUN` recognises functions only, so a public `val`/`var` member was invisible to both assertions — the surface could gain an exported property with nothing failing | `propertyMembers` parses direct `val`/`var` members at brace depth zero, so a local variable in a function or lambda is not collected, and it requires an explicit declared type. The keyword is part of the signature, because a `var` is write access that `§11.6` does not expose. Fixtures: the four one-sided property fixtures and `aPropertyKindChangeIsRejected` |
| 3 | Only `private` was filtered, so an `internal` or `protected` helper was compared against `§20.10` and held to the scope and default-argument rules, even though it never reaches Swift | Visibility is classified four ways and the exported-surface filters keep only `public`. Fixtures: `anInternalSwiftFacingHelperIsNotComparedAgainstTheContract`, `aProtectedSwiftFacingHelperIsNotComparedAgainstTheContract`, `anInternalSwiftFacingHelperWithAScopeAndADefaultIsAccepted`, `aProtectedSwiftFacingHelperWithAScopeAndADefaultIsAccepted`, `anInternalStateHolderFunctionWithADefaultIsAccepted` |

**Mutation evidence for round 5** is rows 21 to 23 of the table under "Acceptance Evidence". Each
row was applied to the real repository, the check run, and the file restored; `git status`
reported only the two intended files afterwards.

Two controls prove the fix did not trade one silent gap for another: appending `private val
unreviewed: String` to `SwiftAppGraph` leaves `contractCheck` at `PASS`, and the assertion-14
scope mutation on `syncStateHolder` still fails. Assertion ids, names, the member order and the
missing-side diagnostics are unchanged; only the compared signature grew.

## Review Round 6

The owner's sixth gated review of pull request #71 found one more gap of the same class as rounds 3
to 5 — a comparison reporting `PASS` while covering less than it claimed — and it was fixed on the
same branch after a failing fixture each. `D-180` keeps its wording and `Accepted` status.

| Finding | Defect | Fix |
|---------|--------|-----|
| 1 | `members(...)` concatenated `functionMembers(body) + propertyMembers(body)`, so every function was ordered before every property. A contract declaring `fun A`, `val B`, `fun C` and an implementation declaring `fun A`, `fun C`, `val B` both normalized to `[A, C, B]` and assertions 34 and 35 passed on a documented ordering violation. Order is part of the surface definition in `docs/CONTRACTS.md` and `D-180` | `Member` gained a `sourceOffset` that both producers populate in the same character space — functions from the match start, properties from the absolute line offset plus the keyword offset — and the combined list is sorted by it. `sourceOffset` is excluded from `signature`, so the comparison stays on kind, name, parameters, declared type, defaults and visibility. Fixtures: `anInterleavedFunctionAndPropertyOrderIsRejectedOnTheKotlinFacingSurface` and its Swift-facing counterpart |

**Mutation evidence for round 6** is rows 26 and 27 of the table under "Acceptance Evidence". Row 26
is the real-repository proof: the member exists on both sides and only its position differs, and
the diagnostic quotes both real orders. Row 27 is the control that shows the fix does not report a
false positive when the orders agree.

## Review Round 7

The review of pull request #71 at `72392dd` reproduced eight further defects, all in the two checks
this story added and all of the same class as rounds 3 to 6: the check reported `PASS` while covering
less than it claimed, or it aborted instead of reporting. All eight were closed on the same branch
after the ten-regression suite was observed RED, and no decision changed — `D-178`, `D-179` and
`D-180` keep their wording, their policy and their `Accepted` status.

| # | Defect | Fix |
|---|--------|-----|
| F1 | The Koin exemption removed a declaration before `isProductLogic` could see its modifiers, so `expect val bindings: Module` and `actual val bindings: Module = module { }` passed although `D-178` rejects `expect`/`actual` in every shape | `isExpectOrActual` is evaluated first in the filter and never behind the Koin exemption |
| F2 | The annotation-name scanner did not accept `.` and its delimiter scanner counted parentheses inside literals, so `@kotlin.Deprecated("legacy") internal class StrayMapper` and `@Deprecated("(") internal class StrayMapper` were invisible | A shared offset-preserving lexical mask; annotation names accept `_`, `:` and `.`, delimiters are counted on masked text |
| F3 | `containsMatchIn` could find a later `= module {` anywhere on the line, so `internal val leaked = run { val bindings = module { }; 1 }` and `internal val leaked = "= module {"` passed as Koin bindings | The initialiser is matched on the text after the declaration's own assignment, anchored to the start of that initialiser, on masked text |
| F4 | Visibility was classified from raw header text, so a word inside an annotation message made a public function look `internal` and its forbidden default was skipped | Visibility reads masked lexical header text; the real parameter text is still reported |
| F5 | Function scanning had no brace-depth filter and searched raw comment text, so a local function or a commented-out declaration became an exported member | Functions are matched in masked code and only at brace depth zero |
| F6 | `PROPERTY` accepted at most one modifier and could not read a qualified annotation, so `public final val extra: String` and `@kotlin.Deprecated("temporary") val extra: String` were invisible to assertion 35 | Repeated legal modifiers and qualified annotations are recognised, with source offsets and the `val`/`var` kind preserved |
| F7 | An escaped identifier bypassed the default-argument check, so `` fun `dismissAnonymousReminder`(force: Boolean = false) `` passed | `FUN` accepts a backticked name, the comparison key drops the backticks, and the parameters are still inspected |
| F8 | A malformed `§20.10` block threw `IllegalStateException`, and `ContractCheck.runAll()` lost the remainder of its report | `contractBlock` returns null and the calling assertion emits a FAIL with an unbalanced-block diagnostic; `validate()` always returns 14, 34 and 35 |

**RED evidence.** `Pr71ReviewRegressionTest` was added alone and run before any production change: 10
tests, 10 failures, none of them a compilation error. The observed causes were
`Expected wiring-product-logic for expect val bindings: Module; got []`,
`Expected wiring-product-logic for @kotlin.Deprecated("legacy") internal class StrayMapper; got []`,
`Expected wiring-product-logic for internal val leaked = run { val bindings = module { }; 1 }; got []`,
`expected:<FAIL> but was:<PASS>` for the visibility and property cases,
`SwiftAppGraph.localProbe defaults retries: Int = 1 expected:<PASS> but was:<FAIL>` for the local
function, `SwiftAppGraph.ghost defaults retries: Int = 1 expected:<PASS> but was:<FAIL>` for the
commented-out declaration, and `IllegalStateException: Unbalanced braces in the interface AppGraph
block of docs/CONTRACTS.md` plus its Swift counterpart for the malformed blocks.

**GREEN evidence.** After the implementation change the three suites report 92 tests and zero
failures on the reviewed baseline: 82 pre-existing (46 in `SwiftSurfaceContractTest`, 36 in
`ArchitectureCheckerTest`) plus the 10 regressions.

**Documentation.** Two ADR-0181 claims were removed because they are false, and both were refuted
against the running check before removal: an annotation on its own preceding line does **not** hide
the state-holder class, and `FUN` **does** already cross whitespace and newlines, so a declaration
whose name is on the following line is matched. The claim that the emptiness guard detects every
inferred property was replaced by the accurate limitation. ADR-0179's first Negative bullet was
corrected: private non-constant top-level properties are admitted, not rejected.

## Review Round 8

The owner's eighth gated review reproduced six defects and one false documentation claim. All six
were fixed after the regression suite was observed RED, and `D-180` keeps its wording and `Accepted`
status. The table shape follows Review Round 1.

| Finding | Defect | Fix |
|---------|--------|-----|
| A | `PROPERTY` enumerated its modifiers, so a public property carrying `abstract`, `inline`, `expect`, `actual` or `external` was invisible to assertions 34 and 35 | The modifier list carries them; `anUnrecognisedPropertyModifierDoesNotHideAnExportedMember` |
| B | `STATE_HOLDER` enumerated seven modifiers, so `expect class VehicleFormStateHolder` dropped out of assertion 14. The per-source guard could not bound it: each of the three `HOLDER_SOURCES` declares two holders | The pattern is `class <Name>StateHolder` on masked source; `anUnrecognisedClassModifierDoesNotHideAStateHolderFromAssertion14` |
| C | `bodyOf` and `contractBlock` located a declaration with `indexOf`, which matches a name prefix, so `SessionStateHolderShim` shadowed the real holder body | `declarationIndex` matches the whole declaration; `aClassWhoseNameExtendsAHolderNameDoesNotShadowTheRealHolder` |
| D | An accessor or delegate on the declaration line was part of the captured type, producing `val isClosed: Boolean get()` | `declaredPropertyType` strips ` get(`, ` set(` and ` by `; `anAccessorOnTheDeclarationLineIsNotPartOfThePropertyType` |
| E | `propertyMembers` tracked brace depth only, so a nested class's constructor `val` parameters became members of the enclosing declaration | Parenthesis depth is tracked too; `constructorParametersOfANestedClassAreNotMembersOfTheEnclosingDeclaration` |
| F | A leading `context(…)` clause put a `(` before the keyword, which `TOP_LEVEL_DECLARATION` cannot cross, so the declaration was skipped with no report | The clause is stripped with the annotations in the lexical module; `aContextParameterClauseDoesNotHideAWiringDeclaration` plus a rule-level fixture in `ArchitectureCheckerTest` |
| Doc | ADR-0181 and this handoff recorded the no-parsed-class guard as bounding the `STATE_HOLDER` modifier limit. It does not: the guard fires only when a source yields no holder at all | Both sentences corrected, and the `STATE_HOLDER` limit recorded as closed rather than open |

## Review Round 9

Four findings, all in artifacts this story introduced. Two are executable defects, two are
documentation divergences. No decision changed: `D-178` and `D-180` are corrected in their
implementation, not in their choice, which is the precedent set by review round 3.

- **Finding 1 — assertions 34 and 35 discarded `suspend`.** `Member.signature` carried kind, name,
  parameter shapes and declared type. Mutating `AppGraph.syncController()` to
  `suspend fun syncController(): SyncController` with `§20.10` unchanged left `[14:PASS, 34:PASS,
  35:PASS]`. `interface AppGraph` is `@HiddenFromObjC`, so the golden header could not see it
  either — the exact blind spot assertion 34 exists to close. `suspend` is now read from the
  annotation-masked declaration header and prefixed to the signature. Two fixtures added.
- **Finding 2 — the wiring rule rejected a wrapped Koin binding.** `isKoinModuleDeclaration` read
  the initialiser from the declaration's own line only, so `val firebaseModule =` followed by
  `    module { … }` was reported as `wiring-product-logic`. That is a false positive against the
  exact shape `docs/TECHNICAL_PLAN.md §4` admits, and it would have blocked the first story to add
  a Koin module to `:wiring:firebase`. The initialiser is now read from the next recorded source
  line of the same file when the declaration line ends at its `=`, on masked text. Six fixtures
  added, three accepting and three still rejecting.
- **Finding 3 — the `§20.10` `SyncStateHolder` block was out of order.** It declared
  `refreshDebug()` before `clearMessage()`; the class declares the reverse. The member sets were
  equal, so only the order diverged, and no assertion compares state-holder blocks. The contract
  block was reordered to the class order; the class and the golden header are unchanged.
- **Finding 4 — the `E3-08` story section carried no gate line.** The story index row was changed to
  `Yes` in this pull request without the matching `Human review required.` line in the section. The
  gate is correct by path and by topic under `AGENTS.md § Human Review Gates`, so the line was added
  and the canonical gate list is unchanged.

Both ADRs were corrected where their text no longer described the code: ADR-0181's enumerated
signature limit now names `suspend` and states which modifiers remain unmodelled, and ADR-0179 no
longer implies the Koin initialiser must sit on the declaration line.

**No new TDD exemption.** Findings 1 and 2 are behaviour changes in check code, so both went
through RED before GREEN. Findings 3 and 4 are documentation.

## Files Changed

- `build-logic/convention/src/main/kotlin/.../architecture/ArchitectureChecker.kt` — the two new
  rules, their classification helpers and constants.
- `build-logic/convention/src/main/kotlin/.../contract/SwiftSurfaceContract.kt` — new; assertions
  14, 34 and 35.
- `build-logic/convention/src/main/kotlin/.../source/KotlinSourceText.kt` — new; the
  offset-preserving lexical mask shared by the architecture and contract checks.
- `build-logic/convention/src/test/kotlin/.../contract/Pr71ReviewRegressionTest.kt` — new; the ten
  regressions the PR #71 review reproduced.
- `build-logic/convention/src/main/kotlin/.../contract/ContractCheck.kt` — registers it.
- `build-logic/convention/src/test/kotlin/.../architecture/ArchitectureCheckerTest.kt` — the two
  rule fixtures, the exact-`Module` fixtures and the message-text assertions.
- `build-logic/convention/src/test/kotlin/.../contract/SwiftSurfaceContractTest.kt` — new; one
  fixture per problem branch of assertions 14, 34 and 35 plus the regression that requires all three
  present and passing against the real repository.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/SwiftAppGraphLifecycleTest.kt` — cache-key
  and close-guard coverage.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/TestAppGraphDependenciesTest.kt` — the
  Koin-free construction proof.
- `docs/CONTRACTS.md` — `§11.6` names assertions 34 and 35 and the `@HiddenFromObjC` declaration
  convention, `§18` declares assertion 35, `§20.10` declares `syncStateHolder(scope)` plus the two
  `@HiddenFromObjC` members of `FuelEntryFormStateHolder`, and review round 3 moved
  `observeSaveCompletions()` to its real position between `setNotes` and `save`.
- `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`,
  `docs/adr/README.md` — `D-178` through `D-180`.
- `docs/adr/0179-…`, `docs/adr/0180-…`, `docs/adr/0181-…` — new ADRs.
- `docs/BACKLOG.md`, `AGENTS.md`, `docs/PROJECT_LOG.md`, this handoff.

## Decisions Made

- **`D-178` — `:wiring:firebase` product logic is a declaration shape.** Any function is admitted,
  a `val`/`var` only when `private`, a Koin `Module` by its type or initialiser, and every
  `class`/`interface`/`object`/`enum class`/`typealias`/`fun interface` is rejected at any
  visibility. Rejected alternatives: reject only non-private declarations and admit everything
  `private` (a private mapper is still a mapper, and the prose names mappers), and require an AST
  (a new build-logic dependency for one rule that the other sixteen line-based rules do not need).
  `fun interface` counts as an interface, because the `fun` there modifies a type declaration
  rather than introducing the abstraction factory `§4` admits. See ADR-0179.
- **`D-179` — integration implementations are named in `:wiring:firebase` only.** A source rule
  rejects any mention of `com.ruizurraca.carapp.integration.` outside `:wiring:firebase` and
  `:integration:*`. The direct case is already a compile error, so the rule earns its place on the
  transitive case a one-word `api` change opens, which the dependency graph and the golden header
  both miss. Rejected: tightening the architecture table to require `implementation` edges (needs
  a new configuration-aware dimension and still does not cover the composition root), and relying
  on the golden header (it cannot see a named-but-unexported type). See ADR-0180.
- **`D-180` — both `AppGraph` surfaces are guarded by parsing the contract.** Rejected: fixing the
  divergence and implementing assertion 14 alone (closes it without preventing it, and `E3-03`
  showed it happens), and generating `§20.10` from the sources (the contract is a human-reviewed
  normative document and is not assembled from a build step anywhere else). See ADR-0181.
- **TDD order exemption used for architecture-rule fixtures**, exactly as permitted by
  `docs/SPECIFICATION.md §11`: the fixtures and their checker implementation were completed in the
  same RED/GREEN cycle, and each forbidden shape has an executable failing fixture (`D-16`).
- **TDD order exemption used for the contract-check rules (review round 1 removed its scope).**
  The original exemption read that assertion 14 and assertion 34 are checks over committed
  repository files, so their failing evidence was mutation runs rather than a test. Review found
  that left every branch of `SwiftSurfaceContract` without a fixture, contrary to `D-16`. The
  exemption no longer applies to the parser: `SwiftSurfaceContractTest` now holds one failing
  fixture per problem branch of both assertions, and only the *end-to-end* proof that the
  repository itself is green remains the mutation run and the real-`contractCheck` test.
- **No new TDD exemption was taken for review round 2.** Every one of the six corrections was made
  after a failing fixture was written, run and observed RED: findings 1 and 2 by
  `aSupertypeNamedModuleAndAnAnnotatedDeclarationDoNotEscapeTheRule` (`Expected rule
  'wiring-product-logic' to fire … got: []`), finding 3 by the six assertion-35 fixtures
  (`assertion 35 is missing`) and `contractCheckGuardsTheSwiftFacingSurface` (`contract-check
  assertion 35 is not implemented`), finding 5 by
  `aDefaultAfterAFunctionTypedParameterIsReportedUnderItsOwnName` (`expected … defaults retries: Int
  = 1 but was … defaults callback: (Int) -> Unit, retries: Int = 1`). Finding 4 is documentation
  only, proved by `contractCheck` assertion 1 staying `PASS`, and finding 6's two fixtures passed
  without a production change, which is what the review intended them to prove.
- **No new TDD exemption was taken for review round 3.** The finding was a false negative, so both
  fixtures were written first and observed RED (`expected a failure, got: expected:<FAIL>
  but was:<PASS>`) before `bodyBrace` and the per-class guard were added. The end-to-end proof
  remains the mutation run against the real repository.
- **SHOULD-level deferral: the `§20.10` Konsist fixture.** Stated with its reason under Out of
  Scope above.

## Verification Run

- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test --stacktrace` — `BUILD SUCCESSFUL`, 642 actionable tasks.
- `./gradlew architectureCheck` — `16 rules from docs/TECHNICAL_PLAN.md §4, 23 modules`; passes.
- `./gradlew contractCheck` — every assertion `PASS`, including the new 14 and 34, with no
  `PENDING` line.
- `./gradlew :build-logic:convention:test` — 109 tests, 0 failures, including the fixture per
  problem branch of assertions 14 and 34.

**Review round 2, re-run.** The canonical command above passes again with 642 actionable tasks.
`./gradlew :build-logic:convention:test` reports **119 tests, 0 failures**; the ten test names of the
verification criteria are present and green. `./gradlew contractCheck` reports assertions **1, 13, 14,
34 and 35** `PASS`, with 35 present in the output, and no `PENDING` line.
`./gradlew architectureCheck` passes with no `wiring-product-logic` violation for the real
`:wiring:firebase`. `./gradlew ktlintCheck detekt` passes with no new suppression. The three
mutation proofs of the criteria were applied to the real repository and reverted, each recorded in
the table above; `git status` reported nothing after every restore.

**Review round 3, re-run.** The canonical command above passes again with 642 actionable tasks.
`./gradlew :build-logic:convention:test` reports **121 tests, 0 failures** — the two fixtures this
round added — and `./gradlew contractCheck` reports exit code 0 with assertions **1, 13, 14, 34 and
35** `PASS` and no `PENDING` line. `SwiftSurfaceContract.kt` contains **zero** occurrences of
`indexOf('{'`, so neither `bodyOf` nor `contractBlock` selects a brace by position any more.
Mutation 16 reproduces the review's own proof: with
`dismissAnonymousReminder(force: Boolean = false)` on `SessionStateHolder`, `contractCheck` exits 0
before this round and exits 1 after it with
`shared/src/commonMain/kotlin/com/ruizurraca/carapp/StateHolders.kt: class SessionStateHolder.dismissAnonymousReminder defaults force: Boolean = false`.
Mutation 17 produces `class SessionStateHolder declares no parsed member`, and the control mutation
on `VehicleListStateHolder.selectVehicle` still fails with
`class VehicleListStateHolder.selectVehicle defaults vehicleId: String? = null`, so the coverage
that already worked is unchanged. `git diff --name-only` lists no file under `shared/src`,
`feature/`, `wiring/`, `integration/` or `composition/`: only the two `build-logic` Kotlin files and
documentation.
- `./gradlew -Pcarapp.excludeFirebaseProviders=true :shared:testAndroidHostTest` — passes; the
  provider-free graph is unaffected.
- Mutation runs recorded in the table under "Acceptance Evidence" — all twelve rows re-run after
  review round 1, each failing with the message recorded there and each restoring a clean tree.
- `git status` after each mutation row — only the single mutated path was reported before the
  restore, and nothing after it.

## Contract Impact

- `docs/CONTRACTS.md §20.10` — the Kotlin-facing `AppGraph` block gains
  `fun syncStateHolder(scope: CoroutineScope): SyncStateHolder`, the member `E3-03` shipped. This
  is a representational clarification of an already-implemented interface, not a behaviour change,
  and it is the contract being corrected to match the code rather than the reverse.
- `docs/CONTRACTS.md §11.6` — the `AppGraph` rule names assertion 34 as the check that keeps the
  block and the interface equal, names assertion 35 for the Swift-facing block and the real class,
  and states that a public `@HiddenFromObjC` member of an exported state-holder class is declared in
  `§20.10` carrying the annotation.
- `docs/CONTRACTS.md §18` — assertion 34 is declared, with the reason no other assertion can
  replace it; assertion 35 is declared for the Swift-facing block, because the generated header is
  regenerated with the change that alters the class and cannot report a stale block.
- `docs/CONTRACTS.md §20.10` — review round 2 declared the two public `@HiddenFromObjC` members of
  `FuelEntryFormStateHolder`, `isLoading` and `observeSaveCompletions()`, which the generated header
  cannot show. Both already exist in code; this is the contract being corrected to match it.

## Decision Board Impact

- Added `D-178`, `D-179` and `D-180`, with ADR-0179, ADR-0180 and ADR-0181 and identical rows in
  `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`. `contractCheck`
  assertions 2 and 3 report `181 decisions` and `181 ADRs`, both `PASS`.

## Shared-Write Modules Touched

- None. `:core:database` is unmodified.

## Project Log Entry

- [x] Entry appended on 2026-09-18.

## Risks or Follow-ups

- **The `§20.10` Konsist fixture is deferred** to the story that first adds a `requestSync` call
  site under `iosMain`. Recorded under Out of Scope with its reason.
- **`E3-03` was corrected here, not there.** The `syncStateHolder` divergence was introduced by
  `E3-03` and merged, so this story corrected the contract rather than the code. The guard that
  would have caught it at the time is assertion 34, which did not exist; it does now.
- **The declaration classifier is textual.** A declaration shape the regular expression cannot see
  fails open. Bounded by the rule's direction and by the two modules it guards being small: an
  unparseable `AppGraph` or `SwiftAppGraph` reports that it could not be parsed rather than passing.
- **`SwiftSurfaceContract`'s coverage limits are enumerated in ADR-0181 under Negative**, after
  review found they were covered only by one general sentence. Review rounds 5 to 8 closed the
  modifier and delimiter limits; what remains open is the hardcoded three-file `HOLDER_SOURCES`
  list, a property without an explicit declared type, and textual type recognition. The
  no-parsed-class guard reports only a source that yields no holder at all; review round 8 proved it
  does not bound a single holder going missing, and closed the `STATE_HOLDER` modifier limit instead.
- **Review round 2 corrected the round-1 records, not the decisions.** `D-178` and `D-180` are
  unchanged; `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and
  `docs/adr/README.md` were not edited, and no decision ID, ADR file or mirror row was added,
  because every finding was a defect in an implementation of an accepted decision.
- **Two silent-stall classes and one known assertion flake were observed across review rounds 2, 3
  and 4; every failed job passed on re-run.** The `E1-14` Kotlin/Native assertion
  (`LocalOwnerAdoptionTriggerTest.aFuelEntryWriteTriggersAcquisitionAndAdoptionAfterAnEarlierAttemptFailed[iosSimulatorArm64]`)
  failed once on run `35338122967`; the Android/KMP host step was killed at its 10-minute cap with no
  test result on runs `35333547781`, `35336079709` and `35366069672` (first attempt), the silent
  stall classified in `docs/PROJECT_LOG.md` and left latent by `D-175`/`D-177`; and the same stall
  hit the Native step on run `35338122967`'s second attempt and on `35363599393`'s first,
  `35366069672`'s second and `35373955424`'s first. It also reached a **new site** on run
  `35376856797`: the `Run provider-free Android host tests` step of `provider-decoupling` was killed
  at its 8-minute cap with no test result and passed on re-run in 3m52s. So the stall is not specific
  to `shared-tests`, which widens what a re-run can hide. Nothing is a regression: this round's
  Kotlin changes are confined to `build-logic/convention/`, the exact two CI steps re-run locally
  with `--rerun-tasks` complete in about 3 minutes and 36 seconds, and every failed job passed on
  re-run. Fixing any is outside `E3-08`, but the owner should know all are reachable, because
  `E1-14` and `E1-17` being open is what makes a red required job ambiguous.
- **Review round 3 corrected an implementation of an accepted decision, not the decision.**
  `D-180` is unchanged; only its implementation and its four mirror rows were corrected, because
  the rows named only assertion 34 while the shipped check registers three assertions and no check
  can see that drift.
- **`docs/DECISION_BOARD.md` and `AGENTS.md` are the authoritative state.** This handoff preserves
  what was observed on 2026-09-18 at `story/E3-08-app-graph-and-firebase-wiring`.

## Human Review Gate

- Applied: `docs/CONTRACTS.md` and `docs/adr/**` are gated paths, and the Swift-facing API surface
  and the module dependency rules are gated topics. The pull request requires the owner's review
  before merge.
