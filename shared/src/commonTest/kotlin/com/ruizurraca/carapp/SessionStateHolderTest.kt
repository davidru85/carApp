package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.testing.FakeAuthClient
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionStateHolderTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun anonymousSignInPublishesTheFirebaseSessionPhase() =
        runTest {
            val session =
                AuthSession(
                    uid = "anonymous-owner",
                    isAnonymous = true,
                    providers = setOf(AuthProvider.ANONYMOUS),
                )
            val authClient = FakeAuthClient(sessionResult = Outcome.Ok(session))
            val graph = SwiftAppGraph(testAppGraphDependencies(authClient = authClient))
            val stateHolder = graph.sessionStateHolder()

            stateHolder.startAnonymousSignIn()
            advanceUntilIdle()

            assertEquals(SessionPhase.ANONYMOUS, stateHolder.state.value.phase)
            assertEquals(listOf(AuthProvider.ANONYMOUS), stateHolder.state.value.providers)
            assertEquals(false, stateHolder.state.value.isBusy)
            assertEquals(null, stateHolder.state.value.message)
            graph.close()
        }
}
