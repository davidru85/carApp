# Specification - Vehicle Expense Tracking App

> Version 1.1. Normative specification for the MVP. Authoritative for **behaviour**: scope, business rules, flows and non-functional targets. Representational detail — types, field names, signatures, numeric semantics — is authoritative in `docs/CONTRACTS.md`. Authority rules and normative language are defined in `AGENTS.md`.

## 1. Vision

The app lets a user track costs associated with their vehicles. The MVP is limited to fuel expenses. Users can register vehicles, log refueling events, and understand real fuel consumption in L/100 km.

Later versions may add maintenance, insurance, taxes, and additional cost types, but those are outside the MVP.

MVP success metric: a user can create a vehicle, log refueling events offline, and obtain a reliable average consumption value after at least two valid full-to-full segments. This normally requires at least three full-tank refueling events.

## 2. Product Principles

| ID | Principle | Implication |
|----|-----------|-------------|
| P1 | Minimal logging friction | Logging a fuel entry MUST be achievable in under 15 seconds. |
| P2 | Always works | The MVP MUST be fully usable without network coverage, **including first launch**. |
| P3 | No entry barrier | Anonymous use is supported and can later be converted to a permanent account. |
| P4 | Cloud provider portability | Firebase MUST NOT leak outside integration boundaries. |

## 3. Scope

### 3.1 In Scope

- Onboarding and authentication.
- Offline-capable first launch with a local identity, adopted into an anonymous Firebase identity when connectivity allows.
- Anonymous login.
- Google sign-in on Android and iOS.
- Apple sign-in on iOS.
- Anonymous account conversion without data loss.
- Vehicle CRUD.
- Fuel entry CRUD.
- Consumption calculation per fuel entry where applicable.
- Average vehicle consumption.
- Local-first persistence.
- Offline-first synchronization with Cloud Firestore.
- Discreet sync status indicator with manual retry.
- Settings, exactly: `currency` (editable), `distanceUnit` and `volumeUnit` (visible, read-only in MVP), sync status, analytics opt-in, sign-out or local-data deletion, account deletion, app version. Language is inherited from the system; there is no in-app language switch in the MVP.
- Spanish and English localization.

This settings list is the single source. `README.md`, `docs/DEFINITION.md` and `docs/BACKLOG.md` MUST NOT restate a different one.

### 3.2 Out of Scope

- Non-fuel expenses.
- Advanced charts and statistics.
- CSV/PDF export.
- Receipt images and OCR.
- Reminders and notifications.
- Vehicle sharing.
- Widgets, Wear OS, watchOS, and web.
- Official fuel-price integrations.
- Firebase App Check.
- Automatic account merging.
- Real-time Firestore listeners.
- Remote synchronization of user settings.

Rule for agents: any work touching out-of-scope functionality MUST be rejected or escalated. MVP scope changes require updating this specification and are a human review gate.

## 4. Actors

| Actor | Description |
|-------|-------------|
| Local-only user | First launch without connectivity. Data is local under the `LOCAL_OWNER` sentinel and is not yet synchronized. Adopted into an anonymous identity as soon as connectivity allows. |
| Anonymous user | Uses the app immediately. Data is local and synchronized under an anonymous Firebase identity. If the user uninstalls before conversion, data loss is an accepted risk. |
| Authenticated user | Uses Google or Apple. Data can be recovered on another device after sync. |

A user owns zero or more vehicles. A vehicle belongs to exactly one user. Shared ownership is out of scope. If the user has zero non-deleted vehicles after authentication, the app routes to first vehicle creation before showing the main app shell.

## 5. Domain Model

Field names, types, scales and persistence formats are normative in `docs/CONTRACTS.md §3` and `§20`. This section describes meaning and constraints only; where the two disagree on a name or a type, `docs/CONTRACTS.md` wins.

### 5.1 Vehicle

| Field | Required | Meaning and rules |
|-------|----------|-------------------|
| `id` | Yes | Client-generated identifier. Never assigned by the server. |
| `ownerId` | Yes | Backend user ID, or `LOCAL_OWNER` before an anonymous UID exists. Stamped by the repository, never supplied by the UI. |
| `name` | Yes | Trimmed length 1..40. Unique per user, case-insensitive, among non-deleted vehicles. Uniqueness is a local pre-write check only; it is not enforceable across devices. |
| `initialOdometerKm` | Yes | 0..2,000,000. Editable only while the vehicle has no non-deleted fuel entries. |
| `currentOdometerKm` | Yes | Derived read model: the maximum of `initialOdometerKm` and the highest odometer among non-deleted fuel entries. Never accepted from user input, never used for remote conflict arbitration. |
| `brand`, `model` | No | Null, or trimmed length 1..40. |
| `fuelType` | Yes | Default `GASOLINE`. Stored in the MVP, not exposed as a selector. Metadata only: it does not alter validation, units or consumption. |
| `createdAt`, `updatedAt` | Yes | UTC. |
| `deletedAt` | No | Tombstone timestamp. |

### 5.2 FuelEntry

| Field | Required | Meaning and rules |
|-------|----------|-------------------|
| `id` | Yes | Client-generated identifier. |
| `ownerId` | Yes | Same rules as on `Vehicle`. |
| `vehicleId` | Yes | References `Vehicle`. |
| `date` | Yes | Defaults to now. Bounded by `docs/CONTRACTS.md §5`; in particular it cannot be more than 1 hour in the future. |
| `odometerKm` | Yes | Odometer at refueling time. See R-1. |
| `litersScaled` | Yes | Greater than 0 and at most 500 L. |
| `pricePerLiterScaled` | Yes after validation | Supplied or derived by R-2 before persistence. Draft form state may be partial. |
| `totalCostMinor` | Yes after validation | Supplied or derived by R-2 before persistence. Minor currency units. |
| `currency` | Yes | Defaults from settings. Stored per entry; changing the setting never rewrites existing entries. |
| `isFullTank` | Yes | Default `true`. |
| `hasMissedEntries` | Yes | Default `false`. Means refuels were not logged **between the previous logged entry and this one**. Invalidates the segment ending at this entry and any segment containing it. |
| `odometerInconsistent` | Yes | Derived, not user-editable. Recomputed whenever a neighbouring entry changes. |
| `notes` | No | Null, or trimmed length 1..280. |
| `createdAt`, `updatedAt` | Yes | UTC. |
| `deletedAt` | No | Tombstone timestamp. |

Sync metadata (`syncState`, `localRevision`, `serverUpdatedAt`, `schemaVersion`) is **not** part of the domain model. It exists only on the local row, and reaches the UI only as the aggregate sync status.

### 5.3 UserSettings

| Field | Rules |
|-------|-------|
| `currency` | Defaults from locale, fallback `EUR`. Only 2-decimal ISO-4217 currencies are supported in the MVP. |
| `distanceUnit` | `KM` in the MVP. `MILES` is prepared but not user-switchable. |
| `volumeUnit` | `LITER` in the MVP. `GALLON` is prepared but not user-switchable. |
| `analyticsEnabled` | Default `false`. Analytics collection starts only after an explicit opt-in. |

Settings are device-local and are not synchronized in the MVP.

## 6. Business Rules

### R-1 Odometer Consistency

The odometer of a fuel entry MUST be strictly greater than the previous non-deleted fuel entry odometer for the same vehicle in chronological order, and MUST be greater than or equal to `vehicle.initialOdometerKm` — unless the user explicitly confirms the inconsistency.

If the user enters an inconsistent value, the first save attempt MUST return a warning and mutate nothing. The UI MUST ask for explicit confirmation. A confirmed save stores the entry with `odometerInconsistent = true`. Segments containing an inconsistent entry produce no consumption.

The same rule applies on **edit**, evaluated against the entry's neighbours in its target chronological position. Because editing or deleting one entry changes the validity of its neighbours, `odometerInconsistent` is recomputed for the affected entry and its immediate successor within the same transaction.

Chronological order and the exact warning protocol are defined in `docs/CONTRACTS.md §4` and `§5`.

### R-2 Price and Total Cost

The user supplies exactly two of `liters`, `pricePerLiter` and `totalCostMinor`; the third is calculated. Which two were supplied is carried explicitly by the command, so the derivation is never ambiguous.

`liters` is always required for consumption. Money is represented as integer minor units plus a currency code. `Float` and `Double` are FORBIDDEN for monetary values.

The exact integer arithmetic, rounding mode, scales, currency factor and golden test values are normative in `docs/CONTRACTS.md §2`. This section MUST NOT restate a formula.

### R-3 Full-to-Full Consumption

For a fuel entry `E` with `isFullTank = true`, let `P` be the previous full-tank entry for the same vehicle.

```text
segment     = entries X for the same vehicle where P.odometerKm < X.odometerKm <= E.odometerKm
liters      = sum(X.liters for X in segment)
distanceKm  = E.odometerKm - P.odometerKm
consumption = liters / distanceKm * 100
```

A segment produces no consumption when any invalidation reason applies. The exhaustive list of reasons, the ordering used to select `P`, and the treatment of entries sharing an odometer with `P` are normative in `docs/CONTRACTS.md §4` and `ConsumptionInvalidReason` in `docs/CONTRACTS.md §20.6`. This section MUST NOT restate that list.

Average vehicle consumption is distance-weighted:

```text
sum(validSegmentLiters) / sum(validSegmentDistanceKm) * 100
```

It is NOT the arithmetic mean of segment consumption values.

Presentation: values are rounded to 2 decimal places. If no valid segment exists, show an empty state explaining that two full-tank entries are required. A reliable average requires at least two valid full-to-full segments.

Deleted entries never participate in consumption. Orphan fuel entries whose vehicle has not yet been synchronized are excluded until their vehicle arrives.

### R-4 Deletion

All synchronized deletes are logical tombstones. Hard deletes are rejected by the Firestore rules by design. Deleting a vehicle tombstones its fuel entries in one local transaction.

Synchronized tombstones may be purged locally once they are confirmed synced, older than 90 days, and have no pending outbox row. Remote tombstones are never purged in the MVP. Exact conditions are in `docs/CONTRACTS.md §8`; the purge is implemented by story `E3-07`.

## 7. Functional Flows

### F-1 First Launch and Authentication

1. Welcome screen with "Sign in" and "Continue without account".
2. "Continue without account" works **offline**: the app creates a local session under `LOCAL_OWNER` and the user can immediately use every MVP feature. An anonymous Firebase UID is acquired in the background when connectivity allows, and local data is adopted into it without loss.
3. Sign in offers providers by platform: Android offers Google; iOS offers Google and Apple.
4. Routing MUST NOT happen while the authentication state is still undetermined. Once determined: if the user has no vehicles, route to first vehicle creation; otherwise route to the vehicle list.

### F-2 First Vehicle Creation

The form requires `name` and `initialOdometerKm`. `brand`, `model` and `fuelType` are optional in the domain, and `fuelType` is not exposed in the MVP UI. After save, route to vehicle detail with an empty state inviting the first fuel entry.

### F-3 Fuel Logging

The form is optimized for speed:

- Date defaults to now.
- Odometer is suggested from `currentOdometerKm`. The suggestion never bypasses R-1.
- `isFullTank` defaults to true.
- Currency defaults from settings.
- The derived R-2 value recalculates live.
- `hasMissedEntries` is a secondary toggle, default false, deliberately off the fast path.

Save is local and immediate. Sync is asynchronous and transparent.

### F-4 Anonymous Account Conversion

From settings, the user can link Google or Apple credentials to the current anonymous identity. Existing data remains attached. If a provider flow would change the UID, conversion is aborted with a typed error rather than risking data loss.

Credential collision:

- If the credential belongs to another account, the MVP does not merge accounts.
- The user can enter the existing account and discard the current anonymous data only after explicit destructive confirmation, which reports how much data will be lost.
- The user can cancel; cancelling leaves the anonymous session and local data untouched.

### F-5 Sign-Out and Account Deletion

Sign-out is offered only to a permanently authenticated user. It warns if there are pending local changes and offers to wait for sync, cancel, or discard pending changes after destructive confirmation. All local data for that owner is cleared after sign-out; recovery is by signing in again and pulling.

For an anonymous session there is no sign-out. The equivalent action is "delete local data" and requires the same two-step destructive confirmation, because the identity cannot be recovered.

Account deletion is required for store compliance. It re-authenticates if needed, deletes remote data first, then the auth account, then local data. The exact order and failure semantics are in `docs/CONTRACTS.md §11.5`.

## 8. Technical Architecture

### 8.1 Stack

| Layer | Technology |
|-------|------------|
| Shared logic | Kotlin Multiplatform |
| Android UI | Jetpack Compose |
| iOS UI | SwiftUI |
| Build | Gradle Kotlin DSL, version catalog, convention plugins |
| Local database | Room 3.0 KMP with `androidx.sqlite:sqlite-bundled` |
| Remote backend | Cloud Firestore |
| Auth | Firebase Authentication through GitLive 2.6.x |
| Metrics | Firebase Analytics behind `AnalyticsTracker` |
| Async | Coroutines and Flow |
| DI | Koin KMP for wiring, constructor injection for implementation classes |
| iOS interop | SKIE only in `:shared` |
| Serialization | `kotlinx.serialization` |
| Dates | `kotlinx-datetime` |
| Logging | Kermit behind `Logger` |

Exact versions are pinned in `docs/versions-matrix.md` and declared only in `gradle/libs.versions.toml`.

### 8.2 Modules

```text
build-logic/
gradle/libs.versions.toml

:core:model
:core:common
:core:database
:core:auth
:core:sync
:core:analytics
:core:testing

:integration:firebase-auth
:integration:firebase-firestore
:integration:firebase-analytics

:feature:vehicle
:feature:fuel
:feature:session

:shared
:wiring:firebase
:androidApp
iosApp/
firestore/
```

### 8.3 Dependency Rules

1. Feature `domain` packages are Kotlin pure and depend only on `:core:model` and `:core:common`.
2. Feature `data` packages depend on their own `domain`, `:core:model`, `:core:common`, `:core:database` and `:core:sync` — never on `:integration:*` and never on `:core:auth`. The current owner reaches them through `OwnerContext` in `:core:common`.
3. Feature `presentation` packages depend on their own `domain` and `:core:common`, never on `data`.
4. Features never depend on other features.
5. `:core:sync` depends on `:core:model`, `:core:common`, `:core:database` and `:core:auth`, never on `:integration:*`.
6. `:shared` never depends on `:integration:*`.
7. Firebase and GitLive types never cross integration boundaries.
8. Koin is used only for dependency wiring and MUST NOT be accessed from domain or use case logic.
9. Ktor is deferred and MUST NOT be added until an HTTP API remote implementation is approved by ADR.
10. Only `:wiring:firebase` constructs Firebase implementations.
11. `vehicle.currentOdometerKm` and `fuel_entry.odometerInconsistent` are written only by `:core:database`.

Module-level rules are enforced by a Gradle configuration check; package-level rules require source analysis. Both MUST be executable checks in CI, and each rule MUST have a failing fixture test proving the check fires.

### 8.4 Shared Presentation

Presentation logic is shared in `commonMain` through state holders exposing `StateFlow<UiState>` and intent functions. Android adapts them to Compose. iOS wraps them in SwiftUI `ObservableObject`s. SwiftUI and Compose contain rendering and event forwarding, not business rules.

`UiState` carries no user-facing text; each platform maps typed values to its own string resources. Lifecycle, dispatcher and threading rules are in `docs/CONTRACTS.md §14`.

### 8.5 Cloud Provider Decoupling

`:shared` exposes a graph factory that accepts abstractions:

```kotlin
fun createAppGraph(dependencies: AppGraphDependencies): AppGraph
```

The provider decoupling criterion is executable: excluding `:integration:*` and `:wiring:firebase` from settings MUST leave `:core:*` and `:feature:*` compiling and testing with local fakes. `AppGraphDependencies` and all public interface contracts are defined in `docs/CONTRACTS.md`.

## 9. Synchronization

### 9.1 Principles

- The local database is the only UI source of truth.
- Firestore is a backup and synchronization replica only; it is never the product source of truth.
- Every write is local first.
- Remote sync is background work.
- IDs are UUID v4 generated on the client.
- Firestore offline persistence is disabled.
- Nothing is enqueued for synchronization while the owner is `LOCAL_OWNER`.

### 9.2 Local Control Tables

`outbox`: `seq`, `entityType`, `entityId`, `payload`, `localRevision`, `attemptCount`, `nextAttemptAt`, `lastError`, `lastErrorCode`, with `UNIQUE(entityType, entityId)` coalescing multiple local changes into one pending snapshot while preserving the original `seq`.

`sync_cursor`: `entityType`, `lastServerUpdatedAt`, `lastDocumentId`.

`quarantine`: documents whose `schemaVersion` exceeds what this client supports.

Payload format, coalescing semantics and purge conditions are in `docs/CONTRACTS.md §8`.

### 9.3 Push

1. Select due outbox rows ordered by `seq`, limit 50.
2. Partition the batch by dependency order: vehicle upserts, fuel entry upserts, fuel entry tombstones, vehicle tombstones. Ordering by `seq` is preserved inside each group.
3. Write the document using the client-generated ID and a server timestamp.
4. Obtain the authoritative `serverUpdatedAt` from the write result, or by re-reading the document.
5. Confirm locally in one transaction, keyed on `localRevision`.
6. Retry network failures with exponential backoff and jitter, up to the attempt ceiling.
7. Mark validation and permission failures as poisoned.

### 9.4 Pull

1. Pull `VEHICLE` before `FUEL_ENTRY`.
2. Start the cycle from `max(0, cursor.lastServerUpdatedAt - 30 seconds)`, applying the overlap once per cycle, not per page.
3. Query ordered by `updatedAt` and document ID, paginated with `startAfter` on the stored `(lastServerUpdatedAt, lastDocumentId)`, limit 200.
4. Include tombstones.
5. Apply each page in one local transaction.
6. If an outbox row exists for a remote entity, do not overwrite local data.
7. Otherwise apply remote data when it is newer than the local `serverUpdatedAt`. The local `updatedAt` never participates in this comparison.
8. Advance the cursor only after the local apply succeeds, and fail the cycle rather than loop if a full page produces no cursor progress.

Fuel entries whose vehicle has not yet arrived are persisted but hidden until it does.

### 9.5 Conflict Resolution

Last-write-wins at document level using the server `updatedAt`. Tombstones are regular documents and win over older updates. `(updatedAt, documentId)` orders the pull stream; it is not a tie-breaker for a single document, whose two sides share the same id.

Accepted limitation: two devices editing different fields of the same document concurrently can lose one whole-document update.

## 10. Firestore Security

Structure:

```text
users/{uid}/vehicles/{vehicleId}
users/{uid}/fuelEntries/{entryId}
```

Rules MUST enforce authentication, owner match, `ownerId == uid`, `updatedAt == request.time`, a supported `schemaVersion` lower bound, document ID consistency, and presence, type **and range** of every field. Hard deletes are rejected. The complete rule shape and the required emulator tests are in `docs/CONTRACTS.md §16`.

## 11. Non-Functional Requirements

| Area | Requirement |
|------|-------------|
| Platforms | Android `minSdk 26`, iOS 16+. |
| Performance | Cold start to first content under 2 s, median of 10 runs on the reference devices in a release build. A 1,000-entry list scrolls with no frame over 32 ms. Consumption for 1,000 entries under 100 ms, median of 20 runs. Reference devices and measurement method are fixed in `docs/versions-matrix.md`. |
| Offline | 100% of MVP functionality usable without network, including first launch. |
| Accessibility | System font size up to 200%, content labels, WCAG AA contrast, TalkBack and VoiceOver for F-1 through F-3. |
| Localization | Spanish and English from day one; no hardcoded user-facing strings. |
| Quality | ktlint, detekt, architecture checks, contract check, unit tests with coverage thresholds, sync convergence tests, Firestore emulator tests. |
| CI | Build, tests, lint, Android, iOS simulator and shared framework verification on every PR, on a macOS runner from the first PR. |
| Privacy | Analytics off by default, privacy policy, store privacy labels, in-app account deletion. |

## 12. Closed Technical Decisions

`docs/DECISION_BOARD.md` is the sole registry of decision IDs. This table mirrors its decision IDs and statuses and MUST stay identical.

| ID | Decision | Choice | Status |
|----|----------|--------|--------|
| D-0 | Backend | Cloud Firestore. | Accepted |
| D-1 | Local database | Room 3.0 KMP with `androidx.sqlite:sqlite-bundled`. | Accepted |
| D-2 | Kotlin-to-Swift interop | SKIE, only in `:shared`. | Accepted |
| D-3 | Dependency injection | Koin KMP for wiring, constructor injection for implementation classes. | Accepted |
| D-4 | `fuelType` | Stored on `Vehicle` from day one, not exposed in MVP UI. | Accepted |
| D-5 | Firestore access from KMP | GitLive Firestore 2.6.x behind `RemoteSyncSource`. | Accepted |
| D-6 | Firebase Auth from KMP | GitLive Auth 2.6.x behind `AuthClient`. | Accepted |
| D-7 | Navigation | Native navigation per platform. | Accepted |
| D-8 | Presentation layer | Shared KMP state holders. | Accepted |
| D-9 | Firestore offline persistence | Disabled. | Accepted |
| D-10 | Metrics | Firebase Analytics behind `AnalyticsTracker`. | Accepted |
| D-11 | HTTP/API client | Ktor deferred until a future API-based remote implementation exists. | Deferred |
| D-12 | Image loading | Coil, only if a story ever requires image loading. | Deferred |
| D-13 | Firestore location | `europe-west1` single region. | Accepted |
| D-14 | Firebase project topology | One development Firebase project plus the local emulator; production topology deferred until release preparation. | Accepted |
| D-15 | Logging implementation | Kermit behind `Logger`. | Accepted |
| D-16 | Architecture checks | Konsist for package rules, custom Gradle check for module rules. | Accepted |
| D-17 | Flow testing helper | Turbine. | Accepted |
| D-18 | Coverage measurement | Kover with per-module thresholds. | Accepted |
| D-19 | Result type | Custom `Outcome<T, E>` in `:core:common`; Arrow rejected for the MVP. | Accepted |
| D-20 | Localization implementation | Native platform resources; `UiState` carries no user-facing text. | Proposed |
| D-21 | Crash reporting | Firebase Crashlytics, Phase 4. | Pending |
| D-22 | Application identifiers | Fixed in `docs/identifiers.md`; production Firebase project IDs deferred by `D-14`. | Accepted |

Each decision is recorded as an ADR in `docs/adr/`. During Phase 0, ADRs MUST be validated against the selected tool versions and the version catalog, and every `Proposed` decision MUST be confirmed or changed by the project owner before the story that depends on it starts.

## 13. Glossary

| Term | Definition |
|------|------------|
| Fuel entry | A refueling event recorded by the user. |
| Full tank | Refueling event where the tank is filled completely. |
| Segment | Interval between two consecutive full-tank fuel entries. |
| Tombstone | Logical deletion marker propagated through sync. |
| Outbox | Local queue of pending snapshots to push remotely. |
| LWW | Last-write-wins conflict resolution. |
| `LOCAL_OWNER` | Sentinel owner used before an anonymous Firebase UID exists. |
| Adoption | Rewriting `LOCAL_OWNER` rows to a real UID and enqueueing them for sync. |
| Orphan entry | A synchronized fuel entry whose vehicle has not been pulled yet. |
| Quarantine | Local storage for remote documents with an unsupported future schema version. |
