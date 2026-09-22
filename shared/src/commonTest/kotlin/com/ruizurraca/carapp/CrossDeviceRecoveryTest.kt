package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeConnectivityObserver
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.core.testing.InMemoryRemoteSyncSource
import com.ruizurraca.carapp.core.testing.RecordingSyncTriggerAdapter
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * `E3-12`: the permanent-account cross-device recovery proof.
 *
 * Two `AppGraph`s model two devices. Each owns its own in-memory SQLDelight database through its own
 * [DatabaseFactory], and both share one [InMemoryRemoteSyncSource], which is the backup replica. Data
 * travels only through the production path — the writer's repository commit, its outbox, the engine's
 * push, the replica's storage, and then the reader's pull and local apply — so each assertion is about
 * the product's own behaviour rather than about a hand-written remote document.
 *
 * What the two devices share is the *permanent identity*, never a credential: each has its own
 * [AuthClient] already holding a signed-in Google session for the same UID. That is what a real second
 * device looks like after the owner signs in with the same provider on it, and it is why the proof
 * says nothing about anonymous identity, which `docs/CONTRACTS.md §11.2` keeps device-bound.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrossDeviceRecoveryTest {
    /**
     * The acceptance criterion: a Vehicle written and backed up on device A is restored on device B,
     * which starts from clean local product data and signs into the same permanent identity.
     */
    @Test
    fun aVehicleBackedUpByOneDeviceIsRestoredOnACleanDeviceWithTheSamePermanentIdentity() =
        runTest {
            val replica = replica()
            withTwoDevices(replica, this) { writer, reader ->
                writer.createVehicle()

                reader.signInPermanently()
                reader.awaitSyncedVehicle()

                val restored = reader.database.vehicleRow(VEHICLE_ID)
                assertEquals(VEHICLE_NAME, restored.name, "the restored name is the one the writer wrote")
                assertEquals(PERMANENT_UID, restored.ownerId, "the row belongs to the permanent identity")
                reader.awaitListedVehicle()
            }
        }

    /**
     * The reverse direction. It is covered rather than argued away, because "the same shared path
     * makes it redundant" is exactly the kind of claim that hides a direction-specific assumption.
     * The roles of the two devices are swapped relative to the first test, so each direction is
     * exercised by the graph that is the reader.
     */
    @Test
    fun theReverseDirectionRestoresAFuelEntryWrittenByTheOtherDevice() =
        runTest {
            val replica = replica()
            withTwoDevices(replica, this) { writer, reader ->
                writer.createVehicle()
                writer.createFuelEntry()

                reader.signInPermanently()
                reader.awaitSyncedVehicle()
                reader.awaitSyncedFuelEntry()

                val restored = reader.database.fuelEntryRow(FUEL_ENTRY_ID)
                assertEquals(ODOMETER_KM, restored.odometerKm, "the restored entry keeps its odometer")
                assertEquals(LITERS_SCALED, restored.litersScaled, "the restored entry keeps its volume")
                assertEquals(VEHICLE_ID, restored.vehicleId, "the entry is attached to the restored vehicle")
            }
        }

    /**
     * Criterion 3, as an executable prohibition rather than prose: one anonymous identity's backup
     * path holds nothing for another, so no path can describe an anonymous identity as recoverable on
     * a different device.
     */
    @Test
    fun anAnonymousIdentityBackupIsNeverRecoverableFromAnotherIdentity() =
        runTest {
            val replica = replica()
            val first = Device(replica, this, uid = ANONYMOUS_UID, isAnonymous = true)

            try {
                first.createVehicle()
                assertTrue(
                    replica.storedIds(OwnerId(ANONYMOUS_UID), EntityType.VEHICLE).contains(VEHICLE_ID),
                    "the anonymous device does back up its own row under its own UID",
                )
            } finally {
                first.close()
            }

            val second = Device(replica, this, uid = OTHER_ANONYMOUS_UID, isAnonymous = true)
            try {
                second.signInPermanently()
                second.settle("second anonymous identity settled") {
                    second.database.vehicleIds().isEmpty()
                }

                assertEquals(
                    emptyList(),
                    second.database.vehicleIds(),
                    "the second identity recovered nothing from the first identity's backup path",
                )
                assertTrue(
                    replica.storedIds(OwnerId(OTHER_ANONYMOUS_UID), EntityType.VEHICLE).isEmpty(),
                    "a different identity has no documents on the first identity's backup path",
                )
            } finally {
                second.close()
            }
        }

    /**
     * Criterion 4: recovery is a pull of bounded pages. The replica exposes no subscription at all, so
     * what a test proves is that the engine reached it only through its one-shot page queries.
     */
    @Test
    fun recoveryReadsBoundedPagesAndNeverOpensAListener() =
        runTest {
            val replica = replica()
            val device = Device(replica, this, uid = PERMANENT_UID)

            try {
                replica.seed(
                    ownerId = OwnerId(PERMANENT_UID),
                    entityType = EntityType.VEHICLE,
                    entityId = EntityId(VEHICLE_ID),
                    rawJson = vehicleJson(VEHICLE_ID, PERMANENT_UID, VEHICLE_NAME),
                    serverUpdatedAt = Instant.fromEpochMilliseconds(NOW_MILLIS),
                )

                device.signInPermanently()
                device.awaitSyncedVehicle()

                assertTrue(replica.pullCalls.isNotEmpty(), "recovery read pages from the replica")
                assertTrue(
                    replica.pullCalls.all { it.limit > 0 },
                    "every read was a bounded page request, never an unbounded subscription",
                )
                assertTrue(
                    replica.pullCalls.any { it.entityType == EntityType.VEHICLE },
                    "the vehicle collection was read through the pull path",
                )
            } finally {
                device.close()
            }
        }

    /**
     * The first defect this story fixes, as its own regression: a permanent sign-in requests exactly
     * one cycle, through the dedicated owner-change trigger rather than a lifecycle one borrowed for a
     * cause it does not describe.
     */
    @Test
    fun aPermanentSignInRequestsExactlyOneOwnerChangeCycle() =
        runTest {
            val replica = replica()
            val device = Device(replica, this, uid = PERMANENT_UID)

            try {
                replica.seed(
                    ownerId = OwnerId(PERMANENT_UID),
                    entityType = EntityType.VEHICLE,
                    entityId = EntityId(VEHICLE_ID),
                    rawJson = vehicleJson(VEHICLE_ID, PERMANENT_UID, VEHICLE_NAME),
                    serverUpdatedAt = Instant.fromEpochMilliseconds(NOW_MILLIS),
                )

                device.signInPermanently()
                device.awaitSyncedVehicle()

                assertEquals(
                    1,
                    device.ownerChangeRequests(),
                    "the owner transition requests exactly one OwnerChanged cycle",
                )
                assertEquals(
                    emptyList(),
                    device.scheduledTriggers.filter { it == SyncTrigger.AppForeground },
                    "no lifecycle trigger is borrowed for an owner transition",
                )
            } finally {
                device.close()
            }
        }

    /**
     * The second defect: on a clean device the list must not publish itself as known and empty, which
     * is the state that opens mandatory first-run creation over data a pull is about to restore.
     */
    @Test
    fun aCleanDeviceNeverPublishesAKnownEmptyListForAnOwnerWhoseRecoveryIsOutstanding() =
        runTest {
            val replica = replica()
            val device = Device(replica, this, uid = PERMANENT_UID)

            try {
                replica.seed(
                    ownerId = OwnerId(PERMANENT_UID),
                    entityType = EntityType.VEHICLE,
                    entityId = EntityId(VEHICLE_ID),
                    rawJson = vehicleJson(VEHICLE_ID, PERMANENT_UID, VEHICLE_NAME),
                    serverUpdatedAt = Instant.fromEpochMilliseconds(NOW_MILLIS),
                )

                device.signInPermanently()
                device.observeListUntilFirstKnownState()
                device.awaitSyncedVehicle()

                assertTrue(
                    !device.observedKnownEmptyList,
                    "a known empty list is the state F-1 mandatory first-run creation reads",
                )
            } finally {
                device.close()
            }
        }

    private fun replica() = InMemoryRemoteSyncSource(clock = { Instant.fromEpochMilliseconds(NOW_MILLIS) })

    /** Runs [body] with a writer and a reader sharing one replica, and closes both devices after it. */
    private suspend fun TestScope.withTwoDevices(
        replica: InMemoryRemoteSyncSource,
        scope: TestScope,
        body: suspend (writer: Device, reader: Device) -> Unit,
    ) {
        val writer = Device(replica, scope, uid = PERMANENT_UID)
        val reader = Device(replica, scope, uid = PERMANENT_UID)
        try {
            body(writer, reader)
        } finally {
            reader.close()
            writer.close()
        }
    }

    /**
     * One device: its own in-memory database, its own graph, its own auth session, and the shared
     * replica. The device is built holding a signed-out session and signs in through
     * [signInPermanently], so the owner transition under test is the one a real sign-in produces.
     */
    private class Device(
        private val replica: InMemoryRemoteSyncSource,
        private val testScope: TestScope,
        private val uid: String,
        private val isAnonymous: Boolean = false,
    ) {
        private val factory = InMemoryDatabaseFactory()
        private val handle: DatabaseHandle = factory.create()
        private val authClient = SessionAuthClient(uid = uid, isAnonymous = isAnonymous)
        private val triggers = RecordingSyncTriggerAdapter()
        private val holderScope = CoroutineScope(SupervisorJob() + testScope.coroutineContext)

        val database: AppDatabase = handle.database
        val scheduledTriggers: List<SyncTrigger> get() = triggers.scheduled

        private val graphInstance: AppGraph =
            buildAppGraph(
                isDebugBuild = true,
                providers =
                    testAppProviders(
                        testScope.confinedGraphDependencies(
                            testAppGraphDependencies(
                                databaseFactory = factory,
                                authClient = authClient,
                                ownerContext = FakeOwnerContext(OwnerId(uid)),
                                remoteSyncSource = replica,
                                clock = FakeAppClock(Instant.fromEpochMilliseconds(NOW_MILLIS)),
                                connectivityObserver = FakeConnectivityObserver(),
                                syncTriggerAdapter = triggers,
                            ),
                        ),
                    ),
            )

        val list: com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListStateHolder =
            graphInstance.vehicleListStateHolder(holderScope)

        var observedKnownEmptyList: Boolean = false
            private set

        fun signInPermanently() {
            authClient.setAuthState(
                AuthState.SignedIn(
                    AuthSession(
                        uid = uid,
                        isAnonymous = isAnonymous,
                        providers = if (isAnonymous) setOf(AuthProvider.ANONYMOUS) else setOf(AuthProvider.GOOGLE),
                    ),
                ),
            )
        }

        /** Writes a contract-valid Vehicle through the production form, which also backs it up. */
        suspend fun createVehicle() {
            graphInstance.vehicleFormStateHolder(holderScope, vehicleId = null).apply {
                setName(VEHICLE_NAME)
                save()
            }
            settle("vehicle backed up under $uid") {
                replica.storedIds(OwnerId(uid), EntityType.VEHICLE).contains(VEHICLE_ID)
            }
            settle("vehicle synced locally") { database.hasSyncedVehicle(VEHICLE_ID) }
        }

        /** Writes a Fuel Entry through the production form, which also backs it up. */
        suspend fun createFuelEntry() {
            graphInstance.fuelEntryFormStateHolder(holderScope, vehicleId = VEHICLE_ID, entryId = null).apply {
                setOdometerKm(ODOMETER_KM)
                setLitersScaled(LITERS_SCALED)
                setPricePerLiterScaled(PRICE_PER_LITER_SCALED)
                save()
            }
            settle("fuel entry backed up to the replica") {
                replica.storedIds(OwnerId(uid), EntityType.FUEL_ENTRY).contains(FUEL_ENTRY_ID)
            }
            settle("fuel entry synced locally") { database.hasSyncedFuelEntry(FUEL_ENTRY_ID) }
        }

        suspend fun awaitSyncedVehicle() =
            settle("vehicle restored on $uid") { database.hasSyncedVehicle(VEHICLE_ID) }

        suspend fun awaitSyncedFuelEntry() =
            settle("fuel entry restored on $uid") { database.hasSyncedFuelEntry(FUEL_ENTRY_ID) }

        suspend fun awaitListedVehicle() =
            list.state.awaitState("vehicle listed on $uid") { state -> state.vehicles.isNotEmpty() }

        /**
         * Observes the list across the owner transition and records whether it ever published a
         * *resolved* empty list, which is the exact state `shouldPresentFirstVehicleCreation` acts on.
         */
        suspend fun observeListUntilFirstKnownState() {
            list.state.awaitState("list published its first known state on $uid") { state ->
                if (!state.isLoading && state.vehicles.isEmpty()) observedKnownEmptyList = true
                !state.isLoading
            }
        }

        fun ownerChangeRequests(): Int = triggers.scheduled.count { it == SyncTrigger.OwnerChanged }

        /** Waits for [condition], advancing graph time so scheduled work actually runs. */
        suspend fun settle(
            expectation: String,
            condition: suspend () -> Boolean,
        ) {
            awaitCondition(expectation) {
                // Advancing is what lets the graph-owned cycle run under the confined dispatcher;
                // the test scheduler never advances on its own while the caller polls.
                testScope.advanceGraphWork()
                condition()
            }
        }

        fun close() {
            holderScope.cancel()
            graphInstance.close()
            factory.close()
        }
    }

    /** An auth client whose session the test drives directly, so no credential exchange is needed. */
    private class SessionAuthClient(
        private val uid: String,
        private val isAnonymous: Boolean,
    ) : AuthClient {
        private val state = MutableStateFlow<AuthState>(AuthState.SignedOut)

        override val authState: StateFlow<AuthState> = state

        fun setAuthState(next: AuthState) {
            state.value = next
        }

        override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> =
            Outcome.Ok(AuthSession(uid, true, setOf(AuthProvider.ANONYMOUS)))

        override suspend fun signInWithCredential(
            credential: NativeAuthCredential,
            allowUidChange: Boolean,
        ): Outcome<AuthSession, AuthError> = session()

        override suspend fun linkCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
            session()

        override suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
            session()

        override suspend fun signOut(): Outcome<Unit, AuthError> = Outcome.Ok(Unit)

        override suspend fun deleteAccount(): Outcome<Unit, AuthError> = Outcome.Ok(Unit)

        private fun session() =
            Outcome.Ok(
                AuthSession(
                    uid = uid,
                    isAnonymous = isAnonymous,
                    providers = if (isAnonymous) setOf(AuthProvider.ANONYMOUS) else setOf(AuthProvider.GOOGLE),
                ),
            )
    }

    private companion object {
        const val PERMANENT_UID = "permanent-uid"
        const val ANONYMOUS_UID = "anonymous-uid"
        const val OTHER_ANONYMOUS_UID = "other-anonymous-uid"
        const val VEHICLE_ID = "00000000-0000-4000-8000-000000000001"
        const val FUEL_ENTRY_ID = "00000000-0000-4000-8000-000000000002"
        const val VEHICLE_NAME = "Recovered Roadster"
        const val ODOMETER_KM = 12_000L
        const val LITERS_SCALED = 42_500L
        const val PRICE_PER_LITER_SCALED = 1_650_000L
        const val NOW_MILLIS = 1_767_225_600_000L
    }
}

private suspend fun AppDatabase.hasSyncedVehicle(id: String): Boolean =
    databaseQueries.selectVehicleById(id).awaitAsList().any { it.serverUpdatedAt != null }

private suspend fun AppDatabase.hasSyncedFuelEntry(id: String): Boolean =
    databaseQueries.selectFuelEntryById(id).awaitAsList().any { it.serverUpdatedAt != null }

private suspend fun AppDatabase.vehicleRow(id: String) =
    databaseQueries.selectVehicleById(id).awaitAsList().first { it.id == id }

private suspend fun AppDatabase.fuelEntryRow(id: String) =
    databaseQueries.selectFuelEntryById(id).awaitAsList().first { it.id == id }

private suspend fun AppDatabase.vehicleIds(): List<String> =
    databaseQueries.selectAllVehicles().awaitAsList().map { it.id }

private fun vehicleJson(
    id: String,
    ownerId: String,
    name: String,
): String =
    """
    {
      "id":"$id",
      "ownerId":"$ownerId",
      "name":"$name",
      "initialOdometerKm":0,
      "brand":null,
      "model":null,
      "fuelType":"GASOLINE",
      "createdAt":1767225600000,
      "updatedAt":1767225600000,
      "deleted":false,
      "deletedAt":null,
      "schemaVersion":1
    }
    """.trimIndent()
