package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.DispatcherProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppGraphCloseTest {
    @Test
    fun kotlinGraphCloseReleasesItsDatabaseConnection() =
        runTest {
            assertGraphCloseReleasesDatabase { graph, _ ->
                graph.close()
                graph.close()
            }
        }

    @Test
    fun swiftGraphCloseTransitivelyReleasesItsDatabaseConnection() =
        runTest {
            assertGraphCloseReleasesDatabase { graph, dispatchers ->
                val swiftGraph = wrapAppGraphForSwift(graph, dispatchers)
                swiftGraph.close()
                swiftGraph.close()
            }
        }

    @Test
    fun kotlinGraphCanCloseImmediatelyWhileSettingsBootstrapStartsWithoutAConsumer() =
        runTest {
            val owningFactory = InMemoryDatabaseFactory()
            val recordingFactory = RecordingDatabaseFactory(owningFactory)
            val graph =
                buildAppGraph(
                    isDebugBuild = true,
                    providers =
                        testAppProviders(
                            testAppGraphDependencies(databaseFactory = recordingFactory),
                        ),
                )

            try {
                graph.close()
                advanceUntilIdle()

                assertEquals(1, recordingFactory.closeCalls)
            } finally {
                owningFactory.close()
            }
        }

    @Test
    fun graphCloseStopsAuthStateObservationOnAutoCloseableAuthClient() =
        runTest {
            val observationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val authFlow = MutableSharedFlow<AuthState>(replay = 1)
            var observationActive = true

            val client =
                object : AuthClient, AutoCloseable {
                    private val state = MutableStateFlow<AuthState>(AuthState.Unknown)
                    override val authState: StateFlow<AuthState> = state
                    private val job =
                        observationScope.launch {
                            try {
                                authFlow.collect { state.value = it }
                            } finally {
                                observationActive = false
                            }
                        }

                    override fun close() {
                        job.cancel()
                    }

                    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> =
                        error("unused")

                    override suspend fun signInWithCredential(
                        credential: NativeAuthCredential,
                        allowUidChange: Boolean,
                    ): Outcome<AuthSession, AuthError> = error("unused")

                    override suspend fun linkCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
                        error("unused")

                    override suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
                        error("unused")

                    override suspend fun signOut(): Outcome<Unit, AuthError> = error("unused")

                    override suspend fun deleteAccount(): Outcome<Unit, AuthError> = error("unused")
                }

            val dependencies = testAppGraphDependencies(authClient = client)
            val graph = buildAppGraph(isDebugBuild = true, providers = testAppProviders(dependencies))

            assertTrue(observationActive)
            graph.close()
            advanceUntilIdle()

            assertFalse(observationActive)
        }

    private fun assertGraphCloseReleasesDatabase(closeGraph: (AppGraph, DispatcherProvider) -> Unit) {
        val owningFactory = InMemoryDatabaseFactory()
        val recordingFactory = RecordingDatabaseFactory(owningFactory)
        val dependencies =
            testAppGraphDependencies(
                databaseFactory = recordingFactory,
            )
        val graph =
            buildAppGraph(
                isDebugBuild = true,
                providers = testAppProviders(dependencies),
            )

        try {
            closeGraph(graph, dependencies.dispatchers)
            assertEquals(1, recordingFactory.closeCalls)
        } finally {
            owningFactory.close()
        }
    }
}
