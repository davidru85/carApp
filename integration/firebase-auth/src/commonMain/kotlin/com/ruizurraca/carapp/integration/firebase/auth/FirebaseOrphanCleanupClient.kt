@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.integration.firebase.auth

import com.ruizurraca.carapp.core.auth.OrphanCleanupClient
import com.ruizurraca.carapp.core.auth.OrphanCleanupTicket
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.Outcome
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseException
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import dev.gitlive.firebase.functions.code
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.functions.httpsCallable
import kotlin.native.HiddenFromObjC

/** Firebase callable adapter for the E3-11 ticket issue and orphan deletion operations. */
@HiddenFromObjC
class FirebaseOrphanCleanupClient internal constructor(
    private val gateway: FirebaseFunctionsGateway,
) : OrphanCleanupClient {
    constructor() : this(GitLiveFirebaseFunctionsGateway())

    override suspend fun issueOrphanCleanupTicket(): Outcome<OrphanCleanupTicket, AuthError> =
        runCallable {
            val ticket = gateway.issueOrphanCleanupTicket()[TICKET_FIELD]
            require(ticket != null && TICKET_PATTERN.matches(ticket))
            OrphanCleanupTicket(ticket)
        }

    override suspend fun deleteOrphanedAnonymousAccount(ticket: OrphanCleanupTicket): Outcome<Unit, AuthError> =
        runCallable {
            require(TICKET_PATTERN.matches(ticket.value))
            gateway.deleteOrphanedAnonymousAccount(ticket.value)
        }

    @Suppress("SwallowedException")
    private suspend fun <T> runCallable(block: suspend () -> T): Outcome<T, AuthError> =
        try {
            Outcome.Ok(block())
        } catch (failure: FirebaseFunctionsGatewayException) {
            Outcome.Err(failure.failure.toAuthError())
        } catch (failure: IllegalArgumentException) {
            Outcome.Err(AuthError.Unknown)
        }

    private companion object {
        const val TICKET_FIELD = "cleanupTicket"
        val TICKET_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")
    }
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

private class GitLiveFirebaseFunctionsGateway : FirebaseFunctionsGateway {
    private val functions = Firebase.functions(FUNCTIONS_REGION)

    override suspend fun issueOrphanCleanupTicket(): Map<String, String> =
        invoke { functions.httpsCallable(ISSUE_FUNCTION)().data() }

    override suspend fun deleteOrphanedAnonymousAccount(ticket: String): Unit =
        invoke {
            val response =
                functions
                    .httpsCallable(DELETE_FUNCTION)(mapOf(TICKET_FIELD to ticket))
                    .data<Map<String, String>>()
            require(response[STATUS_FIELD] == ORPHAN_DELETED_STATUS)
        }

    @Suppress("SwallowedException")
    private suspend fun <T> invoke(block: suspend () -> T): T =
        try {
            block()
        } catch (failure: FirebaseFunctionsException) {
            throw FirebaseFunctionsGatewayException(failure.code.toGatewayFailure())
        } catch (failure: FirebaseException) {
            throw FirebaseFunctionsGatewayException(FirebaseFunctionsFailure.UNKNOWN)
        }

    private companion object {
        const val FUNCTIONS_REGION = "europe-west1"
        const val ISSUE_FUNCTION = "issueOrphanCleanupTicket"
        const val DELETE_FUNCTION = "deleteOrphanedAnonymousAccount"
        const val TICKET_FIELD = "cleanupTicket"
        const val STATUS_FIELD = "status"
        const val ORPHAN_DELETED_STATUS = "ORPHANED_ANONYMOUS_ACCOUNT_DELETED"
    }
}

private fun FunctionsExceptionCode.toGatewayFailure(): FirebaseFunctionsFailure =
    when (name) {
        "UNAVAILABLE" -> FirebaseFunctionsFailure.UNAVAILABLE
        "DEADLINE_EXCEEDED" -> FirebaseFunctionsFailure.DEADLINE_EXCEEDED
        "UNAUTHENTICATED" -> FirebaseFunctionsFailure.UNAUTHENTICATED
        "PERMISSION_DENIED" -> FirebaseFunctionsFailure.PERMISSION_DENIED
        "FAILED_PRECONDITION" -> FirebaseFunctionsFailure.FAILED_PRECONDITION
        else -> FirebaseFunctionsFailure.UNKNOWN
    }

private fun FirebaseFunctionsFailure.toAuthError(): AuthError =
    if (this == FirebaseFunctionsFailure.UNAVAILABLE || this == FirebaseFunctionsFailure.DEADLINE_EXCEEDED) {
        AuthError.NetworkUnavailable
    } else if (
        this == FirebaseFunctionsFailure.UNAUTHENTICATED ||
        this == FirebaseFunctionsFailure.PERMISSION_DENIED ||
        this == FirebaseFunctionsFailure.FAILED_PRECONDITION
    ) {
        AuthError.PermissionDenied
    } else {
        AuthError.Unknown
    }
