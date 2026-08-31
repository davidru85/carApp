# Project Log - carApp

> Append-only record of what actually happened in this project: decisions taken, stories completed, problems found, direction changes. It is the fastest way for a new agent or a returning human to learn the current state without reading every normative document.
>
> This log is **history**, not rules. It never overrides `docs/SPECIFICATION.md`, `docs/CONTRACTS.md` or `docs/DECISION_BOARD.md`. If the log and a normative document disagree, the normative document wins and the discrepancy is escalated.

## How to use this log

**Read:** before starting any work, read the three most recent entries. They tell you what was just done, what is in flight and what is blocked.

**Write:** appending an entry is part of the Definition of Done (`AGENTS.md`). Add exactly one entry per completed story, per accepted or changed decision, and per significant event such as a scope change, an incident, a phase gate or a handoff to a different agent.

**Rules:**

- Newest entries at the top, immediately under this section.
- Never edit or delete a past entry. If something was wrong, append a new entry that corrects it and say which entry it corrects.
- Dates are absolute and ISO-8601 (`2026-08-17`), never relative.
- Written in technical English, like every other repository artifact.
- Keep entries short. Link to the PR, the story and the documents rather than re-explaining them.
- Do not put secrets, tokens, personal data or user data in this log.

## Entry template

```markdown
### YYYY-MM-DD — <Short title>

- **Type:** story | decision | milestone | incident | handoff | correction
- **Story / Decision:** `E0-00` / `D-13` / —
- **Author:** human name or agent identifier
- **What changed:** one or two sentences.
- **Why:** the reason, especially if a non-obvious option was rejected.
- **Documents touched:** `docs/CONTRACTS.md §9`, `docs/BACKLOG.md`, …
- **Verification:** commands run, tests added, gates passed.
- **Follow-ups / risks:** anything left open, with an owner if known.
```

---

## Entries

### 2026-08-31 — E1-08 Android Fuel Entry UI completed

- **Type:** story
- **Story / Decision:** `E1-08` / `D-92` through `D-98`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** implemented the reactive Android Fuel Entry list, create/edit form, live money
  derivation, two-step odometer warning, consumption summary and accessible row explanations and
  indicators. Fuel presentation now belongs to `:feature:fuel` and is exported through the sole
  Shared framework with stable names and signatures.
- **Why:** E1-08 makes the complete local Fuel Entry workflow usable on Android while preserving
  feature isolation, shared arithmetic, deterministic device-local calendar days and the staged
  boundaries owned by E1-10 and E3-03.
- **Documents touched:** D-92 through D-98 and ADR-0093 through ADR-0099 in the four decision
  mirrors, `docs/CONTRACTS.md`, current-state documents, `docs/BACKLOG.md` and
  `docs/handoff-E1-08.md`.
- **Verification:** RED, GREEN and REFACTOR are separate commits. The complete repository command,
  99.13% `:feature:fuel` line coverage, all 7 API 36 instrumented tests, the iOS framework link and
  exact generated-versus-golden header comparison pass; detailed commands are in the handoff.
- **Follow-ups / risks:** E1-09 must reuse D-96 on iOS, E1-10 replaces the temporary locale currency
  at the composition point, and E3-03 replaces D-95 constant `Idle`. Owner review is required
  before merge.

### 2026-08-31 — E1-07 second-round review corrections

- **Type:** correction
- **Story / Decision:** `E1-07`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** restored Android form drafts are republished into fresh state holders, fields
  edited before initial facts are preserved, and the Android odometer adapter now consumes the
  Kotlin-only domain range instead of duplicating it.
- **Why:** second-round review found that saveable UI text could diverge from the command persisted
  after process restoration or late edit facts, and that Android duplicated a shared validation
  boundary. The corrections add no decision, ADR or Swift ABI change.
- **Documents touched:** `docs/handoff-E1-07.md` and this log; D-84 through D-91 remain unchanged.
- **Verification:** both defects have RED, GREEN and REFACTOR commits. Targeted common and API 36
  tests pass; the complete repository and unchanged golden evidence is recorded in the handoff and
  PR #37.
- **Follow-ups / risks:** N-3 navigation lifetime cleanup and N-4 locale-independent assertion stay
  out of scope alongside the six pre-existing follow-ups. PR #37 remains owner-gated and MUST NOT
  be merged by the agent.

### 2026-08-31 — E1-07 owner code-review corrections

- **Type:** correction
- **Story / Decision:** `E1-07` / `D-90`, `D-91`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** creation holders remain creation-only after save; Swift holder caches now have
  keyed release; reviewed common enums have exact Objective-C and Swift names; Android form drafts
  survive configuration changes; and raw invalid odometer text remains visible with a localized
  error.
- **Why:** owner review found a cached-holder data-corruption path, unbounded Swift child scopes,
  configuration-dependent enum names, draft loss during activity recreation and silently discarded
  odometer input. D-90 and D-91 make the two Swift ABI changes explicit while the Android fixes stay
  host-private.
- **Documents touched:** `docs/CONTRACTS.md §15.3` and `§20.10`, D-90 / ADR-0091 and D-91 /
  ADR-0092 in all decision mirrors, `docs/BACKLOG.md` and `docs/handoff-E1-07.md`.
- **Verification:** each correction has RED, GREEN and REFACTOR commits. Focused common, shared,
  Android compilation and API 36 instrumented tests pass; complete repository, framework-header
  and protected-check evidence is recorded in the handoff and PR #37.
- **Follow-ups / risks:** E3-03 owns the staged `syncController()` and restoration error handling.
  Refreshing the loaded edit odometer, hermetic UI database setup, fully qualified D-28 detection
  and the missing delete/refresh presentation tests require future backlog assignment. PR #37
  remains owner-gated and MUST NOT be merged by the agent.

### 2026-08-30 — E1-07 owner-review database lifetime correction

- **Type:** correction
- **Story / Decision:** `E1-07` / `D-89`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `DatabaseFactory` now returns a `DatabaseHandle` that owns its SQLDelight
  database and driver. Kotlin and Swift application graphs release the handle exactly once when
  closed, and architecture checks guard the new lifetime boundary.
- **Why:** human review found that `DefaultAppGraph` discarded the created driver's ownership, so
  both direct and Swift-transitive close leaked the local database connection. D-89 keeps that
  closeable resource in `:core:database` without changing the Swift ABI.
- **Documents touched:** `docs/CONTRACTS.md §11.6`, `§20.3.2` and `§20.10`, D-89 and ADR-0090 in
  the four decision mirrors, `docs/BACKLOG.md` and `docs/handoff-E1-07.md`. Owner-ratified D-84
  through D-88 and ADR-0085 through ADR-0089 remain unchanged.
- **Verification:** the Android-host release tests failed before implementation, then direct and
  Swift-transitive release passed on Android host and iOS. Affected database, fake, feature,
  shared, architecture and contract checks pass with 90 accepted decision/ADR mirrors, and the
  generated Objective-C header remains byte-exact with its unchanged golden. Full repository and
  protected-CI evidence is recorded in the handoff.
- **Follow-ups / risks:** PR #37 remains owner-gated and MUST NOT be merged by the agent. The other
  review observations were explicitly left outside this correction.

### 2026-08-30 — E1-07 Android Vehicle UI completed

- **Type:** story
- **Story / Decision:** `E1-07` / `D-84` through `D-88`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** implemented the Compose Vehicle list, create/edit form and detail shell, shared
  Vehicle presentation state holders, reactive edit facts, Kotlin/Swift graph separation and the
  protected API 36 creation test. D-28 feature package rules now execute with one firing fixture
  per rule.
- **Why:** E1-07 makes the local Vehicle slice usable on Android while keeping validation in the
  repository, display copy native, provider types outside the shared framework and final sync
  ownership staged for E3-03.
- **Documents touched:** D-84 through D-88 and ADR-0085 through ADR-0089 in the four decision
  mirrors, current-state and CI records, `docs/BACKLOG.md` and `docs/handoff-E1-07.md`.
- **Verification:** Vehicle presentation and graph behavior were RED before implementation; the
  API 36 instrumented creation flow, Android-host and iOS tests, lint, detekt, coverage,
  architecture, contract, Android assembly and Shared framework header checks pass. Detailed
  commands and results are in the handoff.
- **Follow-ups / risks:** E1-08 owns Android Fuel Entry UI. E3-03 replaces D-88 constant `Idle`
  with the single final `SyncController`; E3-08 completes the staged AppGraph factories. Owner
  review is required before merge.

### 2026-08-29 — E1-06 second owner-review corrections applied

- **Type:** correction
- **Story / Decision:** `E1-06` / `D-82`, `D-83`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** aligned persisted odometer inconsistency with both validation branches, made
  bounded projections retain their highest canonical-order windows and replaced the quadratic list
  projection scan with one segment index per emission.
- **Why:** confirmed initial-odometer warnings lost their persisted trace, ascending SQL limits hid
  new rows after the memory cap, and repeated segment searches made list projection quadratic.
- **Documents touched:** `docs/CONTRACTS.md §3.1` and `§12`, D-82 and D-83 in the four decision
  mirrors, ADR-0083, ADR-0084 and `docs/handoff-E1-06.md`.
- **Verification:** four behavior tests failed first on Android host and iOS, then the complete
  repository command passed in 7 seconds with 602 actionable tasks. The Shared framework link
  passed in 4 seconds with 69 actionable tasks; detailed evidence is in the handoff.
- **Follow-ups / risks:** GitHub issue #36 owns the pre-existing Vehicle cascade Fuel Entry
  tombstone payload omission. PR #35 remains human-gated and MUST NOT be merged by the agent.

### 2026-08-29 — D-81 implementation constraints approved

- **Type:** decision
- **Story / Decision:** `E1-06` / `D-81`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the owner approved D-81 as implemented and made its dependency encapsulation
  and architecture-exception identity intentional constraints. The calendar boundary now also
  guards `:shared` production code, and §20.3 catalogues the public helper explicitly.
- **Why:** `kotlinx-datetime` must remain an implementation detail of `:core:common`, and matching
  the full helper package path prevents an unrelated file name from claiming the exception.
- **Documents touched:** `docs/CONTRACTS.md §20.3`, ADR-0082 and `docs/handoff-E1-06.md`.
- **Verification:** a new fixture failed before `:shared` entered the guarded scope. The real-tree
  extension found no production violations; focused checks and full evidence are recorded in the
  E1-06 handoff.
- **Follow-ups / risks:** none. The pull request remains human-gated and MUST NOT be merged by the
  agent.

### 2026-08-29 — E1-06 local Fuel Entry data completed

- **Type:** story
- **Story / Decision:** `E1-06` / `D-81`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** implemented owner-scoped SQLDelight Fuel Entry queries, neutral database access,
  transactional local CRUD, canonical mappers and outbox snapshots, list and consumption
  projections, production D-79 filtering and the D-81 UTC calendar-year fact producer.
- **Why:** E1-06 completes the offline Fuel Entry repository while keeping D-77 validation facts and
  D-38 mutation invariants inside one transaction and preserving the UI's local-only source of
  truth.
- **Documents touched:** D-81 and ADR-0082 in the four decision mirrors,
  `docs/CONTRACTS.md §2` and `§5`, current-state documents and `docs/handoff-E1-06.md`.
- **Verification:** 29 repository behavior tests and three D-81 helper tests were RED on both KMP
  targets; GREEN passed the focused Android-host and iOS suites. Lint, detekt, Kover, 16
  architecture rules and 82 decision/ADR mirrors pass; full repository evidence is in the handoff.
- **Follow-ups / risks:** E1-07 is next and owns Android Vehicle UI plus the D-28 package rules. The
  D-80 real-iPhone performance result remains with E4-03. Owner review is required before merge.

### 2026-08-28 — E1-05 consumption calculation implemented

- **Type:** story
- **Story / Decision:** `E1-05` / `D-78`, `D-79`, `D-80`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** implemented the pure full-to-full consumption use case, structural-first
  invalidation precedence, canonical segment and weighted-average arithmetic, cross-platform
  behavior tests and an isolated uninstrumented performance gate. Moved production repository
  filtering evidence verbatim to E1-06 and proved the use case does not filter its direct input.
- **Why:** E1-05 closes the deterministic R-3 functional core without pulling SQLDelight work from
  E1-06 or allowing Kover and debug Native compilation to invalidate performance evidence.
- **Documents touched:** D-78 through D-80 and ADR-0079 through ADR-0081 in the four decision
  mirrors, `docs/CONTRACTS.md §4`, `docs/BACKLOG.md`, `docs/versions-matrix.md`, current-state
  documents and `docs/handoff-E1-05.md`.
- **Verification:** 21 new tests were RED on both Android host and `iosSimulatorArm64`, then all 61
  Android-host and 58 iOS feature tests passed unchanged. The first standalone JVM median was
  3,392,708 ns with `javaAgents=0`; the enabled 100 ms gate passed at 3,568,521 ns and the optimized
  `iosArm64` device-test binary linked. Full repository evidence is in the handoff.
- **Follow-ups / risks:** E1-06 must add `FuelEntryRepositoryConsumptionFilterTest` for the moved
  production criterion. The D-80 optimized real-iPhone result remains open for E4-03: device `—`,
  date `—`; no simulator or linked-binary result substitutes for it.

### 2026-08-28 — E1-04 Fuel Entry domain completed

- **Type:** story
- **Story / Decision:** `E1-04` / `D-77`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** implemented canonical Fuel Entry commands and repository contract, pure
  create/update validation for R-1 and R-2, exact three-way monetary derivation, closed-bound
  validation, note normalization and the two-step odometer warning protocol. Added Android/JVM and
  iOS/Foundation currency evidence plus persistence-shape and floating-point source guards.
- **Why:** E1-04 establishes the independently testable domain boundary consumed by E1-06 while
  preserving database ownership of derived odometer state and canonical-triple-only persistence.
- **Documents touched:** D-77 and ADR-0078 in the four decision mirrors,
  `docs/CONTRACTS.md §5`, `§13`, `§20.5`, current-state documents and
  `docs/handoff-E1-04.md`.
- **Verification:** RED produced 33 expected validator failures on both Android host and
  `iosSimulatorArm64`; GREEN passed all 40 Android-host and 37 iOS tests; REFACTOR passed feature
  lint, detekt, 85% Kover, architecture and contract checks. The complete 600-task repository CI
  command passed.
- **Follow-ups / risks:** E1-05 is next. Before E1-06 constructs the D-77 context, the owner must
  select the exact `vehicle.createdAt - 20 years` representation; E1-06 must then prove fact
  loading, validation and mutation share one transaction.

### 2026-08-28 — E1-03 Vehicle data completed

- **Type:** story
- **Story / Decision:** `E1-03` / —
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** implemented the SQLDelight-backed local Vehicle repository, owner-scoped
  observation, deterministic mapping, transactional create/update/delete operations, shared local
  mutation sequencing and permanent-owner outbox coalescing. Replaced the E0-07 local adapter while
  retaining its staged remote push and recovery behavior.
- **Why:** E1-03 completes the offline Vehicle data boundary while preserving D-38 database-owned
  mutation invariants and D-76's single-transaction validation requirement.
- **Documents touched:** `docs/BACKLOG.md`, current-state documents and `docs/handoff-E1-03.md`.
- **Verification:** 26 focused E1-03 tests were RED before implementation; all 57 Vehicle tests pass
  on Android host and `iosSimulatorArm64`. Database and shared runtime regression tests, feature
  coverage, lint, detekt, architecture and contract checks pass; full repository evidence is in the
  handoff.
- **Follow-ups / risks:** `E1-04` is next. `E1-06` completes Fuel Entry persistence, `E1-07`
  completes presentation and package-level architecture checks, and later sync stories replace the
  remaining staged remote orchestration.

### 2026-08-28 — E1-02 human-review corrections applied

- **Type:** correction
- **Story / Decision:** `E1-02` / `D-76`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** recorded owner approval of D-76's selected option and corrected its rationale
  to the established functional-core / imperative-shell pattern. Added
  `ValidationError.EditNotAllowed` so a locked initial-odometer edit is not reported as a numeric
  range failure, and declared the public `canonicalVehicleName` signature.
- **Why:** `CalculateConsumption` already establishes the pure-function-from-prepared-facts
  boundary, while Vehicle-name uniqueness deliberately has no database unique index so remote
  duplicates remain ingestible. Only one local transaction containing fact loading, validation
  and mutation can guarantee the local rule.
- **Documents touched:** `docs/CONTRACTS.md §5`, `§13` and `§20.2`, D-76 and ADR-0077 in the four
  decision mirrors, and `docs/handoff-E1-02.md`.
- **Verification:** the correction RED test failed on `VALIDATION.OUT_OF_RANGE`; GREEN passed the
  `:core:common` Android-host tests and Vehicle tests on Android host and `iosSimulatorArm64`.
  Final repository checks are recorded in `docs/handoff-E1-02.md`.
- **Follow-ups / risks:** E1-03 must prove fact loading, validation and mutation share one local
  transaction. It must not add a `ValidatedCommand` key type or change `VehicleRepository`.

### 2026-08-28 — E1-02 Vehicle domain completed

- **Type:** story
- **Story / Decision:** `E1-02` / `D-76`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** completed the pure `:feature:vehicle` domain with canonical create/update
  commands, the `VehicleRepository` contract, exact Vehicle-name normalisation and create/update
  validators. Added D-76 to make validation consume immutable pre-write facts, and corrected the
  derived backlog wording so only `UpdateVehicleCommand` carries its canonical target ID.
- **Why:** E1-02 owns the business rules that must execute before local persistence. Immutable
  validation contexts keep those rules Kotlin-pure and testable without adding database-shaped
  query methods to the public repository contract.
- **Documents touched:** D-76 and ADR-0077 in the four decision mirrors, `docs/CONTRACTS.md §5`,
  `§13` and `§20.5`, `docs/BACKLOG.md`, current-state documents and `docs/handoff-E1-02.md`.
- **Verification:** the RED Android-host run executed 29 tests with 28 expected behavioral
  failures and the pre-existing FuelType inventory passing; GREEN and REFACTOR passed all 29 tests
  on Android host and `iosSimulatorArm64`, feature lint, detekt and the 85% Kover gate. The complete
  repository CI command passed 583 actionable tasks.
- **Follow-ups / risks:** E1-03 must load the D-76 validation facts and perform validation plus
  mutation in one local transaction, and it replaces the remaining E0-07 Vehicle runtime adapter.
  E1-07 still owns executable feature package-layer rules.

### 2026-08-27 — Post-E0-07 documentation handoff reconciled

- **Type:** handoff
- **Story / Decision:** `E0-07` / —
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** reconciled the complete documentation set after PRs #27 and #28. Current-state
  documents now identify completed stories, E1-02 as next, every remaining phase/story, the sole
  unenforced architecture area and the live development infrastructure. Dated closure notes keep
  historical handoffs accurate without presenting superseded follow-ups as open work.
- **Why:** the implementation and D-73 evidence were complete, but several derived documents still
  described branch protection, Kover, Firestore checks, Objective-C header verification,
  `testAppGraphDependencies` parity and the E3-06/E3-01/E0-07 sequence as future obligations.
- **Documents touched:** `AGENTS.md`, `README.md`, `docs/BACKLOG.md`, `docs/CONTRIBUTING.md`,
  `docs/DEFINITION.md`, `docs/TECHNICAL_PLAN.md`, `docs/SECURITY.md`,
  `docs/SECURITY_ADVISORY_REGISTER.md`, the App Check runbook, and the affected completed-story
  handoffs.
- **Verification:** `git diff --check`, `./gradlew contractCheck architectureCheck`, Functions
  `npm test` (10/10) and `npm run audit` all passed. The production-only audit still reports the
  seven accepted moderate GHSA-w5hq-g745-h8pq entries and no high or critical finding; the dynamic
  full-trigger test still proves the affected path unreachable.
- **Follow-ups / risks:** E1-02 is next. E1-07 still owns the feature package-level Konsist rules,
  the only architecture rules not yet executable. D-68/TD-01 retains its 2026-12-01 quarterly
  review; all other remaining work is enumerated by phase in `AGENTS.md` and `docs/BACKLOG.md`.

### 2026-08-27 — E0-07 walking skeleton completed

- **Type:** story
- **Story / Decision:** `E0-07` / `D-73`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** PR #27 delivered the Android/iOS walking skeleton and was owner-approved and
  merged. The final D-73 gate then observed both eligible Cloud Functions images disappear through
  the Artifact Registry cleanup policy without manual deletion.
- **Why:** E0-07 opens Phase 1 only after proving the complete native-to-local-to-Firebase path and
  observing the development cost-control lifecycle rather than treating configured retention as
  evidence of effective cleanup.
- **Documents touched:** `docs/handoff-E0-07.md`,
  `docs/runbooks/development-firebase-cost-controls.md`, `docs/BACKLOG.md`, `AGENTS.md`, `README.md`,
  `docs/DEFINITION.md`, and this log.
- **Verification:** the final inventory was empty at 2026-08-27T14:31:45Z after both images were
  present at 2026-08-27T13:30:04.831Z; the policy remained `DELETE` / `86400s` / `ANY`; billing
  remained enabled; `stopBilling` remained `ACTIVE` on Node.js 22. `git diff --check` and
  `./gradlew contractCheck` passed for the documentation closure.
- **Follow-ups / risks:** Artifact Registry exposed no separate `BatchDeleteVersions` audit entry
  at verification time, so the deletion timestamp is bounded by the two inventories. E1-02 is the
  next planned Phase 1 story.

### 2026-08-27 — D-75 amended to a graph-derived exception

- **Type:** correction
- **Story / Decision:** `E0-07` / `D-75`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** D-75 now derives the standalone Native-test exception from the transitive
  project graph rooted at the Firebase Auth and Firestore integrations. Its current resolution is
  those two modules, `:wiring:firebase` and `:composition:ios`.
- **Why:** the first full verification after the two-module decision failed in
  `:wiring:firebase`; the linker limitation propagates by transitive closure, so a static list
  encoded the wrong invariant and would fail again when the graph grows.
- **Documents touched:** `docs/adr/0076-exempt-firebase-standalone-native-tests.md`, the four
  decision mirrors, `docs/CONTRACTS.md §18`, `AGENTS.md`, and this log.
- **Verification:** the amended guard derives the qualifying set and will be mutation-tested for
  both missing and stale declarations before the full E0-07 verification resumes.
- **Follow-ups / risks:** `:composition:ios` has no tests and loses nothing today; adding tests to
  it enlarges the coverage loss and requires an explicit coverage review. TD-01 expiry signals are
  unchanged.

### 2026-08-27 — D-75 exact Firebase Native-test exception accepted

- **Type:** decision
- **Story / Decision:** `E0-07` / `D-75`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** exactly `:integration:firebase-auth` and `:integration:firebase-firestore` are
  exempt from standalone `iosSimulatorArm64Test` execution; Android-host unit tests remain, and
  real-host iOS coverage is stated path by path rather than treated as equivalent.
- **Why:** GitLive does not link its Apple dependencies transitively into standalone Kotlin/Native
  test binaries. CocoaPods, beta Kotlin SwiftPM import and a test-only XCFramework chain would each
  introduce a second or experimental dependency path inconsistent with the pinned stack.
- **Documents touched:** `docs/adr/0076-exempt-firebase-standalone-native-tests.md`, the four
  decision mirrors, `docs/adr/0066-pin-firebase-apple-to-gitlive-bindings.md`, TD-01, and this log.
- **Verification:** the E0-07 CI guard will assert the complete exemption set in both directions;
  Android-host tests and the documented XCUITest paths remain required.
- **Follow-ups / risks:** TD-01 reviews GitLive issue #499 and stable compatible Kotlin SwiftPM
  import quarterly; a GitLive/Firebase Apple compatibility upgrade triggers joint D-65/D-75 review.

### 2026-08-25 — D-65 Firebase Apple compatibility pin accepted

- **Type:** decision
- **Story / Decision:** `E0-07` / `D-65`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Firebase Apple SDK 11.8.0 is pinned exactly for direct iOS integration with
  GitLive 2.6.0.
- **Why:** GitLive's Apple cinterop bindings were generated against 11.8.0, and mixing them with a
  different native SDK can compile successfully but fail at runtime.
- **Documents touched:** `docs/adr/0066-pin-firebase-apple-to-gitlive-bindings.md`, the four
  decision mirrors, `docs/versions-matrix.md`, the E0-07 handoff and this log.
- **Verification:** `contractCheck` verifies decision/ADR alignment; the E0-07 Apple build and
  native-path tests will prove the pin in use.
- **Follow-ups / risks:** Upgrade GitLive and Firebase Apple together once GitLive publishes
  bindings for a supported newer Firebase Apple SDK; do not change either side independently.

### 2026-08-25 — D-64 anonymous lifecycle story split accepted

- **Type:** decision
- **Story / Decision:** `E0-07` / `D-64`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** E0-07 retains only real anonymous auth and the minimal Vehicle local/remote
  path; E2-02, E2-04, E2-07, E3-10, E3-11 and E3-12 own the remaining lifecycle behavior.
- **Why:** the owner selected reviewable concern-specific PRs and moved cross-device evidence to
  the first point where permanent auth and complete sync coexist.
- **Documents touched:** `docs/adr/0065-split-anonymous-lifecycle-delivery.md`, the four decision
  mirrors, `docs/BACKLOG.md`, the E0-07 handoff and this log.
- **Verification:** `contractCheck` reports 65 aligned decisions and ADRs.
- **Follow-ups / risks:** E3-12 remains a human-gated permanent-account Android/iOS recovery proof.

### 2026-08-25 — D-63 owned user-data cleanup accepted

- **Type:** decision
- **Story / Decision:** `E3-10`, `E3-11` / `D-63`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** an idempotent deletion service and executable data-location registry serve
  user-requested deletion, direct collision cleanup and native anonymous cleanup; only
  `onAnonymousUserDeleted` may use Cloud Functions 1st gen.
- **Why:** Firebase Extensions has an externally imposed 2027-03-31 management sunset, while a
  narrow owned trigger debt is versioned and migratable on the project's schedule.
- **Documents touched:** `docs/adr/0064-own-user-data-cleanup-service.md`, the four decision mirrors,
  `docs/CONTRACTS.md §11.5`, `docs/TECHNICAL_PLAN.md §13` (`TD-01`), `docs/BACKLOG.md` and this log.
- **Verification:** `contractCheck` reports 65 aligned decisions and ADRs; TD-01 names the exact
  migration surface, owner, first review and quarterly cadence.
- **Follow-ups / risks:** David Ruiz reviews TD-01 first on 2026-12-01 and quarterly thereafter.

### 2026-08-25 — D-62 anonymous sign-in benefit timeline accepted

- **Type:** decision
- **Story / Decision:** `E2-07` / `D-62`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** foreground retention notices use elapsed days 1, 3, 8 and 18, anchor to the
  Firebase creation timestamp and collapse missed notices to the highest due index.
- **Why:** the owner selected a deterministic, non-blocking timeline that warns before native
  cleanup without replaying an inactive user's prompt backlog.
- **Documents touched:** `docs/adr/0063-anonymous-sign-in-benefit-reminders.md`, the four decision
  mirrors, `docs/CONTRACTS.md §11.3`, `docs/BACKLOG.md` and this log.
- **Verification:** `contractCheck` reports 65 aligned decisions and ADRs; E2-07 lists the seven
  required time-boundary tests.
- **Follow-ups / risks:** the physical persistence location is deliberately left to E2-07 intake;
  choosing it is a separate implementation decision if the existing contract does not force it.

### 2026-08-25 — D-61 current anonymous snapshot precedence accepted

- **Type:** decision
- **Story / Decision:** `E2-04` / `D-61`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** after explicit destructive confirmation, the current anonymous-session snapshot
  replaces pre-existing permanent-account data through a resumable flow; cancellation changes
  nothing.
- **Why:** the owner chose the current device's active data over the older account and rejected both
  automatic merge and silent data loss.
- **Documents touched:** `docs/adr/0062-current-anonymous-data-wins-linking-collision.md`, the four
  decision mirrors, `docs/SPECIFICATION.md §7 F-4`, `docs/CONTRACTS.md §11.3`, `docs/BACKLOG.md` and
  this log.
- **Verification:** `contractCheck` reports 65 aligned decisions and ADRs; the story now requires
  interruption-boundary and idempotent-resume tests.
- **Follow-ups / risks:** E2-04 depends on E3-11 so orphan cleanup exists before collision delivery.

### 2026-08-25 — D-60 anonymous identity portability corrected

- **Type:** decision
- **Story / Decision:** `E0-07`, `E2-02`, `E3-12` / `D-60`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** an unlinked anonymous Firebase identity is device-bound, native Identity
  Platform cleanup uses the fixed 30-day eligibility threshold, and only a linked permanent
  provider enables new-device recovery.
- **Why:** Firebase exposes no supported portable anonymous credential, so the original anonymous
  cross-device walking-skeleton promise was not implementable safely.
- **Documents touched:** `docs/adr/0061-anonymous-identity-is-device-bound.md`, the four decision
  mirrors, the scope, auth, backup, backlog and E0-07 handoff records, and this log.
- **Verification:** `contractCheck` reports 65 aligned decisions and ADRs; repository-wide searches
  leave no current E0-07 clean-second-device acceptance claim.
- **Follow-ups / risks:** E2-02 enables native cleanup; E3-12 proves permanent-account recovery.

### 2026-08-25 — D-59 explicit AppProviders port accepted

- **Type:** decision
- **Story / Decision:** `E0-07` / `D-59`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `AppProviders` is defined as explicit typed properties for every graph
  dependency except `isDebugBuild`, which `buildAppGraph` applies directly.
- **Why:** the owner selected the compile-time-visible provider boundary over an opaque dependency
  factory or a Firebase-only port that would leave platform construction unresolved.
- **Documents touched:** `docs/adr/0060-explicit-app-providers-port.md`, the four decision mirrors,
  the E0-07 handoff and this log.
- **Verification:** `contractCheck` reports 60 aligned decisions and ADRs.
- **Follow-ups / risks:** E0-07 must prove provider parity on Android host and Kotlin/Native.

### 2026-08-25 — D-58 iOS composition framework ownership accepted

- **Type:** decision
- **Story / Decision:** `E0-07` / `D-58`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** a thin `:composition:ios` module becomes the sole producer of the `Shared`
  framework and Swift graph factory; `:shared` remains provider-free and no longer produces a
  framework.
- **Why:** the owner selected an umbrella composition root to avoid the unavoidable
  `:shared`/`:wiring:firebase` Gradle cycle without global registration or duplicate
  Kotlin/Native runtimes.
- **Documents touched:** `docs/adr/0059-ios-composition-owns-shared-framework.md`, D-2's
  supersession record, the four decision mirrors, the E0-07 handoff and this log.
- **Verification:** `contractCheck` reports 60 aligned decisions and ADRs.
- **Follow-ups / risks:** E0-07 must move SKIE, Xcode embedding and header generation to the new
  composition module while retaining Swift's `import Shared`.

### 2026-08-24 — E3-01 Firestore security rules completed

- **Type:** story
- **Story / Decision:** `E3-01` / `D-46` through `D-52`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** added exact closed-schema Firestore rules, an empty composite-index
  configuration, 154 emulator tests and protected CI execution with a reproducible official test
  stack.
- **Why:** the first application Firestore client must use fully reviewed owner isolation and
  schema validation rather than temporary remote rules.
- **Documents touched:** `docs/handoff-E3-01.md`, the Firestore contract and story records, the
  D-46 through D-52 decision records and this log.
- **Verification:** 154 emulator tests and the complete local Gradle CI command passed; the delta
  query paginated with tombstones and without a composite index.
- **Follow-ups / risks:** E0-07 must add executable disabled-persistence client configuration;
  Firebase CLI retains the D-52 moderate development-tool-only audit residual.

### 2026-08-24 — D-52 Firebase CLI audit residual accepted

- **Type:** decision
- **Story / Decision:** `E3-01` / `D-52`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** retained Firebase CLI 15.28.1 with its five moderate transitive audit findings.
- **Why:** the forced remediation would replace the accepted CLI with a breaking major version;
  the affected dependency tree is development-only and install scripts are disabled.
- **Documents touched:** `docs/adr/0053-retain-firebase-cli-with-moderate-audit-residual.md`,
  `docs/SECURITY.md` and the four decision mirrors.
- **Verification:** `npm audit --json` reports no high or critical finding; clean install, emulator
  tests and the full local CI command pass.
- **Follow-ups / risks:** re-evaluate on the next CLI update or any high/critical advisory.

### 2026-08-24 — D-51 dependency install scripts disabled

- **Type:** decision
- **Story / Decision:** `E3-01` / `D-51`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** repository npm installs now set `ignore-scripts=true`.
- **Why:** the rules toolchain needs no dependency lifecycle script, so disabling them reduces
  supply-chain execution without weakening the tests.
- **Documents touched:** `docs/adr/0052-disable-npm-dependency-install-scripts.md`, `.npmrc` and the
  four decision mirrors.
- **Verification:** a clean `npm ci` and all 154 emulator tests pass with scripts disabled.
- **Follow-ups / risks:** a future package requiring a lifecycle script needs an explicit decision.

### 2026-08-24 — D-50 first delta page uses timestamp-only boundary

- **Type:** decision
- **Story / Decision:** `E3-01` / `D-50`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the first delta page starts at `overlapSince`; later pages start after the full
  `(updatedAt, documentId)` cursor.
- **Why:** the pinned Firebase SDK rejects an empty document-ID cursor while the timestamp-only
  first boundary preserves the required overlap semantics.
- **Documents touched:** `docs/adr/0051-firestore-first-page-cursor-is-timestamp-only.md`,
  `docs/CONTRACTS.md` and the four decision mirrors.
- **Verification:** emulator tests prove first and later page boundaries, stable tie-breaking,
  tombstone inclusion and complete pagination.
- **Follow-ups / risks:** E3-02 must implement this exact query contract.

### 2026-08-24 — D-49 exact MVP Firestore schema version accepted

- **Type:** decision
- **Story / Decision:** `E3-01` / `D-49`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Firestore rules accept exactly `schemaVersion == 1` for both document types.
- **Why:** accepting arbitrary positive versions would admit payload semantics this client cannot
  interpret safely.
- **Documents touched:** `docs/adr/0050-mvp-firestore-schema-version-is-exact.md`,
  `docs/CONTRACTS.md` and the four decision mirrors.
- **Verification:** emulator tests accept version 1 and reject versions 0 and 2.
- **Follow-ups / risks:** any remote schema evolution requires a superseding decision and rules.

### 2026-08-24 — D-48 walking skeleton owns client cache configuration

- **Type:** decision
- **Story / Decision:** `E3-01` / `D-48`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** E3-01 owns server rules and emulator proof; E0-07 owns the first real Firestore
  client and executable disabled-persistence proof on Android and iOS.
- **Why:** this keeps one implementation owner for provider configuration without adding a
  temporary client module to the rules story.
- **Documents touched:**
  `docs/adr/0049-walking-skeleton-owns-firestore-client-cache-config.md` and the four decision
  mirrors.
- **Verification:** E3-01 contains no application provider module; backlog acceptance assigns the
  client proof to E0-07.
- **Follow-ups / risks:** E0-07 cannot complete without the two-platform persistence evidence.

### 2026-08-24 — D-47 rules tests added to protected contract check

- **Type:** decision
- **Story / Decision:** `E3-01` / `D-47`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the Firestore emulator suite runs inside the existing required
  `contract-check` job.
- **Why:** it makes rule regressions merge-blocking without changing the nine-check branch
  protection contract.
- **Documents touched:** `docs/adr/0048-firestore-rules-run-in-contract-check.md`, CI configuration
  and the four decision mirrors.
- **Verification:** the named CI step installs the lockfile and runs the emulator suite.
- **Follow-ups / risks:** moving or renaming the protected job requires a superseding decision and
  branch-protection update.

### 2026-08-24 — D-46 official Firestore emulator test stack accepted

- **Type:** decision
- **Story / Decision:** `E3-01` / `D-46`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** pinned Node 22.22.3, Firebase CLI 15.28.1, Firebase JavaScript SDK 12.18.0,
  `@firebase/rules-unit-testing` 5.0.1 and `node:test`.
- **Why:** the official stack provides deterministic rule evaluation and real query behavior with
  the smallest additional test surface.
- **Documents touched:** `docs/adr/0047-firestore-rules-use-official-node-test-stack.md`,
  `docs/versions-matrix.md` and the four decision mirrors.
- **Verification:** exact versions are locked; clean install, 154 emulator tests and protected CI
  wiring pass.
- **Follow-ups / risks:** version changes require the normal decision and compatibility review.

### 2026-08-24 — E3-06 provider decoupling proof completed

- **Type:** story
- **Story / Decision:** `E3-06` / `D-39` through `D-45`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** replaced the provider-decoupling placeholder with an explicit conditional
  provider registry, a canonical Gradle-property exclusion mode, functional settings tests and a
  macOS CI proof covering Android host and `iosSimulatorArm64`.
- **Why:** the first Firebase integration must land only after the protected branch can prove that
  provider modules are removable without breaking the local and shared graph.
- **Documents touched:** `docs/handoff-E3-06.md`, `docs/BACKLOG.md`, `AGENTS.md`, `README.md`,
  `docs/DEFINITION.md`, the D-39 through D-45 decision records and this log.
- **Verification:** RED/GREEN Gradle TestKit tests passed; the provider-free Android host and
  `iosSimulatorArm64` tests passed; the complete local CI command passed.
- **Follow-ups / risks:** `E3-01` is next, followed by `E0-07`. Each new provider path must update
  the explicit registry and continue to pass both provider modes.

### 2026-08-24 — D-45 provider proof target coverage accepted

- **Type:** decision
- **Story / Decision:** `E3-06` / `D-45`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the single required `provider-decoupling` job now runs Android host and
  `iosSimulatorArm64` tests on macOS.
- **Why:** provider leakage can be target-specific, so Android/JVM coverage alone would not prove
  the supported Kotlin/Native graph remains provider-free.
- **Documents touched:** `docs/adr/0046-provider-proof-runs-jvm-and-kotlin-native.md` and the four
  decision mirrors.
- **Verification:** `contractCheck` matches D-45 to ADR-0046; the provider-free multiplatform
  command passes locally.
- **Follow-ups / risks:** splitting or renaming the job requires a superseding decision and a
  matching branch-protection update.

### 2026-08-24 — D-44 explicit provider registry accepted

- **Type:** decision
- **Story / Decision:** `E3-06` / `D-44`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Firebase provider modules use a closed path registry and are included only when
  their directories exist.
- **Why:** this is testable before provider modules exist, avoids empty Gradle projects and does not
  silently admit unknown provider directories.
- **Documents touched:** `docs/adr/0045-provider-modules-use-explicit-conditional-registry.md` and
  the four decision mirrors.
- **Verification:** functional settings tests prove exact inclusion, exclusion and missing-path
  behavior.
- **Follow-ups / risks:** every new provider module must receive an explicit reviewed registry row.

### 2026-08-24 — D-43 provider exclusion input accepted

- **Type:** decision
- **Story / Decision:** `E3-06` / `D-43`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** canonical settings consume `carapp.excludeFirebaseProviders=true` to omit the
  Firebase provider registry.
- **Why:** one reproducible build model avoids a duplicate or CI-generated settings graph.
- **Documents touched:** `docs/adr/0044-provider-exclusion-uses-gradle-property.md` and the four
  decision mirrors.
- **Verification:** functional settings tests evaluate the normal and excluded modes; CI invokes
  the accepted property.
- **Follow-ups / risks:** a separate provider-free settings file remains forbidden.

### 2026-08-24 — D-42 provider decoupling prerequisite accepted

- **Type:** decision
- **Story / Decision:** `E3-06` / `D-42`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the prerequisite order is `E3-06 -> E3-01 -> E0-07`.
- **Why:** provider decoupling must be executable before the first Firebase integration appears,
  while keeping one owning story per pull request.
- **Documents touched:** `docs/adr/0043-provider-decoupling-precedes-first-integration.md` and the
  four decision mirrors.
- **Verification:** the backlog dependency chain and repository status expose the accepted order.
- **Follow-ups / risks:** no provider integration may land before E3-06 is merged.

### 2026-08-24 — D-41 development Firebase key restriction accepted

- **Type:** decision
- **Story / Decision:** `E0-07` / `D-41`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the development Android Firebase API key will be restricted to the debug app ID
  and the owner's current local debug signing certificate; the iOS key will use the debug bundle
  identifier restriction.
- **Why:** this secures client configuration in the public repository without creating another
  private signing key.
- **Documents touched:**
  `docs/adr/0042-development-firebase-key-uses-local-debug-certificate.md` and the four decision
  mirrors, plus `docs/SECURITY.md` and `docs/identifiers.md`.
- **Verification:** `contractCheck` matches D-41 to ADR-0042; E0-07 owns verification of the cloud
  restriction before client configuration is committed.
- **Follow-ups / risks:** a new development machine requires an explicit additional certificate
  fingerprint; no fingerprint or key value may enter repository documentation.

### 2026-08-24 — D-40 Firestore rules prerequisite accepted

- **Type:** decision
- **Story / Decision:** `E3-01` / `D-40`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `E3-01` must complete before the `E0-07` walking skeleton.
- **Why:** the first real mobile Firestore write must use the complete reviewed schema rules rather
  than temporary or cloud-only rules.
- **Documents touched:** `docs/adr/0041-firestore-rules-precede-walking-skeleton.md` and the four
  decision mirrors.
- **Verification:** the backlog places E3-01 before E0-07; E3-01 owns the emulator evidence.
- **Follow-ups / risks:** E0-07 cannot start until E3-01 is merged.

### 2026-08-24 — D-39 walking-skeleton entity slice accepted

- **Type:** decision
- **Story / Decision:** `E0-07` / `D-39`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** E0-07 will use a minimal contract-valid `Vehicle` slice whose edited proof value
  is the vehicle name.
- **Why:** it proves the final local and remote schema without introducing a temporary collection or
  absorbing the complete vehicle feature stories.
- **Documents touched:** `docs/adr/0040-walking-skeleton-uses-minimal-vehicle.md` and the four
  decision mirrors.
- **Verification:** `contractCheck` matches D-39 to ADR-0040; E0-07 owns the two-device vehicle
  round-trip evidence.
- **Follow-ups / risks:** E1-02 and E1-03 will replace or extend the deliberately narrow adapter.

### 2026-08-24 — E1-01 core database completed

- **Type:** story
- **Story / Decision:** `E1-01` / `D-36`, `D-37`, `D-38`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** added `:core:database` with SQLDelight schema v1, typed asynchronous queries,
  AndroidX bundled SQLite persistence on Android and iOS, a transaction facade for database-owned
  read-model invariants, and an executable direct-mutation boundary.
- **Why:** E1-01 opens Phase 1 and supplies the local source of truth required by the E0-07 walking
  skeleton while preserving the exact SQLite constraints and recomputation contracts.
- **Documents touched:** `docs/handoff-E1-01.md`, `docs/BACKLOG.md`, `AGENTS.md`, `README.md`,
  `docs/DEFINITION.md`, the D-36 through D-38 decision records and this log.
- **Verification:** full Gradle CI command passed with Android host and `iosSimulatorArm64` tests;
  file-backed close/reopen tests passed on both platforms; the ARM64 shared framework and iOS app
  built successfully with Xcode.
- **Follow-ups / risks:** E0-07 is next and must exercise this database through both real app
  composition paths. New entity mutations must extend the facade and its architecture fixture;
  future schema versions require committed `.sqm` migrations and populated migration tests.

### 2026-08-24 — D-38 database transaction facade accepted

- **Type:** decision
- **Story / Decision:** `E1-01` / `D-38`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** synchronized entity writes are routed through a Kotlin/SQLDelight `DatabaseMutations` facade in `:core:database`; direct generated entity-mutation calls outside that module are forbidden.
- **Why:** the facade can capture pre-write state, apply the exact de-duplicated recompute set and notify SQLDelight observers inside one transaction. SQLite triggers obscure pre/post successor behavior and do not reliably expose indirect table changes to observed queries.
- **Documents touched:** `docs/adr/0039-database-mutations-use-transaction-facade.md`, `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md`, `docs/adr/README.md`, `AGENTS.md`, `docs/handoff-E1-01.md`, and this log.
- **Verification:** `contractCheck` must report 39 decisions with matching statuses; `E1-01` owns RED/GREEN recomputation tests and the direct-mutation architecture fixture.
- **Follow-ups / risks:** every new synchronized entity mutation must extend both `DatabaseMutations` and the architecture rule; pull and local-owner adoption entry points must preserve supplied mutation sequences.

### 2026-08-23 — D-37 ARM64-only iOS targets accepted

- **Type:** decision
- **Story / Decision:** `E1-01` / `D-37`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Kotlin Multiplatform support is limited to `iosArm64` and `iosSimulatorArm64`; the unlinked `iosX64` target is removed from the shared conventions and framework.
- **Why:** the complete bundled-SQLite stack accepted by `D-36` publishes no Intel-simulator variants, while the application and CI already build only ARM64 iOS paths. A target-specific driver would defeat the accepted single-engine guarantee.
- **Documents touched:** `docs/adr/0038-supported-ios-targets-are-arm64.md`, `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md`, `docs/adr/README.md`, `docs/versions-matrix.md`, `AGENTS.md`, `docs/handoff-E1-01.md`, and this log.
- **Verification:** `contractCheck` must report 38 decisions with identical IDs and statuses; `E1-01` owns the target removal and full Android, Kotlin/Native and iOS application verification.
- **Follow-ups / risks:** Intel Macs and x86_64 simulators are unsupported. Reintroducing `iosX64` requires a complete compatible dependency set, application linking, CI verification and a decision superseding `D-37`.

### 2026-08-22 — D-36 SQLDelight with AndroidX bundled SQLite accepted

- **Type:** decision
- **Story / Decision:** `E1-01` / `D-36`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `D-1` was superseded. The local database now uses SQLDelight 2.3.2 with the SQLite 3.24 dialect, `sqldelight-androidx-driver` 0.2.1 and AndroidX bundled SQLite 2.7.0 on Android and iOS.
- **Why:** Room 3 KMP could not represent the mandatory table-level `CHECK` constraints as one generated schema, while SQLDelight's official Android driver would execute against SQLite 3.18 on API 26 and could not run the exact SQLite 3.24 outbox UPSERT. The accepted adapter preserves the committed SQL, `minSdk 26` and one bundled SQLite implementation across platforms.
- **Documents touched:** `docs/adr/0037-local-database-sqldelight-androidx-sqlite.md`, `docs/adr/0002-local-database-room-kmp.md`, `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/versions-matrix.md`, `AGENTS.md`, `README.md`, `docs/DEFINITION.md`, and this log.
- **Verification:** temporary compatibility build generated and compiled the exact SQL for Android and `iosSimulatorArm64`; `contractCheck` reports 37 decisions with identical IDs and statuses across all five sources.
- **Follow-ups / risks:** `E1-01` owns execution tests on Android and Kotlin/Native, the Android host-test SQLite artifact substitution, and the full repository verification. The third-party adapter remains confined to `:core:database`.

### 2026-08-21 — Documentation brought level with the built system for handover

- **Type:** milestone
- **Story / Decision:** — (no backlog story; handover readiness, owner-directed)
- **Author:** Claude Opus 5, on behalf of David Ruiz
- **What changed:** `AGENTS.md` gained a `## Repository State` section as its second section, after Rule 0: which modules exist and which deliberately do not, the three-line template for creating a module, the exact command CI runs, a table of what each check proves, what is enforced on `main`, what is not yet enforced with the story that owns each gap, and a pointer to the per-story handoffs. The Document Map now lists `docs/handoff-*.md`. `README.md` gained a `Build and verify` section and an accurate status line. `docs/CONTRIBUTING.md` gained a "Before Opening a Pull Request" section and now states that `main` is protected, names the nine required checks, and says plainly that administrator bypass is an escape hatch and not a workflow. Stale claims were corrected: `README.md` said "There is no CI yet; `E0-05` creates it", `docs/DEFINITION.md` said "The repository is greenfield: there is no product code yet", and both still listed a "Phase 0.5" for the walking skeleton that `D-30` had already folded into Phase 1.
- **Why:** the project is handing over to another agent. An incoming agent reads `AGENTS.md` and `README.md` first, and neither mentioned — once — how to build the project, how to run a check, or that any of this existed. `AGENTS.md` was written when the repository had no code and had never been updated to describe the system that grew under it, so its instructions were complete about process and silent about the thing being built.
- **Documents touched:** `AGENTS.md`, `README.md`, `docs/DEFINITION.md`, `docs/CONTRIBUTING.md`, and this log.
- **Verification:** full suite green — `ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug testAndroidHostTest iosSimulatorArm64Test`. A grep sweep for "no product code", "there is no CI", "not configured yet", "Phase 0.5" and "Implementation starts with" now returns only the project log, where those statements are history and MUST NOT be edited.
- **Follow-ups / risks:** `## Repository State` is a snapshot and will drift like any snapshot. Two things limit the damage: the story that changes the module set or the check set is the story that updates it, and the section defers to `contractCheck` for the live list of what cannot be verified yet rather than repeating it. `docs/E0-01-READY-CHECK.md` is kept rather than deleted: `E0-01` predates the current handoff format and `docs/PROJECT_LOG.md` references the file, so removing it would leave a dangling reference in an append-only record. It is now listed in the Document Map so nobody mistakes it for a stray file.

### 2026-08-21 — Repository made public, branch protection activated, first CI run measured

- **Type:** decision
- **Story / Decision:** `D-34`, superseding `D-33`
- **Author:** Claude Opus 5, on behalf of David Ruiz
- **What changed:** the repository is now public, and the `D-31` branch protection was applied in the same change, as `D-33` required: the nine `docs/CONTRACTS.md §18` check names, a required pull request, no force pushes, no branch deletion, administrator enforcement off. `D-33` is `Superseded`. `docs/SECURITY.md` and `docs/identifiers.md` were updated, and `E0-07` gained an acceptance criterion requiring the Firebase API keys to be restricted **before** `google-services.json` or `GoogleService-Info.plist` is committed. The `objc-header-golden-check` job moved from `macos-latest` to `ubuntu-latest`, with `E0-07` required to move it back.
- **Why:** two problems shared one solution. Branch protection is unavailable to a private repository on the GitHub Free plan — both `/branches/main/protection` and `/rulesets` returned `403` — so `§18` was unsatisfiable and a red pull request could be merged. Separately the account exhausted its Actions minutes. The first CI run of this repository cost about **115 billed minutes**, of which 100 came from three macOS jobs, because GitHub bills macOS at ten times wall-clock with a one-minute minimum; at that rate a 2,000-minute allowance is roughly 17 runs a month. Public repositories get free standard runners and can use branch protection. The repository was checked before publishing: no `google-services.json`, no `GoogleService-Info.plist`, no keystore, no private key and no API key is committed, and `E0-07` is the story that introduces them — so this was the cheapest moment to publish.
- **Documents touched:** `docs/adr/0035-repository-public-and-branch-protection-active.md` (new), `docs/adr/0034-...` (now `Superseded`), `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/adr/README.md`, `docs/identifiers.md`, `docs/SECURITY.md`, `docs/BACKLOG.md` (`E0-07`), `.github/workflows/ci.yml`, and this log.
- **Verification:** `gh repo view` reports `visibility=PUBLIC`. `gh api repos/davidru85/carApp/branches/main/protection` lists the nine contexts with `enforce_admins: false`, `allow_force_pushes: false` and `allow_deletions: false`. The first CI run, on PR #21, finished with **all nine checks green**, including both macOS jobs, so the workflow works on GitHub runners as written. `contract-check` passes with the new decision set.
- **Follow-ups / risks:** the API-key restriction in `docs/SECURITY.md` is now a **precondition, not advice**: in a public repository anyone can read the keys the moment those files are committed, and only the package-name, bundle-id and signing-certificate restrictions keep them unusable elsewhere. `E0-07` MUST restrict them first and say so in its handoff, and MUST move `objc-header-golden-check` back to `macos-latest`. Administrator enforcement stays off, so the owner can still bypass a red build; that is deliberate on a single-maintainer repository. `shared-tests` and `ios-simulator-build` still run on macOS. Merging them was proposed while minutes were metered and the owner rejected it once the saving disappeared, recorded as `D-35` ([ADR-0036](adr/0036-ci-keeps-shared-tests-and-ios-build-separate.md)): they are complementary diagnostics, they run in parallel so separating them costs no wall-clock, and merging would hide the Kotlin/Native tests behind a check named for the iOS build.

### 2026-08-21 — Firestore database created in `europe-west1`; `D-33` defers branch protection

- **Type:** milestone
- **Story / Decision:** `D-13`, `D-32`, `D-33`
- **Author:** Claude Opus 5, on behalf of David Ruiz
- **What changed:** the Cloud Firestore API was enabled on `davidruiz-carapp-dev` with `gcloud services enable firestore.googleapis.com`, and the `(default)` database was created in **`europe-west1`** in **Native mode**. `gcloud firestore databases describe` confirms `europe-west1` and `FIRESTORE_NATIVE`. Separately, the owner decided the repository stays private for now, recorded as `D-33` ([ADR-0034](adr/0034-repository-stays-private-branch-protection-deferred.md)).
- **Why:** the Firebase CLI cannot enable a Google Cloud service API, so this step waited on `gcloud` being installed. The database was created now rather than inside `E0-07` because its location is **immutable** under `D-13`: creating it deliberately, verified, is safer than creating it as a side effect of the walking skeleton, where a wrong default would be permanent.
- **Documents touched:** `docs/adr/0034-repository-stays-private-branch-protection-deferred.md` (new), `docs/adr/0032-...` (points at `D-33`), `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/adr/README.md`, `docs/identifiers.md`, `docs/BACKLOG.md` (`E4-04`), and this log.
- **Verification:** `gcloud firestore databases describe --project davidruiz-carapp-dev` returns `projects/davidruiz-carapp-dev/databases/(default) europe-west1 FIRESTORE_NATIVE`. `contract-check` reports 34 decisions identical across all five sources.
- **Follow-ups / risks:** **`D-33` is the one open obligation and it has a trigger, not a reminder.** Branch protection MUST be applied in the same change that makes the repository public or moves it to a plan where protection is available; `E4-04` now fails if the repository is public without it, and `docs/identifiers.md` records the constraint beside the repository visibility. Until then CI reports but does not gate, so a red pull request can be merged and only discipline prevents it. The Firestore database currently has closed default rules; `E3-01` owns the real rules, and the emulator remains the only CI target — CI MUST NOT hold credentials for this project (`docs/identifiers.md`).

### 2026-08-21 — Phase 0 decision closure: `D-26` to `D-32` accepted, and Phase 0 closes

- **Type:** decision
- **Story / Decision:** `D-26`, `D-27`, `D-28`, `D-29`, `D-30`, `D-31`, `D-32`
- **Author:** Claude Opus 5, on behalf of David Ruiz
- **What changed:** the seven decisions raised by the Phase 0 stories were taken by the owner and recorded as ADR-0027 to ADR-0033, with rows in all four mirroring documents. `D-26` corrects the contradictory monetary golden row of `docs/CONTRACTS.md §2` to `0` and adds a genuine HALF_UP round-up row (`1_000` litres-scaled at `5` price-scaled is exactly 0.5 minor units and rounds to `1`). `D-27` moves `testAppGraphDependencies(...)` from `E0-03` to `E0-07`. `D-28` moves the three feature-layer package rules to `E1-07`, where Konsist will have a module to live in. `D-29` allows a contract type to be declared inline in its owning section rather than only in `§20`, rewords `§18` assertion 1 accordingly, rewrites the `§20` opener, and lifts two prose paragraphs out of a `kotlin` fence in `§20.9`. `D-30` moves `E0-07` to the start of Phase 1, immediately after `E1-01`. `D-31` requires all nine CI checks on `main`. `D-32` changes the development Firebase project ID to `davidruiz-carapp-dev`. The Firebase project was created with that ID. `docs/PHASE0_OPEN_DECISIONS.md` was deleted, as that document said it would be.
- **Why:** each decision existed because a Phase 0 story hit a contradiction that no agent may resolve alone. Two are worth restating. `D-30` exists because `E0-07` needs Room, which lives in `:core:database`, a module the Phase 0 preamble forbids and `E0-04` now enforces — so the walking skeleton could not be a Phase 0 story without punching an exception through the rule that guards Phase 0. `D-32` exists because Google Cloud project IDs are globally unique and `carapp-dev`, fixed by `D-22`, is held by another customer: `409 ALREADY_EXISTS` on create and `403 PERMISSION_DENIED` on `addFirebase`. Since `docs/identifiers.md` forbids an agent inventing an identifier, the replacement had to come from the owner.
- **Documents touched:** `docs/CONTRACTS.md §2`, `§18`, `§20`, `§20.9`; `docs/DECISION_BOARD.md`; `docs/SPECIFICATION.md §12`; `docs/TECHNICAL_PLAN.md §2`; `docs/adr/README.md`; `docs/adr/0027`–`0033` (new); `docs/identifiers.md`; `docs/BACKLOG.md`; `docs/PHASE0_OPEN_DECISIONS.md` (deleted); and this log. Code: `MonetaryArithmeticTest` and the `contract-check` assertion-1 implementation.
- **Verification:** `ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug testAndroidHostTest iosSimulatorArm64Test` — `BUILD SUCCESSFUL in 19s`. `contract-check` reports 33 decisions with identical IDs and statuses across all five sources, assertion 1 now passes, and the prose-in-fence report is clean. `firebase projects:list` shows `davidruiz-carapp-dev`.
- **Follow-ups / risks:** **two owner actions remain and neither can be done from the CLI.** (1) The Cloud Firestore API is not enabled on the new project, so the `europe-west1` database of `D-13` could not be created: `firebase firestore:databases:create` returns `403 Cloud Firestore API has not been used in project davidruiz-carapp-dev before or it is disabled`. The Firebase CLI cannot enable a Google Cloud service API; that needs `gcloud services enable firestore.googleapis.com` or one click in the console. (2) `D-31` was accepted but **could not be applied**: `carApp` is a private repository on the GitHub Free plan, and both `PUT /branches/main/protection` and `POST /rulesets` return `403 Upgrade to GitHub Pro or make this repository public`. Until that changes, every check runs and reports but nothing stops a red pull request from merging. The owner chooses between GitHub Pro, a public repository and advisory-only CI, and that choice is itself a decision to record.

### 2026-08-21 — `E0-05` Quality Tooling and CI completed; branch protection remains an owner action

- **Type:** story
- **Story / Decision:** `E0-05` (`docs/BACKLOG.md`)
- **Author:** Claude Opus 5, on behalf of David Ruiz
- **What changed:** `.editorconfig` (`ktlint_official`) and `detekt.yml` were committed at the root, with **no baseline file anywhere** and a CI step that fails if one appears. `carapp.quality` (ktlint + detekt) and `carapp.coverage` (Kover, with the `D-18` thresholds) are applied by the module convention plugins, so a new module cannot opt out. `contractCheck` implements the assertions of `docs/CONTRACTS.md §18`: 10 pass and 3 report `PENDING` with the story that unblocks them. `.github/workflows/ci.yml` defines the nine check names fixed by `§18`, unchanged.
- **Why:** everything before this story was advisory. Until a check fails a build, a rule is a sentence in a document. The `PENDING` status exists for the same reason: three assertions cannot run until `E0-07`, `E3-01` and `DEC-2` deliver their inputs, and silently skipping them would report coverage that does not exist.
- **Documents touched:** `docs/handoff-E0-05.md` (new), `docs/BACKLOG.md`, and this log. Code and config: `.editorconfig`, `detekt.yml`, `.github/workflows/ci.yml`, `build-logic/**`, `build.gradle.kts`, `gradle/libs.versions.toml`, lint fixes across `core/**` and `shared/**`, and a new `ArithmeticGuardsTest`.
- **Verification:** `ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug testAndroidHostTest iosSimulatorArm64Test` — `BUILD SUCCESSFUL in 23s`. This is the first story in which every quality box in the handoff can honestly be ticked. Two real defects were found while writing the checks: `koverVerify` caught `:core:model` at 82.6% against its 90% bound, which the new guard tests closed, and `contractCheck` assertion 5 found six interfaces named in the contract that appeared in no backlog story, which `docs/BACKLOG.md` now names.
- **Follow-ups / risks:** **CI has never actually run**; the first merge is its first real execution. **Branch protection for `main` is not configured** — it needs repository admin rights and the checks must run once before GitHub offers them by name, so it is an owner action (`DEC-6`), and until it is set a PR can merge red. `MagicNumber` is suppressed in the two arithmetic files with the reason in the file: those literals are the canonical formula of `§2`, and naming them would hide the one thing a reviewer must check. Assertion 1 accepts a declaration anywhere in `docs/CONTRACTS.md` rather than only in `§20`, because implemented literally it fails today — `Logger` is declared in `§17`, `AnalyticsTracker` in `§16.1`, `RemoteSyncSource` in `§10`, `AppGraphDependencies` in `§11.6`, the repositories in `§12` and the use cases in `§13`, while `§20` claims to hold every type; recorded as `DEC-4`. detekt 1.23.8 predates Kotlin 2.4.10, so its analysis is syntactic and type-resolution rules are off.

### 2026-08-21 — `E0-04` Architecture Guards completed, minus the feature-layer package rules

- **Type:** story
- **Story / Decision:** `E0-04` (`docs/BACKLOG.md`)
- **Author:** Claude Opus 5, on behalf of David Ruiz
- **What changed:** `carapp.architecture` registers an `architectureCheck` task on the root project whose rules are **generated from the dependency table of `docs/TECHNICAL_PLAN.md §4`** — the task parses that table, so editing it changes the check, and a table the parser cannot understand fails instead of being skipped. Module-graph rules cover forbidden edges, undeclared edges and forbidden library capabilities resolved to real coordinates; source rules cover the Phase 0 module set, SKIE outside `:shared`, feature-to-feature edges, `expect`/`actual` in `:core:crash`, `AppDatabase`/`DatabaseFactory` leaks, `Float`/`Double`, free-text `Logger` fields, logging from `:core:database`, `outbox.lastError` reads, read-model writes, `ConsumptionInvalidReason`/`SegmentResult` placement, `createAppGraph` in `:integration:*`, and unreferenced image-loading dependencies. 23 fixtures, one per rule, assert both that the offending shape is rejected and that the legal shape beside it is accepted.
- **Why:** the rules are written as pure functions over plain data rather than as checks that inspect real Gradle modules, because most of them protect `:core:sync`, `:core:auth`, `:core:database`, `:integration:*` and `:feature:*` — modules the Phase 0 preamble forbids creating and that `E0-04` is itself required to reject. A fixture that had to create the offending module could never exist for those rules. Fabricated modules prove each rule fires today and keep proving it when the real modules arrive.
- **Documents touched:** `docs/handoff-E0-04.md` (new), `docs/BACKLOG.md`, and this log. Code: `build-logic/convention/src/main/kotlin/.../architecture/**` and its tests (new), `build.gradle.kts`, `build-logic/convention/build.gradle.kts`. No normative document changed and no decision was taken.
- **Verification:** `architectureCheck` reports `14 rules from docs/TECHNICAL_PLAN.md §4, 8 modules` and passes on the real graph; `:build-logic:convention:test` runs 23 fixtures with 0 failures. The fixtures found and fixed two defects that would have made the check vacuous: the glob matcher used `Regex.escape`, which wraps the pattern in `\Q…\E` so `*` was never substituted and `:core:*` matched nothing, and the capability parser matched tokens exactly, so `:core:testing`'s "platform APIs in `commonMain` public API (…)" parsed to no rule at all. Both would have passed everything silently.
- **Follow-ups / risks:** **the three feature-layer rows of `§4` are not enforced** — feature `domain`, `data` and `presentation` are package-level rules inside one Gradle module, which `D-16` assigns to Konsist, and no `:feature:*` module exists to host them; recorded as `DEC-3` for the owner. Konsist is pinned by `E0-06` and still unused. The `:wiring:firebase` "product logic" rule needs a Kotlin declaration parser and the module itself, so it belongs with `E3-08`. The source scan is line-based and deliberately conservative: it catches the realistic mistake, not a determined workaround. The check is not wired into `check` or CI until `E0-05`.

### 2026-08-21 — `E0-08` `:core:analytics` Abstraction completed

- **Type:** story
- **Story / Decision:** `E0-08` (`docs/BACKLOG.md`)
- **Author:** Claude Opus 5, on behalf of David Ruiz
- **What changed:** `:core:analytics` was created with `AnalyticsTracker`, the 13-leaf closed `AnalyticsEvent` hierarchy, `SyncStatusCategory`, `ConversionFailureReason`, `DeletionFailureReason`, `AnalyticsUserProperties` and `CountBucket`, all matching `docs/CONTRACTS.md §20.9`, plus the two normative `AuthError` mappings as exhaustive extension functions and `CountBucket.ofCount` writing the exact bucket bounds in one place. `NoOpAnalyticsTracker` and `RecordingAnalyticsTracker` were added to `:core:testing`. The module depends only on `:core:common` and contains no Firebase, GitLive or Android type.
- **Why:** `AnalyticsTracker` is a mandatory member of `AppGraphDependencies` (`§11.6`), so the graph cannot be constructed or tested without it, which is why the abstraction is Phase 0 while the Firebase implementation is `E3-09`. The closed hierarchy is what makes the forbidden-payload rule of `§16.1` enforceable by the type system instead of by review: no leaf can carry a free-text `String`, so an exact odometer value or a note has nowhere to go.
- **Documents touched:** `docs/handoff-E0-08.md` (new), `docs/BACKLOG.md`, and this log. Code: `core/analytics/**` (new), `core/testing/**`, `settings.gradle.kts`. No normative document changed and no decision was taken.
- **Verification:** `:core:analytics` and `:core:testing` pass on both the Android host and `iosSimulatorArm64`. Closedness is enforced by an exhaustive `when` with no `else`, so adding, renaming or removing a leaf stops the test compiling. The opt-in tests assert the case an implementation is most likely to get wrong: enabling collection after events were dropped while disabled MUST NOT replay them.
- **Follow-ups / risks:** the criterion "a no-op `AnalyticsTracker` … is the default in `testAppGraphDependencies(...)`" cannot be closed while `DEC-2` is open, because that factory does not exist. The `SyncStatus -> SyncStatusCategory` mapping of `§20.9` is not implemented here: `SyncStatus` belongs to `:core:sync`, a Phase 3 module Phase 0 forbids creating, so the mapping and its connectivity-code edge case are owned by `E3-03`/`E3-09`, as is the `setUserProperties` call-cadence fixture.

### 2026-08-21 — `E0-03` Base Core Modules completed, with two contract questions open

- **Type:** story
- **Story / Decision:** `E0-03` (`docs/BACKLOG.md`)
- **Author:** Claude Opus 5, on behalf of David Ruiz
- **What changed:** `:core:model`, `:core:common`, `:core:crash` and `:core:testing` were created, implementing the Phase 0 canonical types of `docs/CONTRACTS.md §20` — the identifier, money and scaled-value types of `§20.0`, the named constants of `§20.0.1`, `Outcome` and its five extensions, the complete `AppError` taxonomy with all 44 stable codes, `Confirmation`, the platform abstractions of `§20.3`, `Logger` of `§17`, `CrashReporter` and its no-op, and deterministic fakes for every Phase 0 abstraction. The five canonical formulas of `§2` are implemented as exact integer arithmetic in `:core:model` and covered by every golden value in the document, with the average test additionally asserting that the distance-weighted result differs from the arithmetic mean of the rounded segments. Each module's build file is four lines or fewer, which is the first real evidence for the `E0-02` "no more than five lines" criterion.
- **Why:** these are the types every later story depends on, and `§20` exists precisely so two agents cannot produce two incompatible implementations. Writing them against the document leaf by leaf, with the codes pinned in a test, is what makes a later rename fail the build instead of silently breaking the Firestore rules and the log allowlist that refer to those codes as literals.
- **Documents touched:** `docs/handoff-E0-03.md` (new), `docs/BACKLOG.md`, and this log. Code: `core/model/**`, `core/common/**`, `core/crash/**`, `core/testing/**` (new), `settings.gradle.kts`, `build-logic/**`, `shared/build.gradle.kts`. No normative document changed and no decision was taken.
- **Verification:** every module passes on both the Android host and `iosSimulatorArm64`. The `Float`/`Double` ban is enforced by a source-scanning JVM host test, which was proven to fail on an injected `val temporaryOffender: Double` before being returned to green — a runtime assertion cannot detect a floating-point implementation, because it returns the right answer for most inputs and drifts only where nobody looks.
- **Follow-ups / risks:** **two acceptance criteria could not be met and are put to the owner.** `DEC-1`: `docs/CONTRACTS.md §2` golden row 3 expects `totalCostMinor = 1` for `litersScaled = 1`, `pricePerLiterScaled = 1`, EUR, but the formula in the same section — which that section says MUST be implemented literally — gives `0`, and the formula is the one that is right, since 0.001 L at 0.001 €/L is 0.0001 minor units and HALF_UP of 0.0001 is 0. The other three rows agree with the formula exactly. `DEC-2`: `testAppGraphDependencies(...)` cannot exist in Phase 0, because four of the 15 `AppGraphDependencies` members have types owned by `:core:database`, `:core:auth` and `:core:sync`, which the Phase 0 preamble forbids creating and which `E0-04` is required to enforce. Coverage stays unmeasured until `E0-05` applies Kover, so the Kover criterion of this story is not closed either. The fakes use `Dispatchers.Unconfined`, which will not survive `:core:sync` needing virtual time; `E3-03` should revisit `TestDispatcherProvider`.

### 2026-08-21 — `E0-02` Gradle Convention Plugins completed

- **Type:** story
- **Story / Decision:** `E0-02` (`docs/BACKLOG.md`)
- **Author:** Claude Opus 5, on behalf of David Ruiz
- **What changed:** `build-logic` was added as an included build with five class-based convention plugins — `carapp.kmp.library`, `carapp.android.application`, `carapp.compose`, `carapp.skie` and `carapp.room` — all reading `gradle/libs.versions.toml`, so no version literal exists in build logic. `carapp.kmp.library` derives each module's Android namespace from its Gradle path per `D-24`; `carapp.skie` refuses to apply itself to any module other than `:shared`, turning the `D-2` rule into a build failure instead of a review item; `carapp.room` fixes the schema directory so schema export cannot be quietly disabled. `:shared` and `:androidApp` were migrated onto them and the root build file stopped configuring modules.
- **Why:** every remaining Phase 0 and Phase 1 story creates modules — `docs/TECHNICAL_PLAN.md §3` plans 17 — and without convention plugins each one would repeat the KMP targets, the Android namespace, the SDK levels, the toolchain and the test wiring, which is exactly where drift starts. Class-based plugins were chosen over precompiled script plugins because they can read the version catalog directly and can refuse to apply themselves, which is what makes the SKIE rule enforceable.
- **Documents touched:** `docs/handoff-E0-02.md` (new), and this log. Build files: `build-logic/**` (new), `settings.gradle.kts`, `build.gradle.kts`, `shared/build.gradle.kts`, `androidApp/build.gradle.kts`, `gradle/libs.versions.toml`. No normative document changed and no decision was taken.
- **Verification:** `:androidApp:assembleDebug`, `:shared:testAndroidHostTest` and `:shared:iosSimulatorArm64Test` pass, and the iOS simulator app returns `** BUILD SUCCEEDED **` from `xcodebuild` on Xcode 26.6.
- **Follow-ups / risks:** the "no more than five lines per module" criterion has no instance inside this story, because the repository's only two modules are the iOS framework host and the Android app; `E0-03` provides the first four ordinary modules, each with a three-line build file, so the two stories should be reviewed together. `carapp.room` is written but applied to nothing until `E1-01`. `E0-04` should add an architecture rule asserting that no module other than `:shared` applies SKIE, so the rule survives someone bypassing the convention plugin. The convention plugins themselves have no tests.

### 2026-08-21 — `E0-06` ADRs, Version Matrix and Decision Board Validation completed

- **Type:** story
- **Story / Decision:** `E0-06` (`docs/BACKLOG.md`)
- **Author:** Claude Opus 5, on behalf of David Ruiz
- **What changed:** the decision records were validated and found already consistent — 24 ADRs for `D-0` to `D-23`, every ADR `## Status` equal to its board row, and an identical decision ID and status set across `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`; nothing needed correcting. Two new decisions were then taken during implementation and recorded properly: `D-24` derives every module's Android build namespace from its Gradle module path ([ADR-0025](adr/0025-module-android-namespaces.md)), and `D-25` pins `targetSdk` independently of `compileSdk` at 36 against 37 ([ADR-0026](adr/0026-targetsdk-separate-from-compilesdk.md)). `AGENTS.md` now makes that mandatory: any decision taken while implementing a story MUST get a decision ID, an ADR and rows in the four mirroring documents, in the same PR, because a decision recorded only in a handoff or in this log is history rather than authority. `README.md` and `docs/BACKLOG.md` were refreshed to the real state: Phase 0 in progress, `E0-01` and `E0-06` completed, no CI yet. The toolchain was then pinned: all 25 `TBD` cells of `docs/versions-matrix.md` now carry a concrete version and a "Backed by" citation, and every one of them is declared in `gradle/libs.versions.toml` and nowhere else. The canonical timestamp type is fixed as **`kotlin.time.Instant`** and guarded by a new test, `PinnedInstantPackageTest`, which resolves a kotlinx-datetime extension declared on that receiver so a relocation fails the build. The performance baselines gained their reference OS versions (Pixel 6a on Android 16, iPhone 12 on iOS 26). Pinning forced the toolchain to move as one set: Kotlin 2.0.21 → 2.4.10, KSP 2.0.21-1.0.28 → 2.3.11, Gradle 8.9 → 9.7.1, AGP 8.5.2 → 9.3.1, `compileSdk`/`targetSdk` 35 → 37, Compose BOM 2024.10.01 → 2026.08.00, coroutines 1.9.0 → 1.11.0; SKIE stays at 0.10.14, which supports Kotlin 2.4.10.
- **Why:** the story could not be satisfied by filling cells with the versions `E0-01` had left provisional. `D-1` requires Room 3 KMP, whose artifacts are `androidx.room3:room3-*` at 3.0.x; the current Compose BOM requires `compileSdk 37` and AGP 9.1.0 or higher; and `E0-01` had itself recorded AGP 8.5.2 and Gradle 8.9 as workarounds to revalidate here. Pinning the old set would have frozen the MVP on a deliberately stale Compose and left `D-1` unimplementable. AGP 9 then forced three build changes that are not optional: Kotlin support is built into AGP so `org.jetbrains.kotlin.android` is rejected, `com.android.library` is incompatible with the KMP plugin so `:shared` moved to `com.android.kotlin.multiplatform.library`, and that plugin creates no host test runner so `withHostTestBuilder` was added to keep the common tests running on the JVM as well as on Kotlin/Native. `kotlin.time.Instant` was chosen over the kotlinx-datetime 0.6.x compatibility artifact because 0.8.0 consumes the standard library type and the compat artifacts exist only to keep the old package alive.
- **Documents touched:** `docs/versions-matrix.md`, `docs/identifiers.md`, `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/adr/README.md`, `docs/adr/0025-module-android-namespaces.md` (new), `docs/adr/0026-targetsdk-separate-from-compilesdk.md` (new), `AGENTS.md`, `README.md`, `docs/BACKLOG.md`, `docs/handoff-E0-06.md` (new), and this log. `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `gradle.properties`, `build.gradle.kts`, `androidApp/build.gradle.kts`, `shared/build.gradle.kts` and one new test changed on the code side. No pre-existing decision status moved.
- **Verification:** TDD cycle in three pushes — red (`a186646`, 7 unresolved-reference errors), green (`21936bb`), documentation. `:shared:testAndroidHostTest` and `:shared:iosSimulatorArm64Test` both run `GreetingTest` (3) and `PinnedInstantPackageTest` (1) with 0 failures and 0 errors; `:androidApp:assembleDebug` succeeds; the iOS simulator app returns `** BUILD SUCCEEDED **` from `xcodebuild` on Xcode 26.6; no Gradle deprecation warnings. A baseline build was run before any change so that failures were attributable. After `D-25`, the merged Android manifest reports `targetSdkVersion="36"` and `minSdkVersion="26"`. A parity script over the four mirroring documents plus the ADR files reports 26 decisions in all five sources and `PARITY OK`. Requires human review before merge (gated path `docs/versions-matrix.md`; gated topic "technical stack or pinned versions").
- **Follow-ups / risks:** most pins are declared but unused — Room 3, Firebase, GitLive, Koin, Kermit, Turbine, Konsist, Kover, detekt and ktlint are first exercised by `E1-01`, `E0-07`, `E2-02` and `E0-05`, so each pin is only really proven by the story that consumes it; `D-17` explicitly asks for Turbine to be checked against the pinned coroutines version there. `E0-02` must write its convention plugins against the AGP 9 built-in-Kotlin model rather than the AGP 8 model `E0-01` used. `:shared` still carries its namespace as a literal: `D-24` says the value is derived and that no module build script should hold it, but nothing computes it yet, so `E0-02` MUST derive it from the Gradle project path and delete the literal. `D-25` leaves a `targetSdk` bump owed before release, owned by `E4-04`, which must review the runtime behaviour changes of the new level against the flows and the design assets. The `E0-01` gap where the Xcode project links only the `iosSimulatorArm64` framework, so an x86_64 simulator build fails, is still open and will matter when `E0-07` puts the simulator in CI. Nothing here is enforced by CI until `E0-05` exists.

### 2026-08-21 — Language rule promoted to Rule 0; the "owner wrote in English" loophole closed

- **Type:** correction
- **Story / Decision:** — (no backlog story; `AGENTS.md` governance change, owner-directed; corrects the 2026-08-19 entry "Language rule elevated to critical priority in `AGENTS.md`")
- **Author:** Claude Opus 5, on behalf of David Ruiz
- **What changed:** the language rule became `## Rule 0 - Language`, the first H2 section of `AGENTS.md`, ahead of the normative keywords. The top callout was rewritten as `RULE 0 — LANGUAGE. READ THIS BEFORE YOUR FIRST REPLY.` The section gained four new parts: (1) a "When Rule 0 applies" list stating that the rule governs every sentence addressed to the owner — replies, clarifying questions, plans, progress narration, checklist text, escalations, refusals, error explanations and the conversational summary accompanying a handoff — and that it is in force with no story open; (2) a **closed** "What does NOT authorise a reply in English" list whose first item is the owner writing their own message in English, followed by quoted English text, the system prompt and tooling, the repository itself being in English, the subject being technical, a previous agent having replied in English, the rule being absent after a context compaction, and the reply being short or urgent; (3) a mandatory self-check before the first token of every reply; (4) a four-step recovery protocol — switch immediately, correct in one sentence, do not re-send the previous reply translated, record the violation in the handoff. The override clause now requires an explicit, unambiguous instruction. `## Normative Language` was renamed `## Normative Keywords`, with a closing sentence separating rule *weight* from rule *language*. The Story Intake and Definition of Done bullets were rewritten to cite "Rule 0" by name, and the Definition of Done now requires a corrected violation to appear in the handoff under "Decisions Made".
- **Why:** the 2026-08-19 entry applied the obvious remedy — callout, section order, MUST weight, checklist echoes — and it did not work: the agent of this very session replied in English twice before the owner intervened. Restating the rule a fifth time was therefore not the fix. The diagnosis is that the previous wording, "Unless the owner states otherwise", was read as satisfied by the owner writing in English, which is what the owner does routinely; an agent mirroring the language of the incoming message could believe it was complying. Naming that case first in a closed list removes the inference. The rename of `## Normative Language` removes a second failure mode: two near-identical adjacent headings invited an agent scanning the document to treat the second as a duplicate of the first and skip it.
- **Documents touched:** `AGENTS.md`, and this log. No normative rule was added or changed: conversation in Spanish (es-ES) and repository artifacts in technical English were already the rule. This change restates its priority, closes the override loophole, defines its scope and adds a recovery protocol.
- **Verification:** `grep -n '^## ' AGENTS.md` confirms `## Rule 0 - Language` is the first H2 and `## Normative Keywords` the second. `grep -rn 'AGENTS.md#' .` confirms no document links the renamed anchor. A Spanish-token grep over `AGENTS.md` returns nothing, so the artifact itself stays in technical English. Requires human review before merge (gated path `AGENTS.md`; gated topic "MVP scope / quality rules").
- **Follow-ups / risks:** **this remains prose, and prose only binds an agent that has the file in context.** In this session `AGENTS.md` was not loaded until the agent opened it explicitly, two turns into the work. There is no `CLAUDE.md` at the repository root and `.claude/settings.local.json` holds only permissions, so nothing places Rule 0 in front of a Claude Code agent automatically. Rule 0 is also the only MUST in the repository with no enforcement mechanism, which contradicts `## Normative Keywords` ("MUST — enforced. A violation fails CI or blocks review"). Two mechanical measures were proposed to the owner and **declined**: a root `CLAUDE.md` pointer that Claude Code loads on every session, and a `UserPromptSubmit` hook in `.claude/settings.json` that injects Rule 0 on every turn — the only form that survives context compaction. The owner decided that Rule 0 lives in `AGENTS.md` and nowhere else, so an agent that never opens `AGENTS.md` is still unbound by it. If Rule 0 is violated again after this change, the remedy is not a fourth prose iteration; this entry and the 2026-08-19 one are the evidence that prose alone does not hold.

### 2026-08-21 — F-1 design pass applied to the live Figma file; dark-row clones were inheriting prototype links

- **Type:** correction
- **Story / Decision:** — (closes the follow-up of the 2026-08-20 entry, affects `E2-03`)
- **Author:** Claude Opus 5, on behalf of David Ruiz
- **What changed:** the Figma file was brought level with the scripts by running `02`, `07`, `13`, `16`, `15` in that order. Both welcome screens were rebuilt to the closed provider set, the dark row was regenerated, all 24 status bars reapplied, and the prototype re-wired to the provider buttons. Separately, `design/figma/13-dark-screen-row.figma.js` was fixed: it now strips prototype reactions from the screens it clones.
- **Why:** `clone()` copies reactions along with everything else, so once `15` had run, regenerating the dark row gave every dark screen live links into the **light** flow — a click on a dark screen would jump the viewer back to the light prototype, contradicting the documented rule that the dark row is a colour reference and not a second prototype. The defect was latent on the first build only because `13` happened to run before `15` ever existed; the re-run reversed that order and made it real, stripping 10 reactions per page.
- **Documents touched:** `design/figma/13-dark-screen-row.figma.js`, `design/figma/README.md`, and this log. No normative document changed.
- **Verification:** each script's return value was checked rather than the rendering alone — `phantomSignInPresent: false` (Android), `phantomPrimaryPresent: false` and `dividerRowPresent: false` (iOS), `reactionsStripped: 10` per page, 12 status bars replaced per page, 12 prototype links wired on Android and 13 on iOS. Screenshots of both rebuilt welcome frames confirm the button stacks and the native status bars.
- **Follow-ups / risks:** `03-android-home.figma.js` is still not re-runnable — it lacks the idempotence guard added to `02` and `07`, so running it would stack a duplicate `screen-home` at x=452. It is absent from the re-run order, so nothing depends on this today, but a replay on a fresh file would hit it.

### 2026-08-20 — Welcome screen sign-in options fixed to the closed provider set

- **Type:** decision
- **Story / Decision:** — (no backlog story; owner-directed correction of `F-1`, affects `E2-03`)
- **Author:** Claude Opus 5, on behalf of David Ruiz
- **What changed:** `docs/SPECIFICATION.md §7 F-1` now states that the welcome screen offers the platform's sign-in providers and "Continue without account" in a single step, that there MUST NOT be an intermediate provider-selection screen or a provider-less "Sign in" control, that the screen presents exactly two actions on Android and three on iOS, and that the MVP has no sign-in method beyond anonymous, Google and Apple. `docs/BACKLOG.md` `E2-03` gained two matching acceptance criteria. `README.md` §MVP Scope and `docs/DESIGN.md` §7 were updated to repeat the same rule. The design assets were then corrected to match: `design/figma/02-android-welcome.figma.js` and `07-ios-welcome.figma.js` dropped the generic sign-in button, `15-prototype-motion.figma.js` was re-wired to the provider buttons, and `design/stitch/` was regenerated from them.
- **Why:** both welcome screens shipped a provider-less "Iniciar sesión" button (`btn-signin-filled` on Android, `btn-primary` on iOS) that no product document backs. `AuthProvider` is a closed enum of `ANONYMOUS`, `GOOGLE`, `APPLE` (`docs/CONTRACTS.md §20.3`) and the only permanent sign-in intent is `startPermanentSignIn(provider)` (`§20.10`), so a provider-less button has no intent to invoke. `F-1` step 1 previously read "Welcome screen with 'Sign in' and 'Continue without account'", which also admitted a two-step welcome → provider-picker flow; the owner chose the one-step flow, because it needs no extra screen, matches `E2-03` as a single story, and serves principle P3 "No entry barrier". Email and password, email link, phone or one-time code and other SSO providers were never in the repository and are now stated as excluded rather than merely absent.
- **Documents touched:** `docs/SPECIFICATION.md §7 F-1`, `docs/BACKLOG.md` `E2-03`, `docs/DESIGN.md` §6 and §7, `README.md`, `design/figma/**`, `design/stitch/**`, and this log. `docs/CONTRACTS.md` needed no change: the contracts were already correct and the design contradicted them.
- **Verification:** `grep -rn "Iniciar sesión" design/figma/*.figma.js` returns nothing. Every welcome button maps to a concrete intent; the prototype triggers are the provider buttons rather than the removed ones. All 19 Figma scripts pass a syntax check. Requires human review before merge: `docs/SPECIFICATION.md` is a gated path and "authentication" is a gated topic (`AGENTS.md`).
- **Follow-ups / risks:** the Figma file itself is one pass behind the scripts; re-run `02`, `07`, `13`, `16`, `15` in that order (`design/figma/README.md`). Anonymous account conversion (`F-4`) still has no designed entry point in settings; recorded in `docs/DESIGN.md` §6 and owned by `E2-04`.

### 2026-08-19 — TDD commit and push workflow made a MUST

- **Type:** decision
- **Story / Decision:** — (no backlog story; governance change, owner-directed)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added the `### TDD commit and push workflow` subsection to `docs/SPECIFICATION.md §11`, making the per-phase commit-and-push sequence (red → green → refactoring → PR) a MUST for every TDD story. Each phase is a separate commit and a separate push; phases MUST NOT be combined in a single commit; the PR MUST contain the full cycle in order; the refactoring phase is skipped if no refactoring is needed. `AGENTS.md` Technical Rules gained an explicit reference to the workflow as a MUST unless the owner exempts a story explicitly.
- **Why:** the owner directed that, from this point forward, the TDD process must produce a commit and push per phase (red, green, refactoring) and a PR only after the cycle completes, so the version history reflects the TDD intent and each phase is independently reviewable.
- **Documents touched:** `docs/SPECIFICATION.md §11`, `AGENTS.md`, and this log. No backlog story acceptance criterion changed; the rule applies to all product-code stories going forward.
- **Verification:** `grep -n 'TDD commit and push workflow' docs/SPECIFICATION.md AGENTS.md` confirms the subsection and the cross-reference. Requires human review before merge (gated paths `AGENTS.md` and `docs/SPECIFICATION.md`; gated topic "MVP scope / quality rules").
- **Follow-ups / risks:** applies from the next product-code story onward. `E0-01` was committed before this rule existed and is exempt; the handoff declares the TDD exemption for the KMP scaffold.

### 2026-08-19 — E0-01 KMP Project Bootstrap completed

- **Type:** story
- **Story / Decision:** `E0-01` (`docs/BACKLOG.md:43`)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** created the KMP project skeleton with Android and iOS targets, `:shared` framework named `Shared` (canonical SPM module name), Android host app (`:androidApp`) using Compose, and iOS host app (`iosApp/`) using SwiftUI. The `Greeting` class in `commonMain` is consumed by both hosts — Android shows `Greeting().greet("Android")`, iOS shows `Greeting().greet(platform: "iOS")` via `import Shared`. `gradle/libs.versions.toml` is the single source of dependency versions with minimal build-essential pins (Kotlin 2.0.21, KSP, SKIE 0.10.14, AGP 8.5.2, Compose BOM, coroutines, Gradle 8.9, targetSdk 35); remaining versions are `TBD` for `E0-06`. Gradle wrapper, Kotlin DSL build scripts only, root `plugins` block declaring versions once. iOS Xcode project generated via `xcodegen` with `Shared.framework` (static) embedded and linked. `AndroidManifest.xml` has `android:allowBackup="false"` and no backup/settings-sync surface; no iOS entitlements file.
- **Why:** `E0-01` is the first implementation story and blocks all others. The skeleton proves both platforms consume `commonMain`, identifiers match `docs/identifiers.md` exactly, and no platform backup/settings-sync API surface exists.
- **Documents touched:** `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/`, `shared/build.gradle.kts`, `shared/src/commonMain/kotlin/com/ruizurraca/carapp/Greeting.kt`, `shared/src/commonTest/kotlin/com/ruizurraca/carapp/GreetingTest.kt`, `androidApp/build.gradle.kts`, `androidApp/src/main/AndroidManifest.xml`, `androidApp/src/main/java/com/ruizurraca/carapp/MainActivity.kt`, `iosApp/project.yml`, `iosApp/Info.plist`, `iosApp/carAppApp.swift`, `iosApp/ContentView.swift`, `iosApp/carApp.xcodeproj/`, `docs/E0-01-READY-CHECK.md` (preserved per owner request, to be deleted at story close), `docs/handoff-E0-01.md`, and this log. No normative document changed.
- **Verification:** `./gradlew :shared:allTests` → BUILD SUCCESSFUL (TDD: red phase confirmed `Unresolved reference 'Greeting'` before `Greeting.kt` existed, green after). `./gradlew :androidApp:assembleDebug` → BUILD SUCCESSFUL. `xcodebuild -project iosApp/carApp.xcodeproj -scheme carApp -destination 'platform=iOS Simulator,name=iPhone 17' -configuration Debug build` → BUILD SUCCEEDED; app installs and launches on simulator (PID 56281); binary contains `Shared.Greeting` symbol (`nm` output: `_$sSo14SharedGreetingCABycfC`). All 7 ACs verified. No human review gate (E0-01 is not gated; the Phase 0 gate is E0-07).
- **Follow-ups / risks:** version revalidation by E0-06 (Kotlin 2.0.21, AGP 8.5.2 below current stable 8.7.x, Gradle 8.9, SKIE 0.10.14 warns AGP > 8.5 untested). `xcodegen` is a brew dependency; `project.yml` is the source of truth and the `.xcodeproj` is committed. iOS framework path is hardcoded to `iosSimulatorArm64/debugFramework`; E0-07 MUST switch to XCFramework. `docs/E0-01-READY-CHECK.md` must be deleted when E0-01 closes. TDD exemption declared for KMP scaffold (native UI / wiring, no behavior unit); `Greeting` was written test-first.

### 2026-08-19 — Merged Figma runner added for the two unrun iOS scripts

- **Type:** milestone
- **Story / Decision:** — (no backlog story; non-normative design tooling)
- **Author:** Claude Opus 5 (Claude Code session), on behalf of David Ruiz
- **What changed:** added `design/figma/11-ios-forms-and-settings-merged.figma.js`, a generated merge of `09-ios-forms.figma.js` and `10-ios-settings.figma.js` that creates the same three iOS frames — `screen-vehicle-form`, `screen-fuel-form` and `screen-settings` — in **one** `use_figma` call instead of two. The merge wraps each source script in an async IIFE, so their identically-named top-level declarations (`page`, `F`, `ST`, `glass`, `icon`, `T`) do not collide, and combines the two return values. `design/figma/README.md` gained the script as row `11` of the status table, an explicit warning that running the merged script *and* the originals would duplicate the three frames, a note that `11` is generated and must be regenerated rather than edited, and a `Current blocker` section recording the state as checked on 2026-08-19. Committed as `2f89c39`; this log entry is the commit that follows it.
- **Why:** the redesign is two frames short of complete and blocked purely on Figma quota. The account is Starter tier with a View seat, which caps MCP usage at 20 tool calls per month; the quota is currently exhausted and every read tool returns the rate-limit paywall error. `use_figma` is not on Figma's rate-limit exemption list — only `add_code_connect_map`, `generate_figma_design` and `whoami` are — so the remaining work cannot run until the monthly quota resets or the plan is upgraded to Pro with a Full or Dev seat. On a 20-call budget, spending one call instead of two to finish the redesign is worth a generated file.
- **Documents touched:** `design/figma/11-ios-forms-and-settings-merged.figma.js` (new), `design/figma/README.md`, and this log. No normative document touched and no rule changed; `design/` is design tooling and carries no authority (`AGENTS.md`, `docs/DESIGN.md`).
- **Verification:** the script was reviewed in full before being committed, as it was found uncommitted in the working tree rather than authored in this session. All three scripts (`09`, `10`, `11`) pass `node --check` when wrapped in an async IIFE, matching how `use_figma` wraps them. The merge was proved faithful by diff: lines 8–234 of the merged file are byte-identical to `09-ios-forms.figma.js` and lines 238–417 to `10-ios-settings.figma.js`, the only difference being the four-line header comment of each source, which the merged file replaces with its own. The script has **not** been executed — that is the blocker itself. No product code exists, so no tests, lint, coverage, architecture or contract checks apply.
- **Follow-ups / risks:** three iOS frames remain absent from the live Figma file and the redesign stays incomplete until quota resets or the plan is upgraded. Run either `11` or the pair `09` + `10`, never both: each path creates the same three frames and running both would duplicate them. `11` is generated output — edits belong in `09` and `10`, followed by a regeneration; editing `11` directly would silently diverge it from its sources. The `design/stitch/` equivalents of these three screens are complete and unaffected, so an implementer is not blocked by the Figma quota.

### 2026-08-19 — TDD made compulsory for product code

- **Type:** decision
- **Story / Decision:** — (no backlog story; governance change, owner-directed)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** made test-driven development (TDD) compulsory for product code across the MVP. Two coordinated edits: (1) `AGENTS.md` Technical Rules gained a bullet stating TDD is compulsory, per behavior unit, with the anti-paraguas clause of `docs/SPECIFICATION.md §11`, and that exemptions are limited to the list in that section and MUST be declared in the handoff. (2) `docs/SPECIFICATION.md §11` gained a `Development` row in the non-functional requirements table stating "Test-driven development (TDD) is compulsory for product code, per the rule below", followed by a full `### TDD rule` subsection defining: the behavior unit as the unit of TDD (not the line of code, not the feature); the red-then-green-then-refactor cycle with the "fails for the right reason" requirement; the anti-paraguas clause (a test MUST be specific to the behavior being introduced, not a paraguas test bundling unrelated behaviors); the orthogonality of Kover coverage thresholds (govern the result, not the order); and the closed list of exemptions (native UI, Room schemas/migrations, Firestore rules, Koin wiring/provider integration, architecture-rule fixtures) that still require tests but not written-first, and that MUST be declared in the handoff under "Decisions Made" as a SHOULD deviation.
- **Why:** the owner observed that TDD was not compulsory in the existing rules. The Definition of Done required "relevant tests pass" and CI failed on failing tests, but no rule forced the test-first order; an agent could implement first and add tests after, satisfying the letter of the DoD without the TDD cycle. The owner directed that TDD be made compulsory. The chosen design (behavior-unit TDD with anti-paraguas clause, Kover as orthogonal coverage oracle, closed exemption list with handoff declaration) follows the repo's existing pattern of a brief rule in `AGENTS.md` with the detailed development in the normative `SPECIFICATION.md`, and aligns with the existing SHOULD-deviation-in-handoff mechanism.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md §11`, and this log. No backlog story acceptance criterion changed; the rule applies to all product-code stories going forward.
- **Verification:** `grep -n 'TDD\|test-driven' AGENTS.md docs/SPECIFICATION.md` confirms the rule is present in both documents. The `### TDD rule` subsection renders as a valid Markdown heading under `## 11. Non-Functional Requirements`. Requires human review before merge (gated paths `AGENTS.md` and `docs/SPECIFICATION.md`; gated topic "MVP scope / quality rules").
- **Follow-ups / risks:** the first story that must apply the rule is `E0-01` (KMP Project Bootstrap). `E0-01` is largely scaffold/boilerplate; the agent should declare in its handoff which parts were TDD-exempt (likely most of the Gradle/Xcode scaffolding falls under the "native UI / wiring" spirit, though it is not literally in the closed exemption list — if the agent finds scaffolding that is neither product code nor on the exemption list, it MUST escalate rather than silently skip TDD). A future refinement may be needed if the exemption list proves too narrow for pure-scaffolding stories like `E0-01`/`E0-02`.

### 2026-08-19 — Language rule elevated to critical priority in `AGENTS.md`

- **Type:** correction
- **Story / Decision:** — (no backlog story; `AGENTS.md` governance change)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** elevated the Language rule priority in `AGENTS.md` via four coordinated edits. (1) Added a `> **CRITICAL — Language Rule (read before your first reply).**` callout immediately after the entry-point paragraph, summarizing the two rules (conversation in Spanish es-ES, repository artifacts in technical English) and pointing to `## Language` for the full text. (2) Moved the `## Language` section from its previous position as the fifth H2 section (between Document Authority and Owner Decisions) to the second H2 position, immediately after `## Normative Language` and before `## Document Map`, so an agent reading the document in order encounters the rule before any operational content. (3) Hardened the section opening with `**This section is CRITICAL. A violation fails review.**` and added a fourth consequence bullet: "An agent that replies in English when Spanish was required, or writes Spanish into a repository artifact, has violated a MUST and MUST self-correct before continuing." (4) Added an echo of the rule to the two operational checklists that agents actually use: Story Intake gained "the reply language (Spanish es-ES) is confirmed for this story" as a ready-check field, and Definition of Done gained "every chat reply during this story was in Spanish (es-ES); every repository artifact is in English" as a closing bullet.
- **Why:** the Language rule was well-written but structurally buried: it was the fifth H2 section, behind Document Map and Document Authority, and neither the entry-point paragraph nor the Story Intake / Definition of Done checklists mentioned it. An agent that scans the document for operational guidance (DoR, DoD, gates) could skip the rule entirely, and the owner observed this happening in practice. The four edits place the rule where it cannot be missed: first in the callout at the very top, second in section ordering, third in explicit MUST-weight language, and fourth in the two checklists an agent consults when starting and closing a story.
- **Documents touched:** `AGENTS.md`, and this log. No normative rule added or changed: the two language rules (conversation in Spanish, artifacts in English) and the three existing consequences were already present and correct; this change only repositions, strengthens and echoes them.
- **Verification:** `grep -n '^## ' AGENTS.md` confirms the new section order (Normative Language → Language → Document Map → … → Story Intake → Definition of Done). `grep '## Language' AGENTS.md` confirms a single `## Language` section remains. The diff was reviewed in full before commit. Requires human review before merge (gated path `AGENTS.md`).
- **Follow-ups / risks:** none. The change is self-contained within `AGENTS.md`. If a future agent violates the rule, the new fourth consequence bullet makes the violation a MUST breach requiring self-correction, not a stylistic lapse.

### 2026-08-19 — `docs/DESIGN.md` added as the entry point for the design assets

- **Type:** milestone
- **Story / Decision:** — (no backlog story; documentation, non-normative)
- **Author:** Claude Opus 5 (Claude Code session), on behalf of David Ruiz
- **What changed:** added `docs/DESIGN.md`, a non-normative entry point that describes the design in general terms — two design systems for one product, Material 3 Expressive on Android and Liquid Glass on iOS — and indexes every asset in `design/stitch/`: the two platform design systems and the twelve screen descriptions, screen by screen and platform by platform. It also records the constraints and known gaps of the design, and a table mapping each thing a design asset appears to decide to the normative document that actually decides it. `AGENTS.md` gained the document in its "Records and references" map plus an explicit paragraph stating that `design/` is tooling from which no rule may be derived; `README.md` gained a `Design` section and a documentation-table row; both design folder READMEs now link back to `docs/DESIGN.md`. On the owner's decision the same change also added a non-normative design pointer to the four UI stories `E1-07`, `E1-08`, `E1-09` and `E4-01`, a `docs/DESIGN.md` row to the short document map in `docs/DEFINITION.md §3`, a working rule in `docs/CONTRIBUTING.md` stating that a design asset contradicting a normative document is escalated rather than implemented, and a correction of a pre-existing drift in `AGENTS.md`: the map referred to a temporary `docs/AUDIT_GUARDRAILS.md` that no longer exists, while the present `docs/DOCUMENTATION_AUDIT.md` was absent from it. The latter is now listed as a closed, historical, fully-absorbed audit kept only so that the `AUDIT-NN` IDs cited by this log resolve.
- **Why:** the design assets committed on 2026-08-19 were reachable only by browsing `design/`, and nothing in the documentation set pointed at them. An agent assigned `E1-07`, `E1-08`, `E1-09` or `E4-01` had no way to discover that a design description of the screen existed. Placing the index in `docs/` makes it discoverable through the entry point every agent already reads, while the explicit non-normative framing keeps the design from acquiring authority it must not have over behaviour or contracts.
- **Documents touched:** `docs/DESIGN.md` (new), `AGENTS.md`, `README.md`, `docs/DEFINITION.md §3`, `docs/CONTRIBUTING.md`, `docs/BACKLOG.md` (`E1-07`, `E1-08`, `E1-09`, `E4-01`), `design/stitch/README.md`, `design/figma/README.md`, and this log. No normative rule added or changed; the backlog additions are pointers and are explicitly marked non-normative, so no acceptance criterion changed.
- **Verification:** every relative markdown link in the changed documents resolves to an existing path, checked mechanically. The screen index was generated from the actual file names and headings in `design/stitch/`. No product code exists, so no tests, lint, coverage, architecture or contract checks apply.
- **Follow-ups / risks:** touches the gated path `AGENTS.md` and requires human review before merge. Three open items were surfaced while writing the index and are recorded in `docs/DESIGN.md §6` rather than fixed here: the designs have never been audited for WCAG AA contrast or 200% font scaling, which `E4-02` owns and which may force design changes; the screens are drawn in Spanish only, while Spanish and English are both required from day one, so layouts must still be proven against the longer language; and loading, empty, error and the two-step odometer warning states are specified normatively but undrawn. `docs/DOCUMENTATION_AUDIT.md` is now fully absorbed and could be deleted; it was kept because this log cites its `AUDIT-NN` IDs, and deleting it would leave those citations unresolvable. `E3-05` backup status UI has design surface too — the sync chip on screen 02 and the backup row on screen 06 — but was left without a pointer, as the owner's decision covered the four screen-building stories.

### 2026-08-19 — UI redesign to strict platform design systems, with Figma and Stitch assets committed

- **Type:** milestone
- **Story / Decision:** — (no backlog story; design work, non-normative)
- **Author:** Claude Opus 5 (Claude Code session), on behalf of David Ruiz
- **What changed:** the six conceptual screens were redesigned to strict **Material 3 Expressive** (Android) and strict **Liquid Glass** (iOS), and the results committed as `design/` — a new non-normative folder. `design/figma/` holds eleven Figma Plugin API scripts; nine were executed against the live Figma file, producing two token collections (46 M3 variables, 35 Liquid Glass variables) and nine of twelve screens. `design/stitch/` holds the same designs translated into Google Stitch's `DESIGN.md` format plus twelve screen prompts. The original Figma concept boards were left untouched.
- **Why:** the Figma file had no design system at all — zero variables, zero components, every value hardcoded and duplicated across both platform boards, with copy diverged between them for identical functionality. A token foundation was a precondition for applying either design system "strictly". `design/` sits outside `docs/` deliberately, so it acquires no authority over behaviour or contracts.
- **Documents touched:** `design/figma/**` (new), `design/stitch/**` (new), and this log. No normative document touched.
- **Verification:** all eleven Figma scripts syntax-check clean under `node --check` when wrapped in an async IIFE, matching how `use_figma` wraps them. Nine were executed successfully and verified by screenshot. The `design/stitch/` files follow the six-section `DESIGN.md` structure published in `google-labs-code/stitch-skills`. No product code exists, so no tests, lint, coverage, architecture or contract checks apply.
- **Follow-ups / risks:** three iOS screens (`09-ios-forms`, `10-ios-settings`) remain unrun — Figma's Starter plan allows 20 MCP tool calls per month and the quota was exhausted; the scripts are complete and blocked only on quota or a plan upgrade. Free-plan variable collections are capped at one mode, so Light/Dark cannot be expressed as variable modes and no dark theme exists. The `design/stitch/` assets have not been validated against a live Stitch project. The brief's "map integration" and "real-time status updates" were not designed: they appear in no concept screen and adding them would have been the structural change the brief forbade — they need their own story if real.

### 2026-08-18 — Documentation audit AUDIT-16 applied: `AuthToken` gains `issuedAt` for freshness check

- **Type:** correction
- **Story / Decision:** `AUDIT-16` (`docs/DOCUMENTATION_AUDIT.md` §3.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added an `issuedAt: Instant` field to `AuthToken` in `docs/CONTRACTS.md §20.8`, populated by `TokenProvider`. Updated `§11.5` step 1 to define the freshness check as `AppClock.now() - issuedAt <= FRESH_LOGIN_THRESHOLD_MS`, using the new `issuedAt` field. `E2-02` and `E2-05` MUST test the freshness check.
- **Why:** `§11.5` said the app MUST verify the Firebase ID token is "fresh", meaning "younger than `FRESH_LOGIN_THRESHOLD_MS`". `AuthToken` carried `expiresAt` but no `issuedAt`. "Younger than 5 minutes" is a statement about issuance age, not expiry. An agent cannot compute issuance age from `expiresAt` alone (Firebase tokens have a 1-hour validity, so `expiresAt - 5 min` approximates issuance, but the contract did not state this).
- **Documents touched:** `docs/CONTRACTS.md` (`§20.8`, `§11.5`), and this log.
- **Verification:** documentation-only change. The freshness check will be tested by `E2-02` and `E2-05`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md`).
- **Follow-ups / risks:** `TokenProvider` implementations MUST populate `issuedAt`; a fake that omits it will fail the freshness check. All 20 findings of `docs/DOCUMENTATION_AUDIT.md` are now applied (19 closed by direct fix, 1 closed by AUDIT-04 as a duplicate).

### 2026-08-18 — Documentation audit AUDIT-14 applied: `confirmDelete` signatures simplified

- **Type:** correction
- **Story / Decision:** `AUDIT-14` (`docs/DOCUMENTATION_AUDIT.md` §3.1, blocking)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** changed `VehicleListStateHolder.confirmDelete(vehicleId: String, confirmation: Confirmation)` to `confirmDelete(vehicleId: String)` and `FuelEntryListStateHolder.confirmDelete(entryId: String, confirmation: Confirmation)` to `confirmDelete(entryId: String)` in `docs/CONTRACTS.md §20.10`. Added a normative statement that entity deletion is a direct action, not a typed-warning confirmation; pending-sync warnings are surfaced through `UiMessage` before the destructive action, not through `Confirmation`. The `Confirmation` enum is reserved for typed warnings that require an explicit override.
- **Why:** `Confirmation` had four leaves (`OdometerInconsistent`, `DiscardPendingChanges`, `DeleteAccount`, `AdoptExistingAccount`), none of which represented "confirm vehicle deletion" or "confirm fuel-entry deletion". An agent had to either pass an unrelated confirmation or invent a new leaf.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.10`), and this log.
- **Verification:** documentation-only change. The signature change will be exercised by `E1-07`/`E1-08`/`E1-09`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface").
- **Follow-ups / risks:** if a future story requires a typed confirmation before entity deletion (e.g. "this vehicle has N fuel entries, confirm?"), it MUST be added as a new `Confirmation` leaf and the `confirmDelete` signature MUST be revisited. The remaining 1 finding requiring owner decision (`AUDIT-16`) is still open.

### 2026-08-18 — Documentation audit AUDIT-10 applied: `cycleId` column added to `outbox` DDL

- **Type:** correction
- **Story / Decision:** `AUDIT-10` (`docs/DOCUMENTATION_AUDIT.md` §2.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a `cycleId TEXT` column to the `outbox` DDL in `docs/TECHNICAL_PLAN.md §6`, populated on every failed attempt. The sync engine reads it only for log correlation; it MUST NOT use it for retry or poison decisions (which read `lastErrorCode` only, per `§9.7`). Updated `docs/CONTRACTS.md §17` to replace the ambiguous "stored in `outbox.lastError` context" wording with "stored in the `outbox.cycleId` column". An `E3-03` migration test MUST verify the column is populated on failure and NULL on success.
- **Why:** `§17` said `cycleId` is "stored in `outbox.lastError` context", but the outbox DDL had only `lastError TEXT` and `lastErrorCode TEXT`; no `cycleId` column existed and the serialization format was undefined. `§9.7` says `lastError` is debug/UI-only and MUST NOT be read by the sync engine, so the engine cannot parse `cycleId` out of it.
- **Documents touched:** `docs/TECHNICAL_PLAN.md` (`§6`), `docs/CONTRACTS.md` (`§17`), and this log.
- **Verification:** documentation-only change. The migration test will be exercised by `E3-03`; no product code exists yet. Requires human review before merge (gated paths `docs/TECHNICAL_PLAN.md` and `docs/CONTRACTS.md`).
- **Follow-ups / risks:** the `cycleId` column is nullable (NULL on success, populated on failure); a future query that filters by `cycleId` MUST handle NULL. The remaining 2 findings requiring owner decision (`AUDIT-14`, `AUDIT-16`) are still open.

### 2026-08-18 — Documentation audit AUDIT-25 applied: `SyncController.retryFailed()` error leaves defined

- **Type:** correction
- **Story / Decision:** `AUDIT-25` (`docs/DOCUMENTATION_AUDIT.md` §5.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative statement in `docs/CONTRACTS.md §20.7` that `SyncController.retryFailed()` returns `Err(PersistenceError.TransactionFailed)` if the reset transaction fails; otherwise `Ok(Unit)`. It MUST NOT return `SyncError` or `RemoteError` leaves because it performs no remote work. An `E3-03` fixture MUST assert the only failure path is local-transaction failure.
- **Why:** `SyncController.retryFailed()` returns `Outcome<Unit, AppError>`, but the `SyncController` contract had no enumeration of the `AppError` leaves it may return. An agent implementing `E3-03` could return `PersistenceError.TransactionFailed`, `SyncError.RemoteUnavailable`, or nothing.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.7`), and this log.
- **Verification:** documentation-only change. The fixture will be exercised by `E3-03`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "error taxonomy").
- **Follow-ups / risks:** all automatic findings of `docs/DOCUMENTATION_AUDIT.md` have been applied. The 3 findings requiring owner decision (`AUDIT-10`, `AUDIT-14`, `AUDIT-16`) are still open.

### 2026-08-18 — Documentation audit AUDIT-24 applied: `VehicleListItemUi.deleted` documented as always-false in MVP

- **Type:** correction
- **Story / Decision:** `AUDIT-24` (`docs/DOCUMENTATION_AUDIT.md` §5.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative statement in `docs/CONTRACTS.md §20.10` that the MVP `VehicleListStateHolder` calls `observeVehicles(includeDeleted = false)`; `VehicleListItemUi.deleted` is present for future/debug use and is always `false` in the MVP list. A debug screen (referenced by `E3-03`) MAY call `observeVehicles(includeDeleted = true)` outside the state holder. An `E1-07` fixture MUST assert the production list never contains `deleted = true`.
- **Why:** `VehicleListItemUi` exposes `deleted: Boolean`, but `VehicleListStateHolder` had no intent that sets `includeDeleted = true` on `observeVehicles`. If `includeDeleted` is always `false`, the `deleted` flag is always `false` and the field is dead. The contract did not say whether the list ever includes deleted vehicles.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.10`), and this log.
- **Verification:** documentation-only change. The fixture will be exercised by `E1-07`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface").
- **Follow-ups / risks:** all automatic findings of `docs/DOCUMENTATION_AUDIT.md` have been applied. The 3 findings requiring owner decision (`AUDIT-10`, `AUDIT-14`, `AUDIT-16`) are still open.

### 2026-08-18 — Documentation audit AUDIT-23 applied: `setFuelType` documented as MVP-hidden

- **Type:** correction
- **Story / Decision:** `AUDIT-23` (`docs/DOCUMENTATION_AUDIT.md` §5.1, drift)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative statement in `docs/CONTRACTS.md §20.10` that `VehicleFormUiState.fuelType` is present for round-trip fidelity and defaults to `GASOLINE`; `VehicleFormStateHolder.setFuelType` exists for testability and future use, but the MVP UI MUST NOT render a `fuelType` selector (`SPECIFICATION.md §7 F-2`, `§5.1`, decision `D-4`). An `E1-07` acceptance criterion MUST assert no `fuelType` control is rendered, while the field round-trips on save.
- **Why:** `VehicleFormUiState.fuelType: FuelType` and `VehicleFormStateHolder.setFuelType(value: FuelType)` are declared in `§20.10`, but `SPECIFICATION.md §7 F-2` says "`fuelType` is not exposed in the MVP UI" and `§5.1` says it is "Metadata only". The state holder exposes a setter for a field the UI must not show. An agent could either render a selector (violating F-2) or hide the setter (violating the contract signature).
- **Documents touched:** `docs/CONTRACTS.md` (`§20.10`), and this log.
- **Verification:** documentation-only change. The acceptance criterion will be exercised by `E1-07`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface").
- **Follow-ups / risks:** the remaining 1 finding of `docs/DOCUMENTATION_AUDIT.md` is still open.

### 2026-08-18 — Documentation audit AUDIT-22 applied: `setUserProperties` trigger cadence defined

- **Type:** correction
- **Story / Decision:** `AUDIT-22` (`docs/DOCUMENTATION_AUDIT.md` §4.2, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative cadence rule in `docs/CONTRACTS.md §16.1`: `setUserProperties` is called once on analytics opt-in, and thereafter on every successful vehicle or fuel-entry create/delete, from the presentation layer. It MUST NOT be called from domain or data. Buckets are computed from the current list size. An `E3-09` fixture MUST assert the call cadence.
- **Why:** `AnalyticsTracker.setUserProperties` carries `vehicleCountBucket` and `entryCountBucket`, but the contract did not state when it is called. Two agents could implement "set on every write" (chatty) or "set on app foreground only" (stale buckets).
- **Documents touched:** `docs/CONTRACTS.md` (`§16.1`), and this log.
- **Verification:** documentation-only change. The fixture will be exercised by `E3-09`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md`).
- **Follow-ups / risks:** the remaining 2 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-21 applied: `CrashReporter.recordNonFatal` trigger policy defined

- **Type:** correction
- **Story / Decision:** `AUDIT-21` (`docs/DOCUMENTATION_AUDIT.md` §4.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a `recordNonFatal` trigger policy in `docs/CONTRACTS.md §20.3.1`: it MUST be called for every `UnexpectedError` and for every `SyncError.Poisoned` / `FAILED_POISONED` transition; it MUST NOT be called for validation warnings, expected `AuthError` leaves (`Cancelled`, `RequiresRecentLogin`, `CredentialAlreadyInUse`), or connectivity-only `RemoteError` codes. `fields` follows the same allowlist as `Logger` (`§17`). An `E3-03` / `E4-04` fixture MUST assert the call sites.
- **Why:** `CrashReporter.recordNonFatal` is the only non-fatal API, but no documented flow called it. `§17` says `Logger` is not a crash-reporting API. The boundary between "log this error" and "report this as a non-fatal crash" was unspecified. An agent could report every `UnexpectedError` as a non-fatal, or never report anything.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.3.1`), and this log.
- **Verification:** documentation-only change. The fixture will be exercised by `E3-03` / `E4-04`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md`).
- **Follow-ups / risks:** the remaining 3 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-20 applied: `§16.1` allows failure event tracking

- **Type:** correction
- **Story / Decision:** `AUDIT-20` (`docs/DOCUMENTATION_AUDIT.md` §4.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** rewrote the tracking rule in `docs/CONTRACTS.md §16.1` to: "Shared presentation or application-level orchestration may track product events after a use case returns `Ok` **or** `Err`, provided the event payload carries no user data. Success and failure events are both permitted; the closed `AnalyticsEvent` hierarchy is the sole source of allowed events." A fixture MUST assert failure events are emitted from presentation, not domain or data.
- **Why:** `§16.1` said "track product events **after successful use case results**", but `AnalyticsEvent` includes `AccountConversionFailed`, `AccountDeletionFailed` and `SyncStatusChanged` — failure/state events, not success events. The rule contradicted the existence of failure leaves. An agent could either omit failure tracking (following the rule) or emit it (following the type), and neither was provably wrong.
- **Documents touched:** `docs/CONTRACTS.md` (`§16.1`), and this log.
- **Verification:** documentation-only change. The fixture will be exercised by `E3-09`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "logging and privacy rules").
- **Follow-ups / risks:** the remaining 4 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-18 applied: `AuthError` to analytics bucket enum mappings defined

- **Type:** correction
- **Story / Decision:** `AUDIT-18` (`docs/DOCUMENTATION_AUDIT.md` §4.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added two normative mapping tables in `docs/CONTRACTS.md §20.9`: `AuthError -> ConversionFailureReason` (`Cancelled -> CANCELLED`, `CredentialAlreadyInUse -> CREDENTIAL_IN_USE`, `NetworkUnavailable -> NETWORK`, `UidWouldChange -> UID_WOULD_CHANGE`, everything else -> `UNKNOWN`) and `AuthError -> DeletionFailureReason` (`RequiresRecentLogin -> REQUIRES_RECENT_LOGIN`, `AccountDeletionRemoteFailed -> REMOTE_FAILED`, `NetworkUnavailable -> NETWORK`, everything else -> `UNKNOWN`). Unit tests MUST assert exhaustiveness of both mappings.
- **Why:** `AnalyticsEvent.AccountConversionFailed(reason: ConversionFailureReason)` and `AccountDeletionFailed(reason: DeletionFailureReason)` use analytics-specific bucket enums, but there was no defined mapping from `AuthError` to those buckets. `AuthError.PermissionDenied` had no corresponding bucket in `ConversionFailureReason`; two agents could bucket `PermissionDenied` as `UNKNOWN` or as `CREDENTIAL_IN_USE`.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.9`), and this log.
- **Verification:** documentation-only change. The unit tests will be exercised by `E2-04`/`E2-05`/`E3-09`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "error taxonomy / analytics").
- **Follow-ups / risks:** the remaining 5 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-17 applied: `SyncStatus` convergence rule for state holders

- **Type:** correction
- **Story / Decision:** `AUDIT-17` (`docs/DOCUMENTATION_AUDIT.md` §3.2, cosmetic)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative statement in `docs/CONTRACTS.md §14` that every state holder exposing `SyncStatus` (`VehicleListUiState.syncStatus`, `FuelEntryListUiState.syncStatus` and `SyncUiState.status`) observes the same `SyncController.status` flow; values are eventually consistent and converge. List state holders MUST NOT independently compute `SyncStatus`; they MUST relay the single `SyncController.status` source. A unit test MUST assert that two holders fed by the same `SyncController` converge.
- **Why:** `SyncStatus` is embedded in three `UiState` classes, all derived from the same `SyncController.status` flow, but the contract did not state whether the three emissions are guaranteed to agree at any instant, or whether list state holders may snapshot a stale value while `SyncStateHolder` holds the latest.
- **Documents touched:** `docs/CONTRACTS.md` (`§14`), and this log.
- **Verification:** documentation-only change. The unit test will be exercised by the presentation stories; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md`).
- **Follow-ups / risks:** the remaining 6 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-15 applied: `SessionStateHolder` gains F-4 conversion intents

- **Type:** correction
- **Story / Decision:** `AUDIT-15` (`docs/DOCUMENTATION_AUDIT.md` §3.1, blocking)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added `fun startAccountConversion(provider: AuthProvider)` and `fun confirmAccountConversion(confirmation: Confirmation)` to `SessionStateHolder` in `docs/CONTRACTS.md §20.10`. Added a normative statement that `startAccountConversion` calls `AuthClient.linkCredential` (not `signInWithCredential`), preserves the UID, and maps `AuthError.UidWouldChange` / `AuthError.CredentialAlreadyInUse` to the F-4 collision flow. Updated `E2-04` acceptance criteria to reference the new intents.
- **Why:** `SPECIFICATION.md §7 F-4` (Anonymous Account Conversion) is a distinct flow: from settings, the user links Google or Apple credentials to the current anonymous identity. `SessionStateHolder` had no intent for it; `startPermanentSignIn` signs in, it does not link to an existing anonymous identity. An agent implementing `E2-04` had no contract entry point.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.10`), `docs/BACKLOG.md` (E2-04), and this log.
- **Verification:** documentation-only change. The intents will be exercised by `E2-04`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface").
- **Follow-ups / risks:** the remaining 7 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-13 applied: `SessionPhase` `LOCAL -> DELETING` semantics clarified

- **Type:** correction
- **Story / Decision:** `AUDIT-13` (`docs/DOCUMENTATION_AUDIT.md` §3.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative statement in `docs/CONTRACTS.md §20.10` clarifying that from `LOCAL`, `DELETING` means "clearing local data only" (no server operation, because there is no Firebase Auth account); from `ANONYMOUS` or `PERMANENT`, `DELETING` means "running the `D-23` server operation then clearing local data". The `DELETING -> UNKNOWN` transition is followed by `UNKNOWN -> SIGNED_OUT` only after the local-data clear completes. `E2-05` MUST test both paths.
- **Why:** the `SessionPhase` transition table allows `LOCAL -> DELETING`, but for a `LOCAL_OWNER` session there is no Firebase Auth account, so the `D-23` server operation cannot run. `SPECIFICATION.md §7 F-5` says the anonymous equivalent is "delete local data". The contract did not distinguish the two meanings of `DELETING`.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.10`), and this log.
- **Verification:** documentation-only change. The tests will be exercised by `E2-05`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md`).
- **Follow-ups / risks:** the remaining 8 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-12 applied: `FuelEntryListItemUi` gains `hasMissedEntries` and `odometerInconsistent`

- **Type:** correction
- **Story / Decision:** `AUDIT-12` (`docs/DOCUMENTATION_AUDIT.md` §2.2, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added `hasMissedEntries: Boolean` and `odometerInconsistent: Boolean` to `FuelEntryListItemUi` in `docs/CONTRACTS.md §20.10`. Updated `E1-08` and `E1-09` acceptance criteria to render the flags on every row, including partial refuels where `invalidReason = EndEntryNotFullTank`.
- **Why:** `invalidReason: ConsumptionInvalidReason?` covers `MissedEntriesInSegment` and `InconsistentOdometerInSegment` for full-tank entries, but for a partial (non-full-tank) entry `invalidReason = EndEntryNotFullTank` and the underlying `hasMissedEntries`/`odometerInconsistent` flags are lost. The UI cannot show a "missed refuels" or "inconsistent odometer" indicator on a partial row, even though those flags are user-visible per `SPECIFICATION.md §5.2` and F-3.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.10`), `docs/BACKLOG.md` (E1-08, E1-09), and this log.
- **Verification:** documentation-only change. A fixture proving a partial entry with `hasMissedEntries = true` surfaces the flag on the Swift side will be exercised by `E1-08`/`E1-09`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface").
- **Follow-ups / risks:** the remaining 9 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-11 applied: `RemoteCursor.INITIAL` null exemption clarified

- **Type:** correction
- **Story / Decision:** `AUDIT-11` (`docs/DOCUMENTATION_AUDIT.md` §2.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative statement in `docs/CONTRACTS.md §20.7` that `RemoteCursor.INITIAL` is a sentinel representing "no cursor stored yet" and is never passed to `RemoteSyncSource.pullChanges`; the sync engine materialises the first page cursor as `(overlapSince, "")`. Updated `§9.4` to clarify that the `null` prohibition applies to cursors passed to `startAt`/`startAfter`; the `INITIAL` sentinel is exempt because it is translated before reaching Firestore. An `E3-03` test MUST prove `INITIAL` never reaches `RemoteSyncSource`.
- **Why:** `RemoteCursor.INITIAL` has `lastDocumentId = null`, but `§9.4` states "`null` MUST NOT be used as a cursor component." A literal reading makes `INITIAL` illegal. An agent could "fix" `INITIAL` by setting `lastDocumentId = ""`, which would then be passed to `startAfter` on a resumed cycle and produce wrong pagination.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.7`, `§9.4`), and this log.
- **Verification:** documentation-only change. The test will be exercised by `E3-03`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md`).
- **Follow-ups / risks:** the remaining 10 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-09 applied: `quarantine` DDL `reason` CHECK constraint added

- **Type:** correction
- **Story / Decision:** `AUDIT-09` (`docs/DOCUMENTATION_AUDIT.md` §2.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added `CHECK (reason IN ('UnsupportedSchemaVersion','MalformedPayload'))` to the `quarantine` DDL in `docs/TECHNICAL_PLAN.md §6`, matching the closed `QuarantineReason` enum (`docs/CONTRACTS.md §20.7`). An `E3-03` migration test MUST prove the constraint rejects an unknown reason.
- **Why:** the DDL stored `reason TEXT NOT NULL` but `QuarantineReason` is a closed enum with two leaves. Without a CHECK constraint, unlike `outbox.entityType` which has one, an agent could persist an arbitrary reason string.
- **Documents touched:** `docs/TECHNICAL_PLAN.md` (`§6`), and this log.
- **Verification:** documentation-only change. The migration test will be exercised by `E3-03`; no product code exists yet. Requires human review before merge (gated path `docs/TECHNICAL_PLAN.md`).
- **Follow-ups / risks:** if a new `QuarantineReason` leaf is added, the CHECK constraint MUST be updated in the same change. The remaining 11 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-08 applied: `sync_cursor` table DDL defined

- **Type:** correction
- **Story / Decision:** `AUDIT-08` (`docs/DOCUMENTATION_AUDIT.md` §2.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added the exact DDL for the `sync_cursor` table to `docs/TECHNICAL_PLAN.md §6`: `CREATE TABLE sync_cursor (entityType TEXT NOT NULL CHECK (entityType IN ('VEHICLE','FUEL_ENTRY')), lastServerUpdatedAt INTEGER NOT NULL, lastDocumentId TEXT NOT NULL, PRIMARY KEY (entityType))`. `lastDocumentId` is `TEXT NOT NULL` because `docs/CONTRACTS.md §9.4` forbids `null` as a cursor component; the `RemoteCursor.INITIAL` sentinel is never stored as a row. An `E1-01` migration test MUST verify the constraint rejects an unknown `entityType`.
- **Why:** `§6` listed `sync_cursor` as a table and `SPECIFICATION.md §9.2` named its columns, but no DDL with column types, nullability, primary key, or `entityType` CHECK constraint was provided. An agent implementing `E1-01` would have to invent the column types.
- **Documents touched:** `docs/TECHNICAL_PLAN.md` (`§6`), and this log.
- **Verification:** documentation-only change. The migration test will be exercised by `E1-01`; no product code exists yet. Requires human review before merge (gated path `docs/TECHNICAL_PLAN.md`).
- **Follow-ups / risks:** the remaining 12 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-07 applied: `SyncStatus -> SyncStatusCategory` mapping defined

- **Type:** correction
- **Story / Decision:** `AUDIT-07` (`docs/DOCUMENTATION_AUDIT.md` §1.2, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative mapping in `docs/CONTRACTS.md §20.9` from `SyncStatus` to `SyncStatusCategory` using the same connectivity-code rule as `§9.9`: `Idle -> IDLE`, `Syncing -> SYNCING`, `Pending -> PENDING`, and `Failed -> FAILED` only when at least one counted row has `lastErrorCode` not in `CONNECTIVITY_ERROR_CODES`; otherwise `Failed -> PENDING`. A unit test MUST assert the mapping under all combinations.
- **Why:** there was no defined mapping from the sealed `SyncStatus` to the flat `SyncStatusCategory` enum used by `AnalyticsEvent.SyncStatusChanged`. An agent could map `Failed(_, _) -> FAILED` verbatim, reporting a failure event for a connectivity-only condition that `§9.9` says is `Pending`.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.9`), and this log.
- **Verification:** documentation-only change. The unit test will be exercised by `E0-08` / `E3-09`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "error taxonomy / analytics").
- **Follow-ups / risks:** the remaining 13 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-06 applied: Swift-facing `SyncStateHolder.requestSync` trigger surface restricted

- **Type:** correction
- **Story / Decision:** `AUDIT-06` (`docs/DOCUMENTATION_AUDIT.md` §1.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative statement in `docs/CONTRACTS.md §20.10` that `SyncStateHolder.requestSync` is intended for user-initiated sync only. The Swift-facing surface MUST pass `SyncTrigger.PullToRefresh` (and `SyncTrigger.AppForeground` if the platform emits it from a lifecycle hook). `PostWriteDebounce`, `ConnectivityRecovered` and `Periodic` are fired exclusively by `SyncTriggerAdapter` from platform wiring and MUST NOT be invoked from Swift UI code, to avoid duplicating `BGTaskScheduler`/`WorkManager` wiring and bypassing the single-`SyncController` invariant of `§9.1`. A Konsist fixture MUST ban those three leaves from any `iosMain` call site of `SyncStateHolder.requestSync`.
- **Why:** exposing system triggers to Swift invites the iOS layer to fire them manually, duplicating platform wiring and bypassing the single-`SyncController` invariant. The audit proposed a single solution.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.10`), and this log.
- **Verification:** documentation-only change. The Konsist fixture will be exercised by `E3-08`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface").
- **Follow-ups / risks:** if the iOS layer ever needs to emit `AppForeground` from a lifecycle hook, that leaf remains permitted. The remaining 14 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-05 applied: removed `:core:sync -> :core:auth` dependency edge

- **Type:** correction
- **Story / Decision:** `AUDIT-05` (`docs/DOCUMENTATION_AUDIT.md` §1.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** removed `:core:auth` from the allowed-dependency list of `:core:sync` in `docs/TECHNICAL_PLAN.md §4`, moving it to the forbidden list. Updated `docs/SPECIFICATION.md §8.3` rule 5 to state that `:core:sync` depends on `:core:model`, `:core:common` and `:core:database`, never on `:core:auth` and never on `:integration:*`, and that token handling lives entirely in `RemoteSyncSource`. Added an explanatory note in `docs/CONTRACTS.md §10` that the sync engine never calls `AuthClient` or `TokenProvider`; the `AuthExpired` state-machine transition is sync-internal and re-authentication is delegated to the session/presentation layer. Added a failing fixture to `E0-04` asserting that `:core:sync` does not depend on `:core:auth` and does not reference `AuthClient` or `TokenProvider`.
- **Why:** the `:core:sync -> :core:auth` edge was allowed by the `§4` table but no documented flow uses it: `RemoteSyncSource` (`§10`) handles token refresh on `Unauthenticated` and retries once before mapping to `RemoteError.Unauthenticated`; the sync engine only consumes the resulting `RemoteError`. The edge was dead coupling. The owner chose Option B (eliminate the edge) over Option A (justify it by documenting a `TokenProvider.getIdToken` call on the `AuthExpired` retry path), because the contracts already delegate token handling to the integration layer and aligning `:core:sync` with the "feature data MUST NOT depend on `:core:auth`" rule is cleaner.
- **Documents touched:** `docs/TECHNICAL_PLAN.md` (`§4`), `docs/SPECIFICATION.md` (`§8.3` rule 5), `docs/CONTRACTS.md` (`§10`), `docs/BACKLOG.md` (E0-04), and this log.
- **Verification:** documentation-only change. The new fixture will be exercised by `E0-04`; no product code exists yet. Requires human review before merge (gated paths `docs/TECHNICAL_PLAN.md` and `docs/SPECIFICATION.md`, gated topic "module boundaries and dependency rules").
- **Follow-ups / risks:** if a future story needs `:core:sync` to call `TokenProvider` directly (e.g. to force a refresh before a critical push without going through `RemoteSyncSource`), the edge MUST be re-added with a documented justification in `§10` or `§7`. The remaining 15 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-04 applied: `contract-check` ignored-set extended with Room-generated types

- **Type:** correction
- **Story / Decision:** `AUDIT-04` (`docs/DOCUMENTATION_AUDIT.md` §1.1, guardrail); also closes `AUDIT-19` (`docs/DOCUMENTATION_AUDIT.md` §5.2, guardrail), which is the same finding described from the `AppDatabase`/`DatabaseFactory` angle.
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** extended the `contract-check` assertion 1 ignored-identifier set in `docs/CONTRACTS.md §18` with an explicit "Room-generated types owned by `:core:database`" category covering `AppDatabase`, Room `Dao` supertypes, and `@Entity`-generated row classes. The extension states that these types are allowed only in `:core:database` signatures and in `DatabaseFactory` (`§20.3.2`); any appearance in `:core:common`, `:core:sync`, feature `domain` or the `:shared` public API remains a violation. Added a matching failing fixture to the `E0-04` acceptance criteria: the check does not flag `AppDatabase` in a `:core:database` or `DatabaseFactory` signature, but does flag it elsewhere.
- **Why:** the `DatabaseFactory` move in `AUDIT-03` introduced `AppDatabase` (a Room-generated type not declared in `§20`) into a `§20.3.2` code block; assertion 1 would flag it as undeclared without this extension. The owner accepted the audit's single proposed solution verbatim. `AUDIT-19` is the same issue viewed from the `DatabaseFactory.create(): AppDatabase` signature in `§20.3`, so it is closed by the same change.
- **Documents touched:** `docs/CONTRACTS.md` (`§18`), `docs/BACKLOG.md` (E0-04), and this log.
- **Verification:** documentation-only change. The fixture will be exercised by `contract-check` (implemented by `E0-05`); no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface / module boundaries").
- **Follow-ups / risks:** if Room-generated types ever need to appear outside `:core:database` and `DatabaseFactory`, this rule MUST be revisited. The remaining 16 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-03 applied: `DatabaseFactory` moved to `:core:database`

- **Type:** correction
- **Story / Decision:** `AUDIT-03` (`docs/DOCUMENTATION_AUDIT.md` §1.1, blocking)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** moved `interface DatabaseFactory { fun create(): AppDatabase }` out of `§20.3` (`:core:common`) into a new `§20.3.2 Database types — :core:database`, and declared `AppDatabase` as the Room-generated type owned by `:core:database`. Added a note in `§11.6` that `AppGraphDependencies.databaseFactory` imports `DatabaseFactory` from `:core:database`. Added a failing fixture to `E0-04` asserting that `:core:common` references neither `AppDatabase` nor `DatabaseFactory`, and that both types may appear only in `:core:database`, `:core:testing` fakes and the `AppGraphDependencies` field of `:shared`.
- **Why:** `DatabaseFactory`'s return type `AppDatabase` is a Room-generated type owned by `:core:database`, but the interface lived in `:core:common`, which is forbidden from depending on Room (`docs/TECHNICAL_PLAN.md §4`). The dependency-rule table row added by `AUDIT-01` now allows `:core:testing` to depend on `:core:database`, so the fake can be provided by `:core:testing`. The audit proposed a single solution; the owner accepted it verbatim.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.3`, new `§20.3.2`, `§11.6`), `docs/BACKLOG.md` (E0-04), and this log.
- **Verification:** documentation-only change. The `contract-check` ignored-set extension for Room-generated types (audit findings 4 and 19) is still pending and will be applied when those findings are processed; until then the new `§20.3.2` code block references `AppDatabase`, which `contract-check` assertion 1 would currently flag. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "module boundaries").
- **Follow-ups / risks:** `AUDIT-04` and `AUDIT-19` MUST extend the `contract-check` ignored-identifier set with "Room-generated types owned by `:core:database`" so `AppDatabase` and `DatabaseFactory` signatures do not trip assertion 1. The remaining 17 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-02 applied: `AuthProvider` moved to `:core:common`

- **Type:** correction
- **Story / Decision:** `AUDIT-02` (`docs/DOCUMENTATION_AUDIT.md` §1.1, blocking)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** moved the `AuthProvider` enum (`ANONYMOUS, GOOGLE, APPLE`) from `§20.8` (`:core:auth`, Phase 2) to `§20.3` (`:core:common`, Phase 0). `:core:auth` now imports it from `:core:common` instead of declaring it. Added `contract-check` assertion 18 asserting that `AuthProvider` is declared in `§20.3` before any reference in `§20.8` (`:core:auth`) or `§20.9` (`:core:analytics`). No backlog or phase change: `E0-08` (Phase 0) and `E2-01` (Phase 2) keep their assignments.
- **Why:** `AnalyticsEvent.PermanentSignInSelected(val provider: AuthProvider)` in `§20.9` is required by `E0-08` (Phase 0), but `AuthProvider` lived in `:core:auth`, created only in Phase 2 (`E2-01`). A Phase 0 module cannot compile against a Phase 2 module. `AuthProvider` is a pure enum referenced by two modules, so it belongs in `:core:common` alongside `SyncTrigger` and `LogLevel`. The owner chose Option A (move to `:core:common`) over moving it to `:core:model` (mixes identity with business vocabulary) or moving `:core:analytics` to Phase 2 (reorders the whole plan for one enum).
- **Documents touched:** `docs/CONTRACTS.md` (`§20.3`, `§20.8`, `§18`), and this log.
- **Verification:** documentation-only change. The new assertion 18 will be exercised by `contract-check` (implemented by `E0-05`); no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface / module boundaries").
- **Follow-ups / risks:** if `AuthProvider` gains non-enum semantics later it MUST stay a pure enum on the `:core:common` surface, since both `:core:analytics` and `:core:auth` depend on it. The remaining 18 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-01 applied: dependency-rule rows for `:core:auth`, `:core:analytics`, `:core:testing`

- **Type:** correction
- **Story / Decision:** `AUDIT-01` (`docs/DOCUMENTATION_AUDIT.md` §1.1, blocking)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added three explicit rows to the `docs/TECHNICAL_PLAN.md §4` dependency-rule table for `:core:auth`, `:core:analytics` and `:core:testing`, which previously had no enforceable rule even though they appear in the canonical module inventory (`docs/CONTRACTS.md §1.1`). Added an explanatory paragraph pinning the `:core:testing` platform-API permission to `expect`/`actual` test doubles only (`docs/CONTRACTS.md §15.1`), keeping its `commonMain` public surface Kotlin-pure. Added matching failing fixtures to the `E0-04` acceptance criteria (one per new row). The architecture check generated from that table can now enforce the boundaries of all three modules.
- **Why:** the architecture check is generated from the `§4` table, so the three modules were previously unenforceable; an agent could make `:core:auth` depend on `:feature:*` or `:integration:*` and the check would not fire. The owner accepted the audit's single proposed solution verbatim and the agent's interpretation that `:core:testing` forbids platform APIs only in its `commonMain` public surface (permitted in `expect`/`actual` test doubles).
- **Documents touched:** `docs/TECHNICAL_PLAN.md §4`, `docs/BACKLOG.md` (E0-04), and this log.
- **Verification:** documentation-only change. The new architecture rules and fixtures will be exercised by `E0-04`; no product code exists yet. Requires human review before merge (gated path `docs/TECHNICAL_PLAN.md` and gated topic "module boundaries and dependency rules").
- **Follow-ups / risks:** the `:core:testing` platform-API carve-out relies on `§15.1` boundaries; if a future change loosens `§15.1`, this row's fixture wording MUST be re-checked. The remaining 19 findings of `docs/DOCUMENTATION_AUDIT.md` are still open and will be processed one by one with their own project-log entries.

### 2026-08-18 — Second documentation audit performed

- **Type:** milestone
- **Story / Decision:** `docs/DOCUMENTATION_AUDIT.md`
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** a fresh Senior KMP Architecture audit of the current documentation state (after the prior 56-finding audit was folded into the normative docs). The audit document was rewritten with 20 new findings: 5 blocking, 13 guardrails, 1 drift, 1 cosmetic. Top blockers: missing dependency-rule rows for `:core:auth`/`:core:analytics`/`:core:testing`; `:core:analytics` (Phase 0) referencing `AuthProvider` from `:core:auth` (Phase 2); `DatabaseFactory` in `:core:common` returning a `:core:database` type; `SessionStateHolder` missing an F-4 conversion intent; `Confirmation` enum missing deletion leaves for `confirmDelete`.
- **Why:** the prior audit closure left residual gaps that would still let two competent agents produce incompatible implementations; this pass targets those.
- **Documents touched:** `docs/DOCUMENTATION_AUDIT.md`, and this log.
- **Verification:** documentation-only change; cross-referenced findings against the current `AGENTS.md`, `SPECIFICATION.md`, `CONTRACTS.md`, `TECHNICAL_PLAN.md`, `DECISION_BOARD.md`, `BACKLOG.md`, `SECURITY.md`, `identifiers.md` and `versions-matrix.md`.
- **Follow-ups / risks:** the audit is non-normative; each finding must be accepted by the owner and folded into the normative docs in a separate change with a project-log entry. Blocking findings should be resolved before the dependent backlog stories start.

### 2026-08-18 — Documentation audit verification hooks tightened

- **Type:** decision
- **Story / Decision:** `docs/DOCUMENTATION_AUDIT.md`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** remaining recommended audit guardrails were folded into executable story criteria: `Instant` stays an `E0-06` blocker until the version matrix pins its package, `outbox.lastError` is debug/UI-only, Swift scale suffixes require `shared/README.md`, and supported currencies require platform minor-unit verification with `EUR` fallback.
- **Why:** the owner authorised using the recommended option while away, and these entries make already-accepted prose rules testable by the stories that will implement them.
- **Documents touched:** `docs/CONTRACTS.md`, `docs/BACKLOG.md`, and this log.
- **Verification:** documentation-only change; `git diff --check`.
- **Follow-ups / risks:** changes touch gated documentation and still require normal human review before merge.

### 2026-08-18 — Documentation audit review decisions recorded

- **Type:** decision
- **Story / Decision:** `docs/DOCUMENTATION_AUDIT.md`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the owner reviewed the first documentation-audit corrections and accepted the recommended choices for `:core:database` dependency rules, dependency injection over duplicate `expect`/`actual` paths, consumption type ownership in `:core:model`, closed platform API vocabulary, `:core:crash` ownership, and a non-duplicated `SPECIFICATION.md §8.2` module inventory reference. The owner also instructed Codex to use the recommended option for remaining documentation-audit decisions while they are away.
- **Why:** the review keeps decision ownership explicit while allowing the audit cleanup to continue without blocking on every low-risk guardrail choice.
- **Documents touched:** `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/identifiers.md`, and this log.
- **Verification:** manual review in conversation, followed by `git diff --check`.
- **Follow-ups / risks:** every subsequent assumed recommendation in this review should be summarised for owner review after the remaining audit items are processed.

### 2026-08-17 — Documentation audit guardrails folded into project docs

- **Type:** milestone
- **Story / Decision:** `docs/DOCUMENTATION_AUDIT.md`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** processed the documentation audit in order across architecture, data modelling, sync, auth, error handling, Swift-facing ABI and cross-document consistency. The docs now include a canonical module inventory, explicit `:core:database` dependency rules, tighter `expect`/`actual` boundaries, exact local DDL constraints, outbox coalescing SQL, Firestore `validPayload()` shape, local-owner adoption and account-deletion ordering details, logging redaction rules, `CalculateConsumption` signature cleanup, Swift ABI lifecycle rules, Phase 0 module-set constraints and expanded backlog fixtures.
- **Why:** the audit identified places where two agents could implement different behaviours while still claiming to follow the same docs. The corrections turn those areas into precise contract language and story acceptance criteria.
- **Documents touched:** `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/identifiers.md`, and this log.
- **Verification:** manual documentation review with targeted searches, diff review and code-fence counts for the edited Markdown files. No product code or CI exists yet.
- **Follow-ups / risks:** changes touch gated documentation paths and require human review before merge. `contract-check`, `architecture-check`, Firestore emulator tests and generated Objective-C header checks are specified but not yet implemented.

### 2026-08-17 — MVP single-device constraint recorded

- **Type:** decision
- **Story / Decision:** `D-0`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the MVP now explicitly supports one active device per account. Simultaneous multi-device use, active synchronization and moving the source of truth from Room to the remote database are recorded as future scope.
- **Why:** the owner clarified that the remote database is backup-only for the MVP, while future multi-device use will require real synchronization and a remote source of truth.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md`, `docs/CONTRACTS.md §10`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/DEFINITION.md`, `README.md`, `docs/adr/0001-backend-cloud-firestore.md`, `docs/adr/0014-firestore-location-europe-west1.md`, and this log.
- **Verification:** manual documentation update only; no product code exists for this behaviour.
- **Follow-ups / risks:** remaining internal names such as `SyncController`, `RemoteSyncSource` and `syncState` are implementation terms unless a future contract-renaming story changes them. The change touches gated scope, backend and sync topics and requires human review before merge.

### 2026-08-17 — Remote database purpose clarified as backup and recovery

- **Type:** decision
- **Story / Decision:** `D-0`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the remote database purpose is now documented as backup and recovery only, so users can retrieve backed-up data on a new device. Active multi-device collaboration is not an MVP goal.
- **Why:** the owner clarified that the remote database exists solely as a backup, not as the product source of truth or a real-time cross-device data layer.
- **Documents touched:** `docs/SPECIFICATION.md`, `docs/CONTRACTS.md §9` and `§10`, `docs/DECISION_BOARD.md`, `docs/SECURITY.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/DEFINITION.md`, `README.md`, `docs/adr/0001-backend-cloud-firestore.md`, `docs/adr/0015-firebase-project-topology.md`, and this log.
- **Verification:** manual documentation update only; no product code exists for this behaviour.
- **Follow-ups / risks:** remaining `sync` type and module names are technical implementation names unless a future contract-renaming story changes them. The change touches gated scope, backend and sync topics and requires human review before merge.

### 2026-08-17 — Post-MVP OCR and Cloud Functions security recorded

- **Type:** milestone
- **Story / Decision:** —
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** post-MVP roadmap notes now explicitly reserve receipt and odometer image capture with local AI text recognition, targeting receipt total amount, receipt price per liter and odometer reading, plus Cloud Functions-mediated remote read/write validation beyond the `D-23` account deletion server operation.
- **Why:** the owner asked to keep these capabilities visible for the future without expanding MVP scope or authorizing implementation dependencies, models, receipt or odometer image storage, server-mediated product reads, App Check enforcement or broader privileged server-side writes.
- **Documents touched:** `docs/SPECIFICATION.md §3`, `docs/SECURITY.md`, `docs/TECHNICAL_PLAN.md §13`, `README.md`, and this log.
- **Verification:** manual documentation update only; no product code exists for these features.
- **Follow-ups / risks:** both future areas require a dedicated story or ADR before implementation. Changes touch gated documentation and security topics and require human review before merge.

### 2026-08-17 — Temporary audit guardrails file deleted

- **Type:** decision
- **Story / Decision:** audit closure
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** deleted `docs/AUDIT_GUARDRAILS.md` after all `F-01` through `F-19` follow-up findings were absorbed into the normative and derived documentation.
- **Why:** the file was explicitly temporary and no longer carried active work after owner review.
- **Documents touched:** `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual search for active audit findings and `git diff --check`.
- **Follow-ups / risks:** none.

### 2026-08-17 — Partial refuel consumption explanation defined

- **Type:** decision
- **Story / Decision:** `F-15`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `EndEntryNotFullTank` is kept as the list-projection reason for partial refuels. Partial rows show no own consumption, do not produce `SegmentResult`, and still contribute litres to the next full-to-full segment when they fall inside it.
- **Why:** the owner chose to keep a clear UI explanation for non-full refuels while preserving full-to-full consumption as the only calculation model.
- **Documents touched:** `docs/SPECIFICATION.md §6`, `docs/CONTRACTS.md §4`, `§13`, `§20.4` and `§20.6`, `docs/BACKLOG.md` `E1-05`, `E1-06`, `E1-08` and `E1-09`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `F-15`, `EndEntryNotFullTank`, `FuelEntryListItem`, `SegmentResult`, `ConsumptionReport` and partial refuels; `git diff --check`.
- **Follow-ups / risks:** no active audit findings remain. `docs/AUDIT_GUARDRAILS.md` is ready for owner review and later deletion.

### 2026-08-17 — Firestore remote schemas closed

- **Type:** decision
- **Story / Decision:** `F-12`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** remote `Vehicle` and `FuelEntry` documents now have exact closed key sets in `docs/CONTRACTS.md §16`. Unknown collections, missing keys, extra keys, local-only metadata and inconsistent `deleted` / `deletedAt` pairs are rejected by the Firestore contract.
- **Why:** the owner chose the strict schema option to make remote payload validation predictable and prevent malformed or locally-owned fields from entering Firestore.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md §10`, `docs/CONTRACTS.md §16`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `F-12`, `validPayload`, `closed remote schema`, forbidden local-only keys, `schemaVersion` and `deletedAt`; `git diff --check`.
- **Follow-ups / risks:** remaining audit finding is `F-15`.

### 2026-08-17 — Account deletion server operation accepted

- **Type:** decision
- **Story / Decision:** `F-11` / `D-23`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** account deletion now uses a Firebase Admin server operation. The app re-authenticates if needed, calls the authenticated server operation, the server deletes `fuelEntries`, then `vehicles`, then the Firebase Auth user, and only after success does the app clear local data.
- **Why:** the owner chose server/Admin deletion so store-required account deletion can physically purge remote data while client Firestore rules continue to reject hard deletes.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md §7` and `§12`, `docs/CONTRACTS.md §6`, `§11.1`, `§11.5` and `§16`, `docs/DECISION_BOARD.md`, `docs/TECHNICAL_PLAN.md`, `docs/adr/README.md`, `docs/adr/0024-account-deletion-server-admin.md`, `docs/BACKLOG.md`, `docs/SECURITY.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `D-23`, `F-11`, `E3-10`, `AuthError.AccountDeletionRemoteFailed`, account deletion and `allow delete`; `git diff --check`.
- **Follow-ups / risks:** remaining audit findings are `F-12` and `F-15`. `E3-10` must implement and test the server operation before release.

### 2026-08-17 — Malformed remote payload quarantine decided

- **Type:** decision
- **Story / Decision:** `F-16`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** quarantine now covers both unsupported future schema versions and malformed supported-version payloads. `QuarantineReason.MalformedPayload` and `QuarantineRecord` are canonical sync types.
- **Why:** the owner chose to keep malformed remote documents out of product tables without blocking cursor progress, provided the quarantine row is committed successfully.
- **Documents touched:** `AGENTS.md`, `docs/CONTRACTS.md §5`, `§9.5` and `§20.7`, `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md §8` and `§9`, `docs/BACKLOG.md`, `docs/DEFINITION.md`, `docs/SECURITY.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `quarantine`, `MalformedPayload`, `QuarantineReason`, `schemaVersion`, `malformed` and `18 sync tests`; `git diff --check`.
- **Follow-ups / risks:** remaining audit findings are `F-11`, `F-12` and `F-15`.

### 2026-08-17 — Odometer recompute set defined

- **Type:** decision
- **Story / Decision:** `F-14`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `odometerInconsistent` recomputation now has an exact minimal recompute set for create, update, delete and vehicle cascade delete. `currentOdometerKm` remains recomputed for the whole vehicle in the same transaction.
- **Why:** the owner chose the explicit minimal-set option to handle edits that move an entry in chronological order without recomputing the whole vehicle unnecessarily.
- **Documents touched:** `docs/CONTRACTS.md §3.1` and fuel repository side effects, `docs/SPECIFICATION.md §5` / R-1, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `odometerInconsistent`, `currentOdometerKm`, `successor`, `recompute` and `F-14`; `git diff --check`.
- **Follow-ups / risks:** `F-16` remains the next sync/database guardrail.

### 2026-08-17 — Pull overlap cursor anchor fixed

- **Type:** decision
- **Story / Decision:** `F-13`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** pull pagination now uses `startAt(overlapSince, "")` for the first page of an overlapped cycle and `startAfter(pageCursor.lastServerUpdatedAt, pageCursor.lastDocumentId)` for later pages.
- **Why:** the owner chose the concrete-anchor option to avoid an invalid `null` document-id cursor while preserving the 30-second overlap window.
- **Documents touched:** `docs/CONTRACTS.md §9.4` and `§16`, `docs/SPECIFICATION.md §9.3`, `docs/TECHNICAL_PLAN.md §8`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `startAt`, `startAfter`, `overlapSince`, `RemoteCursor` and `null`; `git diff --check`.
- **Follow-ups / risks:** `F-14` and `F-16` remain the next sync/database guardrails.

### 2026-08-17 — Local owner adoption ordering decided

- **Type:** decision
- **Story / Decision:** `F-10`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** first launch now attempts Firebase anonymous authentication automatically; `LOCAL_OWNER` is the offline/Auth-unavailable fallback. Local synchronized rows carry `localMutationSeq`, and adoption builds the initial outbox in dependency-group order and then by `localMutationSeq ASC, id ASC`.
- **Why:** the owner chose deterministic local mutation ordering without creating a staging outbox for `LOCAL_OWNER`, preserving the rule that the outbox stays empty until a real UID exists.
- **Documents touched:** `docs/CONTRACTS.md §3`, `§8`, `§11.2`, `§11.4` and repository rules, `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md §6`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `localMutationSeq`, `local_sequence`, `LOCAL_OWNER`, anonymous authentication and adoption; `git diff --check`.
- **Follow-ups / risks:** `F-13`, `F-14` and `F-16` remain the next sync/database guardrails.

### 2026-08-17 — Sync state storage decided

- **Type:** decision
- **Story / Decision:** `F-09`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `syncState` is now defined as a stored local control column. The outbox influences it but does not fully define it, and `LOCAL_OWNER + PENDING + no outbox` is an explicit legal state before adoption.
- **Why:** the owner chose the stored-state option to resolve the contradiction between first-launch offline writes and the outbox suppression rule.
- **Documents touched:** `docs/CONTRACTS.md §7`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `syncState`, `LOCAL_OWNER`, `PENDING` and `outbox`; `git diff --check`.
- **Follow-ups / risks:** `F-10` remains open and must define how local-owner mutations preserve causal ordering during adoption.

### 2026-08-17 — MVP settings reset semantics decided

- **Type:** decision
- **Story / Decision:** `F-17`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** MVP settings now reset during destructive local-data flows. Sign-out, anonymous "delete local data" and account deletion delete `user_settings`; defaults are recreated from locale with `analyticsEnabled = false`.
- **Why:** the owner confirmed that settings should not survive in the MVP, while settings sync through Google Play services / Android backup / iCloud belongs to future roadmap scope.
- **Documents touched:** `AGENTS.md`, README, `docs/SPECIFICATION.md §3`, `§5.3` and `§7 F-5`, `docs/CONTRACTS.md §3 UserSettings` and `§11.5`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `settings`, `user_settings`, `Google Play`, `Android backup`, `iCloud`, sign-out, local-data deletion and account deletion; `git diff --check`.
- **Follow-ups / risks:** future platform settings sync requires a new story or ADR before adding platform APIs, entitlements, manifest keys or dependencies.

### 2026-08-17 — Electric and hybrid fuel types deferred

- **Type:** decision
- **Story / Decision:** `D-4` / `F-08`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the MVP `FuelType` enum now excludes `ELECTRIC` and `HYBRID`; the canonical values are `GASOLINE`, `DIESEL`, `LPG`, `CNG` and `OTHER`. Electric and hybrid support is recorded as future roadmap scope requiring a dedicated energy model.
- **Why:** the owner confirmed that electric and hybrid vehicles are not included in the MVP, and supporting them correctly requires kWh input, mixed energy units, validation, Firestore rules and migration work.
- **Documents touched:** `AGENTS.md`, `docs/CONTRACTS.md §3`, `§5` and `§20.4`, `docs/SPECIFICATION.md §3` and `§12`, `docs/DECISION_BOARD.md`, `docs/TECHNICAL_PLAN.md §2`, `docs/adr/0005-vehicle-fuel-type-from-day-one.md`, `docs/adr/README.md`, `docs/BACKLOG.md`, `README.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `FuelType`, `ELECTRIC`, `HYBRID`, `energy model` and `D-4`; `git diff --check`.
- **Follow-ups / risks:** future electric/hybrid support requires a new story or ADR before enum/schema expansion.

### 2026-08-17 — Monetary and name data guardrails tightened

- **Type:** correction
- **Story / Decision:** `F-05`, `F-06`, `F-07`; `F-08` remains owner decision
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `MoneyInput` now selects the derived field only during validation, while persistence stores only the canonical monetary triple. `SUPPORTED_CURRENCY_CODES` is now the exact MVP currency allowlist, and vehicle name normalization / `nameFold` are defined as KMP-pure operations.
- **Why:** the previous contract implied an authoritative supplied monetary pair without storing it, left currency support dependent on an unspecified ISO lookup, and described `nameFold` differently from the normalization rules.
- **Documents touched:** `docs/CONTRACTS.md §2`, `§3`, `§5`, `§20.0.1` and `§20.3`, `docs/SPECIFICATION.md §5.3`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `MoneyInput`, `moneyInputKind`, `SUPPORTED_CURRENCY_CODES`, `MinorUnits`, `canonicalVehicleName`, `nameFold`, `FuelType`, `ELECTRIC` and `HYBRID`; `git diff --check`.
- **Follow-ups / risks:** `F-08` still requires owner choice: reject `ELECTRIC` / `HYBRID` in MVP validation or remove them until an energy-model story exists.

### 2026-08-17 — Swift-facing graph contract made explicit

- **Type:** correction
- **Story / Decision:** `D-2` / `F-02`, `F-03`, `F-04`, `F-18`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the Swift-facing ABI is now an explicit allowlist with `createSwiftAppGraph(isDebugBuild)`, `SwiftAppGraph`, concrete state holders, declared `UiState` classes and `UiMessage`; Kotlin-facing graph construction remains available through `createAppGraph(AppGraphDependencies)` but is hidden from the Objective-C header.
- **Why:** the previous contract mixed Kotlin construction APIs with Swift-exported APIs, exposed implementation-facing abstractions such as `SyncController`, and referenced state-holder / `UiState` types that were not declared.
- **Documents touched:** `docs/CONTRACTS.md §11.6`, `§14`, `§15.3`, `§18`, `§20.7` and `§20.10`, `docs/SPECIFICATION.md §8.4` and `§8.5`, `docs/TECHNICAL_PLAN.md §5`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `SwiftAppGraph`, `AppGraphDependencies`, `createAppGraph`, `AppGraph`, `SyncController`, `CoroutineScope`, `Outcome` and `AppError`; `git diff --check`.
- **Follow-ups / risks:** changes touch gated contract and specification documents and require human review before merge. Next open audit block is `F-05`, `F-06`, `F-07` and `F-08`.

### 2026-08-17 — Crash reporting module ownership aligned

- **Type:** correction
- **Story / Decision:** `D-21` / `F-01`, `F-19`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `CrashReporter` is now owned by `:core:crash`, included in `AppGraphDependencies` from Phase 0, and represented consistently in the module lists. Firebase Crashlytics remains a Phase 4 integration behind that abstraction.
- **Why:** the previous wording split ownership between `:core:common`, `:core:crash` and Phase 4 wiring, which made graph construction and module bootstrap ambiguous.
- **Documents touched:** `docs/CONTRACTS.md §11.6` and `§20.3.1`, `docs/SPECIFICATION.md §8.2` and `§8.3`, `docs/TECHNICAL_PLAN.md §4`, `docs/BACKLOG.md`, `README.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `CrashReporter`, `:core:crash` and `:integration:firebase-crashlytics`.
- **Follow-ups / risks:** changes touch gated contract and specification documents and require human review before merge. `F-02`, `F-03`, `F-04` and `F-18` remain open around the Swift-facing graph surface.

### 2026-08-17 — Empty owner-decision queue made explicit

- **Type:** correction
- **Story / Decision:** `E0-00`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the "Decisions Awaiting Owner Confirmation" section now has a clear no-open-decisions state instead of an empty table with a sentence where a row would normally be.
- **Why:** the future `contract-check` should not have to parse a malformed table to determine that no `Proposed` or `Pending` decisions exist.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/CONTRACTS.md §18`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual review of the decision-board empty state and the corresponding `contract-check` assertion.
- **Follow-ups / risks:** if a future `Proposed` or `Pending` decision is introduced, this section must become a real table with a `Needed by` story or phase.

### 2026-08-17 — D-14 future production project clarified

- **Type:** decision
- **Story / Decision:** `D-14`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** documentation now states that development uses only `carapp-dev` for now, but public release requires a separate production Firebase project.
- **Why:** the owner clarified that the future topology is two Firebase projects, development and production, while production project creation and its project ID remain deferred until release preparation.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/identifiers.md`, `docs/BACKLOG.md`, `docs/adr/0015-firebase-project-topology.md`, `docs/adr/README.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for production Firebase topology, production project ID and `D-14` wording.
- **Follow-ups / risks:** the production Firebase project ID remains an owner decision before `E4-04`; agents MUST NOT invent it.

### 2026-08-17 — Firebase identifier wording aligned with D-14

- **Type:** correction
- **Story / Decision:** `E0-00` / `D-14`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `E0-00` and the document map now require only the development Firebase project ID during development, while production Firebase topology and project IDs remain explicitly deferred by `D-14`.
- **Why:** the previous wording said "Firebase project IDs" in plural and could lead an agent to invent a production project ID to satisfy an already-closed owner-decision story.
- **Documents touched:** `AGENTS.md`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for Firebase project ID wording and production-project references.
- **Follow-ups / risks:** production Firebase topology remains an owner decision before `E4-04`.

### 2026-08-17 — E0-00 closure reflected as completed

- **Type:** story
- **Story / Decision:** `E0-00`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** orientation documents now state that owner decision closure is complete and that implementation starts at `E0-01`.
- **Why:** after `D-13` through `D-22` were accepted and pushed, keeping `E0-00` as the next implementation step would send future agents back through already-closed decisions.
- **Documents touched:** `README.md`, `docs/DEFINITION.md`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `E0-00`, `Proposed`, `Pending`, and owner-decision closure wording.
- **Follow-ups / risks:** `docs/AUDIT_GUARDRAILS.md` remains temporary until the owner finishes reviewing every audit-remediation theme.

### 2026-08-17 — D-21 crash reporting accepted

- **Type:** decision
- **Story / Decision:** `D-21`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Firebase Crashlytics was accepted for Phase 4 behind a new `CrashReporter` abstraction.
- **Why:** the owner selected Crashlytics for release hardening while keeping crash reporting separate from Kermit logging and Firebase Analytics.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §8` and `§12`, `docs/CONTRACTS.md §11.6` and `§20.3`, `docs/TECHNICAL_PLAN.md §2` and `§3`, `docs/BACKLOG.md`, `docs/versions-matrix.md`, `docs/SECURITY.md`, `docs/adr/0016-logging-kermit.md`, `docs/adr/0023-firebase-crashlytics.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-21`, Crashlytics and `CrashReporter` references.
- **Follow-ups / risks:** production Firebase topology remains deferred by `D-14` until `E4-04`.

### 2026-08-17 — D-20 localization implementation accepted

- **Type:** decision
- **Story / Decision:** `D-20`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** native Android and iOS resources were accepted for localization, with `UiState` carrying typed values only and no user-facing text.
- **Why:** the owner selected the existing recommendation; the UI is native, so native resource catalogues keep localization idiomatic without adding a shared resource dependency.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/adr/0020-localization-native-resources.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-20`, localization, native resources and `UiState` status references.
- **Follow-ups / risks:** `D-21` remains unresolved and is needed before `E4-04`.

### 2026-08-17 — D-18 coverage measurement accepted

- **Type:** decision
- **Story / Decision:** `D-18`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Kover was accepted as the coverage measurement tool with the existing thresholds: `:core:model` and `:core:common` at 90%, feature `domain` at 85%, and `:core:sync` at 80%.
- **Why:** the owner selected the existing recommendation so coverage targets become CI-enforced pass/fail criteria rather than review judgement.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/0022-coverage-kover.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-18`, Kover and coverage status references.
- **Follow-ups / risks:** Kover version compatibility remains validated in `E0-06`; `D-20` and `D-21` remain unresolved.

### 2026-08-17 — D-17 Flow testing helper accepted

- **Type:** decision
- **Story / Decision:** `D-17`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Turbine was accepted as the Flow testing helper, and the previous combined D-17/D-18 ADR was split into separate ADRs so each decision can carry its own status.
- **Why:** the owner selected Turbine; `D-18` remains unresolved, so sharing one ADR made status verification ambiguous.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/0019-flow-testing-turbine.md`, `docs/adr/0022-coverage-kover.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-17`, `D-18`, Turbine, Kover and ADR status references.
- **Follow-ups / risks:** Turbine compatibility remains validated in `E0-06`; `E0-05` remains blocked by `D-18`.

### 2026-08-17 — D-19 result channel accepted

- **Type:** decision
- **Story / Decision:** `D-19`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the owner accepted the custom `Outcome<T, E>` result channel in `:core:common`.
- **Why:** `kotlin.Result` cannot represent a typed error channel, and Arrow would add a dependency for one core type; the custom type matches the contracts exactly.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/0018-outcome-result-type.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-19`, `Outcome<T, E>` and result type status references.
- **Follow-ups / risks:** `D-17`, `D-18`, `D-20` and `D-21` remain unresolved; `E0-05` is still blocked by `D-17` and `D-18`.

### 2026-08-17 — D-16 architecture checks accepted

- **Type:** decision
- **Story / Decision:** `D-16`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Konsist was accepted for package-level architecture rules, with a custom Gradle configuration check for module-level rules.
- **Why:** the owner selected the existing recommendation; package rules are intra-module source rules that Gradle cannot see, while module rules remain cheaper to enforce from the Gradle graph.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/0017-architecture-checks-konsist.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-16`, Konsist and architecture check status references.
- **Follow-ups / risks:** Konsist version pinning remains part of `E0-06`; `D-17`, `D-18`, `D-19`, `D-20` and `D-21` remain unresolved.

### 2026-08-17 — D-15 logging telemetry boundary clarified

- **Type:** decision
- **Story / Decision:** `D-15`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** documentation now states that Kermit is the logging implementation only and does not replace `AnalyticsTracker`, Firebase Analytics, `CrashReporter` or Firebase Crashlytics.
- **Why:** the owner said Kermit should be used for Firebase Analytics and Crashlytics; the clarification preserves the intent that Firebase integrations may use Kermit-backed logging for diagnostics while keeping analytics events and crash reports on separate contracts.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/CONTRACTS.md §17`, `docs/adr/0016-logging-kermit.md`, and this log.
- **Verification:** manual cross-document search for Kermit, Analytics, Crashlytics, `AnalyticsTracker` and `CrashReporter`.
- **Follow-ups / risks:** `D-21` still decides whether Firebase Crashlytics is part of the MVP hardening phase.

### 2026-08-17 — D-15 logging implementation accepted

- **Type:** decision
- **Story / Decision:** `D-15`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Kermit was accepted as the logging implementation behind the common `Logger` abstraction.
- **Why:** the owner selected the existing recommendation; Kermit provides KMP platform sinks while remaining hidden behind the repository-owned logging contract.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/0016-logging-kermit.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-15`, Kermit and logging status references.
- **Follow-ups / risks:** Kermit version pinning remains part of `E0-06`; `D-16`, `D-17`, `D-18`, `D-19`, `D-20` and `D-21` remain unresolved.

### 2026-08-17 — D-22 application identifiers accepted

- **Type:** decision
- **Story / Decision:** `D-22`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the owner accepted `carApp` as the product name, `com.ruizurraca.carapp` as the Android application ID, Android namespace, iOS bundle identifier and shared package root, `Shared` as the iOS framework name, and `carapp-dev` as the development Firebase project ID.
- **Why:** these identifiers are required before `E0-01` creates platform projects and Firebase app registrations.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/identifiers.md`, `docs/adr/0021-application-identifiers.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-22`, `carApp`, `carapp-dev` and `com.ruizurraca.carapp`.
- **Follow-ups / risks:** production Firebase topology and production project identifiers remain deferred by `D-14` until `E4-04`.

### 2026-08-17 — D-22 application package prefix changed

- **Type:** decision
- **Story / Decision:** `D-22`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the proposed Android `applicationId`, Android namespace, iOS bundle identifier and shared package root changed from `com.davidru85.carapp` to `com.ruizurraca.carapp`.
- **Why:** the owner selected the `ruizurraca` package prefix.
- **Documents touched:** `docs/identifiers.md`, `docs/adr/0021-application-identifiers.md`, and this log.
- **Verification:** manual cross-document search for `com.davidru85.carapp` and `com.ruizurraca.carapp`.
- **Follow-ups / risks:** `D-22` still requires owner confirmation for the remaining identifier values, including the product name and development Firebase project ID.

### 2026-08-17 — D-14 Firebase project topology accepted for development

- **Type:** decision
- **Story / Decision:** `D-14`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Firebase topology changed from proposed `dev` plus `prod` projects to one development Firebase project plus the local emulator; production Firebase topology is deferred until release preparation.
- **Why:** the owner chose a simpler development setup because Firestore is only a backup and synchronization replica, while Room remains the source of truth.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/identifiers.md`, `docs/adr/0015-firebase-project-topology.md`, `docs/adr/README.md`, `docs/SECURITY.md`, and this log.
- **Verification:** manual cross-document search for `D-14`, Firebase topology, project ID, emulator and production references.
- **Follow-ups / risks:** development Firebase project ID remains governed by `D-22`. Production topology and production project IDs must be decided before `E4-04`.

### 2026-08-17 — D-13 Firestore location accepted

- **Type:** decision
- **Story / Decision:** `D-13`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Firestore location changed from the proposed `eur3` multi-region to the accepted `europe-west1` single region, and the documentation now states that Firestore is a backup and synchronization replica while Room remains the source of truth.
- **Why:** the owner chose `europe-west1` after reviewing the cost and availability tradeoff, because Firestore is not the primary product database.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §9` and `§12`, `docs/CONTRACTS.md §10`, `docs/TECHNICAL_PLAN.md §2`, `docs/identifiers.md`, `docs/adr/0014-firestore-location-europe-west1.md`, `docs/adr/README.md`, `docs/BACKLOG.md`, and this log.
- **Verification:** manual cross-document search for `D-13`, `eur3` and Firestore location references.
- **Follow-ups / risks:** `D-14`, `D-15`, `D-16`, `D-17`, `D-18`, `D-19`, `D-20`, `D-21` and `D-22` remain unresolved.

### 2026-08-17 — Agent handoff and contract-check guardrails tightened

- **Type:** milestone
- **Story / Decision:** —
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** added an explicit story intake protocol, required acceptance evidence in handoffs, and expanded `contract-check` to verify decision statuses, ADR status alignment, unresolved decision tracking and PR-template/handoff coverage.
- **Why:** the documentation already defined strong product contracts, but future agents still needed a more mechanical way to prove a story was Ready before implementation and Done after implementation.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md §12`, `docs/CONTRACTS.md §18`, `docs/DECISION_BOARD.md`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/README.md`, `docs/templates/agent-handoff.md`, `.github/pull_request_template.md`, and this log.
- **Verification:** manual documentation review with `rg` and targeted file reads. No product code or CI exists yet.
- **Follow-ups / risks:** changes touch gated documentation paths and require human review before merge.

### 2026-08-17 — Audit findings folded back into the documentation

- **Type:** milestone
- **Story / Decision:** —
- **Author:** Claude (Cowork session), on behalf of David Ruiz
- **What changed:** the 99 findings of the specification audit were applied across the whole documentation set. The main structural changes: document authority is now split into a behaviour axis (`docs/SPECIFICATION.md`) and a representation axis (`docs/CONTRACTS.md`) instead of a single linear ranking; `docs/CONTRACTS.md` gained `§20` with the complete canonical type definitions that were previously referenced but never declared; the field vocabulary was unified on the `…Km` / `…Scaled` / `…Minor` names; the monetary formulas were rewritten as exact integer arithmetic with golden test values; the pull query gained a `startAfter` anchor; LWW arbitration now compares `serverUpdatedAt` instead of the local clock; first launch was made offline-capable through the `LOCAL_OWNER` sentinel with a new adoption story; and human review gates, reading order and normative language were consolidated into `AGENTS.md` as the single entry point.
- **Why:** the definition package was complete in intent but not machine-decidable. Roughly twenty types appeared in normative signatures without being defined anywhere, two documents used incompatible field names, and several rules were mutually contradictory — enough that two competent agents would have produced two incompatible implementations, with no CI check able to catch it.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/DEFINITION.md`, `README.md`, `docs/CONTRIBUTING.md`, `docs/SECURITY.md`, `docs/adr/*`, `docs/identifiers.md`, `docs/versions-matrix.md`, `docs/templates/agent-handoff.md`, `.github/pull_request_template.md`, and this log.
- **Verification:** manual cross-document review. The automated `contract-check` that would enforce these invariants does not exist yet; it is specified in `docs/CONTRACTS.md §18` and implemented by `E0-05`.
- **Follow-ups / risks:** ten decisions are `Proposed` or `Pending` and require owner confirmation before Phase 0 starts — see `E0-00` and the "Decisions Awaiting Owner Confirmation" table in `docs/DECISION_BOARD.md`. Five new stories were added (`E0-00`, `E1-10`, `E2-06`, `E3-07`, `E3-08`) and are not yet estimated against real capacity. `docs/AUDIT_GUARDRAILS.md` remains in the repository as a temporary resolution log and should be deleted once the owner has reviewed the changes.

### 2026-08-17 — Specification audit performed

- **Type:** milestone
- **Story / Decision:** —
- **Author:** Claude (Cowork session), on behalf of David Ruiz
- **What changed:** a full architectural audit of the definition package produced `docs/AUDIT_GUARDRAILS.md`: 99 findings across governance, KMP architecture, data modeling, workflows and state, error handling, API contracts, security and verifiability, of which 16 were classified as blocking. Committed as `f8b70cb`.
- **Why:** the project is intended to be implemented mostly by AI agents, so ambiguity in the specification translates directly into divergent implementations rather than into questions.
- **Documents touched:** `docs/AUDIT_GUARDRAILS.md` (new, temporary).
- **Verification:** every finding cites a literal section of the documents as they stood on 2026-08-16.
- **Follow-ups / risks:** the audit file is temporary and is deleted once its findings are absorbed and reviewed.

### 2026-08-16 — Definition package completed

- **Type:** milestone
- **Story / Decision:** `D-0` to `D-11`
- **Author:** David Ruiz
- **What changed:** the initial definition package was written: specification, contracts, decision board, technical plan, backlog, agent operating guide, and thirteen ADRs covering the closed technical decisions. The repository remains greenfield, with no product code.
- **Why:** to make the project implementable by AI agents with predictable boundaries, before writing any code.
- **Documents touched:** all initial documents.
- **Verification:** —
- **Follow-ups / risks:** version pinning and real CI command validation deferred to Phase 0.
