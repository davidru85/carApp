package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountConversionSurfaceRulesTest {
    @Test
    fun anAnonymousSessionShowsTheLinkAccountRow() {
        assertTrue(showsLinkAccountRow(SessionPhase.ANONYMOUS))
    }

    @Test
    fun everyOtherSessionPhaseHidesTheLinkAccountRow() {
        SessionPhase.entries.filterNot { phase -> phase == SessionPhase.ANONYMOUS }.forEach { phase ->
            assertFalse(showsLinkAccountRow(phase), "$phase must not offer account linking")
        }
    }

    /** `D-224`: a local session has no anonymous identity to link until adoption creates one. */
    @Test
    fun aLocalSessionHidesTheLinkAccountRowUntilAdoptionMakesItAnonymous() {
        assertFalse(showsLinkAccountRow(SessionPhase.LOCAL))
    }

    @Test
    fun theAdoptExistingAccountConfirmationOpensTheCollisionDialog() {
        assertTrue(isAccountConversionConfirmation(collisionMessage()))
    }

    @Test
    fun noOtherSessionMessageOpensTheCollisionDialog() {
        assertFalse(isAccountConversionConfirmation(null))
        assertFalse(isAccountConversionConfirmation(message("AUTH.NETWORK_UNAVAILABLE", confirmation = null)))
        Confirmation.entries.filterNot { it == Confirmation.AdoptExistingAccount }.forEach { other ->
            assertFalse(
                isAccountConversionConfirmation(message("CONFIRMATION.$other", confirmation = other)),
                "$other is not the credential collision",
            )
        }
    }

    @Test
    fun theCollisionConfirmationUsesTheReplacementCopy() {
        assertEquals(R.string.confirm_replace_existing_account, collisionMessage().stringResource())
    }

    @Test
    fun aUidChangeRefusalHasItsOwnMessage() {
        assertEquals(R.string.error_auth_uid_would_change, authStringResource("AUTH.UID_WOULD_CHANGE"))
    }

    @Test
    fun aCredentialAlreadyInUseFailureHasItsOwnMessage() {
        assertEquals(R.string.error_auth_credential_in_use, authStringResource("AUTH.CREDENTIAL_ALREADY_IN_USE"))
    }

    @Test
    fun aDeniedAccountOperationHasItsOwnMessage() {
        assertEquals(R.string.error_auth_permission_denied, authStringResource("AUTH.PERMISSION_DENIED"))
    }

    @Test
    fun aRemoteFailureOnTheSessionChannelSaysTheReplacementKeepsBeingRetried() {
        REMOTE_CODES.forEach { code ->
            assertEquals(
                R.string.error_conversion_replacement_pending,
                sessionMessageStringResource(message(code, confirmation = null)),
                "$code on the session channel is an interrupted account replacement",
            )
        }
    }

    @Test
    fun anAuthFailureOnTheSessionChannelKeepsItsAuthMessage() {
        assertEquals(
            R.string.error_auth_network,
            sessionMessageStringResource(message("AUTH.NETWORK_UNAVAILABLE", confirmation = null)),
        )
    }

    private fun collisionMessage(): UiMessage =
        UiMessage(
            id = 3L,
            kind = UiMessageKind.WARNING,
            code = "CONFIRMATION.AdoptExistingAccount",
            confirmation = Confirmation.AdoptExistingAccount,
        )

    private fun message(
        code: String,
        confirmation: Confirmation?,
    ): UiMessage =
        UiMessage(
            id = 2L,
            kind = if (confirmation == null) UiMessageKind.ERROR else UiMessageKind.WARNING,
            code = code,
            confirmation = confirmation,
        )

    private companion object {
        val REMOTE_CODES =
            listOf(
                "REMOTE.UNAVAILABLE",
                "REMOTE.DEADLINE_EXCEEDED",
                "REMOTE.PERMISSION_DENIED",
                "REMOTE.UNAUTHENTICATED",
                "REMOTE.INVALID_ARGUMENT",
                "REMOTE.NOT_FOUND",
                "REMOTE.UNKNOWN",
            )
    }
}
