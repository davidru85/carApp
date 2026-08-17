# carApp

Cross-platform mobile app for Android and iOS to track vehicle costs.

The MVP is intentionally limited to **fuel expenses**: users can create vehicles, log refueling events, review their history, and calculate real-world fuel consumption in **L/100 km**. Later phases may add maintenance, insurance, taxes, and other expense types, but they are out of scope for the MVP.

> **Project status:** definition phase complete and audited. Owner decision closure (`E0-00`) is complete; there is no product code yet. Implementation starts with `E0-01` in `docs/BACKLOG.md`.

## Start here

**If you are an AI agent, open [AGENTS.md](AGENTS.md) first.** It is the entry point: it defines document authority, normative language, the human review gates, and a map of every document in the repository.

## Documentation

| Document | Purpose |
|----------|---------|
| [AGENTS.md](AGENTS.md) | Entry point and operating contract. Authority, normative language, gates, document map. |
| [SPECIFICATION.md](docs/SPECIFICATION.md) | Normative for **behaviour**: scope, domain model, business rules, flows, architecture, sync, non-functional requirements. |
| [CONTRACTS.md](docs/CONTRACTS.md) | Normative for **representation**: canonical types, API contracts, persistence formats, state machines, errors, boundaries. |
| [DECISION_BOARD.md](docs/DECISION_BOARD.md) | Normative for **allowed technologies**. Sole registry of decision IDs. |
| [TECHNICAL_PLAN.md](docs/TECHNICAL_PLAN.md) | Module architecture, sync design, risks, verification strategy. |
| [BACKLOG.md](docs/BACKLOG.md) | Agent-sized implementation stories with dependencies and acceptance criteria. |
| [PROJECT_LOG.md](docs/PROJECT_LOG.md) | Append-only record of decisions, milestones and completed stories. |
| [DEFINITION.md](docs/DEFINITION.md) | Executive overview and orientation. |
| [CONTRIBUTING.md](docs/CONTRIBUTING.md) | Contribution rules and PR expectations. |
| [SECURITY.md](docs/SECURITY.md) | Vulnerability reporting, security-sensitive areas, secret handling. |
| [docs/adr/README.md](docs/adr/README.md) | ADR index, one per decision ID. |
| [docs/identifiers.md](docs/identifiers.md) | Application identifiers, Firebase projects and Firestore location. |
| [docs/versions-matrix.md](docs/versions-matrix.md) | Pinned toolchain versions, compatibility relation, performance measurement baselines. |
| [docs/templates/agent-handoff.md](docs/templates/agent-handoff.md) | Handoff template every completed story fills in. |

Document authority is **not** a single linear ranking. Behaviour is decided by `docs/SPECIFICATION.md`; representation is decided by `docs/CONTRACTS.md`, which wins on any type, name, format or numeric detail. The full rule is in [AGENTS.md](AGENTS.md).

## Product Principles

| ID | Principle | Practical implication |
|----|-----------|-----------------------|
| P1 | Minimal logging friction | A fuel entry should be recordable in under 15 seconds with the fewest required fields possible. |
| P2 | Always works | The MVP must be fully usable without connectivity, including at first launch. |
| P3 | No entry barrier | Users can start anonymously and later convert to a permanent account without data loss. |
| P4 | Cloud provider portability | Firebase must remain behind integration boundaries and must not leak into domain, data contracts or presentation. |

MVP success metric: a user can create a vehicle, log refueling events offline, and get a reliable average consumption value after at least two valid full-to-full segments. This normally requires at least three full-tank refueling events.

## MVP Scope

The authoritative scope lists are in [SPECIFICATION.md §3](docs/SPECIFICATION.md). In summary: fuel expenses only, offline-first including first launch, anonymous-capable authentication with Google and Apple conversion, vehicle and fuel entry CRUD, full-to-full consumption, Firestore synchronization, minimal settings, Spanish and English.

Out of scope: non-fuel expenses, advanced charts, export, receipt photos and OCR, reminders, shared vehicles, widgets and wearables, official fuel-price integrations, App Check, automatic account merging, real-time Firestore listeners, and remote settings synchronization.

Any task touching out-of-scope functionality must be rejected or escalated before implementation.

## Core Product Rule: Consumption Calculation

The MVP uses the **full-to-full** method. A segment is the interval between two consecutive full-tank refueling events:

```text
liters      = sum of liters for entries in the segment, including partial refuels
distanceKm  = final full-tank odometer - initial full-tank odometer
consumption = liters / distanceKm * 100
```

Vehicle average consumption is weighted by distance, not the arithmetic mean of segment values:

```text
sum(validSegmentLiters) / sum(validSegmentDistanceKm) * 100
```

The exhaustive list of conditions that invalidate a segment, the ordering used to select the previous full tank, and the exact arithmetic are normative in [CONTRACTS.md §4](docs/CONTRACTS.md) and [§20.6](docs/CONTRACTS.md). This section deliberately does not restate them.

## Architecture Summary

Kotlin Multiplatform is used for domain, data, sync and shared presentation logic. UI remains native on each platform.

| Layer | Technology |
|-------|------------|
| Shared logic | Kotlin Multiplatform |
| Android UI | Jetpack Compose |
| iOS UI | SwiftUI |
| Shared presentation | KMP state holders exposing `StateFlow<UiState>` plus intent functions |
| Build | Gradle Kotlin DSL, version catalog, convention plugins in `build-logic` |
| Local database | Room 3.0 KMP with `androidx.sqlite:sqlite-bundled` |
| Remote backend | Cloud Firestore as a remote replica, never as UI source of truth |
| Authentication | Firebase Authentication through GitLive 2.6.x behind `AuthClient` |
| Metrics | Firebase Analytics behind `AnalyticsTracker`, off by default |
| Crash reporting | Firebase Crashlytics behind `CrashReporter`, added in Phase 4 |
| iOS interop | SKIE, applied only in `:shared` |
| Dependency injection | Koin KMP for wiring, constructor injection for implementation classes |
| Logging | Kermit behind `Logger` |

Planned module structure:

```text
build-logic/
gradle/libs.versions.toml

:core:model
:core:common
:core:database
:core:auth
:core:sync
:core:analytics
:core:crash
:core:testing

:integration:firebase-auth
:integration:firebase-firestore
:integration:firebase-analytics
:integration:firebase-crashlytics

:feature:vehicle
:feature:fuel
:feature:session

:shared
:wiring:firebase
:androidApp
iosApp/
firestore/
```

Each feature is one Gradle module with internal `domain`, `data` and `presentation` packages. Module boundaries are enforced by a Gradle configuration check; package boundaries by source analysis. Both run in CI, and every rule has a failing fixture proving the check fires.

## Implementation Phases

| Phase | Goal | Main gate |
|-------|------|-----------|
| 0 - Foundations | Owner decisions closed, KMP skeleton, convention plugins, core modules, quality tooling, CI, ADRs | Android and iOS build in CI; architecture rules fail correctly |
| 0.5 - Walking skeleton | One real screen crosses native UI, shared presentation, Room, Firestore and anonymous auth | Data written on Android appears on iOS and vice versa |
| 1 - Local persistence | Vehicles and fuel entries are useful offline | Consumption calculation fully tested and reviewed |
| 2 - Authentication | Anonymous, local owner adoption, Google, Apple, conversion, sign-out, account deletion | Adoption and conversion preserve data |
| 3 - Backend and sync | Firestore rules, integration, sync engine, wiring, sync status | Convergence and provider decoupling are executable checks |
| 4 - MVP hardening | Settings, accessibility, i18n, performance, release preparation | Store-readiness checklist complete |

## Human Review Gates

Defined canonically in [AGENTS.md](AGENTS.md). No other document defines gates.

## Contribution Rules

See [CONTRIBUTING.md](docs/CONTRIBUTING.md). The short version:

- Do not implement out-of-scope functionality.
- Do not introduce Firebase, GitLive, Koin, Ktor, Room, Android or iOS APIs into feature `domain` packages.
- Do not introduce dependencies between features, and do not make `:shared` depend on `:integration:*`.
- Do not use `Float` or `Double` for money.
- Do not observe Firestore directly from the UI.
- Do not add Ktor during the MVP unless a new ADR introduces an HTTP API implementation.
- Every data model change requires a migration and a migration test.
- A story is not done until tests, lint, relevant builds, acceptance criteria, the handoff and the `docs/PROJECT_LOG.md` entry are complete.
