package com.ruizurraca.carapp.integration.firebase.auth

import com.ruizurraca.carapp.core.auth.OrphanCleanupTicket
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FirebaseOrphanCleanupClientTest {
    @Test
    fun issueCallsTheRegionalCallableAndReturnsTheOpaqueTicket() =
        runTest {
            val gateway = RecordingFunctionsGateway(issueResult = mapOf("cleanupTicket" to RAW_TICKET))
            val client = FirebaseOrphanCleanupClient(gateway)

            val result = client.issueOrphanCleanupTicket()

            assertEquals(OrphanCleanupTicket(RAW_TICKET), assertIs<Outcome.Ok<OrphanCleanupTicket>>(result).value)
            assertEquals(listOf("issueOrphanCleanupTicket"), gateway.calls)
        }

    @Test
    fun deleteSendsOnlyTheTicketToTheRegionalCallable() =
        runTest {
            val gateway = RecordingFunctionsGateway()
            val client = FirebaseOrphanCleanupClient(gateway)

            val result = client.deleteOrphanedAnonymousAccount(OrphanCleanupTicket(RAW_TICKET))

            assertIs<Outcome.Ok<Unit>>(result)
            assertEquals(listOf("deleteOrphanedAnonymousAccount:$RAW_TICKET"), gateway.calls)
        }

    @Test
    fun providerFailuresStayInsideTheClosedAuthErrorBoundary() =
        runTest {
            val gateway = RecordingFunctionsGateway(failure = FirebaseFunctionsFailure.UNAVAILABLE)
            val client = FirebaseOrphanCleanupClient(gateway)

            val result = client.issueOrphanCleanupTicket()

            assertEquals(AuthError.NetworkUnavailable, assertIs<Outcome.Err<AuthError>>(result).error)
        }

    private companion object {
        const val RAW_TICKET = "0123456789012345678901234567890123456789012"
    }
}

private class RecordingFunctionsGateway(
    private val issueResult: Map<String, String> = emptyMap(),
    private val failure: FirebaseFunctionsFailure? = null,
) : FirebaseFunctionsGateway {
    val calls = mutableListOf<String>()

    override suspend fun issueOrphanCleanupTicket(): Map<String, String> {
        calls += "issueOrphanCleanupTicket"
        failure?.let { throw FirebaseFunctionsGatewayException(it) }
        return issueResult
    }

    override suspend fun deleteOrphanedAnonymousAccount(ticket: String) {
        calls += "deleteOrphanedAnonymousAccount:$ticket"
        failure?.let { throw FirebaseFunctionsGatewayException(it) }
    }
}
