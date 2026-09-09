package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.auth.OrphanCleanupClient
import com.ruizurraca.carapp.core.auth.OrphanCleanupTicket
import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.database.AccountConversionOperation
import com.ruizurraca.carapp.core.database.AccountConversionPhase
import com.ruizurraca.carapp.core.database.AccountConversionSnapshotRow
import com.ruizurraca.carapp.core.database.AccountConversionStore
import com.ruizurraca.carapp.core.database.FuelEntryDatabaseRow
import com.ruizurraca.carapp.core.database.VehicleDatabaseRow
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemoteSnapshot
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import com.ruizurraca.carapp.feature.fuel.data.toAdoptionOutboxPayload
import com.ruizurraca.carapp.feature.vehicle.data.toAdoptionOutboxPayload
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Internal orchestration seam for the confirmed credential-collision flow. */
internal fun interface AccountConversionHandler {
    suspend fun confirm(
        anonymousUid: String,
        credential: NativeAuthCredential,
    ): Outcome<AuthSession, AppError>
}

internal enum class AccountConversionCheckpoint {
    SNAPSHOT_CAPTURED,
    CLEANUP_TICKET_SAVED,
    PERMANENT_SESSION_STARTED,
    REMOTE_REPLACED,
    LOCAL_REPLACED,
    ORPHAN_CLEANED,
}

/** Durable, replay-safe implementation of the D-61 destructive account replacement protocol. */
internal class AccountConversionCoordinator(
    private val authClient: AuthClient,
    private val orphanCleanupClient: OrphanCleanupClient,
    private val remoteSyncSource: RemoteSyncSource,
    private val store: AccountConversionStore,
    private val clock: AppClock,
    private val afterCheckpoint: suspend (AccountConversionCheckpoint) -> Unit = {},
) : AccountConversionHandler {
    private val mutex = Mutex()

    override suspend fun confirm(
        anonymousUid: String,
        credential: NativeAuthCredential,
    ): Outcome<AuthSession, AppError> =
        mutex.withLock {
            val session = currentSession()
            if (session == null || !session.isAnonymous || session.uid != anonymousUid) {
                return@withLock Outcome.Err(AuthError.UidWouldChange)
            }
            var operation =
                store.captureIfAbsent(
                    anonymousUid = anonymousUid,
                    vehiclePayload = VehicleDatabaseRow::toAdoptionOutboxPayload,
                    fuelEntryPayload = FuelEntryDatabaseRow::toAdoptionOutboxPayload,
                )
            if (operation.anonymousUid != anonymousUid) {
                return@withLock Outcome.Err(AuthError.UidWouldChange)
            }
            afterCheckpoint(AccountConversionCheckpoint.SNAPSHOT_CAPTURED)

            if (operation.cleanupTicket == null) {
                when (val ticket = orphanCleanupClient.issueOrphanCleanupTicket()) {
                    is Outcome.Err -> return@withLock Outcome.Err(ticket.error)
                    is Outcome.Ok -> store.saveCleanupTicket(ticket.value.value)
                }
                operation = checkNotNull(store.load())
            }
            afterCheckpoint(AccountConversionCheckpoint.CLEANUP_TICKET_SAVED)

            val permanentSession =
                when (val result = authClient.signInWithCredential(credential, allowUidChange = true)) {
                    is Outcome.Err -> return@withLock Outcome.Err(result.error)
                    is Outcome.Ok -> result.value
                }
            if (permanentSession.isAnonymous || permanentSession.uid == anonymousUid) {
                return@withLock Outcome.Err(AuthError.UidWouldChange)
            }
            store.savePermanentUid(permanentSession.uid)
            afterCheckpoint(AccountConversionCheckpoint.PERMANENT_SESSION_STARTED)

            when (val resumed = continueOperation(checkNotNull(store.load()), permanentSession)) {
                is Outcome.Err -> Outcome.Err(resumed.error)
                is Outcome.Ok -> Outcome.Ok(permanentSession)
            }
        }

    suspend fun resumePending(): Outcome<Unit, AppError> =
        mutex.withLock {
            val operation = store.load() ?: return@withLock Outcome.Ok(Unit)
            val session = currentSession() ?: return@withLock Outcome.Err(AuthError.NoAccountAvailable)
            if (session.isAnonymous) return@withLock Outcome.Err(AuthError.ProviderUnavailable)
            if (operation.permanentUid != null && operation.permanentUid != session.uid) {
                return@withLock Outcome.Err(AuthError.UidWouldChange)
            }
            if (operation.permanentUid == null) store.savePermanentUid(session.uid)
            continueOperation(checkNotNull(store.load()), session)
        }

    suspend fun awaitSettled(): Outcome<Unit, AppError> =
        if (store.load() == null) Outcome.Ok(Unit) else resumePending()

    private suspend fun continueOperation(
        operation: AccountConversionOperation,
        permanentSession: AuthSession,
    ): Outcome<Unit, AppError> {
        var current = operation
        if (current.phase == AccountConversionPhase.SESSION_SWITCHED) {
            when (val replaced = replaceRemote(current, permanentSession.uid)) {
                is Outcome.Err -> return replaced
                is Outcome.Ok -> Unit
            }
            store.markRemoteReplaced()
            current = checkNotNull(store.load())
            afterCheckpoint(AccountConversionCheckpoint.REMOTE_REPLACED)
        }
        if (current.phase == AccountConversionPhase.REMOTE_REPLACED) {
            store.replaceLocalSnapshot(
                permanentUid = permanentSession.uid,
                vehicles =
                    current.snapshots.filterType(EntityType.VEHICLE).map { it.toVehicleRow(permanentSession.uid) },
                fuelEntries =
                    current.snapshots.filterType(EntityType.FUEL_ENTRY).map { it.toFuelEntryRow(permanentSession.uid) },
            )
            current = checkNotNull(store.load())
            afterCheckpoint(AccountConversionCheckpoint.LOCAL_REPLACED)
        }
        if (current.phase == AccountConversionPhase.LOCAL_REPLACED) {
            val rawTicket = current.cleanupTicket ?: return Outcome.Err(AuthError.Unknown)
            when (val deleted = orphanCleanupClient.deleteOrphanedAnonymousAccount(OrphanCleanupTicket(rawTicket))) {
                is Outcome.Err -> return Outcome.Err(deleted.error)
                is Outcome.Ok -> Unit
            }
            afterCheckpoint(AccountConversionCheckpoint.ORPHAN_CLEANED)
            store.clear()
        }
        return Outcome.Ok(Unit)
    }

    private suspend fun replaceRemote(
        operation: AccountConversionOperation,
        permanentUid: String,
    ): Outcome<Unit, AppError> {
        val remoteVehicles = pullAll(permanentUid, EntityType.VEHICLE)
        if (remoteVehicles is Outcome.Err) return remoteVehicles
        val remoteFuelEntries = pullAll(permanentUid, EntityType.FUEL_ENTRY)
        if (remoteFuelEntries is Outcome.Err) return remoteFuelEntries

        val capturedIds = operation.snapshots.mapTo(mutableSetOf()) { it.entityType to it.entityId }
        val timestamp = clock.now().toEpochMilliseconds()
        val captured = operation.snapshots.map { row -> row.toEntitySnapshot(permanentUid) }
        val stale =
            ((remoteVehicles as Outcome.Ok).value + (remoteFuelEntries as Outcome.Ok).value)
                .filterNot { it.deleted || (it.entityType.name to it.entityId.value) in capturedIds }
                .map { it.toTombstone(permanentUid, timestamp) }
        val pushes =
            captured.filter { it.entityType == EntityType.VEHICLE && !it.json.deleted() } +
                captured.filter { it.entityType == EntityType.FUEL_ENTRY && !it.json.deleted() } +
                captured.filter { it.entityType == EntityType.FUEL_ENTRY && it.json.deleted() } +
                stale.filter { it.entityType == EntityType.FUEL_ENTRY } +
                captured.filter { it.entityType == EntityType.VEHICLE && it.json.deleted() } +
                stale.filter { it.entityType == EntityType.VEHICLE }

        for (snapshot in pushes) {
            when (val pushed = remoteSyncSource.pushSnapshot(OwnerId(permanentUid), snapshot)) {
                is Outcome.Err -> {
                    return Outcome.Err(pushed.error)
                }

                is Outcome.Ok -> {
                    if ((snapshot.entityType.name to snapshot.entityId.value) in capturedIds) {
                        store.saveRemoteAck(
                            snapshot.entityType.name,
                            snapshot.entityId.value,
                            pushed.value.serverUpdatedAt.toEpochMilliseconds(),
                        )
                    }
                }
            }
        }
        return Outcome.Ok(Unit)
    }

    private suspend fun pullAll(
        ownerId: String,
        entityType: EntityType,
    ): Outcome<List<RemoteSnapshot>, AppError> {
        val snapshots = mutableListOf<RemoteSnapshot>()
        var cursor = RemoteCursor.INITIAL
        do {
            when (val page = remoteSyncSource.pullChanges(OwnerId(ownerId), entityType, cursor, REMOTE_PAGE_SIZE)) {
                is Outcome.Err -> {
                    return Outcome.Err(page.error)
                }

                is Outcome.Ok -> {
                    snapshots += page.value.items
                    cursor = page.value.nextCursor
                    if (!page.value.hasMore) return Outcome.Ok(snapshots)
                }
            }
        } while (true)
    }

    private fun currentSession(): AuthSession? = (authClient.authState.value as? AuthState.SignedIn)?.session

    private companion object {
        const val REMOTE_PAGE_SIZE = 200
    }
}

private fun List<AccountConversionSnapshotRow>.filterType(entityType: EntityType) =
    filter { it.entityType == entityType.name }

private fun AccountConversionSnapshotRow.toEntitySnapshot(permanentUid: String): EntitySnapshot {
    val objectValue = payload.asJsonObject().withPermanentOwner(permanentUid)
    return EntitySnapshot(
        entityType = EntityType.valueOf(entityType),
        entityId = EntityId(entityId),
        schemaVersion = objectValue.requiredLong("schemaVersion").toInt(),
        json = objectValue.toString(),
    )
}

private fun RemoteSnapshot.toTombstone(
    permanentUid: String,
    timestamp: Long,
): EntitySnapshot {
    val values = json.asJsonObject().toMutableMap()
    values["entityType"] = JsonPrimitive(entityType.name)
    values["ownerId"] = JsonPrimitive(permanentUid)
    values["updatedAt"] = JsonPrimitive(timestamp)
    values["deleted"] = JsonPrimitive(true)
    values["deletedAt"] = JsonPrimitive(timestamp)
    return EntitySnapshot(entityType, entityId, schemaVersion, JsonObject(values).toString())
}

private fun AccountConversionSnapshotRow.toVehicleRow(permanentUid: String): VehicleDatabaseRow {
    val value = payload.asJsonObject().withPermanentOwner(permanentUid)
    return VehicleDatabaseRow(
        id = entityId,
        ownerId = permanentUid,
        name = value.requiredString("name"),
        nameFold = value.requiredString("name").lowercase(),
        initialOdometerKm = value.requiredLong("initialOdometerKm"),
        currentOdometerKm = value.requiredLong("initialOdometerKm"),
        brand = value.optionalString("brand"),
        model = value.optionalString("model"),
        fuelType = value.requiredString("fuelType"),
        createdAt = value.requiredLong("createdAt"),
        updatedAt = value.requiredLong("updatedAt"),
        serverUpdatedAt = remoteServerUpdatedAt,
        deletedAt = value.optionalLong("deletedAt"),
        syncState = "SYNCED",
        localRevision = localRevision,
        localMutationSeq = localMutationSeq,
        schemaVersion = value.requiredLong("schemaVersion"),
    )
}

private fun AccountConversionSnapshotRow.toFuelEntryRow(permanentUid: String): FuelEntryDatabaseRow {
    val value = payload.asJsonObject().withPermanentOwner(permanentUid)
    return FuelEntryDatabaseRow(
        id = entityId,
        ownerId = permanentUid,
        vehicleId = value.requiredString("vehicleId"),
        date = value.requiredLong("date"),
        odometerKm = value.requiredLong("odometerKm"),
        litersScaled = value.requiredLong("litersScaled"),
        pricePerLiterScaled = value.requiredLong("pricePerLiterScaled"),
        totalCostMinor = value.requiredLong("totalCostMinor"),
        currency = value.requiredString("currency"),
        isFullTank = value.requiredBoolean("isFullTank"),
        hasMissedEntries = value.requiredBoolean("hasMissedEntries"),
        odometerInconsistent = value.requiredBoolean("odometerInconsistent"),
        notes = value.optionalString("notes"),
        createdAt = value.requiredLong("createdAt"),
        updatedAt = value.requiredLong("updatedAt"),
        serverUpdatedAt = remoteServerUpdatedAt,
        deletedAt = value.optionalLong("deletedAt"),
        syncState = "SYNCED",
        localRevision = localRevision,
        localMutationSeq = localMutationSeq,
        schemaVersion = value.requiredLong("schemaVersion"),
    )
}

private fun String.asJsonObject(): JsonObject = Json.parseToJsonElement(this) as JsonObject

private fun JsonObject.withPermanentOwner(permanentUid: String): JsonObject =
    JsonObject(toMutableMap().apply { this["ownerId"] = JsonPrimitive(permanentUid) })

private fun String.deleted(): Boolean = asJsonObject().requiredBoolean("deleted")

private fun JsonObject.requiredString(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.requiredLong(key: String): Long = getValue(key).jsonPrimitive.long

private fun JsonObject.requiredBoolean(key: String): Boolean = getValue(key).jsonPrimitive.boolean

private fun JsonObject.optionalString(key: String): String? =
    get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

private fun JsonObject.optionalLong(key: String): Long? = get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.long
