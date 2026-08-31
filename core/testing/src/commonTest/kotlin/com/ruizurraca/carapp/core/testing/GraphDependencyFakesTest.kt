package com.ruizurraca.carapp.core.testing

import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class GraphDependencyFakesTest {
    @Test
    fun inMemoryFactoryCreatesAnIsolatedDatabase() {
        val factory = InMemoryDatabaseFactory()
        try {
            assertNotNull(factory.create().database)
        } finally {
            factory.close()
        }
    }

    @Test
    fun authFakeReturnsItsConfiguredSession() =
        runTest {
            val session =
                AuthSession(
                    uid = "test-owner",
                    isAnonymous = true,
                    providers = setOf(AuthProvider.ANONYMOUS),
                )
            val fake =
                FakeAuthClient(
                    initialState = AuthState.SignedIn(session),
                    sessionResult = Outcome.Ok(session),
                )

            assertEquals(AuthState.SignedIn(session), fake.authState.value)
            assertEquals(Outcome.Ok(session), fake.signInAnonymously())
        }

    @Test
    fun remoteFakeReturnsAnEmptyFirstPageByDefault() =
        runTest {
            val result =
                FakeRemoteSyncSource().pullChanges(
                    ownerId = OwnerId("test-owner"),
                    entityType = EntityType.VEHICLE,
                    cursor = RemoteCursor.INITIAL,
                    limit = 200,
                )

            val page = assertIs<Outcome.Ok<RemotePage>>(result).value
            assertEquals(emptyList(), page.items)
            assertEquals(false, page.hasMore)
        }
}
