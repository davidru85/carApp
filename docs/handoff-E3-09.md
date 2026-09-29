# Agent Handoff - E3-09

## Story

`E3-09 - Firebase Analytics Integration - S` (`docs/BACKLOG.md`).

Implement `:integration:firebase-analytics`, the Firebase-backed `AnalyticsTracker` from `E0-08`,
and bind it in `:wiring:firebase`.

## Ready Check

- Backlog story: `E3-09` (`docs/BACKLOG.md`, story section and story index row `E3-09 Firebase
  Analytics integration | 3 | S | —`).
- Acceptance criteria reviewed: the five criteria listed under `E3-09` —
  1. every `AnalyticsEvent` leaf maps to a Firebase event name and parameter set, exhaustively and
     with no `else` branch;
  2. collection disabled at startup and enabled only after an explicit opt-in, including on a fresh
     install, with a test proving nothing is buffered while disabled;
  3. no forbidden payload of `docs/CONTRACTS.md §16.1` can be sent, proven by a test that no
     parameter value derives from odometer, volume, cost, notes, entity IDs or the UID;
  4. only `:wiring:firebase` constructs the implementation, and no Firebase type crosses the module
     boundary;
  5. excluding the module leaves the app building and testing on the `:core:analytics` no-op, per
     `E3-06`.
- Dependencies checked: `E0-08` (the `:core:analytics` abstraction, merged) is the hard
  prerequisite; `E3-06` (provider decoupling proof, merged) governs criterion 5; `E2-02`,
  `E2-03`, `E2-04`, `E2-05` (merged) supply the auth, onboarding and departure surfaces the
  existing event emitters sit in. No dependency is missing.
- Decisions checked: no decision is `Proposed` or `Pending` for this story. Applies `D-10`
  (Firebase Analytics behind `AnalyticsTracker`, `Accepted`), `D-65` (Firebase Apple 11.8.0 as the
  exact GitLive compatibility pin), `D-75` (standalone Native-test exemption derived from the
  transitive project graph), `D-43`/`D-44` (closed explicit provider registry), `D-178`/`D-179`
  (`:wiring:firebase` declaration shape; integration implementations named only in wiring), `D-24`
  (Android namespace derived from the Gradle path), `D-161` (account-deletion analytics boundary),
  `D-105` (continuous progress documentation). `E3-09` introduces new decisions; they are recorded
  before merge — see "Decisions Made".
- Normative sections reviewed: `docs/CONTRACTS.md §16.1` (analytics contract, opt-in semantics,
  forbidden payloads, `setUserProperties` cadence, the no-emission-from-domain-or-data rule),
  `§20.9` (the closed `AnalyticsEvent` hierarchy, `SyncStatusCategory`,
  `ConversionFailureReason`, `DeletionFailureReason`, `AnalyticsUserProperties`, `CountBucket`
  bounds, the `AuthError` mappings), `§9.9` (aggregate status and the connectivity rule the
  `SyncStatus -> SyncStatusCategory` collapse must follow), `§18` (CI and branch protection),
  `§11.6` (app graph contract), `§20.10`; `docs/SPECIFICATION.md §3.1` (settings surface),
  `§11` (privacy: analytics off by default; TDD rule and its exemptions), `§7 F-1`; 
  `docs/TECHNICAL_PLAN.md §3` (module inventory), `§4` (dependency rules and the product-logic
  definition for `:wiring:firebase`), `§13` (`TD-01`).
- Expected verification: the complete CI task command of `AGENTS.md`, plus the module-scoped
  `:integration:firebase-analytics:testAndroidHostTest` and the `contract-check` /
  `architecture-check` guards.
- Human review gates identified before work: `E3-09` is not a gated story. The change touches
  gated topics (analytics payload and privacy rules; module boundaries; the Firebase Apple
  compatibility set) and, in the decisions it introduces, gated paths (`docs/adr/**`,
  `docs/CONTRACTS.md` if amended, `docs/versions-matrix.md`). Merge is therefore owner-reviewed,
  not agent-decided.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every artifact it
  produces is in technical English.

## In-Progress Checkpoint

- Date: 2026-09-29.
- Branch and base: `story/E3-09-firebase-analytics-integration`, based on `main` at `02dffc54`.
- Current phase and latest commit: intake. This handoff is the intake artifact required by `D-105`.
- Push and pull-request status: not pushed; no pull request. No product code written yet.
- Completed since the previous checkpoint: reconnaissance. Verified over the network that
  `dev.gitlive:firebase-analytics:2.6.0` is published on Maven Central with `android`, `iosArm64`
  and `iosSimulatorArm64` variants and the API `Firebase.analytics` ->
  `FirebaseAnalytics.logEvent(name, parameters)`, `setUserProperty(name, value)`,
  `setAnalyticsCollectionEnabled(enabled)`. Established that the iOS app must add the
  `FirebaseAnalytics` SwiftPM product in `iosApp/project.yml` (the `Shared` framework is static), and
  that `:integration:firebase-analytics` must leave
  `ArchitectureChecker.NOT_YET_INTRODUCED_MODULES` and join D-75's root set together with its
  `ci.yml` exclusion.
- Verification evidence and known failures: no build or test has been run for this story yet.
- Open decisions or blockers: one owner decision is outstanding — the emission scope of `E3-09`
  (see "Decisions Made" and the pause raised with the owner). Everything else is unblocked and
  autonomous.
- Exact next step: raise the emission-scope decision with the owner, then begin the RED phase for
  the exhaustive `AnalyticsEvent` mapping and the disabled-by-default gate.

## Scope Completed

- None yet. Intake only.

## Acceptance Evidence

-

## Out of Scope / Not Done

- Nothing recorded yet.

## Files Changed

- `docs/handoff-E3-09.md` (new).

## Decisions Made

- None taken yet. The decisions this story will introduce are recorded here when taken, each with
  its ADR and the four mirroring rows.

## Verification Run

- Not run yet.

## Contract Impact

- Pending. `docs/CONTRACTS.md §16.1` may need an amendment for the emission boundary; that is a
  gated path and is recorded here when it happens.

## Decision Board Impact

- Pending. New decisions will be registered in `docs/DECISION_BOARD.md` with their ADRs.

## Shared-Write Modules Touched

`:core:database` may be modified by only one story at a time.

- `:core:database` — modified: two read-only owner-scoped count queries and a read-only accessor. No
  schema change, so no database-version bump and no `.sqm` migration. `core/database/.story-lock`
  does not exist, so no other in-flight story owns the module (`docs/handoff-E3-07.md` records that
  `E3-07` left it absent).

## Project Log Entry

- [ ] Entry appended

## Risks or Follow-ups

- The `§16.1` obligation "an `E3-09` fixture MUST assert the call cadence" needs a
  `setUserProperties` call site in shared presentation. Feature `presentation` packages cannot reach
  `:core:analytics` (`docs/TECHNICAL_PLAN.md §4`), so the call site belongs in `:shared`
  orchestration, not in the feature state holders — the same constraint `D-155` records for
  `SessionStateHolder`.
- Seven of the thirteen event leaves have no emitter in the repository today
  (`OnboardingStarted`, `OnboardingCompleted`, `AnonymousSignInSelected`, `PermanentSignInSelected`,
  `VehicleCreated`, `FuelEntryCreated`, `SyncStatusChanged`). Whether `E3-09` emits them, or only
  maps them, is the outstanding owner decision.
- `docs/adr/0076` states the Firebase Apple coverage list MUST be reviewed whenever the Firebase
  surface grows; adding the `FirebaseAnalytics` iOS product triggers that review.

## Human Review Gate

Applies: the change touches the gated topics "logging and privacy rules" and "module boundaries",
and the gated path `docs/adr/**` (plus `docs/versions-matrix.md` if a pin row changes).
