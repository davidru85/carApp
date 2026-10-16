package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which prompt each published session message asks for, and which confirmation intent answers a
 * pending-sync warning (`docs/CONTRACTS.md §20.10`). Codes and kinds mirror `StateHolders.kt`.
 */
class SessionPromptTest {
    @Test
    fun noMessageMeansNoPrompt() {
        assertEquals(SessionPrompt.NONE, sessionPrompt(null))
    }

    @Test
    fun thePendingSyncWarningIsThePendingSyncPrompt() {
        val warning =
            message(
                code = "WARNING.PENDING_SYNC",
                kind = UiMessageKind.WARNING,
                confirmation = Confirmation.DiscardPendingChanges,
            )

        assertEquals(SessionPrompt.PENDING_SYNC, sessionPrompt(warning))
    }

    @Test
    fun theAccountDeletionConfirmationIsItsOwnPrompt() {
        val confirmation =
            message(
                code = "CONFIRMATION.DeleteAccount",
                kind = UiMessageKind.WARNING,
                confirmation = Confirmation.DeleteAccount,
            )

        assertEquals(SessionPrompt.CONFIRM_DELETE_ACCOUNT, sessionPrompt(confirmation))
    }

    @Test
    fun theLocalDataDeletionConfirmationIsItsOwnPrompt() {
        val confirmation =
            message(
                code = "CONFIRMATION.DeleteLocalData",
                kind = UiMessageKind.WARNING,
                confirmation = Confirmation.DeleteLocalData,
            )

        assertEquals(SessionPrompt.CONFIRM_DELETE_LOCAL_DATA, sessionPrompt(confirmation))
    }

    @Test
    fun aStaleLoginIsTheReauthenticationPromptAndNotAPlainError() {
        val staleLogin = message(code = "AUTH.REQUIRES_RECENT_LOGIN", kind = UiMessageKind.ERROR, confirmation = null)

        assertEquals(SessionPrompt.REAUTHENTICATE, sessionPrompt(staleLogin))
    }

    @Test
    fun aDepartureErrorIsAnErrorPrompt() {
        val failure =
            message(
                code = "AUTH.ACCOUNT_DELETION_REMOTE_FAILED",
                kind = UiMessageKind.ERROR,
                confirmation = null,
            )

        assertEquals(SessionPrompt.ERROR, sessionPrompt(failure))
    }

    /**
     * A failed `startAnonymousSignIn` publishes its provider failure as a WARNING while it falls back to
     * a local session (`StateHolders.kt:172-193`). That is the F-1 local start, not a departure.
     */
    @Test
    fun theLocalStartNoticeIsNotPrompted() {
        val localStart = message(code = "AUTH.NETWORK_UNAVAILABLE", kind = UiMessageKind.WARNING, confirmation = null)

        assertEquals(SessionPrompt.NONE, sessionPrompt(localStart))
    }

    @Test
    fun theConversionCollisionIsLeftToTheConversionSurface() {
        val collision =
            message(
                code = "CONFIRMATION.AdoptExistingAccount",
                kind = UiMessageKind.WARNING,
                confirmation = Confirmation.AdoptExistingAccount,
            )

        assertEquals(SessionPrompt.NONE, sessionPrompt(collision))
    }

    @Test
    fun aPermanentOwnerDiscardsPendingChangesBySigningOut() {
        assertEquals(PendingSyncDiscard.SIGN_OUT, pendingSyncDiscard(SessionPhase.PERMANENT))
    }

    @Test
    fun aLocalOrAnonymousOwnerDiscardsPendingChangesByDeletingLocalData() {
        assertEquals(PendingSyncDiscard.LOCAL_DATA_DELETION, pendingSyncDiscard(SessionPhase.LOCAL))
        assertEquals(PendingSyncDiscard.LOCAL_DATA_DELETION, pendingSyncDiscard(SessionPhase.ANONYMOUS))
    }

    private fun message(
        code: String,
        kind: UiMessageKind,
        confirmation: Confirmation?,
    ): UiMessage = UiMessage(id = 1L, kind = kind, code = code, confirmation = confirmation)
}
