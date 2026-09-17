package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.database.AccountConversionDatabaseAccess
import com.ruizurraca.carapp.core.database.AccountConversionPhase
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
