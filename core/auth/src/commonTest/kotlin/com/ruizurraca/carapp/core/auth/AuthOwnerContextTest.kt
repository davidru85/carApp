@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.ruizurraca.carapp.core.auth

import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthOwnerContextTest {
    @Test
    fun unknownAuthStateUsesTheLocalOwner() {
        val context = AuthOwnerContext(MutableStateFlow(AuthState.Unknown))

        assertEquals(LOCAL_OWNER, context.current)
    }

    @Test
    fun signedOutAuthStateUsesTheLocalOwner() {
        val context = AuthOwnerContext(MutableStateFlow(AuthState.SignedOut))

        assertEquals(LOCAL_OWNER, context.current)
    }

    @Test
    fun signedInAuthStateUsesTheSessionUid() {
        val context = AuthOwnerContext(MutableStateFlow(signedIn("owner-1")))

        assertEquals(OwnerId("owner-1"), context.current)
    }

    @Test
    fun currentOwnerTracksAuthStateChanges() {
        val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
        val context = AuthOwnerContext(authState)

        authState.value = signedIn("owner-1")

        assertEquals(OwnerId("owner-1"), context.current)
    }

    @Test
    fun ownerObservationTracksAuthStateChanges() =
        runTest {
            val authState = MutableStateFlow<AuthState>(AuthState.Unknown)
            val context = AuthOwnerContext(authState)
            val observed =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    context.observe().take(2).toList()
                }

            authState.value = signedIn("owner-1")

            assertEquals(listOf(LOCAL_OWNER, OwnerId("owner-1")), observed.await())
        }

    @Test
    fun ownerObservationDeduplicatesConsecutiveIdenticalOwnersAcrossStateTransitions() =
        runTest {
            val authState = MutableStateFlow<AuthState>(AuthState.Unknown)
            val context = AuthOwnerContext(authState)
            val observed = mutableListOf<OwnerId>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    context.observe().collect { owner ->
                        observed += owner
                        if (owner == OwnerId("owner-1")) {
                            this.cancel()
                        }
                    }
                }

            authState.value = AuthState.SignedOut
            authState.value = signedIn("owner-1")
            job.join()

            assertEquals(listOf(LOCAL_OWNER, OwnerId("owner-1")), observed)
        }
}

private fun signedIn(uid: String): AuthState =
    AuthState.SignedIn(
        AuthSession(
            uid = uid,
            isAnonymous = true,
            providers = setOf(AuthProvider.ANONYMOUS),
        ),
    )
