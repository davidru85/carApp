package com.ruizurraca.carapp.integration.firebase.auth

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseException
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.code
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.functions.httpsCallable

/** The `D-23` callable as one provider-free call, so the invoker's mapping is testable. */
internal fun interface AccountDeletionCallable {
    suspend fun deleteAccount(targetUid: String): Map<String, String>
}

/**
 * `docs/CONTRACTS.md §11.1` and `§11.5`: the client side of the `D-23` server operation. The
 * Functions SDK authenticates the call as the current Firebase user; the payload names that user as
 * `targetUid`, and the server rejects any other value. Caller rejection maps to `PermissionDenied`,
 * connectivity to `Network`, and every other failure or an unexpected status to
 * `AccountDeletionRemoteFailed`.
 */
internal class CallableAccountDeletionInvoker(
    private val callable: AccountDeletionCallable,
    private val currentUid: () -> String?,
) : AccountDeletionInvoker {
    override suspend fun invokeDeletion(idToken: String) {
        val targetUid = currentUid() ?: throw FirebaseAuthGatewayException.PermissionDenied()
        val response =
            try {
                callable.deleteAccount(targetUid)
            } catch (failure: FirebaseFunctionsGatewayException) {
                throw failure.failure.toAccountDeletionFailure()
            }
        if (response[STATUS_FIELD] != ACCOUNT_DELETED_STATUS) {
            throw FirebaseAuthGatewayException.AccountDeletionRemoteFailed()
        }
    }

    private companion object {
        const val STATUS_FIELD = "status"
        const val ACCOUNT_DELETED_STATUS = "ACCOUNT_DELETED"
    }
}

/** The GitLive callable. `Firebase.functions` is resolved per call, never at construction. */
internal class GitLiveAccountDeletionCallable : AccountDeletionCallable {
    // The provider failure is reduced to its code: no message, cause or payload leaves this class.
    @Suppress("SwallowedException")
    override suspend fun deleteAccount(targetUid: String): Map<String, String> =
        try {
            Firebase
                .functions(FUNCTIONS_REGION)
                .httpsCallable(DELETE_ACCOUNT_FUNCTION)(mapOf(TARGET_UID_FIELD to targetUid))
                .data<Map<String, String>>()
        } catch (failure: FirebaseFunctionsException) {
            throw FirebaseFunctionsGatewayException(failure.code.toGatewayFailure())
        } catch (failure: FirebaseException) {
            throw FirebaseFunctionsGatewayException(FirebaseFunctionsFailure.UNKNOWN)
        }

    private companion object {
        const val FUNCTIONS_REGION = "europe-west1"
        const val DELETE_ACCOUNT_FUNCTION = "deleteAccount"
        const val TARGET_UID_FIELD = "targetUid"
    }
}

private fun FirebaseFunctionsFailure.toAccountDeletionFailure(): FirebaseAuthGatewayException =
    when (this) {
        FirebaseFunctionsFailure.UNAVAILABLE,
        FirebaseFunctionsFailure.DEADLINE_EXCEEDED,
        -> FirebaseAuthGatewayException.Network()

        FirebaseFunctionsFailure.UNAUTHENTICATED,
        FirebaseFunctionsFailure.PERMISSION_DENIED,
        -> FirebaseAuthGatewayException.PermissionDenied()

        FirebaseFunctionsFailure.FAILED_PRECONDITION,
        FirebaseFunctionsFailure.UNKNOWN,
        -> FirebaseAuthGatewayException.AccountDeletionRemoteFailed()
    }
