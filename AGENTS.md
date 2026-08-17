# AI Agent Operating Guide - carApp

**This file is the entry point.** If you are an AI agent starting work on this repository, read this document first and in full. It is the single canonical source for normative language, the document map, document authority, the Definition of Ready and Done, and the human review gates. Other documents link here and MUST NOT restate these rules.

## Normative Language

The key words MUST, MUST NOT, REQUIRED, SHALL, SHOULD, SHOULD NOT, MAY and OPTIONAL are to be interpreted as described in RFC 2119.

- **MUST / MUST NOT** — enforced. A violation fails CI or blocks review. An agent that cannot satisfy a MUST stops and escalates; it does not work around it.
- **SHOULD / SHOULD NOT** — a deviation is permitted only if it is stated explicitly in the handoff under "Decisions Made", with the reason.
- **MAY** — free choice; the choice does not need to be reported.

Any normative sentence in this repository written in bare present tense ("the app warns the user", "IDs are UUID v4") has the force of MUST.

## Document Map

Everything an agent needs is in this repository. Read in this order; the order is didactic and carries no authority.

### Normative documents

| Order | Document | What it decides |
|-------|----------|-----------------|
| 1 | [SPECIFICATION.md](docs/SPECIFICATION.md) | **Behaviour.** Vision, product principles, scope, actors, domain model semantics, business rules R-1 to R-4, functional flows F-1 to F-5, architecture overview, synchronization behaviour, Firestore security requirements, non-functional requirements, closed decisions, glossary. |
| 2 | [CONTRACTS.md](docs/CONTRACTS.md) | **Representation.** Canonical data types and exact monetary arithmetic, entity schemas and database-owned invariants, ordering rules, validation and warning semantics, error taxonomy with stable codes, sync state machine, outbox and sync cycle contracts, `RemoteSyncSource`, auth and app graph contracts, repository and use case contracts, presentation contract, platform boundaries and the Swift-facing surface, Firestore rule and query contract, analytics, logging and privacy, CI checks, and **§20 canonical type definitions**. |
| 3 | [DECISION_BOARD.md](docs/DECISION_BOARD.md) | **Allowed technologies.** Sole registry of decision IDs (`D-n`), decision statuses, the library review matrix, the Firebase decoupling rule, and the list of decisions still awaiting owner confirmation. |

### Derived documents

| Document | What it provides |
|----------|------------------|
| [TECHNICAL_PLAN.md](docs/TECHNICAL_PLAN.md) | Module architecture, the dependency rule table that generates the architecture checks, local data model and outbox DDL, Firestore design, sync engine pseudocode, the 18 required sync tests, phases, risks and the verification strategy. |
| [BACKLOG.md](docs/BACKLOG.md) | Agent-sized stories with dependencies, acceptance criteria, execution order and a story index. Work is assigned one story at a time. |
| [DEFINITION.md](docs/DEFINITION.md) | Executive overview and orientation for humans. Creates no rules. |
| [README.md](README.md) | Public front page and documentation index. |
| [CONTRIBUTING.md](docs/CONTRIBUTING.md) | Branch, commit and pull request conventions. |
| [SECURITY.md](docs/SECURITY.md) | Vulnerability reporting, secret allowlist and denylist, accepted residual risks, privacy commitments. |

### Records and references

| Document | What it provides |
|----------|------------------|
| [PROJECT_LOG.md](docs/PROJECT_LOG.md) | Append-only history of the project: decisions taken, stories completed, problems found, direction changes. **Every completed story appends an entry.** Read the most recent entries before starting work; they are the fastest way to learn the current state. |
| [docs/adr/README.md](docs/adr/README.md) | ADR index, mapping every decision ID to its ADR file. |
| [docs/adr/](docs/adr/) | One ADR per decision, with context, options, consequences, constraints introduced and verification. |
| [docs/identifiers.md](docs/identifiers.md) | Application ID, bundle identifier, namespace, display name, Firebase project status, development Firebase project ID and Firestore location. Agents MUST NOT invent any of these. |
| [docs/versions-matrix.md](docs/versions-matrix.md) | Pinned toolchain versions and their compatibility relation, plus the reference devices and measurement methods for every performance target. |
| [docs/templates/agent-handoff.md](docs/templates/agent-handoff.md) | The handoff template every completed story fills in. |
| [.github/pull_request_template.md](.github/pull_request_template.md) | Pull request template, a superset of the handoff fields. |
| [.github/ISSUE_TEMPLATE/](.github/ISSUE_TEMPLATE/) | Issue templates for agent stories, bug reports and decision records. |

`docs/AUDIT_GUARDRAILS.md`, if present, is a **temporary** working document from a specification audit. It is not normative and will be deleted once its findings are absorbed. Do not treat it as a source of rules.

## Document Authority

Authority is split on two axes. A single linear ranking is NOT used, because `docs/SPECIFICATION.md` is deliberately less precise than `docs/CONTRACTS.md` and must not override it on representational detail.

| Axis | Question it answers | Authoritative document |
|------|---------------------|------------------------|
| **Behaviour** | What must the product do? Scope, business rules, flows, actors, non-functional targets. | `docs/SPECIFICATION.md` |
| **Representation** | How is it expressed in code? Types, signatures, field names, numeric semantics, formats, state machines, boundaries. | `docs/CONTRACTS.md` |

Rules:

- On any **representational** detail, `docs/CONTRACTS.md` MUST win, even against `docs/SPECIFICATION.md`. Where `docs/SPECIFICATION.md` is silent, vague, or uses a different name for the same field, `docs/CONTRACTS.md` is the implementable definition.
- On any **behavioural** detail, `docs/SPECIFICATION.md` MUST win.
- `docs/DECISION_BOARD.md` is authoritative for which libraries, services and technical options are allowed, and is the sole registry of decision IDs.
- `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/DEFINITION.md` and `README.md` are **derived**. They MUST NOT introduce a rule that is absent from the three documents above; if they do, the rule is void and the discrepancy MUST be escalated.
- A conflict that is genuinely behavioural, or that cannot be classified on either axis, MUST be escalated to the project owner. Agents MUST NOT resolve it themselves.
- Whichever document is corrected, every document repeating the same rule MUST be corrected in the same change.

## Language

Two rules apply, and they MUST NOT be confused. The boundary between them is the repository.

**Repository artifacts — technical English.** Documentation and code alike. This covers specifications, contracts, ADRs, backlog entries, project log entries, README and every other document; and source code, identifiers, code comments, KDoc, test names, commit messages, branch names, pull request descriptions and GitHub issues. There is no exception.

**Conversation with the project owner — Spanish (Spain).** Unless the owner states otherwise, an agent replies in Spanish using es-ES vocabulary and conventions. This covers chat replies, clarifying questions, escalation summaries delivered in conversation, and progress narration.

Consequences worth stating explicitly:

- A discussion held in Spanish is written into the repository in English. The language of the conversation never leaks into a file, a commit or an issue.
- A Spanish reply that quotes an English identifier, file path, error code or document section stays a Spanish reply. Those tokens MUST NOT be translated.
- Localized user-facing strings are the one place both languages legitimately appear in the repository (`docs/SPECIFICATION.md §11`). Their **keys** are English; their **values** exist in Spanish and English. This is not an exception to the rule above: a resource value is product content, not a development artifact.

## Owner Decisions

Some decisions belong to the project owner and MUST NOT be made by an agent, even provisionally:

- Application identifiers, bundle identifiers, package namespaces, display names.
- Firebase project names and the Firestore location.
- Any library, service or tool not already `Accepted` in `docs/DECISION_BOARD.md`.
- Anything that changes MVP scope.

If one of these is missing, stop and escalate. A story that depends on a decision still marked `Proposed` or `Pending` is not Ready.

## Scope Discipline

Work only on the assigned backlog story. The authoritative out-of-scope list is `docs/SPECIFICATION.md §3.2`, which currently excludes: non-fuel expenses, advanced charts, export, receipt photos and OCR, reminders, shared vehicles, widgets, wearables and web, official fuel-price integrations, App Check, automatic account merging, real-time Firestore listeners, remote settings synchronization, platform settings sync or backup through Google Play services / Android backup / iCloud, and electric or hybrid energy modelling.

Escalate any request that touches out-of-scope functionality.

## Architecture Rules

The normative table is `docs/TECHNICAL_PLAN.md §4`, which also generates the architecture check configuration. In summary:

- Feature `domain` packages are Kotlin pure and depend only on `:core:model` and `:core:common`.
- Feature `domain` packages do not depend on Android, iOS, Firebase, GitLive, Koin, Room, Ktor, their own `data` or their own `presentation`.
- Feature `data` packages do not depend on `:integration:*` **or on `:core:auth`**. The current owner reaches them through `OwnerContext` in `:core:common`.
- Feature `presentation` packages do not depend on feature `data`.
- Features do not depend on other features.
- `:core:sync` does not depend on integrations or features.
- `:shared` does not depend on integrations.
- Only `:wiring:firebase` constructs Firebase implementations, and it contains no product logic.
- Firebase and GitLive types never leave `:integration:*`.
- `vehicle.currentOdometerKm` and `fuel_entry.odometerInconsistent` are written only by `:core:database`.
- `expect`/`actual` declarations are `internal` and never appear in a public API; anything present in `AppGraphDependencies` is injected, not `expect`/`actual`.
- The `:shared` public API contains no value classes, type parameters or default arguments.

Architecture rules MUST be executable checks, and each rule MUST have a failing fixture proving the check fires.

All API, data, sync, error, logging and platform boundary contracts in `docs/CONTRACTS.md` are mandatory.

## Product Rules

- The UI observes only the local database.
- Every MVP write works without network access, and first launch works offline.
- Nothing is enqueued for synchronization while the owner is `LOCAL_OWNER`.
- IDs are client-generated UUID v4.
- Synchronized deletes are tombstones; client hard deletes are rejected by the Firestore rules. Account deletion hard deletes run only through the `D-23` Firebase Admin server operation.
- Monetary values never use `Float` or `Double`, and the exact integer formulas of `docs/CONTRACTS.md §2` are implemented literally.
- Consumption uses the full-to-full method, and the average is distance-weighted.
- The odometer inconsistency warning is a two-step protocol: the first save mutates nothing.
- `UiState` carries no user-facing text.
- SwiftUI and Compose contain no business logic.

## Technical Rules

- Gradle scripts use Kotlin DSL only.
- Dependency versions live only in `gradle/libs.versions.toml`, explained by `docs/versions-matrix.md`.
- SKIE is applied only to `:shared`.
- Firestore offline persistence is disabled.
- Use GitLive 2.6.x, not 3.0 alpha.
- Use Koin KMP for wiring and constructor injection for implementation classes.
- Do not call Koin from domain, use cases, repositories or state holder business logic.
- Do not add Ktor unless a new ADR introduces an HTTP API implementation.
- Do not add image loading until a story requires it; Coil is then the only approved library.
- `exportSchema = true`; `fallbackToDestructiveMigration` is FORBIDDEN in every build type.
- Data model changes require migrations and migration tests.
- Sync changes require convergence tests.
- Firestore rule changes require emulator tests.
- Public repository or use case contract changes require updating `docs/CONTRACTS.md` in the same change.
- Library or stack decision changes require updating `docs/DECISION_BOARD.md` and the related ADR.

## Definition of Ready

Do not start a story if:

- acceptance criteria are unclear,
- dependencies are unknown,
- the story depends on a decision still marked `Proposed` or `Pending` in `docs/DECISION_BOARD.md`,
- the work expands MVP scope,
- verification expectations are unknown,
- a human review gate is missing.

Escalate instead of guessing.

## Story Intake

Before changing implementation code for a story, an agent MUST record the ready check it used. The ready check contains:

- the exact backlog story ID and title,
- the acceptance criteria that will be satisfied,
- the dependency and decision rows checked,
- the normative sections that govern the work,
- the expected verification commands,
- the human review gates that apply, or `None`.

If a requested change is not tied to a backlog story, the agent may analyse, propose a story, or update definition documents, but it MUST NOT implement product code until the story is made explicit and Ready.

## Definition of Done

A story is done only when:

- acceptance criteria are met,
- acceptance criteria evidence is listed in the handoff,
- relevant tests pass, including the ones the story's acceptance criteria name explicitly,
- lint is clean and coverage thresholds hold,
- relevant builds pass,
- architecture checks and the contract check pass,
- documentation is updated if behaviour, decisions or models changed,
- residual risks are documented,
- human review gates are identified,
- the handoff is filled in from `docs/templates/agent-handoff.md`,
- **an entry has been appended to `docs/PROJECT_LOG.md`.**

## Expected Handoff

At completion, fill in `docs/templates/agent-handoff.md`. Every section of that template is REQUIRED; this document deliberately does not restate the field list. `.github/pull_request_template.md` MUST stay a superset of the same fields.

## Human Review Gates

This is the **canonical gate list**. No other document may define gates; they link here.

A change is gated when it matches at least one of the following. Gated changes MUST NOT be merged on agent judgement alone.

### Gated stories

- `E0-00` owner decision closure
- Phase 0 closure
- `E0-07` walking skeleton
- `E1-05` consumption calculation
- `E2-06` local owner adoption
- `E3-01` Firestore security rules
- `E3-10` account deletion server operation
- `E3-03` synchronization engine

### Gated paths

Enforced by `CODEOWNERS` plus required review, not by prose:

- `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `AGENTS.md`
- `docs/adr/**`, `docs/identifiers.md`, `docs/versions-matrix.md`
- `firestore/**`
- `core/sync/**`, `core/auth/**`, `core/database/**`
- `core/model/**` money and scaled-value types

### Gated topics

Any change to: MVP scope, technical stack or pinned versions, remote backend, authentication, the synchronization algorithm or its state machine, module boundaries and dependency rules, money representation, the error taxonomy, logging and privacy rules, the Firestore rule contract, or the Swift-facing API surface.
