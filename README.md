# carApp

Cross-platform mobile app for Android and iOS to track vehicle costs.

The MVP is intentionally limited to **fuel expenses**: users can create vehicles, log refueling events, review their history, and calculate real-world fuel consumption in **L/100 km**. Later phases may add maintenance, insurance, taxes, and other expense types, but they are out of scope for the MVP.

> Project status: definition phase complete enough to start implementation. There is no product code yet. Implementation starts with Phase 0 in `BACKLOG.md`.

## Documentation

| Document | Purpose |
|----------|---------|
| [DEFINITION.md](DEFINITION.md) | Executive and operational definition package: scope, features, stack, phases, agent rules, risks, and quality gates. |
| [SPECIFICATION.md](SPECIFICATION.md) | Normative product and technical specification. Domain model, business rules, flows, architecture, sync, and non-functional requirements. |
| [TECHNICAL_PLAN.md](TECHNICAL_PLAN.md) | Closed technical decisions, module architecture, sync design, risks, and verification strategy. |
| [BACKLOG.md](BACKLOG.md) | Agent-sized implementation stories with dependencies and acceptance criteria. |
| [AGENTS.md](AGENTS.md) | Operating protocol for AI agents working on this repository. |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contribution rules, PR expectations, and human review gates. |
| [SECURITY.md](SECURITY.md) | Vulnerability reporting and security-sensitive areas. |
| [docs/adr/0000-template.md](docs/adr/0000-template.md) | Template for recording ADRs D-0 through D-9 during Phase 0. |

Conflict resolution order:

1. `SPECIFICATION.md`
2. `TECHNICAL_PLAN.md`
3. `BACKLOG.md`
4. `DEFINITION.md`
5. `AGENTS.md`

## Product Principles

| ID | Principle | Practical implication |
|----|-----------|-----------------------|
| P1 | Minimal logging friction | A fuel entry should be recordable in under 15 seconds with the fewest required fields possible. |
| P2 | Always works | The MVP must be fully usable without connectivity. |
| P3 | No entry barrier | Users can start anonymously and later convert to a permanent account without data loss. |
| P4 | Cloud provider portability | Firebase must remain behind integration boundaries and must not leak into domain, data contracts, or presentation. |

MVP success metric: a user can create a vehicle, log refueling events offline, and get a reliable average consumption value after the third full-tank refueling event.

## MVP Scope

In scope:

- Anonymous authentication, Google sign-in, Apple sign-in on iOS.
- Anonymous account conversion without data loss.
- Vehicle CRUD.
- Fuel entry CRUD.
- Fuel price and total cost calculation.
- Full-to-full fuel consumption calculation.
- Local-first persistence.
- Offline-first synchronization with Cloud Firestore.
- Minimal settings: currency, units, session, account deletion, app version.
- Spanish and English localization from day one.
- Accessibility support for critical flows.

Out of scope:

- Non-fuel expenses.
- Advanced charts and statistics.
- CSV/PDF export.
- Receipt photos and OCR.
- Reminders and notifications.
- Shared vehicles.
- Widgets, Wear OS, watchOS, and web.
- Official fuel-price integrations.
- App Check.
- Automatic account merging.
- Real-time Firestore listeners.

Any task touching out-of-scope functionality must be rejected or escalated before implementation.

## Core Product Rule: Consumption Calculation

The MVP uses the **full-to-full** method. A segment is the interval between two consecutive full-tank refueling events:

```text
liters      = sum of liters for entries in the segment, including partial refuels
distanceKm  = final full-tank odometer - initial full-tank odometer
consumption = liters / distanceKm * 100
```

A segment does not produce consumption if there is no previous full tank, the final entry is partial, any entry in the segment has missed entries, any entry in the segment has an inconsistent odometer, or `distanceKm <= 0`.

Vehicle average consumption is weighted by distance:

```text
sum(validSegmentLiters) / sum(validSegmentDistanceKm) * 100
```

It is not the arithmetic mean of segment consumption values.

## Architecture Summary

Kotlin Multiplatform is used for domain, data, sync, and shared presentation logic. UI remains native on each platform.

| Layer | Technology |
|-------|------------|
| Shared logic | Kotlin Multiplatform |
| Android UI | Jetpack Compose |
| iOS UI | SwiftUI |
| Shared presentation | KMP state holders exposing `StateFlow<UiState>` plus intent functions |
| Build | Gradle Kotlin DSL, version catalog, convention plugins in `build-logic` |
| Local database | Room 3.0 KMP with `androidx.sqlite:sqlite-bundled` |
| Remote backend | Cloud Firestore as remote replica, never as UI source of truth |
| Authentication | Firebase Authentication through GitLive 2.6.x behind `AuthClient` |
| iOS interop | SKIE, applied only in `:shared` |
| Dependency injection | Manual composition root and constructor injection |

Planned module structure:

```text
build-logic/
gradle/libs.versions.toml

:core:model
:core:common
:core:database
:core:auth
:core:sync
:core:testing

:integration:firebase-auth
:integration:firebase-firestore

:feature:vehicle
:feature:fuel
:feature:session

:shared
:wiring:firebase
:androidApp
iosApp/
firestore/
```

Each feature is one Gradle module with internal `domain`, `data`, and `presentation` packages. Layer boundaries are enforced by architecture tests in CI.

## Implementation Phases

| Phase | Goal | Main gate |
|-------|------|-----------|
| 0 - Foundations | KMP skeleton, convention plugins, core modules, quality tooling, CI, ADRs | Android and iOS build in CI; architecture rules fail correctly |
| 0.5 - Walking skeleton | One real screen crosses native UI, shared presentation, Room, Firestore, and anonymous auth | Data written on Android appears on iOS and vice versa |
| 1 - Local persistence | Vehicles and fuel entries are useful offline | Consumption calculation fully tested and reviewed |
| 2 - Authentication | Anonymous, Google, Apple, conversion, sign-out, account deletion | Anonymous conversion preserves data |
| 3 - Backend and sync | Firestore rules, integration, sync engine, UI sync status | Convergence and provider decoupling are executable checks |
| 4 - MVP hardening | Settings, accessibility, i18n, performance, release preparation | Store-readiness checklist complete |

## Human Review Gates

Human review is mandatory for:

- Phase 0 closure.
- E0-07 walking skeleton.
- E1-05 consumption calculation.
- E3-01 Firestore security rules.
- E3-03 synchronization engine.
- Any change to scope, backend, auth, sync, money representation, module boundaries, or technical stack.

## Contribution Rules

- Do not implement out-of-scope functionality.
- Do not introduce Firebase, GitLive, Room, Android, or iOS APIs into feature `domain` packages.
- Do not introduce dependencies between features.
- Do not make `:shared` depend on `:integration:*`.
- Do not use `Float` or `Double` for money.
- Do not observe Firestore directly from UI.
- Every data model change requires a migration and migration test.
- A story is not done until tests, lint, relevant builds, and acceptance criteria pass.
