package com.ruizurraca.carapp.core.sync

import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.CLIENT_MAX_SCHEMA_VERSION
import com.ruizurraca.carapp.core.common.CONNECTIVITY_ERROR_CODES
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import com.ruizurraca.carapp.core.common.MAX_RETRYABLE_ATTEMPTS
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.SUPPORTED_CURRENCY_CODES
import com.ruizurraca.carapp.core.common.SyncError
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.common.UnexpectedError
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.database.SyncDatabaseAccess
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.random.Random
import kotlin.time.Instant

fun interface JitterSource {
    fun nextInt(
        from: Int,
        until: Int,
    ): Int
}

internal data class OutboxRecord(
    val sequence: Long,
    val entityType: EntityType,
    val entityId: EntityId,
    val payload: String,
    val localRevision: Long,
    val attemptCount: Int,
    val nextAttemptAt: Instant,
    val lastErrorCode: String?,
    val deleted: Boolean,
)

internal data class SyncCounts(
    val pending: Int,
    val retryable: Int,
    val poisoned: Int,
)

internal sealed interface PullRecord {
    data class Vehicle(
        val document: RemoteDocument,
        val ownerId: OwnerId,
        val name: String,
        val nameFold: String,
        val initialOdometerKm: Long,
        val brand: String?,
        val model: String?,
        val fuelType: String,
        val createdAt: Instant,
        val updatedAt: Instant,
        val deletedAt: Instant?,
        val schemaVersion: Int,
    ) : PullRecord

    data class FuelEntry(
        val document: RemoteDocument,
        val ownerId: OwnerId,
        val vehicleId: EntityId,
        val date: Instant,
        val odometerKm: Long,
        val litersScaled: Long,
        val pricePerLiterScaled: Long,
        val totalCostMinor: Long,
        val currency: String,
        val isFullTank: Boolean,
        val hasMissedEntries: Boolean,
        val notes: String?,
        val createdAt: Instant,
        val updatedAt: Instant,
        val deletedAt: Instant?,
        val schemaVersion: Int,
    ) : PullRecord

    data class Quarantined(
        val record: QuarantineRecord,
    ) : PullRecord
}

internal interface SyncPersistence {
    suspend fun isOwnerDatabaseEmpty(ownerId: OwnerId): Boolean

    suspend fun dueOutbox(
        now: Instant,
        limit: Int,
    ): List<OutboxRecord>

    suspend fun markSyncing(row: OutboxRecord)

    suspend fun confirmPush(
        row: OutboxRecord,
        serverUpdatedAt: Instant?,
    )

    suspend fun failPush(
        row: OutboxRecord,
        attemptCount: Int,
        nextAttemptAt: Instant,
        errorCode: String,
        poisoned: Boolean,
        cycleId: CycleId,
    )

    suspend fun cursor(entityType: EntityType): RemoteCursor

    suspend fun applyPullPage(
        ownerId: OwnerId,
        entityType: EntityType,
        records: List<PullRecord>,
        cursor: RemoteCursor,
    ): List<QuarantineRecord>

    suspend fun markConnectivityFailuresDue(now: Instant)

    suspend fun resetFailed(now: Instant): Outcome<Unit, AppError>

    suspend fun counts(): SyncCounts
}

fun createSyncController(
    scope: CoroutineScope,
    databaseAccess: SyncDatabaseAccess,
    ownerContext: OwnerContext,
    connectivity: ConnectivityObserver,
    remote: RemoteSyncSource,
    clock: AppClock,
    uuidGenerator: UuidGenerator,
    adoption: suspend () -> Outcome<Unit, AppError>,
    onPoisoned: (AppError, Map<String, String>) -> Unit,
    onQuarantined: (QuarantineRecord) -> Unit,
    isDebugBuild: Boolean,
): SyncController {
    val persistence = SqlDelightSyncPersistence(databaseAccess)
    return DefaultSyncController(
        scope = scope,
        ownerContext = ownerContext,
        connectivity = connectivity,
        remote = remote,
        persistence = persistence,
        clock = clock,
        uuidGenerator = uuidGenerator,
        jitter = JitterSource { from, until -> Random.nextInt(from, until) },
        adoption = adoption,
        onPoisoned = onPoisoned,
        onQuarantined = onQuarantined,
        debugEnabled = isDebugBuild,
        debugLoader = persistence::debugLines,
    )
}

internal class DefaultSyncController(
    private val scope: CoroutineScope,
    private val ownerContext: OwnerContext,
    private val connectivity: ConnectivityObserver,
    private val remote: RemoteSyncSource,
    private val persistence: SyncPersistence,
    private val clock: AppClock,
    private val uuidGenerator: UuidGenerator,
    private val jitter: JitterSource,
    private val adoption: suspend () -> Outcome<Unit, AppError> = { Outcome.Ok(Unit) },
    private val onPoisoned: (AppError, Map<String, String>) -> Unit = { _, _ -> },
    private val onQuarantined: (QuarantineRecord) -> Unit = {},
    private val debugEnabled: Boolean = false,
    private val debugLoader: suspend () -> List<String> = { emptyList() },
) : SyncController {
    private val mutableStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status: StateFlow<SyncStatus> = mutableStatus
    private val cycleMutex = Mutex()
    private var cycleRunning = false
    private var pendingCycle = false
    private var unexpectedFailure = false
    private var cycleFailure = false
    private var adoptionFailure = false
    private var adoptionAttemptCount = 0
    private var adoptionRetryScheduled = false

    override fun requestSync(reason: SyncTrigger) {
        scope.launch {
            val startsCycle =
                cycleMutex.withLock {
                    if (cycleRunning) {
                        pendingCycle = true
                        false
                    } else {
                        cycleRunning = true
                        true
                    }
                }
            if (startsCycle) drainCycles(reason)
        }
    }

    override suspend fun retryFailed(): Outcome<Unit, AppError> {
        val result = persistence.resetFailed(clock.now())
        refreshStatus(running = false)
        if (result is Outcome.Ok) requestSync(SyncTrigger.PullToRefresh)
        return result
    }

    override suspend fun debugLines(): List<String> = if (debugEnabled) debugLoader() else emptyList()

    @Suppress("TooGenericExceptionCaught")
    private suspend fun drainCycles(firstReason: SyncTrigger) {
        var reason = firstReason
        while (true) {
            try {
                runCycle(reason)
                unexpectedFailure = false
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                unexpectedFailure = true
                onPoisoned(
                    UnexpectedError(":core:sync", failure::class.simpleName ?: "Throwable"),
                    mapOf("cycleId" to "unavailable"),
                )
            }
            val runAgain =
                cycleMutex.withLock {
                    if (pendingCycle) {
                        pendingCycle = false
                        true
                    } else {
                        cycleRunning = false
                        false
                    }
                }
            if (!runAgain) {
                refreshStatus(running = false)
                return
            }
            reason = SyncTrigger.PostWriteDebounce
        }
    }

    private suspend fun runCycle(reason: SyncTrigger) {
        cycleFailure = false
        if (!connectivity.isOnline.value) {
            refreshStatus(running = false)
            return
        }
        val ownerId = ownerContext.current
        if (ownerId == LOCAL_OWNER) {
            refreshStatus(running = false)
            return
        }
        mutableStatus.value = SyncStatus.Syncing
        if (reason == SyncTrigger.ConnectivityRecovered) persistence.markConnectivityFailuresDue(clock.now())
        if (adoption() is Outcome.Err) {
            adoptionFailure = true
            adoptionAttemptCount = minOf(adoptionAttemptCount + 1, MAX_RETRYABLE_ATTEMPTS)
            scheduleAdoptionRetry()
            refreshStatus(running = false)
            return
        }
        adoptionFailure = false
        adoptionAttemptCount = 0
        val pullFirst = persistence.isOwnerDatabaseEmpty(ownerId)
        if (pullFirst) {
            pull(ownerId)
            push(ownerId)
        } else {
            push(ownerId)
            pull(ownerId)
        }
    }

    private suspend fun push(ownerId: OwnerId) {
        val cycleId = CycleId(uuidGenerator.newId())
        val rows = persistence.dueOutbox(clock.now(), PUSH_BATCH_LIMIT).stableDependencyOrder()
        for (row in rows) {
            persistence.markSyncing(row)
            when (
                val result =
                    remote.pushSnapshot(
                        ownerId,
                        EntitySnapshot(row.entityType, row.entityId, CLIENT_MAX_SCHEMA_VERSION, row.payload),
                    )
            ) {
                is Outcome.Ok -> persistence.confirmPush(row, result.value.serverUpdatedAt)
                is Outcome.Err -> handlePushFailure(row, result.error, cycleId)
            }
        }
    }

    private suspend fun handlePushFailure(
        row: OutboxRecord,
        error: RemoteError,
        cycleId: CycleId,
    ) {
        if (error == RemoteError.NotFound) {
            persistence.confirmPush(row, null)
            return
        }
        val attemptCount = minOf(row.attemptCount + 1, MAX_RETRYABLE_ATTEMPTS)
        val poisoned =
            when (error) {
                RemoteError.PermissionDenied, RemoteError.InvalidArgument -> true

                RemoteError.Unknown, RemoteError.Unauthenticated -> attemptCount >= MAX_RETRYABLE_ATTEMPTS

                RemoteError.Unavailable, RemoteError.DeadlineExceeded,
                RemoteError.NotFound,
                -> false
            }
        if (poisoned) {
            val syncError =
                when (error) {
                    RemoteError.PermissionDenied -> SyncError.PermissionDenied
                    RemoteError.InvalidArgument -> SyncError.ValidationRejected
                    else -> SyncError.PayloadPoisoned
                }
            onPoisoned(
                syncError,
                mapOf("entityType" to row.entityType.name, "code" to error.code, "cycleId" to cycleId.value),
            )
        }
        val delay = retryDelayMillis(attemptCount, jitter)
        persistence.failPush(
            row = row,
            attemptCount = attemptCount,
            nextAttemptAt = Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds() + delay),
            errorCode = error.code,
            poisoned = poisoned,
            cycleId = cycleId,
        )
    }

    private suspend fun pull(ownerId: OwnerId) {
        for (entityType in EntityType.entries) {
            if (!pullEntity(ownerId, entityType)) return
        }
    }

    private suspend fun pullEntity(
        ownerId: OwnerId,
        entityType: EntityType,
    ): Boolean {
        val stored = persistence.cursor(entityType)
        val overlapSince = maxOf(0L, stored.lastServerUpdatedAt.toEpochMilliseconds() - OVERLAP_MS)
        var requestCursor = RemoteCursor(Instant.fromEpochMilliseconds(overlapSince), null)
        var hasMore = true
        while (hasMore) {
            val result = remote.pullChanges(ownerId, entityType, requestCursor, PULL_PAGE_LIMIT)
            if (result is Outcome.Err) return failPullCycle()
            val page = (result as Outcome.Ok).value
            if (page.items.isEmpty()) return true
            if (!page.nextCursor.strictlyAfter(requestCursor)) return failPullCycle()
            val records = page.items.map { document -> document.toPullRecord(ownerId, clock.now()) }
            persistence.applyPullPage(ownerId, entityType, records, page.nextCursor).forEach(onQuarantined)
            hasMore = page.hasMore
            requestCursor = page.nextCursor
        }
        return true
    }

    private fun failPullCycle(): Boolean {
        cycleFailure = true
        return false
    }

    private suspend fun refreshStatus(running: Boolean) {
        val counts = persistence.counts()
        mutableStatus.value =
            when {
                unexpectedFailure -> {
                    SyncStatus.Failed(retryableCount = 1, poisonedCount = 0)
                }

                adoptionFailure -> {
                    SyncStatus.Failed(retryableCount = 1, poisonedCount = 0)
                }

                cycleFailure -> {
                    SyncStatus.Failed(retryableCount = 1, poisonedCount = 0)
                }

                counts.retryable > 0 || counts.poisoned > 0 -> {
                    SyncStatus.Failed(counts.retryable, counts.poisoned)
                }

                running -> {
                    SyncStatus.Syncing
                }

                counts.pending > 0 -> {
                    SyncStatus.Pending(counts.pending)
                }

                else -> {
                    SyncStatus.Idle
                }
            }
    }

    private fun scheduleAdoptionRetry() {
        if (adoptionRetryScheduled) return
        adoptionRetryScheduled = true
        val delayMillis = retryDelayMillis(adoptionAttemptCount, jitter)
        scope.launch {
            delay(delayMillis)
            adoptionRetryScheduled = false
            requestSync(SyncTrigger.Periodic)
        }
    }
}

internal fun retryDelayMillis(
    attemptCount: Int,
    jitter: JitterSource,
): Long {
    val exponent = minOf(attemptCount, MAX_BACKOFF_EXPONENT)
    val base = minOf(MAX_BACKOFF_MS, BACKOFF_SCALE shl exponent)
    return (
        base * (MIN_JITTER_SCALE + jitter.nextInt(0, JITTER_RANGE)) / BACKOFF_SCALE
    ).coerceIn(MIN_BACKOFF_MS, MAX_BACKOFF_MS)
}

private fun List<OutboxRecord>.stableDependencyOrder(): List<OutboxRecord> =
    sortedWith(compareBy<OutboxRecord>({ it.dependencyGroup() }, { it.sequence }))

private fun OutboxRecord.dependencyGroup(): Int =
    when {
        entityType == EntityType.VEHICLE && !deleted -> VEHICLE_UPSERT_GROUP
        entityType == EntityType.FUEL_ENTRY && !deleted -> FUEL_ENTRY_UPSERT_GROUP
        entityType == EntityType.FUEL_ENTRY -> FUEL_ENTRY_TOMBSTONE_GROUP
        else -> VEHICLE_TOMBSTONE_GROUP
    }

private fun RemoteCursor.strictlyAfter(other: RemoteCursor): Boolean {
    val timestampComparison = lastServerUpdatedAt.compareTo(other.lastServerUpdatedAt)
    if (timestampComparison != 0) return timestampComparison > 0
    val id = lastDocumentId?.value ?: return false
    val otherId = other.lastDocumentId?.value ?: return true
    return id > otherId
}

private fun RemoteDocument.toPullRecord(
    expectedOwner: OwnerId,
    createdAt: Instant,
): PullRecord {
    val objectValue = rawObjectOrNull()
    val schemaVersion = objectValue?.int("schemaVersion")
    if (schemaVersion != null && schemaVersion > CLIENT_MAX_SCHEMA_VERSION) {
        return quarantined(QuarantineReason.UnsupportedSchemaVersion, schemaVersion, createdAt)
    }
    return try {
        requireNotNull(objectValue)
        require(schemaVersion == CLIENT_MAX_SCHEMA_VERSION)
        require(objectValue.string("id") == documentId.value)
        require(objectValue.string("ownerId") == expectedOwner.value)
        require(objectValue.long("updatedAt") == serverUpdatedAt.toEpochMilliseconds())
        val deleted = objectValue.boolean("deleted")
        val deletedAt = objectValue.nullableLong("deletedAt")
        require(deleted == (deletedAt != null))
        when (entityType) {
            EntityType.VEHICLE -> objectValue.toVehicle(this, expectedOwner, schemaVersion, deletedAt)
            EntityType.FUEL_ENTRY -> objectValue.toFuelEntry(this, expectedOwner, schemaVersion, deletedAt)
        }
    } catch (_: IllegalArgumentException) {
        quarantined(QuarantineReason.MalformedPayload, schemaVersion ?: UNKNOWN_SCHEMA_VERSION, createdAt)
    } catch (_: IllegalStateException) {
        quarantined(QuarantineReason.MalformedPayload, schemaVersion ?: UNKNOWN_SCHEMA_VERSION, createdAt)
    }
}

private fun RemoteDocument.rawObjectOrNull(): JsonObject? =
    try {
        Json.parseToJsonElement(rawJson).jsonObject
    } catch (_: IllegalArgumentException) {
        null
    }

private fun RemoteDocument.quarantined(
    reason: QuarantineReason,
    schemaVersion: Int,
    createdAt: Instant,
): PullRecord.Quarantined =
    PullRecord.Quarantined(
        QuarantineRecord(entityType, documentId, reason, schemaVersion, serverUpdatedAt, rawJson, createdAt),
    )

private fun JsonObject.toVehicle(
    document: RemoteDocument,
    ownerId: OwnerId,
    schemaVersion: Int,
    deletedAt: Long?,
): PullRecord.Vehicle {
    require(keys == VEHICLE_KEYS)
    val name = string("name")
    require(name.length in 1..MAX_VEHICLE_TEXT_LENGTH)
    val initialOdometerKm = long("initialOdometerKm")
    require(initialOdometerKm in 0..MAX_ODOMETER_KM)
    val brand = nullableString("brand")
    val model = nullableString("model")
    require(brand == null || brand.length in 1..MAX_VEHICLE_TEXT_LENGTH)
    require(model == null || model.length in 1..MAX_VEHICLE_TEXT_LENGTH)
    val fuelType = string("fuelType")
    require(fuelType in FUEL_TYPES)
    return PullRecord.Vehicle(
        document = document,
        ownerId = ownerId,
        name = name,
        nameFold = name.canonicalName().lowercase(),
        initialOdometerKm = initialOdometerKm,
        brand = brand,
        model = model,
        fuelType = fuelType,
        createdAt = Instant.fromEpochMilliseconds(long("createdAt")),
        updatedAt = Instant.fromEpochMilliseconds(long("updatedAt")),
        deletedAt = deletedAt?.let(Instant::fromEpochMilliseconds),
        schemaVersion = schemaVersion,
    )
}

private fun JsonObject.toFuelEntry(
    document: RemoteDocument,
    ownerId: OwnerId,
    schemaVersion: Int,
    deletedAt: Long?,
): PullRecord.FuelEntry {
    require(keys == FUEL_ENTRY_KEYS)
    val date = long("date")
    require(date >= 0)
    val odometerKm = long("odometerKm")
    val litersScaled = long("litersScaled")
    val pricePerLiterScaled = long("pricePerLiterScaled")
    val totalCostMinor = long("totalCostMinor")
    require(odometerKm in 0..MAX_ODOMETER_KM)
    require(litersScaled in 1..MAX_LITERS_SCALED)
    require(pricePerLiterScaled in 1..MAX_PRICE_PER_LITER_SCALED)
    require(totalCostMinor in 1..MAX_TOTAL_COST_MINOR)
    val currency = string("currency")
    require(currency in SUPPORTED_CURRENCY_CODES)
    val notes = nullableString("notes")
    require(notes == null || notes.length in 1..MAX_NOTES_LENGTH)
    boolean("odometerInconsistent")
    return PullRecord.FuelEntry(
        document = document,
        ownerId = ownerId,
        vehicleId = EntityId(string("vehicleId")),
        date = Instant.fromEpochMilliseconds(date),
        odometerKm = odometerKm,
        litersScaled = litersScaled,
        pricePerLiterScaled = pricePerLiterScaled,
        totalCostMinor = totalCostMinor,
        currency = currency,
        isFullTank = boolean("isFullTank"),
        hasMissedEntries = boolean("hasMissedEntries"),
        notes = notes,
        createdAt = Instant.fromEpochMilliseconds(long("createdAt")),
        updatedAt = Instant.fromEpochMilliseconds(long("updatedAt")),
        deletedAt = deletedAt?.let(Instant::fromEpochMilliseconds),
        schemaVersion = schemaVersion,
    )
}

private fun JsonObject.string(name: String): String =
    getValue(name).jsonPrimitive.contentOrNull ?: throw IllegalArgumentException(name)

private fun JsonObject.long(name: String): Long =
    getValue(name).jsonPrimitive.longOrNull ?: throw IllegalArgumentException(name)

private fun JsonObject.int(name: String): Int? = get(name)?.jsonPrimitive?.intOrNull

private fun JsonObject.boolean(name: String): Boolean =
    getValue(name).jsonPrimitive.booleanOrNull ?: throw IllegalArgumentException(name)

private fun JsonObject.nullableLong(name: String): Long? {
    val value = getValue(name)
    if (value === JsonNull) return null
    return value.jsonPrimitive.longOrNull ?: throw IllegalArgumentException(name)
}

private fun JsonObject.nullableString(name: String): String? {
    val value = getValue(name)
    if (value === JsonNull) return null
    return value.jsonPrimitive.contentOrNull ?: throw IllegalArgumentException(name)
}

private fun String.canonicalName(): String =
    buildString {
        var pendingSpace = false
        for (character in this@canonicalName.trim()) {
            if (character.isWhitespace()) {
                pendingSpace = isNotEmpty()
            } else {
                if (pendingSpace) append(' ')
                append(character)
                pendingSpace = false
            }
        }
    }

private const val PUSH_BATCH_LIMIT = 50
private const val PULL_PAGE_LIMIT = 200
private const val OVERLAP_MS = 30_000L
private const val MIN_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 900_000L
private const val MAX_BACKOFF_EXPONENT = 20
private const val BACKOFF_SCALE = 1_000L
private const val MIN_JITTER_SCALE = 800L
private const val JITTER_RANGE = 401
private const val UNKNOWN_SCHEMA_VERSION = 0
private const val VEHICLE_UPSERT_GROUP = 0
private const val FUEL_ENTRY_UPSERT_GROUP = 1
private const val FUEL_ENTRY_TOMBSTONE_GROUP = 2
private const val VEHICLE_TOMBSTONE_GROUP = 3
private const val MAX_VEHICLE_TEXT_LENGTH = 40
private const val MAX_ODOMETER_KM = 2_000_000L
private const val MAX_LITERS_SCALED = 500_000L
private const val MAX_PRICE_PER_LITER_SCALED = 999_999L
private const val MAX_TOTAL_COST_MINOR = 99_999_999L
private const val MAX_NOTES_LENGTH = 280
private val FUEL_TYPES = setOf("GASOLINE", "DIESEL", "LPG", "CNG", "OTHER")
private val VEHICLE_KEYS =
    setOf(
        "id",
        "ownerId",
        "name",
        "initialOdometerKm",
        "brand",
        "model",
        "fuelType",
        "createdAt",
        "updatedAt",
        "deleted",
        "deletedAt",
        "schemaVersion",
    )
private val FUEL_ENTRY_KEYS =
    setOf(
        "id",
        "ownerId",
        "vehicleId",
        "date",
        "odometerKm",
        "litersScaled",
        "pricePerLiterScaled",
        "totalCostMinor",
        "currency",
        "isFullTank",
        "hasMissedEntries",
        "odometerInconsistent",
        "notes",
        "createdAt",
        "updatedAt",
        "deleted",
        "deletedAt",
        "schemaVersion",
    )
