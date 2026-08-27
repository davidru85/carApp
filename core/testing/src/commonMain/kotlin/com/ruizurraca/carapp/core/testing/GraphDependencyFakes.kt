package com.ruizurraca.carapp.core.testing

import app.cash.sqldelight.db.SqlDriver
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.AuthToken
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.auth.TokenProvider
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Creates isolated SQLDelight databases backed by the bundled in-memory SQLite driver. */
expect class InMemoryDatabaseFactory() : DatabaseFactory {
    override fun create(): AppDatabase

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

    override suspend fun signInWithCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        sessionResult

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

internal class TrackedInMemoryDatabases {
    private val drivers = mutableListOf<SqlDriver>()

    fun create(driver: SqlDriver): AppDatabase {
        drivers += driver
        return AppDatabase(driver)
    }

    fun close() {
        drivers.forEach(SqlDriver::close)
        drivers.clear()
    }
}
