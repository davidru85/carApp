package com.ruizurraca.carapp.core.database

enum class AccountConversionPhase {
    SNAPSHOT_CAPTURED,
    SESSION_SWITCHED,
    REMOTE_REPLACED,
    LOCAL_REPLACED,
}

data class AccountConversionSnapshotRow(
    val entityType: String,
    val entityId: String,
    val payload: String,
    val localRevision: Long,
    val localMutationSeq: Long,
    val remoteServerUpdatedAt: Long?,
)

data class AccountConversionOperation(
    val anonymousUid: String,
    val permanentUid: String?,
    val cleanupTicket: String?,
    val phase: AccountConversionPhase,
    val snapshots: List<AccountConversionSnapshotRow>,
)

interface AccountConversionStore {
    suspend fun load(): AccountConversionOperation?

    suspend fun captureIfAbsent(
        anonymousUid: String,
        vehiclePayload: (VehicleDatabaseRow) -> String,
        fuelEntryPayload: (FuelEntryDatabaseRow) -> String,
    ): AccountConversionOperation

    suspend fun saveCleanupTicket(ticket: String)

    suspend fun savePermanentUid(permanentUid: String)

    suspend fun saveRemoteAck(
        entityType: String,
        entityId: String,
        serverUpdatedAt: Long,
    )

    suspend fun markRemoteReplaced()

    suspend fun replaceLocalSnapshot(
        permanentUid: String,
        vehicles: List<VehicleDatabaseRow>,
        fuelEntries: List<FuelEntryDatabaseRow>,
    )

    suspend fun clear()
}

/** SQLDelight-owned durable operation store for the D-61 replacement flow. */
class AccountConversionDatabaseAccess(
    private val database: AppDatabase,
) : AccountConversionStore {
    override suspend fun load(): AccountConversionOperation? = null

    override suspend fun captureIfAbsent(
        anonymousUid: String,
        vehiclePayload: (VehicleDatabaseRow) -> String,
        fuelEntryPayload: (FuelEntryDatabaseRow) -> String,
    ): AccountConversionOperation = error("E2-04 RED: durable snapshot capture is not implemented")

    override suspend fun saveCleanupTicket(ticket: String) = Unit

    override suspend fun savePermanentUid(permanentUid: String) = Unit

    override suspend fun saveRemoteAck(
        entityType: String,
        entityId: String,
        serverUpdatedAt: Long,
    ) = Unit

    override suspend fun markRemoteReplaced() = Unit

    override suspend fun replaceLocalSnapshot(
        permanentUid: String,
        vehicles: List<VehicleDatabaseRow>,
        fuelEntries: List<FuelEntryDatabaseRow>,
    ) = Unit

    override suspend fun clear() = Unit
}
