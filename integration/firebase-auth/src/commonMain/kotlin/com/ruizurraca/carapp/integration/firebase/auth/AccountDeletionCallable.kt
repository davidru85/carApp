package com.ruizurraca.carapp.integration.firebase.auth

/** The `D-23` callable as one provider-free call, so the invoker's mapping is testable. */
internal fun interface AccountDeletionCallable {
    suspend fun deleteAccount(targetUid: String): Map<String, String>
}

/**
 * `docs/CONTRACTS.md §11.1` and `§11.5`: the client side of the `D-23` server operation. RED stub:
 * GREEN calls the callable and maps its failures.
 */
@Suppress("UnusedPrivateProperty")
internal class CallableAccountDeletionInvoker(
    private val callable: AccountDeletionCallable,
    private val currentUid: () -> String?,
) : AccountDeletionInvoker {
    override suspend fun invokeDeletion(idToken: String) = Unit
}
