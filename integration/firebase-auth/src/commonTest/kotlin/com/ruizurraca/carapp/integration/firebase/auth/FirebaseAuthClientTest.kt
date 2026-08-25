package com.ruizurraca.carapp.integration.firebase.auth

import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.AuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals

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
}

private class FakeFirebaseAuthGateway(
    override val currentUser: FirebaseAuthUser?,
) : FirebaseAuthGateway

private fun anonymousUser(uid: String): FirebaseAuthUser =
    FirebaseAuthUser(
        uid = uid,
        isAnonymous = true,
        providerIds = emptySet(),
    )
