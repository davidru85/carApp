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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
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
                // `D-172`: the handle is released once the graph's work has finished, so a close that
                // races live bootstrap work releases on the scope's completion rather than inline.
                awaitCondition("the database handle to be released") { recordingFactory.closeCalls == 1 }

                assertEquals(1, recordingFactory.closeCalls)
            } finally {
                owningFactory.close()
            }
        }

    @Test
    fun graphCloseStopsAuthStateObservationOnAutoCloseableAuthClient() =
        runTest {
            val authFlow = MutableSharedFlow<AuthState>(replay = 1)
            val client =
                object : AuthClient, AutoCloseable {
                    private val observationJob = SupervisorJob()
                    private val scope = CoroutineScope(coroutineContext + observationJob)
                    private val state = MutableStateFlow<AuthState>(AuthState.Unknown)
                    override val authState: StateFlow<AuthState> = state

                    init {
                        scope.launch {
                            authFlow.collect { state.value = it }
                        }
                    }

                    override fun close() {
                        observationJob.cancel()
                    }

                    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> = error("unused")

                    override suspend fun signInWithCredential(
                        credential: NativeAuthCredential,
                        allowUidChange: Boolean,
                    ): Outcome<AuthSession, AuthError> = error("unused")

                    override suspend fun linkCredential(
                        credential: NativeAuthCredential,
                    ): Outcome<AuthSession, AuthError> = error("unused")

                    override suspend fun reauthenticate(
                        credential: NativeAuthCredential,
                    ): Outcome<AuthSession, AuthError> = error("unused")

                    override suspend fun signOut(): Outcome<Unit, AuthError> = error("unused")

                    override suspend fun deleteAccount(): Outcome<Unit, AuthError> = error("unused")
                }

            val dependencies = testAppGraphDependencies(authClient = client)
            val graph = buildAppGraph(isDebugBuild = true, providers = testAppProviders(dependencies))

            val session1 = AuthSession(uid = "user-1", isAnonymous = false, providers = emptySet())
            authFlow.emit(AuthState.SignedIn(session1))
            runCurrent()
            assertEquals(AuthState.SignedIn(session1), client.authState.value)

            graph.close()

            val session2 = AuthSession(uid = "user-2", isAnonymous = false, providers = emptySet())
            authFlow.emit(AuthState.SignedIn(session2))
            runCurrent()

            assertEquals(AuthState.SignedIn(session1), client.authState.value)
        }

    private suspend fun TestScope.assertGraphCloseReleasesDatabase(closeGraph: (AppGraph, DispatcherProvider) -> Unit) {
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
            // `D-172`: the handle is released when the graph's work has finished, not inline, because
            // releasing it while live work is mid-call is the defect this story fixes. The graph's own
            // bootstrap work runs on real SQLite here, so the wait is real-time and bounded rather
            // than virtual - a virtual advance would expire before that work gets CPU.
            awaitCondition("the database handle to be released") { recordingFactory.closeCalls == 1 }
            assertEquals(1, recordingFactory.closeCalls)
        } finally {
            owningFactory.close()
        }
    }
}
