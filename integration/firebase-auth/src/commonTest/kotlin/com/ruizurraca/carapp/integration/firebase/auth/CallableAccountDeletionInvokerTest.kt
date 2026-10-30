package com.ruizurraca.carapp.integration.firebase.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * `docs/CONTRACTS.md §11.1` / `§11.5`: `deleteAccount` is the 2nd gen callable in `europe-west1`; the
 * payload names the signed-in user as `targetUid`, success is `{ status: "ACCOUNT_DELETED" }`, caller
 * rejection is `PermissionDenied`, connectivity is `Network`, everything else is a remote failure.
 */
class CallableAccountDeletionInvokerTest {
    @Test
    fun theDeletionNamesTheSignedInUserAsItsTarget() =
        runTest {
            val callable = RecordingDeletionCallable()
            val invoker = CallableAccountDeletionInvoker(callable = callable, currentUid = { "owner-uid" })

            invoker.invokeDeletion("fresh-id-token")

            assertEquals(listOf("owner-uid"), callable.targets)
        }

    @Test
    fun noSignedInUserIsRefusedWithoutCallingTheServer() =
        runTest {
            val callable = RecordingDeletionCallable()
            val invoker = CallableAccountDeletionInvoker(callable = callable, currentUid = { null })

            val failure = runCatching { invoker.invokeDeletion("fresh-id-token") }.exceptionOrNull()

            assertIs<FirebaseAuthGatewayException.PermissionDenied>(failure)
            assertEquals(emptyList<String>(), callable.targets)
        }

    @Test
    fun anUnexpectedResponseStatusIsARemoteDeletionFailure() =
        runTest {
            val invoker =
                CallableAccountDeletionInvoker(
                    callable = RecordingDeletionCallable(response = mapOf("status" to "UNEXPECTED")),
                    currentUid = { "owner-uid" },
                )

            val failure = runCatching { invoker.invokeDeletion("fresh-id-token") }.exceptionOrNull()

            assertIs<FirebaseAuthGatewayException.AccountDeletionRemoteFailed>(failure)
        }

    @Test
    fun aPermissionDeniedCallableFailureIsPermissionDenied() =
        runTest {
            assertIs<FirebaseAuthGatewayException.PermissionDenied>(
                failureFor(FirebaseFunctionsFailure.PERMISSION_DENIED),
            )
        }

    @Test
    fun anUnauthenticatedCallableFailureIsPermissionDenied() =
        runTest {
            assertIs<FirebaseAuthGatewayException.PermissionDenied>(
                failureFor(FirebaseFunctionsFailure.UNAUTHENTICATED),
            )
        }

    @Test
    fun anUnavailableCallableFailureIsANetworkFailure() =
        runTest {
            assertIs<FirebaseAuthGatewayException.Network>(failureFor(FirebaseFunctionsFailure.UNAVAILABLE))
        }

    @Test
    fun aDeadlineExceededCallableFailureIsANetworkFailure() =
        runTest {
            assertIs<FirebaseAuthGatewayException.Network>(failureFor(FirebaseFunctionsFailure.DEADLINE_EXCEEDED))
        }

    @Test
    fun anyOtherCallableFailureIsARemoteDeletionFailure() =
        runTest {
            assertIs<FirebaseAuthGatewayException.AccountDeletionRemoteFailed>(
                failureFor(FirebaseFunctionsFailure.UNKNOWN),
            )
        }

    private suspend fun failureFor(failure: FirebaseFunctionsFailure): Throwable? {
        val invoker =
            CallableAccountDeletionInvoker(
                callable = RecordingDeletionCallable(failure = failure),
                currentUid = { "owner-uid" },
            )
        return runCatching { invoker.invokeDeletion("fresh-id-token") }.exceptionOrNull()
    }
}

private class RecordingDeletionCallable(
    private val response: Map<String, String> = mapOf("status" to "ACCOUNT_DELETED"),
    private val failure: FirebaseFunctionsFailure? = null,
) : AccountDeletionCallable {
    val targets = mutableListOf<String>()

    override suspend fun deleteAccount(targetUid: String): Map<String, String> {
        targets += targetUid
        failure?.let { throw FirebaseFunctionsGatewayException(it) }
        return response
    }
}
