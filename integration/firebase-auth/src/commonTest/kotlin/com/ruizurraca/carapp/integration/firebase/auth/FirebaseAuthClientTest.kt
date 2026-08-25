package com.ruizurraca.carapp.integration.firebase.auth

import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FirebaseAuthClientTest {
    @Test
    fun retainedAnonymousUserIsAvailableWhenTheClientIsCreated() {
        val gateway = FakeFirebaseAuthGateway(currentUser = anonymousUser("retained-uid"))

        val client = FirebaseAuthClient(gateway)

        assertEquals(
            AuthState.SignedIn(
                AuthSession(
                    uid = "retained-uid",
                    isAnonymous = true,
                    providers = setOf(AuthProvider.ANONYMOUS),
                ),
            ),
            client.authState.value,
        )
    }

    @Test
    fun anonymousSignInReturnsAndPublishesTheFirebaseSession() =
        runTest {
            val gateway =
                FakeFirebaseAuthGateway(
                    currentUser = null,
                    signedInUser = anonymousUser("new-uid"),
                )
            val client = FirebaseAuthClient(gateway)

            val result = client.signInAnonymously()

            val session = assertIs<Outcome.Ok<AuthSession>>(result).value
            assertEquals("new-uid", session.uid)
            assertEquals(true, session.isAnonymous)
            assertEquals(setOf(AuthProvider.ANONYMOUS), session.providers)
            assertEquals(AuthState.SignedIn(session), client.authState.value)
        }
}

private class FakeFirebaseAuthGateway(
    override val currentUser: FirebaseAuthUser?,
    private val signedInUser: FirebaseAuthUser? = currentUser,
) : FirebaseAuthGateway {
    override suspend fun signInAnonymously(): FirebaseAuthUser = checkNotNull(signedInUser)
}

private fun anonymousUser(uid: String): FirebaseAuthUser =
    FirebaseAuthUser(
        uid = uid,
        isAnonymous = true,
        providerIds = emptySet(),
    )
