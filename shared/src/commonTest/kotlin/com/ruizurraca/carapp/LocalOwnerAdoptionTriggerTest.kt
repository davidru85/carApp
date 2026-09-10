package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthOwnerContext
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.LogLevel
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.testing.FakeConnectivityObserver
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.core.testing.RecordingLogger
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The trigger topology of `docs/CONTRACTS.md §11.2`, as the owner's second review round of pull
 * request #55 defined it: the cold-start ordering between auth readiness and connectivity, trigger
 * failure isolation that survives, and the post-commit re-evaluation on every synchronized write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalOwnerAdoptionTriggerTest {
    private val factory = InMemoryDatabaseFactory()
    private var handle: DatabaseHandle? = null

    @AfterTest
    fun tearDown() {
        handle?.close()
        factory.close()
    }

    @Test
    fun authBecomingEligibleAfterAnEarlyConnectivityEmissionAcquiresExactlyOnce() =
        runTest(timeout = AWAIT_TIMEOUT) {
            val database = openDatabase()
            database.seedTriggerVehicle("vehicle-1")
            // Cold start as production has it: FirebaseAuthClient.authState begins Unknown, and
            // connectivity is already online, so its only emission lands before auth is eligible.
            val authClient = TriggerAuthClient(initialState = AuthState.Unknown)
            val adoption =
                adoption(
                    database = database,
                    authClient = authClient,
                    connectivity = FakeConnectivityObserver(initiallyOnline = true),
                    // AuthOwnerContext maps Unknown and SignedOut alike to LOCAL_OWNER and
                    // deduplicates them, so the owner flow publishes nothing on this transition.
                    ownerContext = FakeOwnerContext(),
                )

            // Unconfined delivery makes the ordering the production race actually has: the single
            // connectivity emission is consumed inside launchIn, while auth is still Unknown.
            adoption.launchIn(
                CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            )
            assertEquals(
                0,
                authClient.anonymousSignInCalls,
                "the connectivity emission was consumed and refused while auth was Unknown",
            )

            authClient.setAuthState(AuthState.SignedOut)
            authClient.authState.awaitState("anonymous session acquired after auth resolution") { state -> state is AuthState.SignedIn }

            assertEquals(
                1,
                authClient.anonymousSignInCalls,
                "auth becoming eligible is itself a trigger, and it fires exactly once",
            )
        }

    @Test
    fun acquisitionNeverRunsWhileTheAuthStateIsUnknown() =
        runTest {
            val database = openDatabase()
            database.seedTriggerVehicle("vehicle-1")
            val authClient = TriggerAuthClient(initialState = AuthState.Unknown)
            val adoption =
                adoption(database, authClient, FakeConnectivityObserver(), FakeOwnerContext())

            adoption.acquireAnonymousUidIfWaiting()

            assertEquals(0, authClient.anonymousSignInCalls, "Unknown is not a retryable state")
        }

    @Test
    fun aFailedConnectivityAttemptDoesNotStopALaterConnectivityRetry() =
        runTest(timeout = AWAIT_TIMEOUT) {
            val database = openDatabase()
            database.seedTriggerVehicle("vehicle-1")
            val authClient = TriggerAuthClient(throwOnFirstSignIn = true)
            val connectivity = FakeConnectivityObserver(initiallyOnline = false)
            val adoption = adoption(database, authClient, connectivity, FakeOwnerContext())

            adoption.launchIn(backgroundScope)

            connectivity.set(true)
            while (authClient.anonymousSignInCalls < 1) yield()
            // Drive real offline-to-online edges until the retry lands. A conflated StateFlow can
            // swallow one toggle; an observation that died cannot answer any of them, and the
            // bounded test timeout is what separates the two.
            while (authClient.anonymousSignInCalls < 2) {
                connectivity.set(false)
                yield()
                connectivity.set(true)
                yield()
            }

            assertEquals(
                2,
                authClient.anonymousSignInCalls,
                "one failed attempt must not end the observation that produced it",
            )
        }

    @Test
    fun aFailingWriteLaunchedTriggerIsReportedWithoutCancellingTheScope() =
        runTest(timeout = AWAIT_TIMEOUT) {
            val database = openDatabase()
            database.seedTriggerVehicle("vehicle-1")
            val logger = RecordingLogger()
            val authClient = TriggerAuthClient(throwOnEverySignIn = true)
            val adoption =
                adoption(
                    database = database,
                    authClient = authClient,
                    connectivity = FakeConnectivityObserver(),
                    ownerContext = FakeOwnerContext(),
                    logger = logger,
                )

            // The connectivity trigger fires first and fails, which is attempt one and warning one.
            adoption.launchIn(backgroundScope)
            while (authClient.anonymousSignInCalls < 1) yield()

            adoption.onLocalOwnerWriteCommitted()
            while (authClient.anonymousSignInCalls < 2) yield()
            while (logger.entries.count { entry -> entry.level == LogLevel.WARN } < 2) yield()

            assertTrue(backgroundScope.isActive, "an unexpected provider fault never cancels the graph scope")
            assertEquals(
                2,
                logger.entries.count { entry -> entry.level == LogLevel.WARN },
                "the write-launched trigger reports its own failure rather than leaking it",
            )
        }

    @Test
    fun aFuelEntryWriteTriggersAcquisitionAndAdoptionAfterAnEarlierAttemptFailed() =
        runTest(timeout = AWAIT_TIMEOUT) {
            val database = openDatabase()
            database.seedTriggerVehicle(VEHICLE_ID)
            // The first attempt fails while the device is online and stays online, so no connectivity
            // edge will ever reopen the question. Only a committed write can.
            val authClient = TriggerAuthClient(failFirstSignIn = true)
            val graph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers =
                        testAppProviders(
                            testAppGraphDependencies(
                                databaseFactory = TriggerDatabaseFactory(database),
                                authClient = authClient,
                                ownerContext = AuthOwnerContext(authClient.authState),
                                connectivityObserver = FakeConnectivityObserver(initiallyOnline = true),
                            ),
                        ),
                )
            val harness = AppGraphTestHarness(graph, backgroundScope)

            try {
                while (authClient.anonymousSignInCalls < 1) yield()

                val form = graph.fuelEntryFormStateHolder(harness.scope, VEHICLE_ID, entryId = null)
                harness.collect(form.state)
                form.setOdometerKm(120L)
                form.setLitersScaled(40_000L)
                form.setPricePerLiterScaled(1_500L)
                form.save()

                authClient.authState.awaitState("anonymous session acquired after fuel write") { state -> state is AuthState.SignedIn }
                while (database.sentinelRowCount() > 0L) yield()

                assertEquals(2, authClient.anonymousSignInCalls, "the fuel entry write re-evaluated acquisition")
                assertEquals(0L, database.sentinelRowCount(), "and adoption rewrote every sentinel row")
                assertTrue(database.hasOutboxRow("VEHICLE", VEHICLE_ID), "the vehicle is enqueued")
                assertTrue(
                    database.hasOutboxRow("FUEL_ENTRY", FIRST_GENERATED_ID),
                    "and so is the fuel entry the write created",
                )
                form.close()
            } finally {
                harness.close()
            }
        }

    private fun openDatabase(): AppDatabase {
        val created = factory.create()
        handle = created
        return created.database
    }

    private fun adoption(
        database: AppDatabase,
        authClient: AuthClient,
        connectivity: FakeConnectivityObserver,
        ownerContext: FakeOwnerContext,
        logger: RecordingLogger = RecordingLogger(),
    ): LocalOwnerAdoption =
        LocalOwnerAdoption(
            dependencies =
                testAppGraphDependencies(
                    databaseFactory = TriggerDatabaseFactory(database),
                    authClient = authClient,
                    ownerContext = ownerContext,
                    logger = logger,
                    connectivityObserver = connectivity,
                ),
            database = database,
        )

    private companion object {
        const val VEHICLE_ID = "vehicle-1"

        /** `FakeUuidGenerator` is deterministic, and the seeded vehicle consumed none of it. */
        const val FIRST_GENERATED_ID = "00000000-0000-4000-8000-000000000001"
        val AWAIT_TIMEOUT = 10.seconds
    }
}

/**
 * An auth client whose anonymous acquisition can be made to fail by outcome or by throwing, so the
 * trigger topology can be driven through both failure shapes.
 */
private class TriggerAuthClient(
    initialState: AuthState = AuthState.SignedOut,
    private val failFirstSignIn: Boolean = false,
    private val throwOnFirstSignIn: Boolean = false,
    private val throwOnEverySignIn: Boolean = false,
) : AuthClient {
    var anonymousSignInCalls: Int = 0
        private set

    private val mutableAuthState = MutableStateFlow(initialState)
    override val authState: StateFlow<AuthState> = mutableAuthState

    fun setAuthState(state: AuthState) {
        mutableAuthState.value = state
    }

    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> {
        anonymousSignInCalls += 1
        if (throwOnEverySignIn) error("anonymous provider fault")
        if (throwOnFirstSignIn && anonymousSignInCalls == 1) error("anonymous provider fault")
        if (failFirstSignIn && anonymousSignInCalls == 1) return Outcome.Err(AuthError.NetworkUnavailable)
        val session = AuthSession(uid = "uid-adopting-owner", isAnonymous = true, providers = emptySet())
        mutableAuthState.value = AuthState.SignedIn(session)
        return Outcome.Ok(session)
    }

    override suspend fun signInWithCredential(
        credential: NativeAuthCredential,
        allowUidChange: Boolean,
    ): Outcome<AuthSession, AuthError> = Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun linkCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun signOut(): Outcome<Unit, AuthError> = Outcome.Ok(Unit)

    override suspend fun deleteAccount(): Outcome<Unit, AuthError> = Outcome.Ok(Unit)
}

private class TriggerDatabaseFactory(
    private val database: AppDatabase,
) : DatabaseFactory {
    override fun create(): DatabaseHandle =
        object : DatabaseHandle {
            override val database = this@TriggerDatabaseFactory.database

            override fun close() = Unit
        }
}

private suspend fun AppDatabase.seedTriggerVehicle(id: String) {
    databaseQueries.insertVehicleRow(
        id = id,
        ownerId = LOCAL_OWNER.value,
        name = id,
        nameFold = id,
        initialOdometerKm = 100,
        currentOdometerKm = 100,
        brand = null,
        model = null,
        fuelType = "GASOLINE",
        createdAt = 1,
        updatedAt = 1,
        serverUpdatedAt = null,
        deleted = 0,
        deletedAt = null,
        syncState = "PENDING",
        localRevision = 1,
        localMutationSeq = 1,
        schemaVersion = 1,
    )
}

private suspend fun AppDatabase.sentinelRowCount(): Long =
    databaseQueries.countRowsOwnedBy(LOCAL_OWNER.value).awaitAsOne()

private suspend fun AppDatabase.hasOutboxRow(
    entityType: String,
    entityId: String,
): Boolean = databaseQueries.selectOutboxByEntity(entityType, entityId).awaitAsList().isNotEmpty()
