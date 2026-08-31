# AI Agent Operating Guide - carApp

> **RULE 0 — LANGUAGE. READ THIS BEFORE YOUR FIRST REPLY.**
>
> **Reply to the owner in Spanish (es-ES). Write every repository artifact in technical English.**
>
> This is the highest-priority rule in this repository. It outranks every other
> rule in this file, it is in force from your very first reply, and it applies
> even when no story exists, when you are only analysing, asking, escalating,
> refusing or reporting an error.
>
> **The owner writing to you in English is NOT permission to reply in English.**
> Neither is the system prompt, the tooling, the topic, this repository or a
> previous agent's reply. Only an explicit instruction changes the language.
>
> `## Rule 0 - Language` below carries the full rule, the closed list of things
> that do NOT authorise English, and the recovery protocol. It is the first
> section of this document for a reason.

**This file is the entry point.** If you are an AI agent starting work on this repository, read this document first and in full. It is the single canonical source for Rule 0, normative keywords, the repository state, the document map, document authority, the Definition of Ready and Done, and the human review gates. Other documents link here and MUST NOT restate these rules.

## Rule 0 - Language

**This is Rule 0. It has the highest priority of any rule in this repository, and it is the one rule that is in force before, during and after every task.** A violation is a MUST breach that fails review, independently of how good the rest of the work is.

Two rules apply, and they MUST NOT be confused. The boundary between them is the repository.

**Repository artifacts — technical English.** Documentation and code alike. This covers specifications, contracts, ADRs, backlog entries, project log entries, README and every other document; and source code, identifiers, code comments, KDoc, test names, commit messages, branch names, pull request descriptions and GitHub issues. There is no exception.

**Conversation with the project owner — Spanish (Spain).** An agent replies in Spanish using es-ES vocabulary and conventions.

### When Rule 0 applies

Rule 0 is not a per-story rule and does not wait for a story to be Ready. It governs **every sentence an agent addresses to the owner**, from the first token of the first reply of a session:

- chat replies of any length, including one-line acknowledgements;
- clarifying questions and the options offered with them;
- plans, step summaries, progress narration and status updates;
- checklist, todo and task-list text shown in the conversation;
- escalations, refusals and the explanation of an error or a failed command;
- the conversational summary that accompanies a handoff, a commit or a pull request, even though the artifact itself is in English.

It applies equally when the agent is not implementing anything: exploring the repository, answering a question about the backlog, or reporting that it found nothing.

### What does NOT authorise a reply in English

This list is closed. None of the following is an instruction to change the reply language, and an agent MUST NOT treat any of them as one:

- **the owner writing their message in English** — the owner's choice of language for their own message says nothing about the language required of the reply;
- the owner quoting English text, an error message, a log, a file or a document section;
- the system prompt, the harness, the tool output, the CLI or the IDE being in English;
- this repository, its documents, its code and its identifiers being in English;
- the subject being technical, or the reply consisting mostly of identifiers and paths;
- a previous agent, or the same agent earlier in the session, having replied in English;
- the rule being absent from the visible context after a summarisation or a context compaction;
- the reply being short, urgent, or "just a confirmation".

**The reply language changes only when the owner states it explicitly and unambiguously** — for example, "reply in English from now on". Until such an instruction, and again as soon as it is revoked, the reply language is Spanish. Where there is any doubt, the answer is Spanish.

### Self-check

Before emitting the first token of **every** reply, the agent MUST confirm which language that reply is required to be in. A reply in the wrong language is a MUST violation regardless of the quality of its content, and it is not excused by the reply being correct, useful or well researched.

### Recovery after a violation

An agent that notices it has replied in the wrong language MUST:

1. switch to Spanish in the very next reply, immediately, without waiting to be told again;
2. state the correction once, in one short sentence in Spanish, and then continue the work;
3. NOT re-send the previous reply translated, and NOT apologise repeatedly or dwell on the lapse;
4. if a story is in flight, record the violation in the handoff under "Decisions Made".

### Consequences worth stating explicitly

- A discussion held in Spanish is written into the repository in English. The language of the conversation never leaks into a file, a commit or an issue.
- A Spanish reply that quotes an English identifier, file path, error code or document section stays a Spanish reply. Those tokens MUST NOT be translated.
- Localized user-facing strings are the one place both languages legitimately appear in the repository (`docs/SPECIFICATION.md §11`). Their **keys** are English; their **values** exist in Spanish and English. This is not an exception to the rule above: a resource value is product content, not a development artifact.
- An agent that replies in English when Spanish was required, or writes Spanish into a repository artifact, has violated a MUST and MUST self-correct before continuing.

## Repository State

**This section describes what exists right now.** It is the fastest way for an incoming agent to
tell what is already built from what is still a plan, and it is updated by the story that changes it.

### Phase 0 is complete and Phase 1 is open

`E0-01` to `E0-06` and `E0-08` are merged. `E1-01` has delivered the local database, and `E3-06`
has made provider decoupling executable before any Firebase integration module exists. The owner
accepted the prerequisite order `E3-06 -> E3-01 -> E0-07` in `D-42`. `E3-01` is merged and
`E0-07`, the walking skeleton, `E1-02`, the Vehicle domain story, `E1-03`, the Vehicle data story,
`E1-04`, the Fuel Entry domain story, and `E1-05`, the human-gated consumption calculation story,
`E1-06`, Fuel Entry data, and `E1-07`, Android Vehicle UI, are complete. `E1-08`, Android Fuel
Entry UI, is the next planned Phase 1 story.

### Delivery status and remaining work

- **Completed:** all Phase 0 stories; `E1-01` through `E1-07`; the pulled-forward `E3-06` and
  `E3-01` prerequisites; and the `E0-07` walking-skeleton gate, including D-73 cleanup evidence.
- **Next:** `E1-08`, Android Fuel Entry UI.
- **Remaining Phase 1:** `E1-08` through `E1-10`.
- **Remaining Phase 2:** `E2-01`, `E2-02`, `E2-03`, `E2-06`, `E2-04`, `E2-07` and `E2-05`.
- **Remaining Phase 3:** `E3-10`, `E3-11`, `E3-02`, `E3-03`, `E3-08`, `E3-04`, `E3-12`,
  `E3-05`, `E3-07` and `E3-09`. `E3-01` and `E3-06` are already complete.
- **Remaining Phase 4:** `E4-01` through `E4-04`.

`docs/BACKLOG.md` contains the acceptance criteria and dependency order for every remaining story.

### Modules that exist

```text
build-logic/       convention plugins, an included build
:core:model        identifiers, money, scaled values, the canonical arithmetic of CONTRACTS §2
:core:common       Outcome, AppError, platform abstractions, named constants, shared UI primitives
:core:analytics    AnalyticsTracker and the closed AnalyticsEvent hierarchy
:core:crash        CrashReporter and its no-op
:core:testing      deterministic fakes for every Phase 0 abstraction
:core:database     SQLDelight schema v1, typed queries, mutation facade and bundled SQLite driver
:core:auth         staged auth contracts for the E0-07 application graph
:core:sync         staged sync contracts for the E0-07 application graph
:integration:firebase-auth       GitLive Firebase Auth adapter used by the E0-07 slice
:integration:firebase-firestore  GitLive Firestore backup adapter used by the E0-07 slice
:feature:vehicle   Vehicle domain, local repository and shared presentation state holders
:feature:fuel      Fuel Entry domain, SQLDelight local repository, projections, R-3 consumption and presentation
:feature:session   final module shell staged for the Swift-facing surface
:shared            provider-free graph contracts, Swift facade and exported shared declarations
:shared:testing    KMP app-graph test factory, consumed from commonTest only
:wiring:firebase   staged Firebase provider composition contract
:composition:ios   sole Shared framework producer and iOS composition root
:androidApp        the Android host app and Compose Vehicle flow
```

`:integration:firebase-analytics` and `:integration:firebase-crashlytics` do **not** exist yet.
D-55 stages only the two provider modules required by the E0-07 slice; later stories create or
complete the remaining integration behavior in place.

### Live development infrastructure

- Firebase project `davidruiz-carapp-dev` uses Firestore Native mode in `europe-west1`.
- Development billing is enabled with the D-66 EUR 10 notification-only budget and the tested
  project-local cutoff. `stopBilling` is an active Gen 2 function on Node.js 22.
- App Check is enforced for Authentication and Firestore; local debug providers are restricted to
  Debug emulator/simulator paths and do not ship.
- D-73 automatically removed the first eligible Functions artifacts while the deployed function
  remained active. The exact evidence is in
  `docs/runbooks/development-firebase-cost-controls.md`.
- No production Firebase project exists. E4-04 owns production identifiers, billing, alerts and
  release configuration; production MUST NOT inherit the development cutoff.

### Creating a module

Apply a convention plugin; do not repeat its configuration. A new shared module is:

```kotlin
plugins {
    id("carapp.kmp.library")
}

dependencies {
    "commonMainApi"(projects.core.common)
}
```

`carapp.kmp.library` sets Android plus the `iosArm64` and `iosSimulatorArm64` targets (`D-37`), the JDK toolchain, the host test runner,
`kotlin-test`, coroutines, ktlint, detekt and Kover. The Android namespace is **derived** from the
Gradle path (`D-24`), so a module MUST NOT declare one. The other plugins are
`carapp.android.application`, `carapp.compose`, `carapp.skie` (refuses to apply outside
`:composition:ios`)
and `carapp.sqldelight`.

### Build and verify

Every non-instrumented CI job runs the tasks in this command:

```bash
./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug testAndroidHostTest iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test
```

Individually:

| Command | What it proves |
|---------|----------------|
| `./gradlew architectureCheck` | The module graph obeys `docs/TECHNICAL_PLAN.md §4`. The rules are **parsed from that table**, so editing it changes the check. |
| `./gradlew :build-logic:convention:test` | Every architecture rule still fires. A rule with no fixture is a rule that passes everything. |
| `./gradlew contractCheck` | The repository invariants of `docs/CONTRACTS.md §18`. **Read its output**: assertions it cannot verify yet print `PENDING` with the story that unblocks them, rather than passing silently. |
| `./gradlew koverVerify` | Coverage thresholds of `D-18`. |
| `./gradlew ktlintCheck detekt` | Style. Baseline suppression files are forbidden and CI fails if one appears. |
| `./gradlew testAndroidHostTest iosSimulatorArm64Test` with the four current D-75 `-x` paths from the complete command above | Common tests on both the JVM and Kotlin/Native. D-75 derives the exception from the transitive Firebase project graph and compares it with the declared paths; Android-host tests and explicitly listed real-host XCUITest paths remain required. |
| `./gradlew :androidApp:connectedDebugAndroidTest` on the D-84 API 36 emulator | The protected `android-instrumented-tests` job exercises the Compose Vehicle creation flow. |

The iOS app is built from `iosApp/` with `xcodebuild`; see `docs/handoff-E0-06.md` for the exact
invocation, including the `ARCHS=arm64` argument the project currently needs.

### What is enforced, and what is not

`main` is protected: ten required checks, a pull request, no force pushes, no branch deletion
(`D-31`, `D-34`, `D-84`). Administrator enforcement is off, so the owner can bypass; nobody else
can. D-28 feature-layer package rules and their firing fixtures are executable through Konsist.

E0-07 made the Objective-C golden-header job executable on macOS and made
`testAppGraphDependencies` parity executable. `contractCheck` currently reports no `PENDING`
assertions.

`provider-decoupling` is executable: the required macOS job excludes the explicit Firebase
provider registry and tests the remaining graph on Android host and `iosSimulatorArm64` (`D-45`).

`contractCheck` prints the live list; trust its output over this one.

### Story records

Each completed story leaves `docs/handoff-<STORY>.md`, filled in from
`docs/templates/agent-handoff.md`. They carry the acceptance evidence, what was deliberately not
done, and the follow-ups. **Read the handoff of any story you are extending**, and read the most
recent entries of `docs/PROJECT_LOG.md` before starting anything.

Handoffs preserve the state observed when their story merged. When a later story closes one of
their follow-ups, a dated **Closure update** at the top records the current interpretation without
rewriting the original evidence. Current open work is listed in this Repository State section and
in `docs/BACKLOG.md`, not inferred from an older handoff's historical "Not Done" section.

## Normative Keywords

The key words MUST, MUST NOT, REQUIRED, SHALL, SHOULD, SHOULD NOT, MAY and OPTIONAL are to be interpreted as described in RFC 2119.

- **MUST / MUST NOT** — enforced. A violation fails CI or blocks review. An agent that cannot satisfy a MUST stops and escalates; it does not work around it.
- **SHOULD / SHOULD NOT** — a deviation is permitted only if it is stated explicitly in the handoff under "Decisions Made", with the reason.
- **MAY** — free choice; the choice does not need to be reported.

Any normative sentence in this repository written in bare present tense ("the app warns the user", "IDs are UUID v4") has the force of MUST.

This section defines the **weight** of a rule. `## Rule 0 - Language` above defines the **language** a rule, a reply or an artifact is written in. The two are different concerns and MUST NOT be confused.

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
| [docs/SECURITY_ADVISORY_REGISTER.md](docs/SECURITY_ADVISORY_REGISTER.md) | Dated, expiring accepted dependency advisories, executable evidence and current-state review history. |
| [docs/runbooks/development-firebase-cost-controls.md](docs/runbooks/development-firebase-cost-controls.md) | Development billing-state history, alerts, budget cadence, destructive cutoff evidence and manual recovery. |
| [docs/DOCUMENTATION_AUDIT.md](docs/DOCUMENTATION_AUDIT.md) | Closed record of the documentation audit of the definition package. **Non-normative and historical**: all 20 findings are applied. It is kept only so that the `AUDIT-NN` IDs cited by `docs/PROJECT_LOG.md` resolve. Do not treat it as a source of rules or as a list of open work. |
| [docs/DESIGN.md](docs/DESIGN.md) | Design entry point. Describes the two platform design systems in general terms and indexes every design asset in `design/stitch/`. **Non-normative**: it creates no rules and decides nothing about behaviour, representation or allowed technologies. |
| [docs/templates/agent-handoff.md](docs/templates/agent-handoff.md) | The handoff template every completed story fills in. |
| [docs/handoff-*.md](docs/) | One per completed story: acceptance evidence, what was not done, follow-ups. `docs/E0-01-READY-CHECK.md` is the same record for `E0-01`, which predates the current handoff format. |
| [.github/pull_request_template.md](.github/pull_request_template.md) | Pull request template, a superset of the handoff fields. |
| [.github/ISSUE_TEMPLATE/](.github/ISSUE_TEMPLATE/) | Issue templates for agent stories, bug reports and decision records. |

An audit or working document, if present, is **temporary and non-normative**, and is deleted once
its findings are absorbed into the normative documents. `docs/AUDIT_GUARDRAILS.md` was one and no
longer exists; `docs/DOCUMENTATION_AUDIT.md` is its successor and is now fully absorbed. Never
treat such a document as a source of rules.

The `design/` folder is **design tooling, not documentation**. It holds the Figma build scripts
and the Stitch design descriptions for the UI. It has no authority over behaviour or contracts,
and an agent MUST NOT derive a rule from it. `docs/DESIGN.md` is its entry point and states the
same limit. Where a design asset and a normative document disagree, the normative document wins
and the discrepancy MUST be escalated.

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

## Owner Decisions

Some decisions belong to the project owner and MUST NOT be made by an agent, even provisionally:

- Application identifiers, bundle identifiers, package namespaces, display names.
- Firebase project names and the Firestore location.
- Any library, service or tool not already `Accepted` in `docs/DECISION_BOARD.md`.
- Anything that changes MVP scope.

If one of these is missing, stop and escalate. A story that depends on a decision still marked `Proposed` or `Pending` is not Ready.

## Scope Discipline

Work only on the assigned backlog story. The authoritative out-of-scope list is
`docs/SPECIFICATION.md §3.2`, which currently excludes: non-fuel expenses, advanced charts,
export, receipt and odometer photos and OCR, local or on-device AI text recognition, fuel and
maintenance reminders, operating-system notifications, shared vehicles, widgets, wearables and
web, official fuel-price integrations, Cloud Functions-mediated product read/write validation
beyond the `D-23` and `D-63` account identity and data-deletion operations, automatic
account merging, simultaneous multi-device use, active multi-device synchronization,
remote-database-as-source-of-truth operation, real-time Firestore listeners, remote settings
synchronization, platform settings sync or backup through Google Play services / Android backup /
iCloud, and electric or hybrid energy modelling. The foreground-only anonymous-account retention
notices selected by `D-62`, D-66 development billing containment and D-67 App Check enforcement
are in scope. The D-66 billing cutoff is infrastructure control and never reads product data.

Escalate any request that touches out-of-scope functionality.

## Architecture Rules

The normative table is `docs/TECHNICAL_PLAN.md §4`, which also generates the architecture check configuration. In summary:

- Feature `domain` packages are Kotlin pure and depend only on `:core:model` and `:core:common`.
- Feature `domain` packages do not depend on Android, iOS, Firebase, GitLive, Koin, SQLDelight, SQLite, Ktor, their own `data` or their own `presentation`.
- Feature `data` packages do not depend on `:integration:*` **or on `:core:auth`**. The current owner reaches them through `OwnerContext` in `:core:common`.
- Feature `presentation` packages do not depend on feature `data`.
- Features do not depend on other features.
- `:core:sync` does not depend on integrations or features.
- `:shared` does not depend on integrations.
- `:composition:ios` depends on `:shared`, `:wiring:firebase` and the D-85 / D-97 export-only
  `:feature:vehicle` / `:feature:fuel` / `:core:common` declarations, owns the single `Shared` framework, contains
  no product logic and never depends directly on `:integration:*`.
- `:shared:testing` depends only on `:shared` and `:core:testing`; consumers depend on it only from
  `commonTest`.
- No module under `:core` depends on `:shared` or `:shared:testing`.
- Only `:wiring:firebase` constructs Firebase implementations, and it contains no product logic.
- Firebase and GitLive types never leave `:integration:*`.
- `vehicle.currentOdometerKm` and `fuel_entry.odometerInconsistent` are written only by `:core:database`.
- Synchronized entity writes from outside `:core:database` use `DatabaseMutations`; direct calls to
  generated SQLDelight entity-mutation functions are forbidden (`D-38`).
- `expect`/`actual` declarations are `internal` and never appear in a public API; anything present in `AppGraphDependencies` is injected, not `expect`/`actual`.
- The `:shared` public API contains no value classes, type parameters or default arguments.

Architecture rules MUST be executable checks, and each rule MUST have a failing fixture proving the check fires.

All API, data, sync, error, logging and platform boundary contracts in `docs/CONTRACTS.md` are mandatory.

## Product Rules

- The UI observes only the local database.
- The MVP supports one active device per account; the remote database is used only for backup and
  permanent-account recovery on a new device. An unlinked anonymous identity is device-bound.
- Every MVP write works without network access, and first launch works offline.
- Nothing is enqueued for remote backup while the owner is `LOCAL_OWNER`.
- IDs are client-generated UUID v4.
- Synchronized deletes are tombstones; client hard deletes are rejected by the Firestore rules.
  Account and orphan cleanup hard deletes run only through the `D-23` / `D-63` Firebase Admin
  operations and their shared deletion service.
- Firestore remote documents use the closed schemas of `docs/CONTRACTS.md §16`; unknown collections, extra keys and local-only metadata are rejected.
- Monetary values never use `Float` or `Double`, and the exact integer formulas of `docs/CONTRACTS.md §2` are implemented literally.
- Consumption uses the full-to-full method, and the average is distance-weighted.
- The odometer inconsistency warning is a two-step protocol: the first save mutates nothing.
- `UiState` carries no user-facing text.
- SwiftUI and Compose contain no business logic.

## Technical Rules

- Test-driven development (TDD) is compulsory for product code: the failing test is written before the code that makes it pass, per behavior unit, with the anti-paraguas clause of `docs/SPECIFICATION.md §11`. Exemptions are limited to the list in that section and MUST be declared in the handoff. The TDD commit and push workflow (red, green, refactoring, PR) of `docs/SPECIFICATION.md §11` is a MUST unless the owner exempts a story explicitly.
- Gradle scripts use Kotlin DSL only.
- Gradle and Kotlin dependency versions live only in `gradle/libs.versions.toml`. Node-only
  dependencies live in exact root or `functions/` `package.json` entries plus adjacent lockfiles.
  Every pin is explained by `docs/versions-matrix.md` and MUST NOT be repeated as a literal in CI.
- npm dependency lifecycle scripts are disabled repository-wide by `.npmrc` (`D-51`). CI and local
  verification MUST NOT override that policy. A required install script needs a superseding owner
  decision after its exact package, version and command are reviewed.
- SKIE is applied only to `:composition:ios`.
- Firestore offline persistence is disabled.
- App Check is enforced for Authentication and Firestore. Production providers are App Attest on
  iOS and Play Integrity on Android; debug providers and tokens are restricted to local/CI-specific
  builds and never ship.
- The development project uses the D-66 EUR 10 alerts-only budget and project-local 2nd gen billing
  cutoff. Budgets are notification-only, reporting is delayed, and production MUST NOT use the
  automatic cutoff.
- The single `functions/` package pins Node.js 22 while D-63 needs one 1st gen Auth trigger. Runtime
  and trigger debt are one TD-01 item with a 2027-10-31 hard deadline.
- Use GitLive 2.6.x, not 3.0 alpha.
- Use Koin KMP for wiring and constructor injection for implementation classes.
- Do not call Koin from domain, use cases, repositories or state holder business logic.
- Do not add Ktor unless a new ADR introduces an HTTP API implementation.
- Do not add image loading until a story requires it; Coil is then the only approved library.
- SQLDelight `.sq` files are the committed schema source, `verifyMigrations` remains enabled, and destructive schema recreation is FORBIDDEN.
- Data model changes require migrations and migration tests.
- Remote backup or recovery changes require backup and recovery tests.
- Firestore rule changes require emulator tests.
- Public repository or use case contract changes require updating `docs/CONTRACTS.md` in the same change.
- Library or stack decision changes require updating `docs/DECISION_BOARD.md` and the related ADR.
- **Every decision taken while implementing a story is a decision.** A build-model choice, an identifier convention, a policy such as how `targetSdk` is pinned, or any option the owner is asked to choose between MUST get a new decision ID in `docs/DECISION_BOARD.md`, its own ADR in `docs/adr/`, and matching rows in `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`, in the same pull request. Recording it only in a handoff, a commit message or `docs/PROJECT_LOG.md` does NOT count: those are history, and the next agent is not bound by them.

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
- the human review gates that apply, or `None`,
- Rule 0 is acknowledged: chat replies for this story are in Spanish (es-ES) and every artifact it produces is in technical English.

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
- every decision taken during the story has a decision ID, an ADR, and identical rows in the four mirroring documents,
- residual risks are documented,
- human review gates are identified,
- the handoff is filled in from `docs/templates/agent-handoff.md`,
- **an entry has been appended to `docs/PROJECT_LOG.md`.**
- **Rule 0 held for the whole story:** every chat reply was in Spanish (es-ES) and every repository artifact is in technical English. A violation that occurred and was corrected is recorded in the handoff under "Decisions Made".

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
