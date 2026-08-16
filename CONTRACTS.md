# System Contracts - carApp MVP

> Normative guardrail layer for implementation. This document defines API contracts, state machines, persistence formats, error taxonomy, and boundary rules that future agents must follow.

## 1. Authority

`SPECIFICATION.md` remains the top-level product specification. This document makes implementation contracts explicit. If this document conflicts with `SPECIFICATION.md`, update both documents in the same change before implementation continues.

Required reading order for implementation:

1. `SPECIFICATION.md`
2. `CONTRACTS.md`
3. `TECHNICAL_PLAN.md`
4. `BACKLOG.md`
5. `AGENTS.md`

## 2. Canonical Data Types

### Identifiers

| Type | Kotlin representation | Persistence | Rules |
|------|-----------------------|-------------|-------|
| `EntityId` | value class wrapping `String` | TEXT / Firestore string | UUID v4 generated on the client. Lowercase canonical text form. |
| `OwnerId` | value class wrapping `String` | TEXT / Firestore string | Firebase UID or `LOCAL_OWNER` before authentication exists in Phase 1. |
| `CurrencyCode` | value class wrapping `String` | TEXT / Firestore string | ISO-4217 uppercase code. MVP default fallback is `EUR`. |

### Time

| Concept | Kotlin representation | Persistence | Rules |
|---------|-----------------------|-------------|-------|
| Domain instant | `kotlinx.datetime.Instant` | epoch milliseconds in Room JSON payloads; Firestore timestamp remotely | UTC only. No local timezone persistence. |
| Local `updatedAt` | `Instant` | INTEGER epoch milliseconds | Provisional local timestamp. Never authoritative for remote conflict wins. |
| `serverUpdatedAt` | `Instant?` | INTEGER epoch milliseconds | Authoritative timestamp received from Firestore. Null means never synced. |

### Money and decimals

`Float` and `Double` are forbidden for money, volume, price, and consumption calculations.

| Concept | Kotlin representation | Scale | Persistence |
|---------|-----------------------|-------|-------------|
| `Money` | value class/data class with `minorUnits: Long`, `currency: CurrencyCode` | ISO-4217 minor units | INTEGER + currency code |
| `FuelVolume` | scaled integer value class | 3 decimals, liters * 1000 | INTEGER |
| `PricePerLiter` | scaled integer value class | 3 decimals, currency units * 1000 | INTEGER |
| `Consumption` | scaled integer or decimal value class | 2 decimals for presentation | Computed read model, not authoritative persistence |

Rounding mode: `HALF_UP` for MVP calculations unless a later ADR changes it.

Canonical formulas:

```text
totalCostMinor = roundHalfUp(litersScaled / 1000 * pricePerLiterScaled / 1000 * minorUnitFactor)
pricePerLiterScaled = roundHalfUp(totalCostMinor / minorUnitFactor / liters * 1000)
litersScaled = roundHalfUp(totalCostMinor / minorUnitFactor / pricePerLiter * 1000)
```

For EUR, `minorUnitFactor = 100`.

## 3. Canonical Entity Schema

### Domain model vs local row vs remote document

Domain models expose business concepts. Local rows add sync metadata. Remote documents contain only synchronized data plus remote metadata.

| Field | Domain | Local Room row | Firestore document | Notes |
|-------|--------|----------------|--------------------|-------|
| `id` | Yes | Yes | Document ID and field | Client-generated UUID v4. |
| `ownerId` | Yes | Yes | Yes | Must equal authenticated UID remotely. |
| `createdAt` | Yes | Yes | Yes | Client-created UTC timestamp. |
| `updatedAt` | Yes | Yes | Yes | Local provisional in Room; server timestamp remotely. |
| `serverUpdatedAt` | No | Yes | No | Local sync metadata only. |
| `deletedAt` | Yes | Yes | Yes | Null when active. |
| `deleted` | No | Derived/stored | Yes | `deleted == deletedAt != null`. |
| `syncState` | No | Yes | No | Local-only. |
| `localRevision` | No | Yes | No | Local-only. |
| `schemaVersion` | No | Yes in payload | Yes | Starts at `1`. |

Remote writes are full-document `set(..., merge = false)` operations. Partial remote updates are forbidden in the MVP sync engine.

### Vehicle

Canonical fields:

- `id`
- `ownerId`
- `name`
- `initialOdometerKm`
- `brand`
- `model`
- `fuelType`
- `createdAt`
- `updatedAt`
- `deleted`
- `deletedAt`
- `schemaVersion`

`currentOdometerKm` is a derived read model. It is never accepted from user input and is not used for remote conflict arbitration. If stored locally for query performance, it must be recomputed transactionally after fuel entry create/update/delete.

`initialOdometerKm` is editable only while the vehicle has no non-deleted fuel entries.

### FuelEntry

Canonical fields:

- `id`
- `ownerId`
- `vehicleId`
- `date`
- `odometerKm`
- `litersScaled`
- `pricePerLiterScaled`
- `totalCostMinor`
- `currency`
- `isFullTank`
- `hasMissedEntries`
- `odometerInconsistent`
- `notes`
- `createdAt`
- `updatedAt`
- `deleted`
- `deletedAt`
- `schemaVersion`

Persisted fuel entries must contain non-null `litersScaled`, `pricePerLiterScaled`, and `totalCostMinor`. Form drafts may contain nullable partial input; repositories must not persist drafts.

### UserSettings

Canonical persistence is metric:

- Distances are stored in kilometers.
- Volumes are stored in liters.
- `distanceUnit = KM` and `volumeUnit = LITER` are fixed defaults until unit switching is explicitly implemented.
- Unit settings affect presentation/input only, never domain storage.

## 4. Ordering Rules

Fuel entry chronological order is deterministic:

```text
date ASC, createdAt ASC, id ASC
```

Odometer validation uses the previous non-deleted fuel entry in chronological order.

Consumption segment membership uses odometer range:

```text
P.odometerKm < X.odometerKm <= E.odometerKm
```

Consumption calculation must sort candidates by:

```text
odometerKm ASC, date ASC, id ASC
```

Duplicate or non-increasing odometer values inside a segment invalidate the segment.

Partial fuel entries before the first full-tank anchor never contribute to a valid segment.

## 5. Validation and Save Semantics

Write use cases validate commands before repository writes.

Expected validation failures return typed errors and do not throw.

Odometer inconsistency is a warning, not an automatic write:

1. First save attempt returns `ValidationWarning.OdometerInconsistent`.
2. UI must ask for explicit confirmation.
3. Confirmed save writes the entry with `odometerInconsistent = true`.

Validation constraints:

| Field | Rule |
|-------|------|
| Vehicle name | Trimmed length 1..40, unique per owner among non-deleted vehicles, case-insensitive. |
| Brand/model | Null or trimmed length 0..40. Empty string is normalized to null. |
| `initialOdometerKm` | 0..2,000,000. |
| Fuel entry `date` | Not more than 1 hour in the future according to injected `Clock`. |
| `odometerKm` | >= vehicle `initialOdometerKm`; strict chronological increase unless confirmed as inconsistent. |
| `litersScaled` | 1..500,000. |
| `pricePerLiterScaled` | > 0. |
| `totalCostMinor` | > 0. |
| `notes` | Null or trimmed length 0..280. Empty string is normalized to null. |

## 6. Result and Error Taxonomy

Public use cases and repositories return `Result<T, AppError>` or `Flow<Result<T, AppError>>` where an expected failure can occur. They do not throw for expected validation, auth, persistence, sync, or remote failures.

### AppError

Required categories:

- `ValidationError`
- `ValidationWarning`
- `AuthError`
- `PersistenceError`
- `SyncError`
- `RemoteError`
- `SecurityError`
- `UnexpectedError`

### ValidationError

- `RequiredField`
- `InvalidLength`
- `DuplicateName`
- `OutOfRange`
- `FutureDate`
- `InvalidMoneyInput`
- `InvalidUnit`
- `EntityDeleted`
- `EntityNotFound`

### ValidationWarning

- `OdometerInconsistent`
- `PendingSyncBeforeSignOut`

Warnings do not mutate state until explicitly confirmed.

### AuthError

- `Cancelled`
- `NetworkUnavailable`
- `CredentialAlreadyInUse`
- `ProviderUnavailable`
- `TokenExpired`
- `PermissionDenied`
- `RequiresRecentLogin`
- `Unknown`

### PersistenceError

- `DatabaseUnavailable`
- `TransactionFailed`
- `MigrationFailed`
- `SerializationFailed`
- `ConstraintViolation`

### SyncError

- `RetryableNetwork`
- `AuthExpired`
- `PermissionDenied`
- `ValidationRejected`
- `PayloadPoisoned`
- `ConflictUnresolved`
- `RemoteUnavailable`

### RemoteError

- `Unavailable`
- `DeadlineExceeded`
- `PermissionDenied`
- `Unauthenticated`
- `InvalidArgument`
- `NotFound`
- `Unknown`

Remote provider errors must be mapped inside integration modules. Firebase/GitLive exceptions must not cross module boundaries.

## 7. Sync State Machine

Use this local sync state enum:

```text
PENDING
SYNCING
SYNCED
FAILED_RETRYABLE
FAILED_POISONED
```

Allowed transitions:

| From | To | Trigger |
|------|----|---------|
| `SYNCED` | `PENDING` | Local create/update/delete. |
| `PENDING` | `SYNCING` | Sync engine starts push for row. |
| `SYNCING` | `SYNCED` | Remote ack received and `localRevision` unchanged. |
| `SYNCING` | `PENDING` | Local revision changed during push. |
| `SYNCING` | `FAILED_RETRYABLE` | Retryable network/remote failure. |
| `FAILED_RETRYABLE` | `PENDING` | Automatic due retry or manual retry. |
| `SYNCING` | `FAILED_POISONED` | Validation/security/payload failure. |
| `FAILED_POISONED` | `PENDING` | User or repair flow edits entity and re-enqueues valid snapshot. |

`FAILED_POISONED` is never retried automatically.

## 8. Outbox Contract

Outbox payload format:

- JSON encoded with `kotlinx.serialization`.
- Includes `schemaVersion`.
- Includes `entityType`.
- Includes full entity snapshot.
- Encodes instants as epoch milliseconds UTC.
- Excludes `syncState`, `localRevision`, and other local-only metadata.

Outbox coalescing:

- There is at most one outbox row per `(entityType, entityId)`.
- `ON CONFLICT DO UPDATE` updates payload, localRevision, attempt fields as needed.
- The original `seq` must be preserved to keep causal order.

Dependency order:

1. Vehicle upserts.
2. Fuel entry upserts.
3. Fuel entry tombstones.
4. Vehicle tombstones.

Vehicle deletion creates tombstones for the vehicle and all non-deleted fuel entries in one local transaction.

## 9. Sync Cycle Contract

Only one sync cycle may run at a time per owner. Concurrent triggers coalesce into one pending cycle.

Default cycle order:

1. Push due local outbox rows.
2. Pull remote changes.

Initial sync on a new authenticated device with an empty local database may pull first.

Push:

- Batch limit: 50 outbox rows.
- Vehicles are pushed before fuel entries.
- Remote writes use client document ID and server timestamp.
- After write, the remote document is re-read to obtain authoritative `updatedAt`.
- Local confirmation happens in one transaction.

Pull:

- Page limit: 200 documents.
- Cursor stores `(lastServerUpdatedAt, lastDocumentId)`.
- Query ordering is `updatedAt ASC, documentId ASC`.
- Overlap window is 30 seconds.
- Apply is idempotent.
- Cursor advances only after local transaction succeeds.

Backoff:

```text
delay = min(15 minutes, 1000ms * 2^attemptCount) +/- 20% jitter
```

Jitter source must be injectable for deterministic tests.

Manual retry sets `nextAttemptAt = now` and preserves `attemptCount`.

## 10. RemoteSyncSource Contract

`RemoteSyncSource` is implemented only by integration modules.

The MVP implementation is Firebase-backed. Future API-backed implementations may use Ktor, but Ktor is not an MVP dependency until such an implementation is explicitly approved by ADR.

Required interface shape:

```kotlin
interface RemoteSyncSource {
    suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Result<RemoteAck, RemoteError>

    suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Result<RemotePage, RemoteError>
}
```

Required DTOs:

```kotlin
data class RemoteAck(
    val entityType: EntityType,
    val entityId: EntityId,
    val serverUpdatedAt: Instant,
)

data class RemoteCursor(
    val lastServerUpdatedAt: Instant,
    val lastDocumentId: EntityId?,
)

data class RemotePage(
    val items: List<RemoteSnapshot>,
    val nextCursor: RemoteCursor,
    val hasMore: Boolean,
)
```

Side effects:

- `pushSnapshot` performs exactly one full-document remote set and one remote read for acknowledgement.
- `pullChanges` performs no local writes.
- No Firebase/GitLive type appears in the interface or DTOs.
- No Ktor type appears in the interface or DTOs.

## 11. Auth Contracts

### AuthClient

```kotlin
interface AuthClient {
    val authState: StateFlow<AuthSession?>
    suspend fun signInAnonymously(): Result<AuthSession, AuthError>
    suspend fun signInWithCredential(credential: NativeAuthCredential): Result<AuthSession, AuthError>
    suspend fun linkCredential(credential: NativeAuthCredential): Result<AuthSession, AuthError>
    suspend fun signOut(): Result<Unit, AuthError>
    suspend fun deleteAccount(): Result<Unit, AuthError>
}
```

`NativeAuthCredential` is an abstraction. Platform UI obtains Google/Apple credentials; common code exchanges or links them.

### TokenProvider

```kotlin
interface TokenProvider {
    suspend fun getIdToken(forceRefresh: Boolean = false): Result<AuthToken, AuthError>
}
```

`AuthToken` includes:

- `value`
- `expiresAt`

If a remote operation returns auth-expired, the sync engine retries once with `forceRefresh = true`.

### First launch

MVP first launch requires network connectivity to establish an anonymous Firebase UID. After a UID exists locally, all MVP writes must work offline.

### Anonymous conversion

Anonymous conversion must preserve UID through Firebase credential linking. If a provider flow would change UID, the MVP must abort conversion and show a typed error.

Credential collision cancellation leaves anonymous session and local data untouched. Choosing the existing account requires destructive confirmation, clears local anonymous data, signs into the existing account, then performs initial pull.

## 11.1 App Graph Contract

`:shared` exposes the application graph through a dependency container:

```kotlin
data class AppGraphDependencies(
    val databaseFactory: DatabaseFactory,
    val authClient: AuthClient,
    val tokenProvider: TokenProvider,
    val remoteSyncSource: RemoteSyncSource,
    val analyticsTracker: AnalyticsTracker,
    val clock: AppClock,
    val dispatchers: DispatcherProvider,
    val uuidGenerator: UuidGenerator,
    val logger: Logger,
    val localeProvider: LocaleProvider,
    val connectivityObserver: ConnectivityObserver,
    val syncTriggerAdapter: SyncTriggerAdapter,
)
```

Rules:

- Koin may construct `AppGraphDependencies` in wiring/platform modules.
- `AppGraphDependencies` must contain abstractions only.
- Firebase, GitLive, Koin, Ktor, Android, and iOS concrete types must not appear in this data class.
- Tests may provide fake dependencies without starting Koin.

## 12. Repository Contracts

Repositories are interfaces owned by feature domain packages. Implementations live in feature data packages.

All write methods:

- Run in local database transactions where multiple rows/outbox entries change.
- Enqueue outbox snapshots for synchronized entities.
- Return `Result<..., AppError>`.
- Never call Firebase directly.

### VehicleRepository

Required operations:

```kotlin
interface VehicleRepository {
    fun observeVehicles(includeDeleted: Boolean = false): Flow<Result<List<Vehicle>, AppError>>
    fun observeVehicle(id: EntityId): Flow<Result<Vehicle?, AppError>>
    suspend fun createVehicle(command: CreateVehicleCommand): Result<EntityId, AppError>
    suspend fun updateVehicle(command: UpdateVehicleCommand): Result<Unit, AppError>
    suspend fun deleteVehicle(id: EntityId): Result<Unit, AppError>
}
```

`deleteVehicle` tombstones the vehicle and its fuel entries in one transaction.

### FuelEntryRepository

Required operations:

```kotlin
interface FuelEntryRepository {
    fun observeFuelEntries(vehicleId: EntityId, includeDeleted: Boolean = false): Flow<Result<List<FuelEntry>, AppError>>
    suspend fun getFuelEntry(id: EntityId): Result<FuelEntry?, AppError>
    suspend fun createFuelEntry(command: CreateFuelEntryCommand): Result<EntityId, AppError>
    suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Result<Unit, AppError>
    suspend fun deleteFuelEntry(id: EntityId): Result<Unit, AppError>
}
```

Fuel entry writes update/recompute the vehicle current odometer read model transactionally if that read model is stored.

### SettingsRepository

Required operations:

```kotlin
interface SettingsRepository {
    val settings: Flow<Result<UserSettings, AppError>>
    suspend fun updateSettings(command: UpdateSettingsCommand): Result<Unit, AppError>
}
```

## 13. Use Case Contracts

Every use case:

- Lives in feature `domain` or appropriate `:core:*` module.
- Accepts immutable command/query models.
- Returns `Result<T, AppError>` for expected failures.
- Does not depend on platform APIs.
- Does not access Room, Firebase, GitLive, Koin, Ktor, Android, or iOS APIs directly.

Write command models are not the same as UI form draft models.

Consumption calculation:

```kotlin
interface CalculateConsumption {
    operator fun invoke(entries: List<FuelEntry>): ConsumptionReport
}
```

`ConsumptionReport` includes:

- per-entry consumption result
- invalidation reason per invalid segment
- vehicle average consumption when available
- count of valid segments

## 14. Presentation State Contract

Shared state holders:

- Live in feature `presentation` packages.
- Expose immutable `StateFlow<UiState>`.
- Accept intent functions.
- Use injected `CoroutineScope` or lifecycle owner abstraction.
- Use injected `DispatcherProvider`.
- Never create `GlobalScope`.
- Never call platform UI APIs.
- Never call Firebase/GitLive APIs.
- Never call Koin directly.

Platform adapters:

- Android may wrap state holders in ViewModel adapters.
- iOS may wrap state holders in `ObservableObject`.
- Adapters contain rendering/lifecycle glue only.
- Validation, formatting decisions, repository calls, and business logic remain shared.

## 15. Platform Boundary Contract

Allowed `expect`/`actual` or platform boundary areas:

- UUID generation if needed.
- Room database builder/driver.
- Platform file path/provider for database location.
- Connectivity observer.
- Background sync trigger adapter.
- Native Google/Apple credential acquisition.
- Locale provider.
- Logger sink.
- Analytics sink.

Forbidden `expect`/`actual` areas:

- Domain entities.
- Business validation.
- Consumption calculation.
- Repository interfaces.
- Sync algorithm.
- Conflict resolution.
- Error taxonomy.

## 15.1 Dependency Injection Contract

Koin KMP is the accepted dependency injection library for the MVP.

Rules:

- Koin modules are wiring artifacts only.
- Koin APIs are allowed in application, wiring, integration, and platform composition modules.
- Koin APIs are forbidden in feature `domain` packages, use cases, repository interfaces, repository implementations, and shared presentation business logic.
- Implementation classes must continue to use constructor injection.
- Tests should instantiate classes directly or use dedicated test modules; domain tests must not require a Koin runtime.
- Koin definitions must bind abstractions to implementations at module boundaries.

Koin must not become a service locator inside product logic.

## 15.2 HTTP/API Client Contract

Ktor is the reserved HTTP client for future API-based remote implementations.

Rules:

- Do not add Ktor dependencies during the MVP while Firebase Firestore is the selected remote database implementation.
- A future Ktor implementation must implement existing provider abstractions, such as `RemoteSyncSource`.
- Ktor types must not appear in feature, domain, repository, or presentation contracts.
- Adding Ktor requires an ADR update and a backlog story that defines the target API contract.

## 15.3 Image Loading Contract

Coil is the approved image loading library if the project needs image loading.

Rules:

- Do not add image loading dependencies until a backlog story requires image loading.
- If image loading is required, use Coil.
- No alternative image loading library may be introduced without updating `DECISION_BOARD.md` and adding or updating an ADR.
- Image loading must remain in UI/platform layers and must not enter domain, data, or sync logic.

## 16. Firestore Contract

Remote collections:

```text
users/{uid}/vehicles/{vehicleId}
users/{uid}/fuelEntries/{entryId}
users/{uid}/meta/settings
```

Remote rule requirements:

- `request.auth != null`
- `request.auth.uid == uid`
- `request.resource.data.ownerId == uid`
- `request.resource.data.updatedAt == request.time`
- `schemaVersion` is supported
- Required fields exist with valid primitive types

Remote queries:

```text
where(updatedAt >= since)
orderBy(updatedAt ASC)
orderBy(documentId ASC)
limit(200)
```

If Firestore requires indexes for these queries, `firestore.indexes.json` must define them.

## 16.1 Analytics Contract

Metrics use this common abstraction:

```kotlin
interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
    fun setUserProperties(properties: AnalyticsUserProperties)
    fun setEnabled(enabled: Boolean)
}
```

Firebase Analytics is the MVP implementation and must live behind `AnalyticsTracker`.

Allowed event categories:

- onboarding started/completed
- anonymous sign-in selected
- permanent sign-in selected
- vehicle created
- fuel entry created
- fuel entry marked full/partial
- sync status changed at aggregate level
- account conversion started/completed/failed by typed reason
- account deletion started/completed/failed by typed reason

Forbidden analytics payloads:

- exact odometer values
- exact fuel volume
- exact cost or price per liter
- notes
- raw entity IDs
- Firebase UID
- auth tokens or credentials
- raw sync payloads

Analytics calls are forbidden in domain logic and data persistence logic. Shared presentation or application-level orchestration may track product events after successful use case results.

## 17. Logging and Privacy

All logging uses a `Logger` abstraction.

Levels:

- `DEBUG`
- `INFO`
- `WARN`
- `ERROR`

Logs must never include:

- ID tokens or credentials.
- Raw Firestore payloads.
- Notes.
- Exact odometer values.
- Exact costs.
- Full Firebase UID in release builds.

Debug builds may log entity IDs. Release builds must redact identifiers.

## 18. CI and Branch Protection Contract

Phase 0 must define exact CI check names. Minimum required checks:

- `android-assemble`
- `shared-tests`
- `ios-simulator-build`
- `ktlint`
- `detekt`
- `architecture-check`
- `contract-check` once contract validation exists

Once CI exists, branch protection for `main` should require those checks before merge.

## 19. Human Review Additions

Human review is mandatory for changes to:

- `CONTRACTS.md`
- canonical data types
- error taxonomy
- sync state machine
- repository contracts
- auth contracts
- Firestore rule contract
- logging/privacy rules
