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
  14), `§20.3`, `§20.10`; `docs/TECHNICAL_PLAN.md §3`, `§4` and `§4.1`; `docs/SPECIFICATION.md`
  `§8.2`, `§8.5` and `§11`; `docs/SECURITY.md`; and the `E0-04`, `E0-07`, `E3-03`, `E3-06` and
  `E3-17` handoffs.
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

- Date: 2026-09-18
- Branch and base: `story/E3-08-app-graph-and-firebase-wiring`, based on `origin/main` at `588ad00`
  (the `E3-17` merge).
- Current phase and latest commit: intake complete; ready check recorded. No commit yet.
- Push and pull-request status: not pushed; no pull request.
- Completed since the previous checkpoint: branch created; the backlog story, the governing
  contract sections and the current implementation of `AppGraph`, `SwiftAppGraph`,
  `:wiring:firebase`, `:shared:testing` and the architecture checker were read.
- Verification evidence and known failures: none yet. Baseline recorded: `architectureCheck`
  reports `16 rules from docs/TECHNICAL_PLAN.md §4, 23 modules` and passes; `contractCheck`
  reports zero `PENDING` assertions and passes.
- Open decisions or blockers: none blocking. The technical decisions this story raises are
  collected for the owner and are recorded under "Decisions Made" as they are settled.
- Exact next step: write the failing fixtures of the RED phase.

## Scope Completed

- None yet.

## Acceptance Evidence

- None yet.

## Out of Scope / Not Done

- `E3-04` owns enforcing `SYNC_POST_WRITE_DEBOUNCE_MS` and `SYNC_MIN_AUTOMATIC_INTERVAL_MS`; this
  story does not touch the trigger constants.
- `E3-09` owns `:integration:firebase-analytics` and `E4-04` owns
  `:integration:firebase-crashlytics`; `:wiring:firebase` keeps the no-op analytics tracker and
  the `CrashReporter` no-op already bound by `E0-08`.
- `E3-12` owns the permanent-account cross-device recovery proof.

## Files Changed

- Pending.

## Decisions Made

- Pending.

## Verification Run

- Pending.

## Contract Impact

- Pending.

## Decision Board Impact

- Pending.

## Shared-Write Modules Touched

- None anticipated: `:core:database` is not modified by this story.

## Project Log Entry

- [ ] Entry appended to `docs/PROJECT_LOG.md` when the story completes.

## Risks or Follow-ups

- Pending.

## Human Review Gate

- Applied: `docs/CONTRACTS.md` and `docs/adr/**` are gated paths, and the Swift-facing API surface
  and the module dependency rules are gated topics.
