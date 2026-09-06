package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.testing.FakeConnectivityObserver
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The automatic side of local owner adoption (`docs/CONTRACTS.md §11.2` and `§11.4`): a device that
 * started offline acquires its UID and adopts its rows without any user action. Story `E2-06`.
 */
class LocalOwnerAdoptionTest {
    private val factory = InMemoryDatabaseFactory()
    private var handle: DatabaseHandle? = null

    @AfterTest
    fun tearDown() {
        handle?.close()
        factory.close()
    }

    @Test
    fun connectivityReturningAcquiresAnAnonymousUidWhenLocalOwnerDataIsWaiting() =
        runTest {
            val authClient = AdoptingAuthClient(uid = ADOPTING_UID)
            val database = openDatabase()
            database.seedLocalOwnerVehicle("vehicle-1")
            val adoption = adoption(database, authClient, FakeConnectivityObserver(), FakeOwnerContext())

            adoption.acquireAnonymousUidIfWaiting()

            assertEquals(1, authClient.anonymousSignInCalls, "connectivity returning starts one acquisition")
        }

    @Test
    fun connectivityReturningDoesNotCreateAnAccountWhenThereIsNothingToAdopt() =
        runTest {
            val authClient = AdoptingAuthClient(uid = ADOPTING_UID)
            val database = openDatabase()
            val adoption = adoption(database, authClient, FakeConnectivityObserver(), FakeOwnerContext())

            adoption.acquireAnonymousUidIfWaiting()

            assertEquals(0, authClient.anonymousSignInCalls, "an empty local database never provokes a sign-in")
        }

    @Test
    fun anAlreadyAuthenticatedSessionNeverRepeatsTheAnonymousAcquisition() =
        runTest {
            val authClient = AdoptingAuthClient(uid = ADOPTING_UID)
            val database = openDatabase()
            database.seedLocalOwnerVehicle("vehicle-1")
            val adoption =
                adoption(database, authClient, FakeConnectivityObserver(), FakeOwnerContext(OwnerId(ADOPTING_UID)))

            adoption.acquireAnonymousUidIfWaiting()

            assertEquals(0, authClient.anonymousSignInCalls, "the retry is for the sentinel owner only")
        }

    @Test
    fun theGateIsOpenForAnOwnerThatIsStillTheLocalSentinel() =
        runTest {
            val database = openDatabase()
            database.seedLocalOwnerVehicle("vehicle-1")
            val adoption =
                adoption(database, AdoptingAuthClient(ADOPTING_UID), FakeConnectivityObserver(), FakeOwnerContext())

            adoption.awaitAdoption()

            // An offline session reads its own rows. Adoption gates the authenticated owner only.
            assertEquals(1L, database.localOwnerRowCount())
        }

    @Test
    fun theGateOnlyReturnsForAnAuthenticatedOwnerOnceAdoptionHasCommitted() =
        runTest {
            val database = openDatabase()
            database.seedLocalOwnerVehicle("vehicle-1")
            val ownerContext = FakeOwnerContext(OwnerId(ADOPTING_UID))
            val adoption =
                adoption(database, AdoptingAuthClient(ADOPTING_UID), FakeConnectivityObserver(), ownerContext)

            adoption.awaitAdoption()

            assertEquals(0L, database.localOwnerRowCount(), "the gate does not return before the rewrite")
            assertEquals(1, database.outboxRowCount("vehicle-1"))
        }

    @Test
    fun authenticationAdoptsTheWaitingRowsAndTheListNeverResolvesEmpty() =
        runTest {
            val database = openDatabase()
            database.seedLocalOwnerVehicle("vehicle-1")
            val ownerContext = FakeOwnerContext()
            val graph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers =
                        testAppProviders(
                            testAppGraphDependencies(
                                databaseFactory = SingleHandleDatabaseFactory(database),
                                authClient = AdoptingAuthClient(ADOPTING_UID),
                                ownerContext = ownerContext,
                            ),
                        ),
                )
            val harness = AppGraphTestHarness(graph, backgroundScope)

            try {
                val holder = graph.vehicleListStateHolder(harness.scope)
                // The offline session reads its own rows: the gate never holds the sentinel owner.
                assertEquals(
                    1,
                    holder.state
                        .first { state -> !state.isLoading }
                        .vehicles.size,
                )

                // Authentication is the only thing that happens here. No UI action follows it.
                ownerContext.set(OwnerId(ADOPTING_UID))
                val adopted = holder.state.first { state -> !state.isLoading }

                // Without the gate this list resolves empty first, and a host reading D-116 would
                // open mandatory first-run creation over data one transaction away from arriving.
                assertEquals(1, adopted.vehicles.size, "the adopted vehicle is the authenticated list")
                assertEquals(0L, database.localOwnerRowCount(), "nothing is left under the sentinel")
                assertEquals(1, database.outboxRowCount("vehicle-1"), "the adopted row is enqueued once")
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
    ): LocalOwnerAdoption =
        LocalOwnerAdoption(
            dependencies =
                testAppGraphDependencies(
                    databaseFactory = SingleHandleDatabaseFactory(database),
                    authClient = authClient,
                    ownerContext = ownerContext,
                    connectivityObserver = connectivity,
                ),
            database = database,
        )

    private companion object {
        const val ADOPTING_UID = "uid-adopting-owner"
    }
}

private class SingleHandleDatabaseFactory(
    private val database: AppDatabase,
) : DatabaseFactory {
    override fun create(): DatabaseHandle =
        object : DatabaseHandle {
            override val database = this@SingleHandleDatabaseFactory.database

            override fun close() = Unit
        }
}

private class AdoptingAuthClient(
    private val uid: String,
) : AuthClient {
    var anonymousSignInCalls: Int = 0
        private set

    private val mutableAuthState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState: StateFlow<AuthState> = mutableAuthState

    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> {
        anonymousSignInCalls += 1
        val session = AuthSession(uid = uid, isAnonymous = true, providers = emptySet())
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

private suspend fun AppDatabase.seedLocalOwnerVehicle(id: String) {
    databaseQueries.insertVehicleRow(
        id = id,
        ownerId = LOCAL_OWNER.value,
        name = id,
        nameFold = id,
        initialOdometerKm = 0,
        currentOdometerKm = 0,
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

private suspend fun AppDatabase.localOwnerRowCount(): Long =
    databaseQueries.countRowsOwnedBy(LOCAL_OWNER.value).awaitAsOne()

private suspend fun AppDatabase.outboxRowCount(entityId: String): Int =
    databaseQueries.selectOutboxByEntity(entityType = "VEHICLE", entityId = entityId).awaitAsList().size
