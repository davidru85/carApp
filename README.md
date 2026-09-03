# carApp

Cross-platform mobile app for Android and iOS to track vehicle costs.

The MVP is intentionally limited to **fuel expenses**: users can create vehicles, log refueling events, review their history, and calculate real-world fuel consumption in **L/100 km**. Later phases may add maintenance, insurance, taxes, and other expense types, but they are out of scope for the MVP.

> **Project status:** **Phase 1 complete; Phase 2 open.** `E1-01` through `E1-13`, `E3-06`, `E3-01` and `E0-07` have delivered the SQLDelight database, complete local Vehicle and Fuel Entry repositories, Fuel Entry validation, reviewed full-to-full consumption, executable provider decoupling, reviewed Firestore rules, the native walking skeleton, both native Vehicle and Fuel Entry flows, device-local settings, deterministic shared graph-test teardown, and executable native locale-provider behavior coverage. `E2-01`, the complete `:core:auth` contracts and models, is next. The project builds on both platforms and `main` is protected by ten required CI checks.

## Start here

**If you are an AI agent, open [AGENTS.md](AGENTS.md) first.** It is the entry point: it defines document authority, normative language, the human review gates, a map of every document in the repository, and a `Repository State` section describing what is already built and how to verify it.

## Build and verify

```bash
./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test \
          koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest \
          testAndroidHostTest iosSimulatorArm64Test \
          -x :integration:firebase-auth:iosSimulatorArm64Test \
          -x :integration:firebase-firestore:iosSimulatorArm64Test \
          -x :wiring:firebase:iosSimulatorArm64Test \
          -x :composition:ios:iosSimulatorArm64Test
```

Those are the non-instrumented CI tasks. The protected Android UI job additionally runs
`./gradlew :androidApp:connectedDebugAndroidTest` on the D-84 API 36 emulator. `AGENTS.md`
§`Repository State` explains what each check proves and how to create a module. Requirements: JDK
21, the Android SDK and, for the iOS targets, Xcode as pinned in
[docs/versions-matrix.md](docs/versions-matrix.md).

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
| [SECURITY_ADVISORY_REGISTER.md](docs/SECURITY_ADVISORY_REGISTER.md) | Dated, expiring accepted dependency advisories and their review evidence. |
| [development-firebase-cost-controls.md](docs/runbooks/development-firebase-cost-controls.md) | Development billing controls, state-change audit trail, destructive test and recovery procedure. |
| [docs/adr/README.md](docs/adr/README.md) | ADR index, one per decision ID. |
| [docs/identifiers.md](docs/identifiers.md) | Application identifiers, Firebase projects and Firestore location. |
| [docs/versions-matrix.md](docs/versions-matrix.md) | Pinned toolchain versions, compatibility relation, performance measurement baselines. |
| [docs/DESIGN.md](docs/DESIGN.md) | Design entry point: the two platform design systems in general terms, and an index of every design asset. Non-normative. |
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

The authoritative scope lists are in [SPECIFICATION.md §3](docs/SPECIFICATION.md). In summary: fuel expenses only, offline-first including first launch, device-bound anonymous authentication with Google and Apple conversion, vehicle and fuel entry CRUD, full-to-full consumption, single-device active use, Firestore backup for permanent-account recovery on a new device, minimal settings, Spanish and English.

Sign-in is anonymous, Google or Apple, and nothing else. The welcome screen offers the platform's providers directly — Google and continue-without-account on Android, Apple, Google and continue-without-account on iOS — with no intermediate provider picker and no provider-less "Sign in" control. The flow is normative in [SPECIFICATION.md §7 F-1](docs/SPECIFICATION.md); the provider set is closed in [CONTRACTS.md §20.3](docs/CONTRACTS.md).

Out of scope: non-fuel expenses, advanced charts, export, receipt and odometer photos with OCR,
local or on-device AI text recognition, fuel and maintenance reminders, operating-system
notifications, shared vehicles, widgets and wearables, official fuel-price integrations,
Cloud Functions-mediated product read/write validation beyond the `D-23` and `D-63` account
identity and data-deletion operations, automatic account merging, simultaneous multi-device use, active
multi-device synchronization, remote-database-as-source-of-truth operation, real-time Firestore
listeners, remote settings synchronization, platform settings sync or backup through Google Play
services / Android backup / iCloud, and electric or hybrid energy modelling. The foreground-only
anonymous-account retention notices selected by `D-62`, D-66 development billing containment and
D-67 App Check enforcement are in scope.

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
| Local database | SQLDelight 2.3.2 with AndroidX bundled SQLite 2.7.0 |
| Remote backend | Cloud Firestore as a backup and recovery replica, never as UI source of truth |
| Authentication | Firebase Authentication through GitLive 2.6.x behind `AuthClient` |
| Metrics | Firebase Analytics behind `AnalyticsTracker`, off by default |
| Crash reporting | Firebase Crashlytics behind `CrashReporter`, added in Phase 4 |
| iOS interop | SKIE, applied only in `:composition:ios` |
| Dependency injection | Koin KMP for wiring, constructor injection for implementation classes |
| Logging | Kermit behind `Logger` |

Current module structure, with the two remaining integration modules shown explicitly as planned:

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
:integration:firebase-analytics      # planned: E3-09
:integration:firebase-crashlytics   # planned: E4-04

:feature:vehicle
:feature:fuel
:feature:session

:shared
:shared:testing
:wiring:firebase
:composition:ios
:androidApp
iosApp/
firestore/
```

Each feature is one Gradle module with internal `domain`, `data` and `presentation` packages.
Module boundaries are enforced by a Gradle configuration check. D-28 feature package-boundary
rules are executable through Konsist and include a firing fixture for every rule.

## Design

The UI follows a **different design system on each platform**: strict Material 3 Expressive on
Android and strict Liquid Glass on iOS. The platforms share the information architecture, the six
screens, the data and the copy, and almost none of their visual language.

[docs/DESIGN.md](docs/DESIGN.md) is the entry point. It describes the design in general terms and
indexes every asset: the two platform design systems and the twelve screen descriptions in
[design/stitch/](design/stitch/), plus the pixel-exact Figma build scripts in
[design/figma/](design/figma/).

Design assets are **non-normative**. They describe how the product looks, never what it does.
Where a design asset and `docs/SPECIFICATION.md` or `docs/CONTRACTS.md` disagree, the normative
document wins and the discrepancy is escalated.

## Implementation Phases

| Phase | Goal | Main gate |
|-------|------|-----------|
| 0 - Foundations **(complete)** | Owner decisions closed, KMP skeleton, convention plugins, core modules, quality tooling, CI, ADRs | Android and iOS build in CI; architecture rules fail correctly |
| 1 - Local persistence **(complete)** | `E1-01` through `E1-13` and the `E0-07` gate are complete | The walking skeleton proves the native local/remote path, both native Vehicle and Fuel Entry flows are executable, device-local settings are persistent, Vehicle outbox payload `entityType` compliance is restored, shared graph tests tear down deterministically, and both native locale-provider paths execute in canonical verification |
| 2 - Authentication **(open)** | E2-01 through E2-07 remain; E2-01 is next | Adoption and normal linking preserve data; confirmed collisions preserve the current anonymous snapshot |
| 3 - Backend and backup **(partially complete)** | E3-06 and E3-01 are complete; the remaining backend, cleanup, sync and recovery stories are open | Recovery and provider decoupling are executable checks |
| 4 - MVP hardening **(planned)** | E4-01 through E4-04 remain | Store-readiness checklist complete |

## Human Review Gates

Defined canonically in [AGENTS.md](AGENTS.md). No other document defines gates.

## Contribution Rules

See [CONTRIBUTING.md](docs/CONTRIBUTING.md). The short version:

- Do not implement out-of-scope functionality.
- Do not introduce Firebase, GitLive, Koin, Ktor, SQLDelight, SQLite, Android or iOS APIs into feature `domain` packages.
- Do not introduce dependencies between features, and do not make `:shared` depend on `:integration:*`.
- Do not use `Float` or `Double` for money.
- Do not observe Firestore directly from the UI.
- Do not add Ktor during the MVP unless a new ADR introduces an HTTP API implementation.
- Every data model change requires a migration and a migration test.
- A story is not done until tests, lint, relevant builds, acceptance criteria, the handoff and the `docs/PROJECT_LOG.md` entry are complete.
