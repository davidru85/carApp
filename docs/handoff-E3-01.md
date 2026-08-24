# Agent Handoff - E3-01

## Story

`E3-01 - Firestore Structure and Security Rules - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — E3-01 creates Firestore rules, empty indexes and emulator tests;
  it does not create an application provider module under D-48.
- [x] Acceptance criteria reviewed — operation-specific authorization; exact closed Vehicle and
  FuelEntry payload validation; schema version, timestamp, tombstone and orphan behavior; every
  required emulator case; delta-pull query/index proof; protected CI execution.
- [x] Dependencies checked — Phase 0, E1-01 and E3-06 are merged; the development Firestore
  database exists in `europe-west1`; CI remains emulator-only and needs no Firebase credentials.
- [x] Decisions checked — D-0, D-5, D-9, D-13, D-14, D-23, D-31, D-34, D-40 and D-42 are
  `Accepted`; on 2026-08-24 the owner also accepted the exact official emulator stack, existing
  protected-job placement, client-configuration ownership and exact MVP schema version as D-46
  through D-49, then accepted the timestamp-only first-page cursor as D-50 after the pinned SDK
  rejected the former empty document-ID anchor, then accepted the repository-wide install-script
  block and the moderate Firebase CLI audit residual as D-51 and D-52. No `Proposed` or `Pending`
  decision blocks the story.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §9`, `§10` and `§11`;
  `docs/CONTRACTS.md §9.5`, `§16`, `§18` and `§20.0.1`; `docs/TECHNICAL_PLAN.md §7` and `§12`;
  `docs/BACKLOG.md E3-01`; `docs/SECURITY.md`; `docs/CONTRIBUTING.md`; and `AGENTS.md`.
- [x] Expected verification identified — focused RED/GREEN `node:test` cases through the
  Firestore emulator; `npm ci`; the complete rules suite; pull-query/index proof; Gradle contract,
  architecture, lint, coverage and multiplatform commands; GitHub's nine protected checks.
- [x] Human review gates identified before work — E3-01 is a gated story; `firestore/**`,
  `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `AGENTS.md`,
  `docs/adr/**` and `docs/versions-matrix.md` are gated paths; Firestore rule and remote-backend
  topics are gated. Owner review is required before merge.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Added the exact D-46 Node, Firebase CLI, Firebase JavaScript SDK and rules-unit-testing stack
  with a reproducible lockfile and repository-wide dependency install-script blocking under D-51.
- Added operation-specific Firestore rules for the two known owner-scoped collections, including
  exact closed schemas, immutable identity fields, server timestamps, numeric ranges, nullable
  fields, enum domains, tombstone shapes and exact MVP schema-version enforcement.
- Added 154 emulator tests covering authorization, schema rejection and acceptance, deletes,
  tombstones, orphan fuel entries and the complete delta-pull query with stable pagination.
- Added the empty composite-index configuration and proved that the required delta query executes
  without an additional index while retaining tombstones.
- Added the rules suite to the existing protected `contract-check` CI job and updated every
  affected contract, decision, security, version and story record.

## Acceptance Evidence

- `firestore/rules/main.rules` authorizes only authenticated owners at their own path, rejects
  unknown collections, validates create and update separately and rejects every client hard
  delete. Emulator tests prove cross-owner and unauthenticated rejection plus anonymous-account
  access to the matching owner path.
- Vehicle and fuel-entry tests independently remove every required key, add extra and local-only
  keys, vary primitive types, cross every numeric and enum boundary, exercise nullable fields and
  reject malformed tombstones. A `null` vehicle brand is accepted while an absent brand is not.
- Schema versions 0 and 2 are rejected and version 1 is accepted for both remote document types;
  literal client timestamps are rejected in favor of `request.time`.
- Fuel entries may reference a vehicle that is not present remotely, preserving the orphan-entry
  recovery contract.
- The real SDK query orders by `updatedAt` and document ID, applies D-50's timestamp-only first
  boundary and the full later-page cursor, paginates without duplicates or gaps, returns
  tombstones and requires no composite index.
- `package.json`, `package-lock.json`, `.npmrc`, `firebase.json` and the named CI step pin and run
  the accepted official emulator stack without Firebase credentials or dependency install scripts.
- D-48 is preserved: this story adds no application Firestore provider or persistence setting;
  E0-07 owns that executable client evidence.
- RED/GREEN history is preserved in the anonymous access, vehicle schema, fuel-entry schema and
  delta-query commit pairs listed in this branch.

## Out of Scope / Not Done

- E0-07 owns real Firestore client configuration and disabled-persistence evidence under D-48.
- E3-02 owns the complete `RemoteSyncSource`; E3-10 owns Admin account deletion hard deletes.
- No rule is deployed to the development project by this pull request.

## Files Changed

- Rules and emulator configuration: `firebase.json`, `firestore/firestore.indexes.json`,
  `firestore/rules/main.rules`, `firestore/tests/firestore.rules.test.mjs`.
- Reproducible test tooling: `package.json`, `package-lock.json`, `.npmrc`, `.gitignore`.
- Protected verification: `.github/workflows/ci.yml`.
- Normative and derived documentation: `AGENTS.md`, `docs/BACKLOG.md`, `docs/CONTRACTS.md`,
  `docs/DECISION_BOARD.md`, `docs/SECURITY.md`, `docs/SPECIFICATION.md`,
  `docs/TECHNICAL_PLAN.md`, `docs/versions-matrix.md`.
- Decision and story records: `docs/adr/README.md`, ADR-0047 through ADR-0053,
  `docs/adr/0043-provider-decoupling-precedes-first-integration.md`, this handoff and
  `docs/PROJECT_LOG.md`.

## Decisions Made

- D-46 through D-49 were accepted by the owner before implementation. D-50 was accepted after the
  official Firebase SDK rejected the former empty document-ID cursor. D-51 and D-52 were accepted
  after a clean npm install exposed version-dependent install-script behavior and moderate
  development-tool advisories. All are recorded in this pull request.
- TDD exemptions are limited to repository scaffolding, configuration, documentation, CI wiring
  and the empty index file. Every Firestore rule behavior was introduced through a focused failing
  emulator test followed by its passing implementation. No `SHOULD` rule was intentionally
  deviated from.

## Verification Run

- [x] `npm ci` — passed; installed 725 locked packages with dependency install scripts disabled.
  The five moderate, development-tool-only audit findings are the accepted D-52 residual.
- [x] `npm run test:firestore-rules` — passed all 154 emulator tests.
- [x] `./gradlew ktlintCheck detekt architectureCheck contractCheck
  :build-logic:convention:test koverVerify :androidApp:assembleDebug testAndroidHostTest
  iosSimulatorArm64Test` — passed all 273 actionable tasks. `contractCheck` matched 53 decisions to
  53 ADRs and reported only the two expected E0-07 pending checks.
- [x] `npm audit --json` — diagnostic completed; confirmed five moderate findings and no high or
  critical finding, with the retained transitive Firebase CLI tree governed by D-52.
- [x] `git diff --check` — passed.

## Contract Impact

- Corrected the conflicting schema-version sentence in `docs/CONTRACTS.md §9.5` under D-49 and
  replaced the SDK-invalid empty document-ID anchor with the D-50 timestamp-only first-page
  boundary; Firestore rules implement the resulting §16 contract.

## Decision Board Impact

- Added accepted decisions D-46 through D-52 and ADR-0047 through ADR-0053.

## Shared-Write Modules Touched

- None. `:core:database` is compiled and tested but not modified.

## Project Log Entry

- [x] Story and D-46 through D-52 entries appended.

## Risks or Follow-ups

- E0-07 follows this story and must configure the first real Firestore client with an in-memory
  cache on Android and iOS.
- Firebase CLI 15.28.1 retains the moderate development-tool-only audit residual accepted by D-52;
  re-evaluate it on the next CLI update or any high/critical advisory.

## Human Review Gate

- Applies: E3-01, every `firestore/**` change, gated normative/decision paths and the Firestore
  rule/backend topics. The owner must review and merge this pull request.
