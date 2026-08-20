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
| P4 | Cloud provider portability | Firebase is a remote backup and recovery backend only, and MUST NOT leak outside integration boundaries. |

## 3. Scope

### 3.1 In Scope

- Onboarding and authentication.
- Automatic Firebase anonymous authentication on first launch, with an offline local fallback adopted into that anonymous identity when connectivity allows.
- Anonymous login.
- Google sign-in on Android and iOS.
- Apple sign-in on iOS.
- Anonymous account conversion without data loss.
- Vehicle CRUD.
- Fuel entry CRUD.
- Consumption calculation per fuel entry where applicable.
- Average vehicle consumption.
- Local-first persistence.
- Offline-first remote backup to Cloud Firestore for recovery on a new device.
- Single active device per account. A backup can be restored on a new device, but simultaneous use on multiple devices is not supported in the MVP.
- Discreet backup status indicator with manual retry.
- Settings, exactly: `currency` (editable), `distanceUnit` and `volumeUnit` (visible, read-only in MVP), backup status, analytics opt-in, sign-out or local-data deletion, account deletion, app version. Language is inherited from the system; there is no in-app language switch in the MVP.
- Spanish and English localization.

This settings list is the single source. `README.md`, `docs/DEFINITION.md` and `docs/BACKLOG.md` MUST NOT restate a different one.

### 3.2 Out of Scope

- Non-fuel expenses.
- Advanced charts and statistics.
- CSV/PDF export.
- Receipt and odometer images with OCR, including local or on-device AI text recognition.
- Reminders and notifications.
- Vehicle sharing.
- Widgets, Wear OS, watchOS, and web.
- Official fuel-price integrations.
- Firebase App Check and Cloud Functions-mediated remote read/write validation beyond the `D-23` account deletion server operation.
- Automatic account merging.
- Simultaneous use on more than one device, active multi-device synchronization, and remote-database-as-source-of-truth operation.
- Real-time Firestore listeners.
- Remote synchronization of user settings.
- Platform backup or synchronization of settings through Google Play services, Android backup or iCloud.
- Electric and hybrid energy modelling, including kWh input, mixed energy units and non-L/100 km consumption.

Rule for agents: any work touching out-of-scope functionality MUST be rejected or escalated. MVP scope changes require updating this specification and are a human review gate.

### 3.3 Post-MVP Roadmap Notes

Future scope may add electric and hybrid vehicles through a dedicated energy model. That work requires a new story or ADR covering `FuelType` expansion, kWh and mixed-unit input, consumption display units, validation, Firestore rules, local migrations and remote schema compatibility. Agents MUST NOT introduce `ELECTRIC` or `HYBRID` as MVP enum values.

Future scope may add settings synchronization through platform mechanisms such as Google Play services / Android backup on Android and iCloud on iOS. That work requires a new story or ADR covering user consent, platform API choice, conflict resolution, privacy wording, backup exclusion rules, test strategy and interaction with app account deletion. Agents MUST NOT add settings sync or platform backup APIs in the MVP. No platform API surface for settings sync — including entitlements, manifest keys, capabilities or dependencies — may be added to Android or iOS app projects in the MVP; adding an entitlement without using it is still a contract violation.

Future scope may add receipt and odometer image capture with local AI text recognition to prefill fuel-entry fields. The target fields are the receipt total amount, the receipt price per liter and the odometer reading. That work requires a new story or ADR covering the on-device OCR or AI engine, supported languages, model packaging, binary size, latency, battery impact, privacy review, image retention rules, user correction flow, confidence thresholds, validation, accessibility, tests and whether any image-loading dependency is required. Receipt images, odometer images, recognized raw text and extracted fields MUST remain local unless a later explicit owner decision changes the privacy model. Agents MUST NOT add image capture, OCR, local AI models, model downloads, image storage, image-loading dependencies or image-derived fuel-entry fields in the MVP.

Future scope may add Cloud Functions-mediated access to the remote database beyond the `D-23` account deletion server operation. The `D-23` account deletion server operation is the only MVP server-side privileged write. No other server-mediated write is in MVP scope. The intended future security goal is to validate incoming user data before it is inserted or updated remotely, and to verify the user's authenticated identity and authorization before any remote database read. That work requires a new story or ADR covering whether clients stop reading or writing Firestore directly, Firebase App Check or equivalent app integrity checks, authenticated callable or HTTPS function boundaries, server-side schema validation, owner matching, read filtering, rate limiting, abuse monitoring, audit logging, secret handling, emulator tests, deployment ownership and interaction with Firestore rules. Agents MUST NOT add Cloud Functions security features beyond `D-23`, App Check enforcement, server-mediated product reads or additional privileged server-side product writes in the MVP.

Future scope may add simultaneous use on multiple devices for the same account. That work requires a new story or ADR covering the migration from Room-as-source-of-truth to remote-database-as-source-of-truth, live or near-live synchronization semantics, conflict resolution, offline edit policy, local cache behaviour, remote validation, recovery from divergent devices, UX for stale data and required data migrations. Agents MUST NOT introduce active multi-device synchronization or make the remote database the product source of truth in the MVP.

## 4. Actors

| Actor | Description |
|-------|-------------|
| Local-only user | First launch without connectivity. Data is local under the `LOCAL_OWNER` sentinel and is not yet eligible for remote backup. Adopted into an anonymous identity as soon as connectivity allows. |
| Anonymous user | Uses the app immediately. Data is local and backed up remotely under an anonymous Firebase identity when connectivity allows. If the user uninstalls before conversion, data loss is an accepted risk. |
| Authenticated user | Uses Google or Apple. Data can be recovered on another device from remote backup. |

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
| `odometerInconsistent` | Yes | Derived, not user-editable. Recomputed whenever the entry's previous chronological neighbour may have changed. |
| `notes` | No | Null, or trimmed length 1..280. |
| `createdAt`, `updatedAt` | Yes | UTC. |
| `deletedAt` | No | Tombstone timestamp. |

Sync metadata (`syncState`, `localRevision`, `localMutationSeq`, `serverUpdatedAt`, `schemaVersion`) is **not** part of the domain model. It exists only on the local row, and reaches the UI only as the aggregate backup status.

### 5.3 UserSettings

| Field | Rules |
|-------|-------|
| `currency` | Defaults from locale, fallback `EUR`. The exact MVP supported set is `SUPPORTED_CURRENCY_CODES` in `docs/CONTRACTS.md §20.0.1`; every supported currency has two decimal minor units. |
| `distanceUnit` | `KM` in the MVP. `MILES` is prepared but not user-switchable. |
| `volumeUnit` | `LITER` in the MVP. `GALLON` is prepared but not user-switchable. |
| `analyticsEnabled` | Default `false`. Analytics collection starts only after an explicit opt-in. |

Settings are device-local and are not synchronized in the MVP. They do not survive destructive data-clearing flows: sign-out, anonymous "delete local data" and account deletion all reset settings to defaults.

## 6. Business Rules

### R-1 Odometer Consistency

The odometer of a fuel entry MUST be strictly greater than the previous non-deleted fuel entry odometer for the same vehicle in chronological order, and MUST be greater than or equal to `vehicle.initialOdometerKm` — unless the user explicitly confirms the inconsistency.

If the user enters an inconsistent value, the first save attempt MUST return a warning and mutate nothing. The UI MUST ask for explicit confirmation. A confirmed save stores the entry with `odometerInconsistent = true`. Segments containing an inconsistent entry produce no consumption.

The same rule applies on **edit**, evaluated against the entry's neighbours in its target chronological position. Because creating, editing or deleting one entry can change the validity of neighbouring entries in both the old and new chronological positions, the exact recompute set is defined in `docs/CONTRACTS.md §3.1` and is applied within the same transaction.

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

An entry with `isFullTank = false` does not close a segment and has no own consumption value. It is still included in the litres of the next full-to-full segment when its odometer places it inside that segment. The list projection uses `ConsumptionInvalidReason.EndEntryNotFullTank` to explain why that partial row has no consumption.

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

1. The welcome screen offers the platform's sign-in providers and "Continue without account" in a single step. There MUST NOT be an intermediate provider-selection screen, and there MUST NOT be a provider-less "Sign in" control: every sign-in affordance names the provider it uses.
2. "Continue without account" first attempts automatic Firebase anonymous authentication. If it succeeds, data is owned by that anonymous UID immediately. If it cannot complete because the app is offline or Firebase Auth is unavailable, the app creates a temporary local session under `LOCAL_OWNER` and the user can immediately use every MVP feature. Anonymous UID acquisition is retried in the background when connectivity allows, and local data is adopted into it without loss.
3. The providers offered are fixed per platform: Android offers Google; iOS offers Google and Apple. iOS MUST offer Apple whenever it offers Google. The welcome screen therefore presents exactly two actions on Android and exactly three on iOS, counting "Continue without account".
4. The MVP has no other sign-in method. Email and password, email link, phone or one-time code, and any third-party identity provider other than Google and Apple are not part of the MVP. The complete provider set is `AuthProvider` in `docs/CONTRACTS.md §20.3` and the complete credential set is `NativeAuthCredential` in `§20.8`; both are closed, and widening either is a gated change under `AGENTS.md`.
5. Routing MUST NOT happen while the authentication state is still undetermined. Once determined: if the user has no vehicles, route to first vehicle creation; otherwise route to the vehicle list.

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

Sign-out is offered only to a permanently authenticated user. If the outbox is non-empty, the sign-out use case returns `Err(ValidationWarning.PendingSyncBeforeSignOut(pendingCount))`; the UI offers to wait for sync, cancel, or discard pending changes after destructive confirmation. All local data for that owner and the local `user_settings` row are cleared after sign-out; recovery is by signing in again and pulling, while settings are recreated from defaults.

For an anonymous session there is no sign-out. The equivalent action is "delete local data" and requires the same two-step destructive confirmation, because the identity cannot be recovered. It clears all local app data, including settings.

Account deletion is required for store compliance. It re-authenticates if needed, requests the server/Admin account deletion operation selected by `D-23`, waits for that operation to delete remote data and the Firebase Auth account, then clears local data. The exact order and failure semantics are in `docs/CONTRACTS.md §11.5`.

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
| Crash reporting | Firebase Crashlytics behind `CrashReporter`, added in Phase 4 |
| Async | Coroutines and Flow |
| DI | Koin KMP for wiring, constructor injection for implementation classes |
| iOS interop | SKIE only in `:shared` |
| Serialization | `kotlinx.serialization` |
| Dates | `kotlinx-datetime` |
| Logging | Kermit behind `Logger` |

Exact versions are pinned in `docs/versions-matrix.md` and declared only in `gradle/libs.versions.toml`.

### 8.2 Modules

The canonical module inventory is defined in `docs/CONTRACTS.md §1.1`. The specification does not duplicate it; this section states only the behavioural dependency rules that matter at product level.

### 8.3 Dependency Rules

1. Feature `domain` packages are Kotlin pure and depend only on `:core:model` and `:core:common`.
2. Feature `data` packages depend on their own `domain`, `:core:model`, `:core:common`, `:core:database` and `:core:sync` — never on `:integration:*` and never on `:core:auth`. The current owner reaches them through `OwnerContext` in `:core:common`.
3. Feature `presentation` packages depend on their own `domain` and `:core:common`, never on `data`.
4. Features never depend on other features.
5. `:core:sync` depends on `:core:model`, `:core:common` and `:core:database`, never on `:core:auth` and never on `:integration:*`. Token handling lives entirely in `RemoteSyncSource` (the integration layer), so the sync engine does not reference `AuthClient` or `TokenProvider`.
6. `:core:analytics` and `:core:crash` contain provider-free abstractions and no product logic; provider SDK types stay in `:integration:*`.
7. `:core:database` depends on `:core:model`, `:core:common` and Room; it never depends on `:integration:*`, features or `:core:sync`.
8. `:shared` never depends on `:integration:*`.
9. Firebase and GitLive types never cross integration boundaries.
10. Koin is used only for dependency wiring and MUST NOT be accessed from domain or use case logic.
11. Ktor is deferred and MUST NOT be added until an HTTP API remote implementation is approved by ADR.
12. Only `:wiring:firebase` aggregates Firebase implementations into the app graph.
13. `vehicle.currentOdometerKm` and `fuel_entry.odometerInconsistent` are written only by `:core:database`.

Module-level rules are enforced by a Gradle configuration check; package-level rules require source analysis. Both MUST be executable checks in CI, and each rule MUST have a failing fixture test proving the check fires.

### 8.4 Shared Presentation

Presentation logic is shared in `commonMain` through state holders exposing `StateFlow<UiState>` and intent functions. Android adapts the Kotlin-facing graph to Compose. iOS consumes the Swift-facing facade and wraps the exported state holders in SwiftUI `ObservableObject`s. SwiftUI and Compose contain rendering and event forwarding, not business rules.

`UiState` carries no user-facing text; each platform maps typed values to its own string resources. Lifecycle, dispatcher and threading rules are in `docs/CONTRACTS.md §14`.

### 8.5 Cloud Provider Decoupling

`:shared` exposes a Kotlin-facing graph factory that accepts abstractions:

```kotlin
fun createAppGraph(dependencies: AppGraphDependencies): AppGraph
```

That factory and `AppGraphDependencies` are not part of the Swift-facing ABI. Swift calls `createSwiftAppGraph(isDebugBuild)` and consumes the facade defined in `docs/CONTRACTS.md §20.10`, which exposes concrete state holders and no provider dependency container.

The provider decoupling criterion is executable: excluding `:integration:*` and `:wiring:firebase` from settings MUST leave `:core:*` and `:feature:*` compiling and testing with local fakes. `AppGraphDependencies`, the Swift-facing facade and all public interface contracts are defined in `docs/CONTRACTS.md`.

## 9. Remote Backup and Recovery

### 9.1 Principles

- The local database is the only UI source of truth.
- Firestore is a backup and recovery replica only; it is never the product source of truth.
- The remote database exists solely so a user can retrieve backed-up data on a new device.
- The MVP does not provide active multi-device collaboration or a real-time cross-device data layer.
- Every write is local first.
- Remote backup is background work.
- IDs are UUID v4 generated on the client.
- Firestore offline persistence is disabled.
- Nothing is enqueued for remote backup while the owner is `LOCAL_OWNER`.

### 9.2 Local Control Tables

`outbox`: `seq`, `entityType`, `entityId`, `payload`, `localRevision`, `attemptCount`, `nextAttemptAt`, `lastError`, `lastErrorCode`, with `UNIQUE(entityType, entityId)` coalescing multiple local changes into one pending snapshot while preserving the original `seq`.

`sync_cursor`: `entityType`, `lastServerUpdatedAt`, `lastDocumentId`.

`quarantine`: remote documents that cannot be safely applied, either because their `schemaVersion` exceeds what this client supports or because their supported-version payload is malformed.

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
2. Start the cycle from `overlapSince = max(0, cursor.lastServerUpdatedAt - 30 seconds)`, applying the overlap once per cycle, not per page.
3. Query ordered by `updatedAt` and document ID. The first page uses `startAt(overlapSince, "")`; later pages use `startAfter(lastServerUpdatedAt, lastDocumentId)`, limit 200.
4. Include tombstones.
5. Apply each page in one local transaction.
6. If an outbox row exists for a remote entity, do not overwrite local data.
7. Otherwise apply remote data when it is newer than the local `serverUpdatedAt`. The local `updatedAt` never participates in this comparison.
8. Advance the cursor only after the local apply succeeds, and fail the cycle rather than loop if a full page produces no cursor progress.

Fuel entries whose vehicle has not yet arrived are persisted but hidden until it does.

### 9.5 Conflict Resolution

Last-write-wins at document level using the server `updatedAt`. Tombstones are regular documents and win over older updates. `(updatedAt, documentId)` orders the pull stream; it is not a tie-breaker for a single document, whose two sides share the same id.

Accepted limitation: concurrent active editing on multiple devices is not a supported MVP workflow. If the same account writes from multiple devices before one device is treated as the recovery target, last-write-wins can lose one whole-document update.

## 10. Firestore Security

Structure:

```text
users/{uid}/vehicles/{vehicleId}
users/{uid}/fuelEntries/{entryId}
```

Rules MUST enforce authentication, owner match, `ownerId == uid`, `updatedAt == request.time`, the closed MVP remote `schemaVersion` contract, document ID consistency, exact allowed keys, presence, type, nullability and range of every field. Hard deletes are rejected. The complete rule shape and the required emulator tests are in `docs/CONTRACTS.md §16`.

## 11. Non-Functional Requirements

| Area | Requirement |
|------|-------------|
| Platforms | Android `minSdk 26`, iOS 16+. |
| Performance | Cold start to first content under 2 s, median of 10 runs on the reference devices in a release build. A 1,000-entry list scrolls with no frame over 32 ms. Consumption for 1,000 entries under 100 ms, median of 20 runs. Reference devices and measurement method are fixed in `docs/versions-matrix.md`. |
| Offline | 100% of MVP functionality usable without network, including first launch. |
| Accessibility | System font size up to 200%, content labels, WCAG AA contrast, TalkBack and VoiceOver for F-1 through F-3. |
| Localization | Spanish and English from day one; no hardcoded user-facing strings. |
| Quality | ktlint, detekt, architecture checks, contract check, unit tests with coverage thresholds, backup and recovery tests, Firestore emulator tests. |
| Development | Test-driven development (TDD) is compulsory for product code, per the rule below. |
| CI | Build, tests, lint, Android, iOS simulator and shared framework verification on every PR, on a macOS runner from the first PR. |
| Privacy | Analytics off by default, privacy policy, store privacy labels, in-app account deletion, crash reporting redaction. |

### TDD rule

Test-driven development (TDD) is compulsory for product code. For every new behavior unit, the agent writes a failing test that expresses the behavior, then writes the minimum code that makes it pass, then refactors. A behavior unit is a single observable rule of the product (a validation branch, a use case outcome, a state transition, a formula), not a line of code and not a whole feature.

The test MUST fail for the right reason before the code is written: it must compile and execute, and fail because the behavior it expresses does not exist yet, not because of a setup or import error. The code written afterwards is the minimum that makes the test pass; no speculative generality.

The anti-paraguas clause: the test MUST be specific to the behavior being introduced. A single test that asserts several unrelated behaviors at once ("create vehicle, edit it, delete it, and verify the list is empty") is a paraguas test and does not satisfy TDD; each behavior gets its own failing test first. A test may cover a behavior that spans several functions, but it must not bundle distinct behaviors to avoid writing separate tests.

Coverage thresholds (Kover) are orthogonal to TDD. TDD governs the order and the intention: the test exists before the code, and expresses a behavior. Kover governs the result: at the end of the module, the fraction of code covered by tests meets the threshold in `docs/versions-matrix.md`. A story that satisfies TDD per behavior unit but leaves a branch uncovered still fails CI on coverage, and the missing test MUST be added (that test is a coverage test, not a TDD test, and is not subject to the order rule).

Exemptions from the TDD order rule are limited to the following, which still require tests (just not written-first):

- Native UI code (SwiftUI, Compose host screens). Verified by UI tests, screenshot tests and the accessibility audit of `E4-02`.
- Room schemas and migrations. Verified by migration tests that assert row preservation, per `docs/TECHNICAL_PLAN.md §6`.
- Firestore security rules. Verified by the emulator tests of `docs/CONTRACTS.md §16`.
- Koin wiring and provider integration code (Firebase, GitLive). Verified by graph-construction tests and integration tests.
- Architecture-rule fixtures. They already require a failing fixture test by `D-16`, which is its own form of test-first.

Any exemption used in a story MUST be declared in the handoff under "Decisions Made", with the reason. An exemption is a SHOULD deviation, not a MUST waiver: the code still requires tests, only the order is relaxed.

### TDD commit and push workflow

The TDD cycle is enforced not only in code order but in version-control order. Unless the owner explicitly states otherwise for a given story, every TDD story MUST follow this exact sequence of commits and pushes:

1. **RED phase:** write the failing test, commit and push. The commit message scope reflects the behavior being tested (e.g. `test(E1-04): reject negative odometer`).
2. **GREEN phase:** write the minimum code that makes the test pass, commit and push (e.g. `feat(E1-04): enforce odometer lower bound`).
3. **REFACTORING phase:** refactor while keeping the test green, commit and push (e.g. `refactor(E1-04): extract validation bound constant`). If no refactoring is needed, this phase is skipped and its commit is omitted.
4. **PR creation:** create the pull request only after the refactoring phase (or the green phase, if refactoring was skipped).

Each phase is a separate commit and a separate push. A phase MUST NOT be combined with another in a single commit. The PR MUST contain the full cycle in order: red, green, refactoring (when present). This workflow is a MUST and applies to every story that writes product code, unless the owner exempts a story explicitly.

## 12. Closed Technical Decisions

`docs/DECISION_BOARD.md` is the sole registry of decision IDs. This table mirrors its decision IDs and statuses and MUST stay identical.

| ID | Decision | Choice | Status |
|----|----------|--------|--------|
| D-0 | Backend | Cloud Firestore. | Accepted |
| D-1 | Local database | Room 3.0 KMP with `androidx.sqlite:sqlite-bundled`. | Accepted |
| D-2 | Kotlin-to-Swift interop | SKIE, only in `:shared`. | Accepted |
| D-3 | Dependency injection | Koin KMP for wiring, constructor injection for implementation classes. | Accepted |
| D-4 | `fuelType` | Stored on `Vehicle` from day one, not exposed in MVP UI; electric/hybrid values are deferred. | Accepted |
| D-5 | Firestore access from KMP | GitLive Firestore 2.6.x behind `RemoteSyncSource`. | Accepted |
| D-6 | Firebase Auth from KMP | GitLive Auth 2.6.x behind `AuthClient`. | Accepted |
| D-7 | Navigation | Native navigation per platform. | Accepted |
| D-8 | Presentation layer | Shared KMP state holders. | Accepted |
| D-9 | Firestore offline persistence | Disabled. | Accepted |
| D-10 | Metrics | Firebase Analytics behind `AnalyticsTracker`. | Accepted |
| D-11 | HTTP/API client | Ktor deferred until a future API-based remote implementation exists. | Deferred |
| D-12 | Image loading | Coil, only if a story ever requires image loading. | Deferred |
| D-13 | Firestore location | `europe-west1` single region. | Accepted |
| D-14 | Firebase project topology | One development Firebase project plus the local emulator now; add a separate production Firebase project before release. | Accepted |
| D-15 | Logging implementation | Kermit behind `Logger`. | Accepted |
| D-16 | Architecture checks | Konsist for package rules, custom Gradle check for module rules. | Accepted |
| D-17 | Flow testing helper | Turbine. | Accepted |
| D-18 | Coverage measurement | Kover with per-module thresholds. | Accepted |
| D-19 | Result type | Custom `Outcome<T, E>` in `:core:common`; Arrow rejected for the MVP. | Accepted |
| D-20 | Localization implementation | Native platform resources; `UiState` carries no user-facing text. | Accepted |
| D-21 | Crash reporting | Firebase Crashlytics behind `CrashReporter`, Phase 4. | Accepted |
| D-22 | Application identifiers | Fixed in `docs/identifiers.md`; the production Firebase project ID is deferred by `D-14`. | Accepted |
| D-23 | Account deletion execution | Firebase Admin server operation; client Firestore hard deletes remain forbidden. | Accepted |

Each decision is recorded as an ADR in `docs/adr/`. During Phase 0, ADRs MUST be validated against the selected tool versions and the version catalog, and every `Proposed` decision MUST be confirmed or changed by the project owner before the story that depends on it starts.

## 13. Glossary

| Term | Definition |
|------|------------|
| Fuel entry | A refueling event recorded by the user. |
| Full tank | Refueling event where the tank is filled completely. |
| Segment | Interval between two consecutive full-tank fuel entries. |
| Tombstone | Logical deletion marker propagated through remote backup. It is a full remote document written with `set(merge = false)`, `deleted = true` and `deletedAt` set; see `docs/CONTRACTS.md §3` and `§16` for the exact shape. |
| Outbox | Local queue of pending snapshots to push remotely. |
| LWW | Last-write-wins conflict resolution. |
| `LOCAL_OWNER` | Sentinel owner used before an anonymous Firebase UID exists. |
| Adoption | Rewriting `LOCAL_OWNER` rows to a real UID and enqueueing them for remote backup in deterministic local mutation order. |
| Orphan entry | A remotely restored fuel entry whose vehicle has not been pulled yet. |
| Quarantine | Local storage for remote documents that cannot be safely applied, including unsupported future schema versions and malformed supported-version payloads. |
