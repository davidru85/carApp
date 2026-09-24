package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthOwnerContext
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.OrphanCleanupClient
import com.ruizurraca.carapp.core.auth.OrphanCleanupTicket
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.database.AccountConversionDatabaseAccess
import com.ruizurraca.carapp.core.database.AccountConversionPhase
import com.ruizurraca.carapp.core.database.AccountConversionStore
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemoteDocument
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import com.ruizurraca.carapp.core.testing.FakeAuthClient
import com.ruizurraca.carapp.core.testing.FakeConnectivityObserver
import com.ruizurraca.carapp.core.testing.FakeOrphanCleanupClient
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AccountConversionAppGraphTest {
    @Test
    fun malformedRemoteDocumentDoesNotEscapeResumePendingOnGraphScope() =
        runTest(timeout = 10.seconds) {
            val owningFactory = InMemoryDatabaseFactory()
            val handle = owningFactory.create()
            val store = AccountConversionDatabaseAccess(handle.database)
            store.captureIfAbsent(ANONYMOUS_UID, { "" }, { "" })
            store.saveCleanupTicket(RAW_TICKET)
            store.savePermanentUid(PERMANENT_UID)
            assertEquals(AccountConversionPhase.SESSION_SWITCHED, store.load()?.phase)
            val authClient =
                FakeAuthClient(
                    initialState = AuthState.SignedOut,
                )
            val remote = MalformedConversionRemoteSource()
            val graph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers =
                        testAppProviders(
                            testAppGraphDependencies(
                                databaseFactory = ExistingDatabaseFactory(handle),
                                authClient = authClient,
                                orphanCleanupClient =
                                    FakeOrphanCleanupClient(deleteResult = Outcome.Ok(Unit)),
                                ownerContext = FakeOwnerContext(OwnerId(PERMANENT_UID)),
                                remoteSyncSource = remote,
                                connectivityObserver = FakeConnectivityObserver(initiallyOnline = false),
                            ),
                        ),
                )

            try {
                authClient.setAuthState(
                    AuthState.SignedIn(
                        AuthSession(PERMANENT_UID, false, setOf(AuthProvider.GOOGLE)),
                    ),
                )
                assertEquals(PERMANENT_UID, (authClient.authState.value as AuthState.SignedIn).session.uid)
                awaitCondition("the conversion pull to start") { remote.pullCalls > 0 }
                advanceGraphWork()

                assertEquals(AccountConversionPhase.SESSION_SWITCHED, store.load()?.phase)
                assertEquals(1, remote.pullCalls)
            } finally {
                graph.close()
                owningFactory.close()
            }
        }

    @Test
    fun ownerChangedRecoveryNeverPullsWhileTheAccountConversionMarkerExists() =
        runTest(timeout = 10.seconds) {
            val owningFactory = InMemoryDatabaseFactory()
            val handle = owningFactory.create()
            val store = AccountConversionDatabaseAccess(handle.database)
            store.captureIfAbsent(ANONYMOUS_UID, { "" }, { "" })
            store.saveCleanupTicket(RAW_TICKET)
            store.savePermanentUid(PERMANENT_UID)
            store.markRemoteReplaced()
            store.replaceLocalSnapshot(
                permanentUid = PERMANENT_UID,
                vehicles = emptyList(),
                fuelEntries = emptyList(),
            )
            assertEquals(AccountConversionPhase.LOCAL_REPLACED, store.load()?.phase)

            val authClient = FakeAuthClient(initialState = AuthState.SignedOut)
            val cleanupClient = BlockingOrphanCleanupClient()
            val remote = MarkerAwareRemoteSource(store)
            val graph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers =
                        testAppProviders(
                            testAppGraphDependencies(
                                databaseFactory = ExistingDatabaseFactory(handle),
                                authClient = authClient,
                                orphanCleanupClient = cleanupClient,
                                ownerContext = AuthOwnerContext(authClient.authState),
                                remoteSyncSource = remote,
                                connectivityObserver = FakeConnectivityObserver(initiallyOnline = true),
                            ),
                        ),
                )

            try {
                authClient.setAuthState(
                    AuthState.SignedIn(
                        AuthSession(PERMANENT_UID, false, setOf(AuthProvider.GOOGLE)),
                    ),
                )
                awaitCondition("the orphan cleanup to hold the conversion marker") {
                    cleanupClient.deleteCalls == 1
                }
                advanceGraphWork()
                assertEquals(
                    AccountConversionPhase.LOCAL_REPLACED,
                    store.load()?.phase,
                    "the durable replacement marker is still present while cleanup is held",
                )

                cleanupClient.allowDelete()
                awaitCondition("the conversion marker to clear") { store.load() == null }
                awaitCondition("owner recovery to reach the remote once conversion has settled") {
                    remote.pullCalls > 0
                }

                assertEquals(
                    emptyList(),
                    remote.pullsWhileTheMarkerExisted,
                    "CONTRACTS.md 11.3 forbids a normal recovery pull while the marker exists",
                )
            } finally {
                graph.close()
                owningFactory.close()
            }
        }
}

private class BlockingOrphanCleanupClient : OrphanCleanupClient {
    private val deletionAllowed = CompletableDeferred<Unit>()

    var deleteCalls = 0
        private set

    override suspend fun issueOrphanCleanupTicket(): Outcome<OrphanCleanupTicket, AuthError> =
        Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun deleteOrphanedAnonymousAccount(ticket: OrphanCleanupTicket): Outcome<Unit, AuthError> {
        deleteCalls += 1
        deletionAllowed.await()
        return Outcome.Ok(Unit)
    }

    fun allowDelete() {
        check(deletionAllowed.complete(Unit)) { "the orphan deletion was already released" }
    }
}

/**
 * Records the conversion phase observed at the moment of each pull, so the prohibition is asserted
 * about the pull itself rather than about a sampled instant. A cycle that reaches the remote during
 * the replacement window is caught whatever the interleaving of the graph's coroutines and the
 * driver's real work.
 */
private class MarkerAwareRemoteSource(
    private val store: AccountConversionStore,
) : RemoteSyncSource {
    var pullCalls = 0
        private set

    val pullsWhileTheMarkerExisted = mutableListOf<AccountConversionPhase>()

    override suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError> = Outcome.Err(RemoteError.Unavailable)

    override suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError> {
        pullCalls += 1
        store.load()?.let { operation -> pullsWhileTheMarkerExisted += operation.phase }
        return Outcome.Ok(
            RemotePage(
                items = emptyList(),
                nextCursor = cursor,
                hasMore = false,
            ),
        )
    }
}

private class ExistingDatabaseFactory(
    private val handle: DatabaseHandle,
) : DatabaseFactory {
    override fun create(): DatabaseHandle =
        object : DatabaseHandle {
            override val database = handle.database

            override fun close() = Unit
        }
}

private class MalformedConversionRemoteSource : RemoteSyncSource {
    var pullCalls = 0
        private set

    override suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError> = Outcome.Err(RemoteError.Unavailable)

    override suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError> {
        pullCalls += 1
        val document =
            RemoteDocument(
                entityType = EntityType.VEHICLE,
                documentId = EntityId("remote-vehicle"),
                serverUpdatedAt = Instant.fromEpochMilliseconds(100),
                rawJson = """{"id":"remote-vehicle","schemaVersion":1}""",
            )
        return Outcome.Ok(
            RemotePage(
                items = listOf(document),
                nextCursor = RemoteCursor(document.serverUpdatedAt, document.documentId),
                hasMore = false,
            ),
        )
    }
}

private const val ANONYMOUS_UID = "anonymous-owner"
private const val PERMANENT_UID = "permanent-owner"
private const val RAW_TICKET = "0123456789012345678901234567890123456789012"
