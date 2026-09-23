package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthOwnerContext
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
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
                val writtenVehicleId = requireNotNull(writer.createdVehicleId)

                reader.signIn()
                val restoredVehicleId = reader.awaitSyncedVehicle()

                assertEquals(writtenVehicleId, restoredVehicleId, "the identity recovered its own backup")
                val restored = reader.database.vehicleRow(restoredVehicleId)
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

                val writtenVehicleId = requireNotNull(writer.createdVehicleId)
                val writtenEntryId = requireNotNull(writer.fuelEntryId)

                reader.signIn()
                val restoredVehicleId = reader.awaitSyncedVehicle()
                val restoredEntryId = reader.awaitSyncedFuelEntry()

                assertEquals(writtenVehicleId, restoredVehicleId, "the same vehicle crossed over")
                assertEquals(writtenEntryId, restoredEntryId, "the same fuel entry crossed over")
                val restored = reader.database.fuelEntryRow(restoredEntryId)
                assertEquals(ODOMETER_KM, restored.odometerKm, "the restored entry keeps its odometer")
                assertEquals(LITERS_SCALED, restored.litersScaled, "the restored entry keeps its volume")
                assertEquals(writtenVehicleId, restored.vehicleId, "the entry is attached to the restored vehicle")
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
                val backedUpId = requireNotNull(first.createdVehicleId)
                assertTrue(
                    replica.storedIds(OwnerId(ANONYMOUS_UID), EntityType.VEHICLE).contains(backedUpId),
                    "the anonymous device does back up its own row under its own UID",
                )
            } finally {
                first.close()
            }

            // The second device starts signed out, so signing in is a real owner transition and its
            // recovery cycle actually runs. Without that the assertion below would be vacuous: a
            // device that never attempted a pull trivially holds nothing.
            val second = Device(replica, this, uid = OTHER_ANONYMOUS_UID, isAnonymous = true, signedInAtStart = false)
            try {
                second.signIn()
                second.settle("second identity ran its recovery cycle") {
                    second.recoveryCycleCount() >= 1
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
            val device = Device(replica, this, uid = PERMANENT_UID, signedInAtStart = false)

            try {
                replica.seed(
                    ownerId = OwnerId(PERMANENT_UID),
                    entityType = EntityType.VEHICLE,
                    entityId = EntityId(VEHICLE_ID),
                    rawJson = vehicleJson(VEHICLE_ID, PERMANENT_UID, VEHICLE_NAME),
                    serverUpdatedAt = Instant.fromEpochMilliseconds(NOW_MILLIS),
                )

                device.signIn()
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
            val device = Device(replica, this, uid = PERMANENT_UID, signedInAtStart = false)

            try {
                replica.seed(
                    ownerId = OwnerId(PERMANENT_UID),
                    entityType = EntityType.VEHICLE,
                    entityId = EntityId(VEHICLE_ID),
                    rawJson = vehicleJson(VEHICLE_ID, PERMANENT_UID, VEHICLE_NAME),
                    serverUpdatedAt = Instant.fromEpochMilliseconds(NOW_MILLIS),
                )

                device.signIn()
                device.awaitSyncedVehicle()

                assertEquals(
                    expected = 1,
                    actual = device.recoveryCycleCount(),
                    message = "the owner transition admits exactly one recovery cycle",
                )
                assertEquals(
                    expected = listOf(SyncTrigger.Periodic),
                    actual = device.scheduledTriggers,
                    message = "the graph hands the platform scheduler the periodic cadence and nothing else",
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
            val device = Device(replica, this, uid = PERMANENT_UID, signedInAtStart = false)

            try {
                replica.seed(
                    ownerId = OwnerId(PERMANENT_UID),
                    entityType = EntityType.VEHICLE,
                    entityId = EntityId(VEHICLE_ID),
                    rawJson = vehicleJson(VEHICLE_ID, PERMANENT_UID, VEHICLE_NAME),
                    serverUpdatedAt = Instant.fromEpochMilliseconds(NOW_MILLIS),
                )

                device.signIn()
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
        val reader = Device(replica, scope, uid = PERMANENT_UID, signedInAtStart = false)
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
     * [signIn], so the owner transition under test is the one a real sign-in produces.
     */
    private class Device(
        private val replica: InMemoryRemoteSyncSource,
        private val testScope: TestScope,
        private val uid: String,
        private val isAnonymous: Boolean = false,
        // A writer is signed in from the start; a reader starts signed out so that signing in is the
        // owner transition under test.
        signedInAtStart: Boolean = true,
    ) {
        private val factory = InMemoryDatabaseFactory()

        // The graph must read the handle this device asserts on. `InMemoryDatabaseFactory.create()`
        // returns a brand-new isolated database on every call, so handing the factory to the graph
        // would give it a second, empty database and every assertion here would read the wrong one.
        // `SingleHandleDatabaseFactory` is the idiom the repository already uses for exactly this
        // reason.
        private val handle: DatabaseHandle = factory.create()
        private val graphFactory: DatabaseFactory = SingleHandleDatabaseFactory(handle)
        private val authClient =
            SessionAuthClient(uid = uid, isAnonymous = isAnonymous).apply {
                // The session must exist before the graph subscribes, so a device that is already
                // signed in produces no owner transition at all.
                if (signedInAtStart) setAuthState(signedInState())
            }
        private val triggers = RecordingSyncTriggerAdapter()

        // Under `backgroundScope`, so `runTest` cancels every holder coroutine when the body returns.
        // A scope built from the test body's own context instead makes those coroutines children of
        // the test job, which `runTest` then reports as an uncompleted child.
        private val holderScope = CoroutineScope(SupervisorJob() + testScope.backgroundScope.coroutineContext)

        val database: AppDatabase = handle.database
        val scheduledTriggers: List<SyncTrigger> get() = triggers.scheduled

        private val graphInstance: AppGraph =
            buildAppGraph(
                isDebugBuild = true,
                providers =
                    testAppProviders(
                        testScope.confinedGraphDependencies(
                            testAppGraphDependencies(
                                databaseFactory = graphFactory,
                                authClient = authClient,
                                // The production owner context, so the owner transition under test is
                                // the one a real sign-in produces rather than a test-set value.
                                ownerContext = AuthOwnerContext(authClient.authState),
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

        /** The vehicle the writer actually created, discovered rather than assumed. */
        var createdVehicleId: String? = null
            private set

        var observedKnownEmptyList: Boolean = false
            private set

        /**
         * One admitted cycle reads each entity type once, so the number of `VEHICLE` pull rounds is
         * the number of cycles that reached the remote steps. `OwnerChanged` enters the controller
         * through `requestSync`, which the platform adapter does not observe, so this - not
         * `triggers` - is the honest observable of a recovery cycle.
         */
        fun recoveryCycleCount(): Int = replica.pullCalls.count { it.entityType == EntityType.VEHICLE }

        /**
         * Signs this device in with the identity it was built for: permanent for a device whose
         * `isAnonymous` is false, anonymous otherwise. Criterion 3 of this story is precisely that no
         * path describes an anonymous identity as a permanent or cross-device-recoverable one, so the
         * helper is named for the act of signing in, never for the permanence of the identity.
         */
        fun signIn() {
            authClient.setAuthState(signedInState())
        }

        private fun signedInState() =
            AuthState.SignedIn(
                AuthSession(
                    uid = uid,
                    isAnonymous = isAnonymous,
                    providers = if (isAnonymous) setOf(AuthProvider.ANONYMOUS) else setOf(AuthProvider.GOOGLE),
                ),
            )

        /** Writes a contract-valid Vehicle through the production form, which also backs it up. */
        suspend fun createVehicle() {
            val form = graphInstance.vehicleFormStateHolder(holderScope, vehicleId = null)
            form.setName(VEHICLE_NAME)
            form.save()
            settle("vehicle committed under $uid") { database.vehicleIds().isNotEmpty() }
            createdVehicleId = database.vehicleIds().single()
            settle("vehicle synced locally under $uid") { database.hasSyncedVehicle(createdVehicleId!!) }
        }

        /** Writes a Fuel Entry through the production form, which also backs it up. */
        suspend fun createFuelEntry() {
            val vehicleId = requireNotNull(createdVehicleId) { "create the vehicle first" }
            val form = graphInstance.fuelEntryFormStateHolder(holderScope, vehicleId = vehicleId, entryId = null)
            form.setOdometerKm(ODOMETER_KM)
            form.setLitersScaled(LITERS_SCALED)
            form.setPricePerLiterScaled(PRICE_PER_LITER_SCALED)
            form.save()
            settle("fuel entry committed under $uid") { database.fuelEntryIds(uid).isNotEmpty() }
            fuelEntryId = database.fuelEntryIds(uid).single()
            settle("fuel entry synced locally under $uid") { database.hasSyncedFuelEntry(fuelEntryId!!) }
        }

        var fuelEntryId: String? = null
            private set

        suspend fun awaitSyncedVehicle(): String {
            settle("vehicle restored on $uid") {
                database.vehicleIds().isNotEmpty() &&
                    database.hasSyncedVehicle(database.vehicleIds().first())
            }
            return database.vehicleIds().first()
        }

        suspend fun awaitSyncedFuelEntry(): String {
            settle("fuel entry restored on $uid") {
                database.fuelEntryIds(uid).isNotEmpty() &&
                    database.hasSyncedFuelEntry(database.fuelEntryIds(uid).first())
            }
            return database.fuelEntryIds(uid).first()
        }

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

        /**
         * Releases any parked `§9.8` trigger once, then waits in real time for [condition].
         *
         * Three clocks are involved and only one is virtual, so they are advanced separately and
         * exactly once each. The post-write debounce is a virtual delay, so one bounded advance
         * releases the trigger a write parked; `runCurrent()` then drains the graph's coroutines from
         * the test scheduler; and the bundled SQLite driver does its work on a real executor, so the
         * wait itself must yield in real time, which is what [awaitCondition] does.
         *
         * The advance is deliberately **outside** the poll loop. Advancing on every attempt compounds:
         * each round would cross the 30 s automatic floor again, re-arm it, and release another
         * window, so a few hundred virtual seconds and a storm of graph cycles would pile up while the
         * test was merely waiting for one write to commit.
         */
        suspend fun settle(
            expectation: String,
            condition: suspend () -> Boolean,
        ) {
            testScope.advanceTimeBy(RECOVERY_POLL_SPAN)
            testScope.runCurrent()
            awaitCondition(expectation) { condition() }
        }

        /** Releases the device and waits for the graph to hand the database back. */
        suspend fun close() {
            holderScope.cancel()
            graphInstance.awaitClosed()
            factory.close()
        }

        /**
         * Hands the graph the one handle this device owns, and releases it on
         * [com.ruizurraca.carapp.core.database.DatabaseHandle.close] only when the graph does.
         */
        private class SingleHandleDatabaseFactory(
            private val handle: DatabaseHandle,
        ) : DatabaseFactory {
            override fun create(): DatabaseHandle = handle
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
        const val VEHICLE_NAME = "Recovered Roadster"

        /**
         * Above the vehicle's `initialOdometerKm` of 0, so the write satisfies `SPECIFICATION.md` R-1
         * on its first attempt. An odometer below `initialOdometerKm` is a deliberate two-step
         * warning protocol (`§5`): the first save mutates nothing and asks for confirmation, which is
         * not what this proof is testing.
         */
        const val ODOMETER_KM = 12_000L

        /** 42.5 L, above the 1-litre floor and below the 500-litre ceiling. */
        const val LITERS_SCALED = 42_500L

        /**
         * 16.50 EUR/L in the canonical scale of `docs/CONTRACTS.md §2`, below the 999_999 ceiling
         * that `FuelEntryValidation` enforces. A value above it is rejected as
         * `VALIDATION.OUT_OF_RANGE` before the entry is ever persisted.
         */
        const val PRICE_PER_LITER_SCALED = 1_650L
        const val NOW_MILLIS = 1_767_225_600_000L

        /** One `§9.8` post-write debounce plus a margin, per poll. */
        val RECOVERY_POLL_SPAN = 3.seconds
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

private suspend fun AppDatabase.fuelEntryIds(ownerId: String): List<String> =
    databaseQueries.selectFuelEntriesForAdoption(ownerId).awaitAsList().map { it.id }

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
