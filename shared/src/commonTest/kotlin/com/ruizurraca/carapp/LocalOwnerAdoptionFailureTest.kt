package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.database.DatabaseMutations
import com.ruizurraca.carapp.core.database.VehicleDatabaseAccess
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeConnectivityObserver
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.FakeUuidGenerator
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.core.testing.TestDispatcherProvider
import com.ruizurraca.carapp.feature.vehicle.data.SqlDelightVehicleRepository
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.presentation.createVehicleListStateHolder
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * `D-125` requires an adoption failure to be a typed, retryable outcome rather than a silent
 * indefinite wait, and requires the two automatic triggers to be independent of each other.
 */
class LocalOwnerAdoptionFailureTest {
    private val factory = InMemoryDatabaseFactory()
    private var handle: DatabaseHandle? = null

    @AfterTest
    fun tearDown() {
        handle?.close()
        factory.close()
    }

    @Test
    fun aFailedAdoptionTransactionIsReportedAsATypedErrorRatherThanThrown() =
        runTest {
            val database = openDatabase()
            database.seedLocalOwnerVehicle("vehicle-1")
            val fault = AdoptionFault(failing = true)
            val adoption = adoption(database, ownerContext = FakeOwnerContext(OwnerId(ADOPTING_UID)), fault = fault)

            val result = adoption.awaitAdoption()

            assertIs<Outcome.Err<*>>(result, "the gate reports the failure instead of throwing")
            assertEquals(PersistenceError.TransactionFailed, (result as Outcome.Err).error)
            assertTrue(coroutineContext.isActive, "the caller's coroutine survives the failure")
        }

    @Test
    fun aFailedAdoptionLeavesTheListUnknownAndRetryableRatherThanConfirmedEmpty() =
        runTest(timeout = AWAIT_TIMEOUT) {
            val database = openDatabase()
            database.seedLocalOwnerVehicle("vehicle-1")
            val fault = AdoptionFault(failing = true)
            val ownerContext = FakeOwnerContext(OwnerId(ADOPTING_UID))
            val repository = gatedRepository(database, ownerContext, fault)
            val holder =
                createVehicleListStateHolder(
                    scope = backgroundScope,
                    repository = repository,
                    dispatchers = TestDispatcherProvider(),
                    refreshVehicles = { Outcome.Ok(Unit) },
                    ownerContext = ownerContext,
                )

            try {
                val failed = holder.state.awaitState("adoption error published") { state -> state.message != null }
                assertTrue(failed.isLoading, "an unreadable list is not known, which is what D-116 means")
                assertTrue(failed.vehicles.isEmpty(), "and it is never published as a confirmed empty list")

                // The owner retries. The gate runs again, so the retry is real and not a redraw.
                fault.failing = false
                holder.refresh()
                val recovered =
                    holder.state.awaitState(
                        "vehicle list recovered after retry",
                    ) { state -> !state.isLoading }

                assertEquals(1, recovered.vehicles.size, "the retry adopts and the list resolves")
                assertEquals(0L, database.localOwnerRowCount())
            } finally {
                holder.close()
            }
        }

    @Test
    fun aFailedAdoptionOnAWritePathReturnsATypedErrorWithoutCancellingTheCaller() =
        runTest {
            val database = openDatabase()
            database.seedLocalOwnerVehicle("vehicle-1")
            val fault = AdoptionFault(failing = true)
            val repository = gatedRepository(database, FakeOwnerContext(OwnerId(ADOPTING_UID)), fault)

            val result =
                repository.createVehicle(
                    CreateVehicleCommand(
                        name = "Roadster",
                        initialOdometerKm = 0,
                        brand = null,
                        model = null,
                        fuelType = FuelType.GASOLINE,
                        confirmations = emptySet(),
                    ),
                )

            assertIs<Outcome.Err<*>>(result, "a write behind a failed gate is a typed error")
            assertEquals(PersistenceError.TransactionFailed, (result as Outcome.Err).error)
            assertTrue(coroutineContext.isActive, "and the caller is not cancelled")
        }

    @Test
    fun aFailingOwnerObserverDoesNotDisableTheConnectivityTrigger() =
        runTest(timeout = AWAIT_TIMEOUT) {
            val database = openDatabase()
            database.seedLocalOwnerVehicle("vehicle-1")
            val authClient = CountingAuthClient(ADOPTING_UID)
            val adoption =
                LocalOwnerAdoption(
                    dependencies =
                        testAppGraphDependencies(
                            databaseFactory = SingleHandleFactory(database),
                            authClient = authClient,
                            ownerContext = ThrowingOwnerContext(),
                            connectivityObserver = FakeConnectivityObserver(),
                        ),
                    database = database,
                )

            adoption.launchIn(backgroundScope)

            authClient.authState.awaitState("anonymous acquisition retried") { state -> state is AuthState.SignedIn }
            assertEquals(1, authClient.anonymousSignInCalls, "the connectivity trigger outlives the owner observer")
        }

    @Test
    fun aFailingConnectivityTriggerDoesNotDisableTheOwnerObserver() =
        runTest(timeout = AWAIT_TIMEOUT) {
            val database = openDatabase()
            database.seedLocalOwnerVehicle("vehicle-1")
            val ownerContext = FakeOwnerContext()
            val adoption =
                LocalOwnerAdoption(
                    dependencies =
                        testAppGraphDependencies(
                            databaseFactory = SingleHandleFactory(database),
                            // Throws rather than returning an error, so the connectivity trigger
                            // fails the way an unexpected provider fault would.
                            authClient = ThrowingAuthClient(),
                            ownerContext = ownerContext,
                            connectivityObserver = FakeConnectivityObserver(),
                        ),
                    database = database,
                )

            adoption.launchIn(backgroundScope)
            ownerContext.set(OwnerId(ADOPTING_UID))

            // The owner observer is the other trigger, and it still adopts.
            while (database.localOwnerRowCount() > 0L) yield()
            assertEquals(0L, database.localOwnerRowCount(), "the owner observer outlives the connectivity trigger")
        }

    private fun openDatabase(): AppDatabase {
        val created = factory.create()
        handle = created
        return created.database
    }

    private fun adoption(
        database: AppDatabase,
        ownerContext: OwnerContext,
        fault: AdoptionFault,
    ): LocalOwnerAdoption =
        LocalOwnerAdoption(
            dependencies =
                testAppGraphDependencies(
                    databaseFactory = SingleHandleFactory(database),
                    authClient = CountingAuthClient(ADOPTING_UID),
                    ownerContext = ownerContext,
                    connectivityObserver = FakeConnectivityObserver(),
                ),
            database = database,
            injectedAdoptRows = fault.transaction(database),
        )

    private fun gatedRepository(
        database: AppDatabase,
        ownerContext: OwnerContext,
        fault: AdoptionFault,
    ) = AdoptionGatedVehicleRepository(
        delegate =
            SqlDelightVehicleRepository(
                databaseAccess = VehicleDatabaseAccess(database),
                ownerContext = ownerContext,
                clock = FakeAppClock(),
                uuidGenerator = FakeUuidGenerator(),
            ),
        adoption = adoption(database, ownerContext, fault),
    )

    private companion object {
        const val ADOPTING_UID = "uid-adopting-owner"
    }
}

/** A transaction that fails while [failing] is set, so the same instance can then succeed. */
private class AdoptionFault(
    var failing: Boolean,
) {
    fun transaction(database: AppDatabase): suspend (String) -> Unit =
        { newOwnerId ->
            if (failing) error("adoption transaction failed") else database.adoptForTest(newOwnerId)
        }
}

private class ThrowingAuthClient : AuthClient {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)

    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> = error("provider fault")

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

private class ThrowingOwnerContext : OwnerContext {
    override val current: OwnerId get() = LOCAL_OWNER

    override fun observe(): Flow<OwnerId> = flow { error("owner observation failed") }
}

private class CountingAuthClient(
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

private class SingleHandleFactory(
    private val database: AppDatabase,
) : DatabaseFactory {
    override fun create(): DatabaseHandle =
        object : DatabaseHandle {
            override val database = this@SingleHandleFactory.database

            override fun close() = Unit
        }
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

private suspend fun AppDatabase.adoptForTest(newOwnerId: String) =
    DatabaseMutations(this)
        .adoptLocalOwner(
            newOwnerId = newOwnerId,
            vehicleOutboxPayload = { row -> """{"id":"${row.id}","ownerId":"${row.ownerId}"}""" },
            fuelEntryOutboxPayload = { row -> """{"id":"${row.id}","ownerId":"${row.ownerId}"}""" },
        )

private suspend fun AppDatabase.localOwnerRowCount(): Long =
    databaseQueries.countRowsOwnedBy(LOCAL_OWNER.value).awaitAsOne()

// Awaiting an effect that a missing behaviour never produces must fail fast, not hang the suite.
private val AWAIT_TIMEOUT = 10.seconds
