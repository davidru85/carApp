@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.core.testing

import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.AuthToken
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.auth.OrphanCleanupClient
import com.ruizurraca.carapp.core.auth.OrphanCleanupTicket
import com.ruizurraca.carapp.core.auth.TokenProvider
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.native.HiddenFromObjC

/** Creates isolated SQLDelight databases backed by the bundled in-memory SQLite driver. */
expect class InMemoryDatabaseFactory() : DatabaseFactory {
    override fun create(): DatabaseHandle

    fun close()
}

/** Auth fake with deterministic, constructor-configurable results and state. */
class FakeAuthClient(
    initialState: AuthState = AuthState.SignedOut,
    var sessionResult: Outcome<AuthSession, AuthError> = Outcome.Err(AuthError.ProviderUnavailable),
    var unitResult: Outcome<Unit, AuthError> = Outcome.Err(AuthError.ProviderUnavailable),
) : AuthClient {
    private val mutableAuthState = MutableStateFlow(initialState)

    override val authState: StateFlow<AuthState> = mutableAuthState

    fun setAuthState(state: AuthState) {
        mutableAuthState.value = state
    }

    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> = sessionResult

    override suspend fun signInWithCredential(
        credential: NativeAuthCredential,
        allowUidChange: Boolean,
    ): Outcome<AuthSession, AuthError> = sessionResult

    override suspend fun linkCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        sessionResult

    override suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        sessionResult

    override suspend fun signOut(): Outcome<Unit, AuthError> = unitResult

    override suspend fun deleteAccount(): Outcome<Unit, AuthError> = unitResult
}

/** Token fake whose result is explicitly controlled by the test. */
class FakeTokenProvider(
    var result: Outcome<AuthToken, AuthError> = Outcome.Err(AuthError.TokenExpired),
) : TokenProvider {
    override suspend fun getIdToken(forceRefresh: Boolean): Outcome<AuthToken, AuthError> = result
}

/** Orphan-cleanup fake whose results are explicitly controlled by the test. */
@HiddenFromObjC
class FakeOrphanCleanupClient(
    var issueResult: Outcome<OrphanCleanupTicket, AuthError> = Outcome.Err(AuthError.ProviderUnavailable),
    var deleteResult: Outcome<Unit, AuthError> = Outcome.Err(AuthError.ProviderUnavailable),
) : OrphanCleanupClient {
    override suspend fun issueOrphanCleanupTicket(): Outcome<OrphanCleanupTicket, AuthError> = issueResult

    override suspend fun deleteOrphanedAnonymousAccount(ticket: OrphanCleanupTicket): Outcome<Unit, AuthError> =
        deleteResult
}

/** Remote fake with an empty successful pull and an unavailable push by default. */
class FakeRemoteSyncSource(
    var pushResult: Outcome<RemoteAck, RemoteError> = Outcome.Err(RemoteError.Unavailable),
    var pullResult: Outcome<RemotePage, RemoteError> =
        Outcome.Ok(
            RemotePage(
                items = emptyList(),
                nextCursor = RemoteCursor.INITIAL,
                hasMore = false,
            ),
        ),
) : RemoteSyncSource {
    override suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError> = pushResult

    override suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError> = pullResult
}

internal class TrackedDatabaseHandles {
    private val handles = mutableListOf<DatabaseHandle>()

    fun track(handle: DatabaseHandle): DatabaseHandle {
        handles += handle
        return handle
    }

    /**
     * Releases every tracked handle **without blocking the caller**.
     *
     * `E1-18`: `SqlDriverDatabaseHandle.close()` takes the driver's writer lock through a
     * `runBlocking`. If the close runs on - or is awaited from - the thread whose scheduler still has
     * graph-owned database work suspended on it, the lock holder can never be resumed and the close
     * waits forever; the test then produces no result at all, which is exactly what killed the
     * `shared-tests` and `provider-decoupling` steps at their timeouts. A live stack capture pinned
     * the form: `LocalOwnerAdoptionTest.tearDown` -> `SqlDriverDatabaseHandle.close` -> `runBlocking`,
     * parked on the test-scheduler thread.
     *
     * Offloading and **not** awaiting is the shape that works here: the caller returns to its
     * scheduler, the suspended transaction drains, releases the writer, and the close completes on the
     * worker. Awaiting it - even from a worker - would re-block the caller and restore the deadlock.
     * Callers that assert on the database do so before this point, so no assertion depends on the
     * close having finished.
     */
    fun close() {
        val closing = handles.toList()
        handles.clear()
        if (closing.isEmpty()) return
        CLOSE_SCOPE.launch { closing.forEach(DatabaseHandle::close) }
    }

    private companion object {
        /**
         * The offload target. A scope rather than a platform executor, because this class is
         * `commonMain` and `ExecutorService` does not exist on Kotlin/Native. Closes are queued and
         * serialized by the single dispatcher, and nothing awaits them.
         */
        val CLOSE_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
