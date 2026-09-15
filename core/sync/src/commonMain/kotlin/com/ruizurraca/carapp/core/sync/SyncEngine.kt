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
import com.ruizurraca.carapp.core.model.canonicalVehicleName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.serialization.json.JsonPrimitive
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

/** A reservation of a cycle: either the caller runs it, or it joins the pending follow-up. */
private data class TriggerRegistration(
    val completion: CompletableDeferred<Outcome<Unit, AppError>>,
    val reasons: Set<SyncTrigger>,
    val startsCycle: Boolean,
)

/**
 * The single pending follow-up cycle a coalesced trigger joins. The joined reasons and the completion
 * handle are held together so the follow-up cannot be flagged without a handle to complete (`§9.1`).
 */
private class PendingFollowUp {
    val reasons = mutableSetOf<SyncTrigger>()
    val completion = CompletableDeferred<Outcome<Unit, AppError>>()
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

    // Monotonic counter incremented each time a new active cycle is reserved under `cycleMutex`. A
    // finishing cycle captures it and passes the value to its terminal status publish, so a publish
    // that lands after a newer cycle started can tell that publishing would clobber `Syncing` and
    // leaves the newer cycle's status alone (`§9.9`).
    private var cycleGeneration = 0L

    // The single pending follow-up. Both the joined reasons and the completion handle live in one
    // value, so the pending flag and its handle cannot be observed separately: there is no
    // inconsistent state in which a follow-up is flagged but no handle exists to complete, which
    // would wedge `drainCycles` and suspend every later `sync()` forever (`§9.1`).
    private var pendingFollowUp: PendingFollowUp? = null

    private var unexpectedFailure = false
    private var cycleFailure = false
    private var lastCycleError: AppError? = null
    private var adoptionFailure = false
    private var adoptionAttemptCount = 0
    private var adoptionRetryScheduled = false

    override fun requestSync(reason: SyncTrigger) {
        scope.launch {
            val registration = registerTrigger(reason)
            if (registration.startsCycle) drainCycles(registration.reasons, registration.completion)
        }
    }

    override suspend fun sync(reason: SyncTrigger): Outcome<Unit, AppError> {
        val registration = registerTrigger(reason)
        if (registration.startsCycle) scope.launch { drainCycles(registration.reasons, registration.completion) }
        return registration.completion.await()
    }

    /**
     * Reserves either a new active cycle or a join on the single pending follow-up. Only the caller
     * that receives `startsCycle = true` runs [drainCycles]; every other concurrent trigger joins the
     * follow-up completion, so multiple triggers still produce exactly one active and one pending
     * cycle (`§9.1`). Each trigger also contributes its own reason to the [PendingFollowUp], so the
     * follow-up cycle runs every reason-dependent step a joined trigger requires.
     */
    private suspend fun registerTrigger(reason: SyncTrigger): TriggerRegistration =
        cycleMutex.withLock {
            if (cycleRunning) {
                val followUp =
                    pendingFollowUp ?: PendingFollowUp().also { pendingFollowUp = it }
                followUp.reasons += reason
                TriggerRegistration(followUp.completion, reasons = setOf(reason), startsCycle = false)
            } else {
                cycleRunning = true
                cycleGeneration += 1
                TriggerRegistration(CompletableDeferred(), reasons = setOf(reason), startsCycle = true)
            }
        }

    override suspend fun retryFailed(): Outcome<Unit, AppError> {
        val result = persistence.resetFailed(clock.now())
        // While a cycle is active it owns the published status (`§9.9` `Failed > Syncing > Pending >
        // Idle`); a manual reset must not replace `Syncing` mid-cycle. The cycle publishes the
        // aggregate when it finishes.
        if (!isCycleRunning()) refreshStatus()
        if (result is Outcome.Ok) requestSync(SyncTrigger.PullToRefresh)
        return result
    }

    private suspend fun isCycleRunning(): Boolean = cycleMutex.withLock { cycleRunning }

    override suspend fun debugLines(): List<String> = if (debugEnabled) debugLoader() else emptyList()

    private suspend fun drainCycles(
        firstReasons: Set<SyncTrigger>,
        firstCompletion: CompletableDeferred<Outcome<Unit, AppError>>,
    ) {
        var reasons = firstReasons
        var completion = firstCompletion
        while (true) {
            val outcome = runCycle(reasons)
            completion.complete(outcome)
            // Capture the generation under the same lock that clears the active-cycle reservation.
            // The terminal publish then skips if a newer cycle started in the window between releasing
            // the lock and publishing, so it cannot overwrite that cycle's `Syncing` (`§9.9`).
            val (nextFollowUp, finishingGeneration) =
                cycleMutex.withLock {
                    val pending = pendingFollowUp
                    if (pending != null) {
                        pendingFollowUp = null
                        pending to null
                    } else {
                        cycleRunning = false
                        null to cycleGeneration
                    }
                }
            if (nextFollowUp == null) {
                refreshStatus(expectedGeneration = finishingGeneration)
                return
            }
            completion = nextFollowUp.completion
            reasons = nextFollowUp.reasons.toSet()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runCycle(reasons: Set<SyncTrigger>): Outcome<Unit, AppError> {
        // Allocate correlation before any cycle step. Push batches, pull pages, fail-closed progress
        // reports and the unexpected-failure boundary all retain this one identifier (`§17`).
        val cycleId = CycleId(uuidGenerator.newId())
        return try {
            val result = executeCycle(reasons, cycleId)
            unexpectedFailure = false
            result
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            unexpectedFailure = true
            val error = UnexpectedError(":core:sync", failure::class.simpleName ?: "Throwable")
            onPoisoned(error, mapOf("cycleId" to cycleId.value))
            Outcome.Err(error)
        }
    }

    private suspend fun executeCycle(
        reasons: Set<SyncTrigger>,
        cycleId: CycleId,
    ): Outcome<Unit, AppError> {
        cycleFailure = false
        lastCycleError = null
        // A refused cycle is a successful outcome with no error: offline or `LOCAL_OWNER` is not a
        // failure the caller must surface (`§9.1`, `§9.2`).
        if (!connectivity.isOnline.value) {
            refreshStatus()
            return Outcome.Ok(Unit)
        }
        val ownerId = ownerContext.current
        if (ownerId == LOCAL_OWNER) {
            refreshStatus()
            return Outcome.Ok(Unit)
        }
        mutableStatus.value = SyncStatus.Syncing
        // Run every reason-dependent step any trigger joined into this cycle requires. A coalesced
        // ConnectivityRecovered still makes connectivity-only failures due (`§9.7`, `§9.8`).
        if (SyncTrigger.ConnectivityRecovered in reasons) persistence.markConnectivityFailuresDue(clock.now())
        val adoptionResult = adoption()
        if (adoptionResult is Outcome.Err) {
            adoptionFailure = true
            adoptionAttemptCount = minOf(adoptionAttemptCount + 1, MAX_RETRYABLE_ATTEMPTS)
            scheduleAdoptionRetry()
            refreshStatus()
            return adoptionResult
        }
        adoptionFailure = false
        adoptionAttemptCount = 0
        val pullFirst = persistence.isOwnerDatabaseEmpty(ownerId)
        if (pullFirst) {
            pull(ownerId, cycleId)
            push(ownerId, cycleId)
        } else {
            push(ownerId, cycleId)
            pull(ownerId, cycleId)
        }
        return if (cycleFailure) Outcome.Err(lastCycleError ?: SyncError.ConflictUnresolved) else Outcome.Ok(Unit)
    }

    private suspend fun push(
        ownerId: OwnerId,
        cycleId: CycleId,
    ) {
        // Drain every due batch in one cycle: a full batch means more work is waiting, so the cycle
        // continues instead of leaving the rest for the next external trigger (`§9.3`, P2).
        //
        // Termination is explicit, not incidental. A successful push deletes the outbox row and a
        // failed push reschedules it behind the backoff, but with a real clock a slow cycle can
        // outlast that backoff and make a failed row due again immediately. Every entity attempted in
        // this push is therefore recorded, and the continuation stops once a full batch contains no
        // entity that was not already attempted — that is the point at which retrying would spin.
        // Each row is attempted at most once per cycle, and the pull step still runs.
        val attempted = mutableSetOf<Pair<EntityType, EntityId>>()
        while (true) {
            val rows = persistence.dueOutbox(clock.now(), PUSH_BATCH_LIMIT).stableDependencyOrder()
            val unprocessed = rows.filterNot { (it.entityType to it.entityId) in attempted }
            if (unprocessed.isEmpty()) return
            for (row in unprocessed) {
                attempted += row.entityType to row.entityId
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
            if (rows.size < PUSH_BATCH_LIMIT) return
        }
    }

    private suspend fun handlePushFailure(
        row: OutboxRecord,
        error: RemoteError,
        cycleId: CycleId,
    ) {
        // `NotFound` on push is treated as success (`§6`): the remote copy is already absent, which is
        // the state this push was trying to reach. `serverUpdatedAt` is cleared to NULL so the entity
        // reads as never-synced to the `§9.6` LWW comparison, and the next pull is therefore allowed
        // to overwrite local data for it. That is intended for a non-tombstone push: there is no
        // remote copy to lose, and a later remote write must be able to win.
        if (error == RemoteError.NotFound) {
            persistence.confirmPush(row, null)
            return
        }
        // `§6` is normative: `Unauthenticated` retries "after a valid auth session, `attemptCount`
        // unchanged". It therefore MUST NOT consume the retry budget — the row keeps its count so the
        // backoff exponent is unchanged — and it MUST NOT poison at any count, exactly as a
        // connectivity code never poisons. It is a non-connectivity retryable failure, so the row is
        // `FAILED_RETRYABLE` (`§7`) and the aggregate reports it as `Failed` (`§9.9`). `Unknown`
        // still increments and still poisons at the ceiling.
        val incrementsAttempt = error != RemoteError.Unauthenticated
        val attemptCount =
            if (incrementsAttempt) minOf(row.attemptCount + 1, MAX_RETRYABLE_ATTEMPTS) else row.attemptCount
        // `NotFound` is excluded by the early return above. Permission and validation failures poison
        // immediately; `Unknown` poisons at the ceiling; connectivity failures and `Unauthenticated`
        // never poison (`§6`, `§9.7`).
        val poisoned =
            when (error) {
                RemoteError.PermissionDenied, RemoteError.InvalidArgument -> true
                RemoteError.Unknown -> attemptCount >= MAX_RETRYABLE_ATTEMPTS
                else -> false
            }
        if (poisoned) {
            // `Unauthenticated` never reaches this branch (`§6`: it never poisons), so it is absent
            // from the mapping rather than kept as a dead arm.
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

    private suspend fun pull(
        ownerId: OwnerId,
        cycleId: CycleId,
    ) {
        for (entityType in EntityType.entries) {
            if (!pullEntity(ownerId, entityType, cycleId)) return
        }
    }

    private suspend fun pullEntity(
        ownerId: OwnerId,
        entityType: EntityType,
        cycleId: CycleId,
    ): Boolean {
        val stored = persistence.cursor(entityType)
        val overlapSince = maxOf(0L, stored.lastServerUpdatedAt.toEpochMilliseconds() - OVERLAP_MS)
        var requestCursor = RemoteCursor(Instant.fromEpochMilliseconds(overlapSince), null)
        var hasMore = true
        while (hasMore) {
            val result = remote.pullChanges(ownerId, entityType, requestCursor, PULL_PAGE_LIMIT)
            if (result is Outcome.Err) return failPullCycle(result.error)
            val page = (result as Outcome.Ok).value
            if (page.items.isEmpty()) return true
            if (!page.nextCursor.strictlyAfter(requestCursor)) {
                return failProgressInvariant(entityType, cycleId)
            }
            val records = page.items.map { document -> document.toPullRecord(ownerId, clock.now()) }
            persistence.applyPullPage(ownerId, entityType, records, page.nextCursor).forEach(onQuarantined)
            hasMore = page.hasMore
            requestCursor = page.nextCursor
        }
        return true
    }

    private fun failPullCycle(error: AppError): Boolean {
        cycleFailure = true
        lastCycleError = error
        return false
    }

    /**
     * The `§9.4` progress invariant failed: a non-empty page did not advance the cursor. This is a
     * stranding condition, not a connectivity failure, so it is reported through the established
     * `onPoisoned` path (`§17` recordNonFatal policy) with the stable code `SYNC.CONFLICT_UNRESOLVED`,
     * and it fails the cycle closed rather than looping. `D-169` / ADR-0170 accept this as the
     * behaviour for an oversized same-millisecond cluster.
     */
    private fun failProgressInvariant(
        entityType: EntityType,
        cycleId: CycleId,
    ): Boolean {
        cycleFailure = true
        lastCycleError = SyncError.ConflictUnresolved
        // Fields follow the `§17` allowlist: enum names and stable codes only, never payload data.
        onPoisoned(
            SyncError.ConflictUnresolved,
            mapOf(
                "entityType" to entityType.name,
                "code" to SyncError.ConflictUnresolved.code,
                "cycleId" to cycleId.value,
            ),
        )
        return false
    }

    /**
     * Publishes the aggregate status with the `§9.9` precedence `Failed > Pending > Idle`. `Syncing`
     * is assigned directly by [runCycle], so it is not a branch here: a status refresh only observes
     * the state left after a cycle step, never a running cycle.
     *
     * [expectedGeneration] is the generation of the cycle that is publishing. When a newer cycle has
     * started since, this publish would replace that cycle's `Syncing` with a stale aggregate, so it
     * is skipped; the newer cycle publishes when it finishes. A `null` value publishes unconditionally,
     * for callers that are not a finishing cycle (for example `retryFailed()`).
     */
    private suspend fun refreshStatus(expectedGeneration: Long? = null) {
        val counts = persistence.counts()
        val newerCycleStarted =
            expectedGeneration != null && cycleMutex.withLock { cycleGeneration != expectedGeneration }
        if (newerCycleStarted) return
        mutableStatus.value =
            when {
                // A failure condition with zero outbox rows must still be representable, so each
                // synthetic dimension takes the maximum of the real count and 1; a real count is
                // never reduced. `unexpectedFailure` and `adoptionFailure` are cycle-level failures
                // that use the real `persistence.counts()` values (R4); `cycleFailure` narrows that
                // further for a connectivity-class error, which `§9.9` forbids rendering as `Failed`.
                unexpectedFailure -> {
                    SyncStatus.Failed(counts.retryableOrAtLeastOne(), counts.poisoned)
                }

                adoptionFailure -> {
                    SyncStatus.Failed(counts.retryableOrAtLeastOne(), counts.poisoned)
                }

                cycleFailure -> {
                    // `§9.9` is a rule about aggregation, not only about admission: a cycle that
                    // failed because the transport dropped mid-cycle is not an error the user must
                    // see. A connectivity-class failure therefore MUST NOT force `Failed` and MUST
                    // fall through to the row-derived buckets below, exactly as the per-row rule in
                    // `SyncDatabaseAccess.failPush` already does for the push path. A non-connectivity
                    // failure — including the progress-invariant `ConflictUnresolved` — keeps the
                    // synthetic count that makes the failure representable with an empty outbox.
                    if (lastCycleError?.code in CONNECTIVITY_ERROR_CODES) {
                        rowDerived(counts)
                    } else {
                        SyncStatus.Failed(counts.retryableOrAtLeastOne(), counts.poisoned)
                    }
                }

                else -> {
                    rowDerived(counts)
                }
            }
    }

    /** The `§9.9` aggregate over the real outbox rows: `Failed > Pending > Idle`. */
    private fun rowDerived(counts: SyncCounts): SyncStatus =
        when {
            counts.retryable > 0 || counts.poisoned > 0 -> SyncStatus.Failed(counts.retryable, counts.poisoned)

            counts.pending > 0 -> SyncStatus.Pending(counts.pending)

            else -> SyncStatus.Idle
        }

    private fun SyncCounts.retryableOrAtLeastOne(): Int = maxOf(retryable, 1)

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
    // A cursor document id is mandatory regardless of the timestamp comparison: a source that
    // returns a later timestamp with a null id cannot advance the `§9.4` progress invariant and MUST
    // fail the cycle closed, not reach `applyPullPage` and throw there.
    val id = lastDocumentId?.value ?: return false
    val timestampComparison = lastServerUpdatedAt.compareTo(other.lastServerUpdatedAt)
    if (timestampComparison != 0) return timestampComparison > 0
    val otherId = other.lastDocumentId?.value ?: return true
    return id > otherId
}

private fun RemoteDocument.toPullRecord(
    expectedOwner: OwnerId,
    createdAt: Instant,
): PullRecord {
    val objectValue = rawObjectOrNull()
    val schemaVersion = objectValue?.schemaVersionOrNull()
    if (schemaVersion != null && schemaVersion > CLIENT_MAX_SCHEMA_VERSION) {
        return quarantined(QuarantineReason.UnsupportedSchemaVersion, schemaVersion, createdAt)
    }
    // The classification MUST be total (`§9.5`, ADR-0171): every field read below is reached through
    // `get(name) + require`, so a missing key raises `IllegalArgumentException` and a wrong-typed
    // value raises `IllegalArgumentException`/`IllegalStateException` — never `NoSuchElementException`
    // escaping to the generic cycle catch. A document that cannot be applied becomes a
    // `MalformedPayload` quarantine record and the cursor still advances.
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

/** A non-throwing read of the diagnostic `schemaVersion`, so a malformed value cannot escape. */
private fun JsonObject.schemaVersionOrNull(): Int? = (get("schemaVersion") as? JsonPrimitive)?.intOrNull

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
        nameFold = canonicalVehicleName(name).lowercase(),
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
    get(name)?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException(name)

private fun JsonObject.long(name: String): Long =
    get(name)?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException(name)

private fun JsonObject.boolean(name: String): Boolean =
    get(name)?.jsonPrimitive?.booleanOrNull ?: throw IllegalArgumentException(name)

private fun JsonObject.nullableLong(name: String): Long? {
    val value = get(name) ?: throw IllegalArgumentException(name)
    if (value === JsonNull) return null
    return value.jsonPrimitive.longOrNull ?: throw IllegalArgumentException(name)
}

private fun JsonObject.nullableString(name: String): String? {
    val value = get(name) ?: throw IllegalArgumentException(name)
    if (value === JsonNull) return null
    return value.jsonPrimitive.contentOrNull ?: throw IllegalArgumentException(name)
}

/** `§9.3` push batch limit; `internal` so tests can size batches against the contract value. */
internal const val PUSH_BATCH_LIMIT = 50

/** `§9.4` pull page limit; `internal` so tests can size pages against the contract value. */
internal const val PULL_PAGE_LIMIT = 200
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
