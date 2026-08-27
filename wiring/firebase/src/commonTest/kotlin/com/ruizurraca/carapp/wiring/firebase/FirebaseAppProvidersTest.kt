package com.ruizurraca.carapp.wiring.firebase

import com.ruizurraca.carapp.SwiftAppGraph
import com.ruizurraca.carapp.buildAppGraph
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.database.createStagedDatabaseFactory
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class FirebaseAppProvidersTest {
    @Test
    fun providerFactoryBuildsTheSharedGraphWithoutGlobalRegistration() {
        val graph = buildAppGraph(isDebugBuild = true, providers = firebaseAppProviders())

        assertIs<SwiftAppGraph>(graph)
    }

    @Test
    fun providerFactoryKeepsRealBoundariesAndDerivesTheCurrentOwner() {
        val authClient = MutableAuthClient()
        val remoteSyncSource = RecordingRemoteSyncSource()
        val databaseFactory = createStagedDatabaseFactory()

        val providers =
            firebaseAppProviders(
                databaseFactory = databaseFactory,
                authClient = authClient,
                remoteSyncSource = remoteSyncSource,
            )

        assertSame(databaseFactory, providers.databaseFactory)
        assertSame(authClient, providers.authClient)
        assertSame(remoteSyncSource, providers.remoteSyncSource)
        assertEquals(LOCAL_OWNER, providers.ownerContext.current)

        authClient.state.value =
            AuthState.SignedIn(
                AuthSession(
                    uid = "anonymous-owner",
                    isAnonymous = true,
                    providers = setOf(AuthProvider.ANONYMOUS),
                ),
            )

        assertEquals(OwnerId("anonymous-owner"), providers.ownerContext.current)
    }
}

private class MutableAuthClient : AuthClient {
    val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState = state

    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> = unavailable()

    override suspend fun signInWithCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        unavailable()

    override suspend fun linkCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        unavailable()

    override suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        unavailable()

    override suspend fun signOut(): Outcome<Unit, AuthError> = unavailable()

    override suspend fun deleteAccount(): Outcome<Unit, AuthError> = unavailable()
}

private class RecordingRemoteSyncSource : RemoteSyncSource {
    override suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError> = Outcome.Err(RemoteError.Unavailable)

    override suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError> = Outcome.Err(RemoteError.Unavailable)
}

private fun <T> unavailable(): Outcome<T, AuthError> = Outcome.Err(AuthError.ProviderUnavailable)
