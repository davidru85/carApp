package com.ruizurraca.carapp.integration.firebase.auth

import com.ruizurraca.carapp.core.auth.OrphanCleanupClient
import com.ruizurraca.carapp.core.auth.OrphanCleanupTicket
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.Outcome

/** Behavior-free RED seam for the E3-11 callable operations. */
class FirebaseOrphanCleanupClient internal constructor(
    private val gateway: FirebaseFunctionsGateway,
) : OrphanCleanupClient {
    override suspend fun issueOrphanCleanupTicket(): Outcome<OrphanCleanupTicket, AuthError> =
        Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun deleteOrphanedAnonymousAccount(
        ticket: OrphanCleanupTicket,
    ): Outcome<Unit, AuthError> = Outcome.Err(AuthError.ProviderUnavailable)
}

internal interface FirebaseFunctionsGateway {
    suspend fun issueOrphanCleanupTicket(): Map<String, String>

    suspend fun deleteOrphanedAnonymousAccount(ticket: String)
}

internal enum class FirebaseFunctionsFailure {
    UNAVAILABLE,
    DEADLINE_EXCEEDED,
    UNAUTHENTICATED,
    PERMISSION_DENIED,
    FAILED_PRECONDITION,
    UNKNOWN,
}

internal class FirebaseFunctionsGatewayException(
    val failure: FirebaseFunctionsFailure,
) : Exception()
