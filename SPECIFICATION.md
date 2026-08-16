# Specification - Vehicle Expense Tracking App

> Version 1.0. Normative specification for the MVP.

## 1. Vision

The app lets a user track costs associated with their vehicles. The MVP is limited to fuel expenses. Users can register vehicles, log refueling events, and understand real fuel consumption in L/100 km.

Later versions may add maintenance, insurance, taxes, and additional cost types, but those are outside the MVP.

MVP success metric: a user can create a vehicle, log refueling events offline, and obtain a reliable average consumption value after enough full-tank refueling events.

## 2. Product Principles

| ID | Principle | Implication |
|----|-----------|-------------|
| P1 | Minimal logging friction | Logging a fuel entry should take under 15 seconds. |
| P2 | Always works | The MVP must be fully usable without network coverage. |
| P3 | No entry barrier | Anonymous use is supported and can later be converted to a permanent account. |
| P4 | Cloud provider portability | Firebase must not leak outside integration boundaries. |

## 3. Scope

### 3.1 In Scope

- Onboarding and authentication.
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
- Minimal settings: currency, units, sign-out, account deletion.
- Spanish and English localization.

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

Rule for agents: any work touching out-of-scope functionality must be rejected or escalated. MVP scope changes require updating this specification.

## 4. Actors

| Actor | Description |
|-------|-------------|
| Anonymous user | Uses the app immediately. Data is local and synchronized under an anonymous Firebase identity. If the user uninstalls before conversion, data loss is an accepted risk. |
| Authenticated user | Uses Google or Apple. Data can be recovered on another device after sync. |

A user owns many vehicles. A vehicle belongs to exactly one user.

## 5. Domain Model

### 5.1 Vehicle

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `id` | UUID string | Yes | Generated on the client. Never assigned by the server. |
| `ownerId` | String | Yes | Backend user ID. |
| `name` | String | Yes | Trimmed length 1..40. Unique per user, case-insensitive. |
| `initialOdometer` | Long | Yes | 0..2,000,000. Immutable after creation unless explicitly edited by user. |
| `currentOdometer` | Long | Yes | Derived as max of `initialOdometer` and latest fuel entry odometer. |
| `brand` | String? | No | Trimmed length 0..40. |
| `model` | String? | No | Trimmed length 0..40. |
| `fuelType` | Enum | Yes | `GASOLINE`, `DIESEL`, `LPG`, `CNG`, `ELECTRIC`, `HYBRID`, `OTHER`. Default `GASOLINE`. Stored in MVP, not exposed as selector. |
| `createdAt` | Instant | Yes | UTC. |
| `updatedAt` | Instant | Yes | UTC. Used locally before server timestamp is known. |
| `deletedAt` | Instant? | No | Tombstone timestamp. |
| `syncState` | Enum | Yes | `PENDING`, `SYNCED`, `FAILED`. Local-only. |

### 5.2 FuelEntry

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `id` | UUID string | Yes | Generated on the client. |
| `vehicleId` | UUID string | Yes | References `Vehicle`. |
| `date` | Instant | Yes | Defaults to now. Cannot be more than 1 hour in the future. |
| `odometer` | Long | Yes | Odometer at refueling time. See R-1. |
| `liters` | Decimal(7,3) | Yes | Greater than 0 and at most 500. |
| `pricePerLiter` | Decimal(6,3) | Conditional | Required or derived by R-2. |
| `totalCostMinor` | Long | Conditional | Required or derived by R-2. Minor currency units. |
| `currency` | ISO-4217 | Yes | Defaults from settings. |
| `isFullTank` | Boolean | Yes | Default `true`. |
| `hasMissedEntries` | Boolean | Yes | Default `false`. Invalidates the segment. |
| `odometerInconsistent` | Boolean | Yes | Set by R-1 when saved with warning. |
| `notes` | String? | No | Trimmed length 0..280. |
| `createdAt` | Instant | Yes | UTC. |
| `updatedAt` | Instant | Yes | UTC. |
| `deletedAt` | Instant? | No | Tombstone timestamp. |
| `syncState` | Enum | Yes | `PENDING`, `SYNCED`, `FAILED`. Local-only. |

### 5.3 UserSettings

| Field | Type | Rules |
|-------|------|-------|
| `currency` | ISO-4217 | Defaults from locale, fallback `EUR`. |
| `distanceUnit` | Enum | `KM` in MVP, `MILES` prepared. |
| `volumeUnit` | Enum | `LITER` in MVP, `GALLON` prepared. |

## 6. Business Rules

### R-1 Odometer Consistency

The odometer of a fuel entry should be strictly greater than the previous fuel entry odometer for the same vehicle by date and greater than or equal to `vehicle.initialOdometer`.

If the user enters an inconsistent value, the app warns but allows saving. The entry is marked `odometerInconsistent = true`. Segments containing an inconsistent entry produce no consumption.

When a fuel entry is saved, `vehicle.currentOdometer` is updated to `max(currentOdometer, entry.odometer)`.

### R-2 Price and Total Cost

The user enters any two of `liters`, `pricePerLiter`, and `totalCostMinor`. The third is calculated:

```text
totalCostMinor = round(liters * pricePerLiter * 100)
pricePerLiter  = totalCostMinor / 100 / liters
liters         = totalCostMinor / 100 / pricePerLiter
```

`liters` is always required for consumption. `pricePerLiter` and `liters` are rounded to 3 decimal places when stored. Money is represented as integer minor units and currency code. `Float` and `Double` are prohibited for monetary values.

### R-3 Full-to-Full Consumption

For a fuel entry `E` with `isFullTank = true`, let `P` be the previous full-tank entry for the same vehicle.

```text
segment     = entries X for the same vehicle where P.odometer < X.odometer <= E.odometer
liters      = sum(X.liters for X in segment)
distanceKm  = E.odometer - P.odometer
consumption = liters / distanceKm * 100
```

A segment produces no consumption if:

1. No previous full tank exists.
2. `E.isFullTank = false`.
3. Any segment entry has `hasMissedEntries = true`.
4. Any segment entry has `odometerInconsistent = true`.
5. `distanceKm <= 0`.

Average vehicle consumption is:

```text
sum(validSegmentLiters) / sum(validSegmentDistanceKm) * 100
```

It is not the arithmetic mean of segment consumption values.

Presentation: values are rounded to 2 decimal places. If no valid segment exists, show an empty state explaining that two full-tank entries are required.

### R-4 Deletion

All synchronized deletes are logical tombstones. Deleting a vehicle tombstones its fuel entries. Synchronized tombstones may be physically purged locally after 90 days.

## 7. Functional Flows

### F-1 First Launch and Authentication

1. Welcome screen with "Sign in" and "Continue without account".
2. Continue without account triggers anonymous login and persists the UID locally.
3. Sign in offers providers by platform:
   - Android: Google.
   - iOS: Google and Apple.
4. If the authenticated user has no vehicles, route to first vehicle creation. Otherwise route to vehicle list.

### F-2 First Vehicle Creation

The form requires `name` and `initialOdometer`. `brand`, `model`, and `fuelType` are optional in the domain, but `fuelType` is not exposed in the MVP UI. After save, route to vehicle detail with an empty state inviting the first fuel entry.

### F-3 Fuel Logging

The form is optimized for speed:

- Date defaults to now.
- Odometer is suggested from `currentOdometer`.
- `isFullTank` defaults to true.
- Currency defaults from settings.
- Derived R-2 value recalculates live.

Save is local and immediate. Sync is asynchronous and transparent.

### F-4 Anonymous Account Conversion

From settings, the user can link Google or Apple credentials to the current anonymous identity. Existing data remains attached.

Credential collision:

- If the credential belongs to another account, the MVP does not merge accounts.
- The user can enter the existing account and discard the current anonymous data only after explicit destructive confirmation.
- The user can cancel.

### F-5 Sign-Out and Account Deletion

Sign-out warns if there are pending local changes and offers to wait for sync. Local data is cleared after sign-out.

Account deletion is required for store compliance. It deletes remote and local data after a two-step confirmation.

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
| Async | Coroutines and Flow |
| DI | Manual composition root |
| iOS interop | SKIE only in `:shared` |
| Serialization | `kotlinx.serialization` |
| Dates | `kotlinx-datetime` |

### 8.2 Modules

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

### 8.3 Dependency Rules

1. Feature `domain` packages are Kotlin pure and depend only on `:core:model` and `:core:common`.
2. Feature `data` packages depend on their own `domain`, `:core:database`, and `:core:sync`, never on `:integration:*`.
3. Feature `presentation` packages depend on their own `domain`, never on `data`.
4. Features never depend on other features.
5. `:core:sync` never depends on `:integration:*`.
6. `:shared` never depends on `:integration:*`.
7. Firebase and GitLive types never cross integration boundaries.
8. Only `:wiring:firebase` constructs Firebase implementations.

These rules must be executable architecture checks in CI.

### 8.4 Shared Presentation

Presentation logic is shared in `commonMain` through state holders exposing `StateFlow<UiState>` and intent functions. Android adapts them to Compose. iOS wraps them in SwiftUI `ObservableObject`s. SwiftUI and Compose contain rendering and event forwarding, not business rules.

### 8.5 Cloud Provider Decoupling

`:shared` exposes a graph factory that accepts abstractions:

```kotlin
fun createAppGraph(remote: RemoteSyncSource, auth: AuthClient): AppGraph
```

The provider decoupling criterion is executable: excluding `:integration:*` and `:wiring:firebase` from settings must leave `:core:*` and `:feature:*` compiling and testing with local fakes.

## 9. Synchronization

### 9.1 Principles

- The local database is the only UI source of truth.
- Every write is local first.
- Remote sync is background work.
- IDs are UUID v4 generated on the client.
- Firestore offline persistence is disabled.

### 9.2 Local Control Tables

`outbox`:

- `seq`
- `entityType`
- `entityId`
- `payload`
- `localRevision`
- `attemptCount`
- `nextAttemptAt`
- `lastError`

`sync_cursor`:

- `entityType`
- `lastServerUpdatedAt`

The outbox stores full snapshots. `UNIQUE(entityType, entityId)` coalesces multiple local changes to one pending snapshot.

### 9.3 Push

1. Select due outbox rows ordered by `seq`, limit 50.
2. Push vehicles before fuel entries.
3. Write document using client-generated ID and server timestamp.
4. Re-read document to obtain authoritative `updatedAt`.
5. In a local transaction:
   - If `outbox.localRevision == entity.localRevision`, remove outbox row and mark synced.
   - Otherwise keep the outbox row and update only `serverUpdatedAt`.
6. Retry network failures with exponential backoff and jitter.
7. Mark validation failures as poisoned.

### 9.4 Pull

1. Use `since = max(0, cursor - 30 seconds)`.
2. Query by `updatedAt`, limit 200.
3. Include tombstones.
4. Apply each page in one local transaction.
5. If an outbox row exists for a remote entity, do not overwrite local data.
6. Otherwise apply remote data if `(remote.updatedAt, remote.id)` is newer than local.
7. Advance cursor only after local apply succeeds.

### 9.5 Conflict Resolution

Last-write-wins at document level using server `updatedAt`, with deterministic lexical `id` tie-breaker. Tombstones are regular documents and win over older updates.

## 10. Firestore Security

Firestore structure:

```text
users/{uid}/vehicles/{vehicleId}
users/{uid}/fuelEntries/{entryId}
users/{uid}/meta/settings
```

Rules must enforce:

- `request.auth != null`.
- `request.auth.uid == uid`.
- Anonymous users are valid authenticated users.
- `request.resource.data.updatedAt == request.time`.

Required emulator tests:

- User A cannot read under `users/B`.
- User A cannot write under `users/B`.
- Anonymous user can read/write under their own UID.
- Writes with client-controlled `updatedAt` are rejected.
- Tombstones are returned by delta pull.

## 11. Non-Functional Requirements

| Area | Requirement |
|------|-------------|
| Platforms | Android `minSdk 26`, iOS 16+. |
| Performance | Cold start to content under 2 seconds; smooth list of 1,000 entries; consumption calculation for 1,000 entries under 100 ms. |
| Offline | 100% of MVP functionality usable without network. |
| Accessibility | System font size, content labels, WCAG AA contrast, TalkBack and VoiceOver for critical flows. |
| Localization | Spanish and English from day one; no hardcoded user-facing strings. |
| Quality | ktlint, detekt, architecture checks, unit tests, sync tests, Firestore emulator tests. |
| CI | Build, tests, lint, Android, iOS simulator, and shared framework verification on every PR. |
| Privacy | Privacy policy, store privacy labels, in-app account deletion. |

## 12. Closed Technical Decisions

| ID | Decision | Choice |
|----|----------|--------|
| D-0 | Backend | Cloud Firestore. |
| D-1 | Local database | Room 3.0 KMP with `androidx.sqlite:sqlite-bundled`. |
| D-2 | Kotlin-to-Swift interop | SKIE, only in `:shared`. |
| D-3 | Dependency injection | Manual composition root and constructor injection. |
| D-4 | `fuelType` | Stored on `Vehicle` from day one, not exposed in MVP UI. |
| D-5 | Firestore access from KMP | GitLive Firestore 2.6.x behind `RemoteSyncSource`. |
| D-6 | Firebase Auth from KMP | GitLive Auth 2.6.x behind `AuthClient`. |
| D-7 | Navigation | Native navigation per platform. |
| D-8 | Presentation layer | Shared KMP state holders. |
| D-9 | Firestore offline persistence | Disabled. |

Each decision must be recorded as an ADR in `docs/adr/` during Phase 0.

## 13. Glossary

| Term | Definition |
|------|------------|
| Fuel entry | A refueling event recorded by the user. |
| Full tank | Refueling event where the tank is filled completely. |
| Segment | Interval between two consecutive full-tank fuel entries. |
| Tombstone | Logical deletion marker propagated through sync. |
| Outbox | Local queue of pending snapshots to push remotely. |
| LWW | Last-write-wins conflict resolution. |
