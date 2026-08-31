# Project Definition - carApp

> Executive overview. **Derived document**: it summarises and orients, it does not create rules. Every normative statement lives in `docs/SPECIFICATION.md` (behaviour), `docs/CONTRACTS.md` (representation) or `docs/DECISION_BOARD.md` (allowed technologies). See `AGENTS.md` for authority, normative language, gates and the document map.
>
> This document previously restated the domain model, business rules and technical stack in slightly different words, which made it a second source of truth. Those sections have been replaced by pointers on purpose.

## 1. Summary

`carApp` is a cross-platform Android and iOS mobile app for tracking vehicle costs. The MVP is limited to fuel expenses and one active device per account. Users can create vehicles, log refueling events, review history, calculate real consumption, keep their data available offline, and back it up for recovery on a new device.

Phase 0 is complete and Phase 1 is open: `E1-01` through `E1-08`, `E3-06`, `E3-01` and `E0-07` have delivered the SQLDelight database, complete local Vehicle and Fuel Entry repositories, Fuel Entry validation, reviewed full-to-full consumption, executable provider decoupling, reviewed Firestore rules, the native walking skeleton and the Android Vehicle and Fuel Entry flows. `E1-09`, iOS UI, is next. See `AGENTS.md` §`Repository State` for what exists and how to verify it.

## 2. MVP Objective

The MVP must let a user:

- Start without creating a permanent account, with no connectivity required at first launch.
- Create at least one vehicle.
- Log fuel entries quickly.
- Use all MVP features without network access.
- Review fuel entry history.
- See per-segment and average fuel consumption using the full-to-full method.
- Convert an anonymous account to Google or Apple without data loss.
- Recover backed-up data on another device.
- Use one active device per account; simultaneous multi-device use is future scope.

Success metric: a user can create a vehicle and log fuel entries offline, obtain a reliable average consumption after at least two valid full-to-full segments, then reconnect, back up the data and recover it on a clean device without manual repair.

## 3. Document Map

The canonical map with links lives in `AGENTS.md`. In short:

| Document | Role |
|----------|------|
| `AGENTS.md` | Entry point for any agent. Authority, normative language, gates, document map. |
| `docs/SPECIFICATION.md` | Normative for behaviour. |
| `docs/CONTRACTS.md` | Normative for representation. |
| `docs/DECISION_BOARD.md` | Normative for allowed technologies. Sole registry of decision IDs. |
| `docs/TECHNICAL_PLAN.md` | Derived. Architecture, sync design, risks, verification. |
| `docs/BACKLOG.md` | Derived. Agent-sized stories. |
| `docs/PROJECT_LOG.md` | Append-only history of what actually happened. |
| `docs/DESIGN.md` | Entry point for the design assets. Non-normative; creates no rules. |
| `docs/DEFINITION.md` | This document. Orientation only. |

## 4. Product Principles

| ID | Principle | Operational rule |
|----|-----------|------------------|
| P1 | Minimal logging friction | Fast entry, sensible defaults, warnings over blocking when safe. |
| P2 | Always works | The local database is the user-facing source of truth. Connectivity is optional, including at first launch. |
| P3 | No entry barrier | Anonymous login is a first-class path and can be converted later. |
| P4 | Cloud provider portability | Firebase stays replaceable because it is isolated behind interfaces and integration modules, and the decoupling is an executable check. |

## 5. Scope

Scope is defined once, in `docs/SPECIFICATION.md §3`. Do not rely on any other list.

In one sentence: fuel expenses only, offline-first, anonymous-capable, two platforms, Spanish and
English, with nothing that requires charts, export, images, operating-system notifications, sharing
or additional expense types; the only reminder-like behavior is the foreground anonymous-account
retention notice selected by `D-62`.

Any out-of-scope request requires a specification change and human approval.

## 6. Domain, Rules and Flows

- Actors, domain model and field semantics: `docs/SPECIFICATION.md §4`–`§5`.
- Field names, types, scales and formats: `docs/CONTRACTS.md §3` and `§20`.
- Business rules R-1 to R-4: `docs/SPECIFICATION.md §6`, with the exact arithmetic and orderings in `docs/CONTRACTS.md §2` and `§4`.
- Functional flows F-1 to F-5: `docs/SPECIFICATION.md §7`.

Two consequences that are easy to miss and that shaped several contracts:

- The odometer warning is a **two-step protocol**, not a passive warning: the first save mutates nothing.
- Nothing is enqueued for synchronization while the owner is `LOCAL_OWNER`, because those snapshots would be rejected by the Firestore rules.

## 7. Technical Stack and Architecture

Stack: `docs/SPECIFICATION.md §8.1`. Modules and dependency rules: `docs/SPECIFICATION.md §8.2`–`§8.3`, expanded in `docs/TECHNICAL_PLAN.md §3`–`§4`. Exact versions: `docs/versions-matrix.md`, declared only in `gradle/libs.versions.toml`.

GitLive 3.0 alpha is out of scope for the MVP. Kotlin, SKIE and Xcode versions are pinned during Phase 0 and not upgraded during the MVP without a new decision.

## 8. Synchronization and Security

Design and algorithms: `docs/TECHNICAL_PLAN.md §8`, normative contract in `docs/CONTRACTS.md §7`–`§9`. Firestore structure and rules: `docs/SPECIFICATION.md §10` and `docs/CONTRACTS.md §16`.

Accepted limitation: active multi-device editing is not a supported MVP workflow. If the same account is edited on multiple devices, last-write-wins can lose one whole-document update; this is documented in `docs/SPECIFICATION.md §9.5`.

## 9. Implementation Phases

| Phase | Status | Goal | Gate |
|-------|--------|------|------|
| 0 | Complete | Owner decisions closed, KMP bootstrap, convention plugins, core modules, quality tooling, CI, architecture and contract checks, ADRs, version matrix | Android and iOS compile in CI; every implemented architecture rule has a failing fixture proving it fires |
| 1 (opening) | Complete | `:core:database` and the walking skeleton across native UI, shared state holder, SQLDelight, Firestore and real anonymous auth | The local/remote Vehicle path works under the same retained anonymous session on both native hosts; the Swift-facing surface constraints hold. Permanent-account cross-device recovery moves to E3-12 |
| 1 | Active; E1-09 next | Vehicles, fuel entries, consumption, settings persistence, native UI, all offline | Local repositories and the Android Vehicle and Fuel Entry flows are complete; real-iPhone performance evidence remains explicit for E4-03 under D-80 |
| 2 | Planned | Auth abstractions, Firebase Auth, onboarding, local owner adoption, conversion, sign-out, account deletion | Adoption and conversion preserve data; collision never destroys data without explicit confirmation |
| 3 | Partial; E3-01 and E3-06 complete | Firestore rules, integration, backup engine, app graph wiring, backup status, purge, decoupling proof | Recovery tests and the emulator tests pass; provider decoupling is an executable check |
| 4 | Planned | Settings UI, accessibility, i18n, performance, release builds, store readiness | Release builds installable; store requirements complete |

## 10. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| iOS/KMP/SKIE/database toolchain friction | High | SQLDelight compatibility proof in `E1-01`, early walking skeleton, macOS CI and pinned versions. |
| Swift export rejects the shared API shape | Medium | Explicit surface constraints plus a committed Objective-C header golden file. |
| Silent data loss in backup or recovery | Critical | Common engine, required tests, deterministic simulation, debug screen. |
| Data loss at the `LOCAL_OWNER` boundary | Critical | Outbox suppressed before a real UID exists, plus an idempotent adoption story. |
| Incorrect Firestore rules | Critical | Emulator tests before any production deployment. |
| Scope creep | Medium | Explicit out-of-scope list and escalation rule. |
| Business logic duplicated in native UI | Medium | Shared state holders and package-level architecture checks. |

## 11. Definition of Ready and Done

Both are defined canonically in `AGENTS.md` and MUST NOT be restated elsewhere. The two additions worth highlighting:

- A story is not Ready if any decision it depends on is still `Proposed` or `Pending` in `docs/DECISION_BOARD.md`.
- A story is not Done until an entry has been appended to `docs/PROJECT_LOG.md`.

## 12. Definition Phase Checklist

- [x] MVP objective and success metric defined.
- [x] In-scope and out-of-scope functionality defined, in exactly one place.
- [x] Actors, domain model, business rules and functional flows defined.
- [x] Technical stack, module architecture and dependency rules defined and made checkable.
- [x] Offline-first sync strategy defined, including the `LOCAL_OWNER` boundary.
- [x] Firestore security strategy defined, including range validation and hard-delete rejection.
- [x] Non-functional requirements defined with measurement methods.
- [x] Implementation backlog defined, with a story for every normative contract.
- [x] Human review gates defined canonically in one place.
- [x] Agent rules, Definition of Ready and Definition of Done defined.
- [x] ADRs materialized in `docs/adr/`.
- [x] Contractual guardrail layer materialized in `docs/CONTRACTS.md`, including canonical type definitions.
- [x] Library and technical decision board materialized in `docs/DECISION_BOARD.md`.
- [x] Specification audit completed and folded back into the documents.
- [x] Owner confirmation of every `Proposed` decision (`E0-00`).
- [ ] Version catalog created and versions pinned (`E0-06`).
- [ ] Real CI commands validated (`E0-05`).

The remaining unchecked items belong to Phase 0 because they require the project skeleton, real tooling and CI validation.
