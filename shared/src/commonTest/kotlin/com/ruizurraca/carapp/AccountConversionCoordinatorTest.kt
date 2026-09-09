package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.AuthToken
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.auth.OrphanCleanupClient
import com.ruizurraca.carapp.core.auth.OrphanCleanupTicket
import com.ruizurraca.carapp.core.auth.TokenProvider
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
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
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSnapshot
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class AccountConversionCoordinatorTest {
    @Test
    fun confirmedCollisionReplacesInsteadOfMergingAndDeletesTheOrphanLast() =
        runTest {
            val fixture = ConversionFixture()

            val result = fixture.coordinator().confirm(ANONYMOUS_UID, CREDENTIAL)

            assertEquals(PERMANENT_UID, assertIs<Outcome.Ok<AuthSession>>(result).value.uid)
            assertEquals(
                listOf(
                    "capture",
                    "issue-ticket",
                    "save-ticket",
                    "sign-in:$PERMANENT_UID:true",
                    "save-permanent:$PERMANENT_UID",
                    "pull:VEHICLE",
                    "pull:FUEL_ENTRY",
                    "push:VEHICLE:anonymous-vehicle:false",
                    "push:FUEL_ENTRY:anonymous-entry:false",
                    "push:FUEL_ENTRY:existing-entry:true",
                    "push:VEHICLE:existing-vehicle:true",
                    "mark-remote",
                    "replace-local:$PERMANENT_UID",
                    "delete-orphan:$RAW_TICKET",
                    "clear",
                ),
                fixture.events,
            )
            assertEquals(listOf("anonymous-vehicle"), fixture.store.installedVehicles.map { it.id })
            assertEquals(listOf("anonymous-entry"), fixture.store.installedFuelEntries.map { it.id })
            assertTrue(fixture.store.installedVehicles.none { it.id == "existing-vehicle" })
            assertNull(fixture.store.operation)
        }

    @Test
    fun retryConvergesAcrossEveryPostConfirmationInterruptionBoundary() =
        runTest {
            AccountConversionCheckpoint.entries.forEach { checkpoint ->
                val fixture = ConversionFixture()
                val interrupted = fixture.coordinator(interruptAfter = checkpoint)

                assertFailsWith<CancellationException> {
                    interrupted.confirm(ANONYMOUS_UID, CREDENTIAL)
                }

                val retry = fixture.coordinator()
                val result =
                    if (fixture.auth.authState.value
                            .isAnonymousSession()
                    ) {
                        retry.confirm(ANONYMOUS_UID, CREDENTIAL).mapToUnit()
                    } else {
                        retry.resumePending()
                    }

                assertIs<Outcome.Ok<Unit>>(result, "retry must converge after $checkpoint")
                assertNull(fixture.store.operation, "the durable marker clears only after convergence")
                assertEquals(
                    1,
                    fixture.orphan.deletedTickets
                        .distinct()
                        .size,
                )
                assertEquals(
                    PERMANENT_UID,
                    fixture.store.installedVehicles
                        .single()
                        .ownerId,
                )
                assertEquals(
                    PERMANENT_UID,
                    fixture.store.installedFuelEntries
                        .single()
                        .ownerId,
                )
            }
        }

    @Test
    fun aReplacementMarkerBlocksNormalReadsUntilResumeCompletes() =
        runTest {
            val fixture = ConversionFixture()
            fixture.store.captureIfAbsent(ANONYMOUS_UID, { "" }, { "" })
            fixture.store.saveCleanupTicket(RAW_TICKET)

            val result = fixture.coordinator().awaitSettled()

            assertIs<Outcome.Err<AppError>>(result)
            assertTrue(fixture.store.operation != null)
        }
}

private class ConversionFixture {
    val events = mutableListOf<String>()
    val auth = ConversionAuthClient(events)
    val orphan = RecordingOrphanCleanupClient(events)
    val remote = RecordingReplacementRemoteSource(events)
    val store = RecordingConversionStore(events)

    fun coordinator(interruptAfter: AccountConversionCheckpoint? = null): AccountConversionCoordinator =
        AccountConversionCoordinator(
            authClient = auth,
            orphanCleanupClient = orphan,
            remoteSyncSource = remote,
            store = store,
            clock = { Instant.fromEpochMilliseconds(500) },
            afterCheckpoint = { checkpoint ->
                if (checkpoint == interruptAfter) throw CancellationException("interrupted after $checkpoint")
            },
        )
}

private class RecordingConversionStore(
    private val events: MutableList<String>,
) : AccountConversionStore {
    var operation: AccountConversionOperation? = null
    var installedVehicles: List<VehicleDatabaseRow> = emptyList()
    var installedFuelEntries: List<FuelEntryDatabaseRow> = emptyList()

    override suspend fun load(): AccountConversionOperation? = operation

    override suspend fun captureIfAbsent(
        anonymousUid: String,
        vehiclePayload: (VehicleDatabaseRow) -> String,
        fuelEntryPayload: (FuelEntryDatabaseRow) -> String,
    ): AccountConversionOperation {
        operation?.let { return it }
        events += "capture"
        operation =
            AccountConversionOperation(
                anonymousUid = anonymousUid,
                permanentUid = null,
                cleanupTicket = null,
                phase = AccountConversionPhase.SNAPSHOT_CAPTURED,
                snapshots =
                    listOf(
                        AccountConversionSnapshotRow(
                            "VEHICLE",
                            "anonymous-vehicle",
                            anonymousVehicleJson(),
                            2,
                            3,
                            null,
                        ),
                        AccountConversionSnapshotRow(
                            "FUEL_ENTRY",
                            "anonymous-entry",
                            anonymousFuelEntryJson(),
                            4,
                            5,
                            null,
                        ),
                    ),
            )
        return checkNotNull(operation)
    }

    override suspend fun saveCleanupTicket(ticket: String) {
        events += "save-ticket"
        operation = checkNotNull(operation).copy(cleanupTicket = ticket)
    }

    override suspend fun savePermanentUid(permanentUid: String) {
        events += "save-permanent:$permanentUid"
        operation =
            checkNotNull(operation).copy(
                permanentUid = permanentUid,
                phase = AccountConversionPhase.SESSION_SWITCHED,
            )
    }

    override suspend fun saveRemoteAck(
        entityType: String,
        entityId: String,
        serverUpdatedAt: Long,
    ) {
        operation =
            checkNotNull(operation).copy(
                snapshots =
                    checkNotNull(operation).snapshots.map { row ->
                        if (row.entityType == entityType && row.entityId == entityId) {
                            row.copy(remoteServerUpdatedAt = serverUpdatedAt)
                        } else {
                            row
                        }
                    },
            )
    }

    override suspend fun markRemoteReplaced() {
        events += "mark-remote"
        operation = checkNotNull(operation).copy(phase = AccountConversionPhase.REMOTE_REPLACED)
    }

    override suspend fun replaceLocalSnapshot(
        permanentUid: String,
        vehicles: List<VehicleDatabaseRow>,
        fuelEntries: List<FuelEntryDatabaseRow>,
    ) {
        events += "replace-local:$permanentUid"
        installedVehicles = vehicles
        installedFuelEntries = fuelEntries
        operation = checkNotNull(operation).copy(phase = AccountConversionPhase.LOCAL_REPLACED)
    }

    override suspend fun clear() {
        events += "clear"
        operation = null
    }
}

private class ConversionAuthClient(
    private val events: MutableList<String>,
) : AuthClient,
    TokenProvider {
    private val state = MutableStateFlow<AuthState>(AuthState.SignedIn(anonymousSessionForConversion()))
    override val authState: StateFlow<AuthState> = state

    override suspend fun signInWithCredential(
        credential: NativeAuthCredential,
        allowUidChange: Boolean,
    ): Outcome<AuthSession, AuthError> {
        val session = AuthSession(PERMANENT_UID, false, setOf(AuthProvider.GOOGLE))
        events += "sign-in:${session.uid}:$allowUidChange"
        state.value = AuthState.SignedIn(session)
        return Outcome.Ok(session)
    }

    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> =
        Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun linkCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        Outcome.Err(AuthError.CredentialAlreadyInUse)

    override suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun signOut(): Outcome<Unit, AuthError> = Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun deleteAccount(): Outcome<Unit, AuthError> = Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun getIdToken(forceRefresh: Boolean): Outcome<AuthToken, AuthError> =
        Outcome.Err(AuthError.ProviderUnavailable)
}

private class RecordingOrphanCleanupClient(
    private val events: MutableList<String>,
) : OrphanCleanupClient {
    val deletedTickets = mutableListOf<String>()

    override suspend fun issueOrphanCleanupTicket(): Outcome<OrphanCleanupTicket, AuthError> {
        events += "issue-ticket"
        return Outcome.Ok(OrphanCleanupTicket(RAW_TICKET))
    }

    override suspend fun deleteOrphanedAnonymousAccount(ticket: OrphanCleanupTicket): Outcome<Unit, AuthError> {
        events += "delete-orphan:${ticket.value}"
        deletedTickets += ticket.value
        return Outcome.Ok(Unit)
    }
}

private class RecordingReplacementRemoteSource(
    private val events: MutableList<String>,
) : RemoteSyncSource {
    override suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError> {
        val deleted = snapshot.json.contains("\"deleted\":true")
        events += "push:${snapshot.entityType.name}:${snapshot.entityId.value}:$deleted"
        return Outcome.Ok(RemoteAck(snapshot.entityType, snapshot.entityId, Instant.fromEpochMilliseconds(900)))
    }

    override suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError> {
        events += "pull:${entityType.name}"
        val item =
            when (entityType) {
                EntityType.VEHICLE -> remoteVehicle("existing-vehicle")
                EntityType.FUEL_ENTRY -> remoteFuelEntry("existing-entry")
            }
        return Outcome.Ok(RemotePage(listOf(item), RemoteCursor(item.serverUpdatedAt, item.entityId), false))
    }
}

private fun AuthState.isAnonymousSession(): Boolean = (this as? AuthState.SignedIn)?.session?.isAnonymous == true

private fun Outcome<AuthSession, AppError>.mapToUnit(): Outcome<Unit, AppError> =
    when (this) {
        is Outcome.Ok -> Outcome.Ok(Unit)
        is Outcome.Err -> this
    }

private fun anonymousSessionForConversion() = AuthSession(ANONYMOUS_UID, true, setOf(AuthProvider.ANONYMOUS))

private fun remoteVehicle(id: String) =
    RemoteSnapshot(
        EntityType.VEHICLE,
        EntityId(id),
        1,
        Instant.fromEpochMilliseconds(100),
        false,
        anonymousVehicleJson(id = id, ownerId = PERMANENT_UID, includeEntityType = false),
    )

private fun remoteFuelEntry(id: String) =
    RemoteSnapshot(
        EntityType.FUEL_ENTRY,
        EntityId(id),
        1,
        Instant.fromEpochMilliseconds(100),
        false,
        anonymousFuelEntryJson(id = id, ownerId = PERMANENT_UID, includeEntityType = false),
    )

private fun anonymousVehicleJson(
    id: String = "anonymous-vehicle",
    ownerId: String = ANONYMOUS_UID,
    includeEntityType: Boolean = true,
): String =
    (
        """{"entityType":"VEHICLE","id":"$id","ownerId":"$ownerId","name":"Roadster",""" +
            """"initialOdometerKm":0,"brand":null,"model":null,"fuelType":"GASOLINE",""" +
            """"createdAt":1,"updatedAt":2,"deleted":false,"deletedAt":null,"schemaVersion":1}"""
    ).let { if (includeEntityType) it else it.replace("\"entityType\":\"VEHICLE\",", "") }

private fun anonymousFuelEntryJson(
    id: String = "anonymous-entry",
    ownerId: String = ANONYMOUS_UID,
    includeEntityType: Boolean = true,
): String =
    (
        """{"entityType":"FUEL_ENTRY","id":"$id","ownerId":"$ownerId",""" +
            """"vehicleId":"anonymous-vehicle","date":1,"odometerKm":100,"litersScaled":50000,""" +
            """"pricePerLiterScaled":1500,"totalCostMinor":7500,"currency":"EUR",""" +
            """"isFullTank":true,"hasMissedEntries":false,"odometerInconsistent":false,""" +
            """"notes":null,"createdAt":1,"updatedAt":2,"deleted":false,"deletedAt":null,""" +
            """"schemaVersion":1}"""
    ).let { if (includeEntityType) it else it.replace("\"entityType\":\"FUEL_ENTRY\",", "") }

private val CREDENTIAL = NativeAuthCredential.Google("colliding-id-token", null)
private const val ANONYMOUS_UID = "anonymous-owner"
private const val PERMANENT_UID = "permanent-owner"
private const val RAW_TICKET = "0123456789012345678901234567890123456789012"
