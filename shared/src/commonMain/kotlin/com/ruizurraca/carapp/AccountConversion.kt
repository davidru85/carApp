package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.auth.OrphanCleanupClient
import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.database.AccountConversionStore
import com.ruizurraca.carapp.core.sync.RemoteSyncSource

/** Internal orchestration seam for the confirmed credential-collision flow. */
internal fun interface AccountConversionHandler {
    suspend fun confirm(
        anonymousUid: String,
        credential: NativeAuthCredential,
    ): Outcome<AuthSession, AppError>
}

internal enum class AccountConversionCheckpoint {
    SNAPSHOT_CAPTURED,
    CLEANUP_TICKET_SAVED,
    PERMANENT_SESSION_STARTED,
    REMOTE_REPLACED,
    LOCAL_REPLACED,
    ORPHAN_CLEANED,
}

/** RED-phase shell for the durable D-61 account-replacement orchestration. */
internal class AccountConversionCoordinator(
    private val authClient: com.ruizurraca.carapp.core.auth.AuthClient,
    private val orphanCleanupClient: OrphanCleanupClient,
    private val remoteSyncSource: RemoteSyncSource,
    private val store: AccountConversionStore,
    private val clock: AppClock,
    private val afterCheckpoint: suspend (AccountConversionCheckpoint) -> Unit = {},
) : AccountConversionHandler {
    override suspend fun confirm(
        anonymousUid: String,
        credential: NativeAuthCredential,
    ): Outcome<AuthSession, AppError> = Outcome.Err(AuthError.ProviderUnavailable)

    suspend fun resumePending(): Outcome<Unit, AppError> = Outcome.Err(AuthError.ProviderUnavailable)

    suspend fun awaitSettled(): Outcome<Unit, AppError> = Outcome.Err(AuthError.ProviderUnavailable)
}
