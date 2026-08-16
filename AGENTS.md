# AI Agent Operating Guide - carApp

This file is the operating contract for AI agents working on this repository.

## Required Reading

Before implementation, read:

1. `SPECIFICATION.md`
2. `CONTRACTS.md`
3. `DECISION_BOARD.md`
4. `TECHNICAL_PLAN.md`
5. `BACKLOG.md`
6. `DEFINITION.md`
7. `AGENTS.md`

If documents conflict, `SPECIFICATION.md` wins.

## Language

Project specifications, code comments, commit messages, ADRs, backlog updates, and development artifacts must be written in technical English.

Conversation with the project owner may happen in Spanish, but repository artifacts remain English.

## Scope Discipline

Work only on the assigned backlog story. Do not implement:

- non-fuel expenses
- advanced charts
- export
- receipt photos or OCR
- reminders
- shared vehicles
- widgets, wearables, or web
- official fuel-price integrations
- App Check
- automatic account merging
- real-time Firestore listeners

Escalate any request that touches out-of-scope functionality.

## Architecture Rules

- Feature `domain` packages are Kotlin pure.
- Feature `domain` packages do not depend on Android, iOS, Firebase, GitLive, Koin, Room, Ktor, data, or presentation.
- Feature `data` packages do not depend on `:integration:*`.
- Feature `presentation` packages do not depend on feature `data`.
- Features do not depend on other features.
- `:core:sync` does not depend on integrations.
- `:shared` does not depend on integrations.
- Only `:wiring:firebase` constructs Firebase implementations.
- Firebase and GitLive types never leave `:integration:*`.

Architecture rules must be executable checks.

API, data, sync, error, logging, and platform boundary contracts in `CONTRACTS.md` are mandatory.

## Product Rules

- The UI observes only the local database.
- Every MVP write works without network access.
- IDs are client-generated UUID v4.
- Synchronized deletes are tombstones.
- Monetary values never use `Float` or `Double`.
- Consumption uses the full-to-full method.
- Average consumption is distance-weighted.
- SwiftUI and Compose contain no business logic.

## Technical Rules

- Gradle scripts use Kotlin DSL only.
- Dependency versions live only in `gradle/libs.versions.toml`.
- SKIE is applied only to `:shared`.
- Firestore offline persistence is disabled.
- Use GitLive 2.6.x, not 3.0 alpha.
- Use Koin KMP for wiring and constructor injection for implementation classes.
- Do not call Koin from domain, use cases, repositories, or state holder business logic.
- Do not add Ktor unless a new ADR introduces an HTTP API implementation.
- Data model changes require migrations and migration tests.
- Sync changes require convergence tests.
- Firestore rule changes require emulator tests.
- Public repository or use case contract changes require documentation updates in `CONTRACTS.md`.
- Library or stack decision changes require updates in `DECISION_BOARD.md` and the related ADR.

## Definition of Ready

Do not start a story if:

- acceptance criteria are unclear
- dependencies are unknown
- the story requires an unresolved decision
- the work expands MVP scope
- verification expectations are unknown
- a human review gate is missing

Escalate instead of guessing.

## Definition of Done

A story is done only when:

- acceptance criteria are met
- relevant tests pass
- lint is clean
- relevant builds pass
- architecture checks pass
- documentation is updated if behavior, decisions, or models changed
- residual risks are documented
- human review gates are identified

## Expected Handoff

At completion, report:

- backlog story implemented
- files changed
- decisions made
- verification run
- risks or follow-ups
- whether a human review gate applies

## Human Review Gates

Do not close these without human review:

- Phase 0 closure
- E0-07 walking skeleton
- E1-05 consumption calculation
- E3-01 Firestore rules
- E3-03 sync engine
- changes to `CONTRACTS.md`
- changes to `DECISION_BOARD.md`
- any change to stack, backend, auth, sync, architecture, money representation, or scope
