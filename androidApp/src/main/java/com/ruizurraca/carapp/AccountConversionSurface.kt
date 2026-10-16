package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.UiMessage

/**
 * `E4-05`: the F-4 entry point is offered only to an anonymous session. `startAccountConversion`
 * refuses every other session (`docs/CONTRACTS.md §20.10`), and `D-224` keeps the row hidden
 * while the session is still `LOCAL`, until adoption makes it anonymous.
 */
internal fun showsLinkAccountRow(phase: SessionPhase): Boolean =
    when (phase) {
        SessionPhase.ANONYMOUS -> {
            true
        }

        SessionPhase.UNKNOWN,
        SessionPhase.LOCAL,
        SessionPhase.PERMANENT,
        SessionPhase.SIGNED_OUT,
        SessionPhase.DELETING,
        -> {
            false
        }
    }

/** The one session message the conversion surface answers itself (`docs/CONTRACTS.md §11.3`). */
internal fun isAccountConversionConfirmation(message: UiMessage?): Boolean =
    message?.confirmation == Confirmation.AdoptExistingAccount

/**
 * Copy for a message of the session channel. On that channel a `REMOTE.*` code is published only by
 * a confirmed account replacement that failed after the session switch, which the app resumes on its
 * own (`docs/CONTRACTS.md §11.3`); departures publish only `AUTH.*` and `PERSISTENCE.*` codes. Every
 * other code keeps the shared mapping.
 */
internal fun sessionMessageStringResource(message: UiMessage): Int =
    if (message.code.startsWith(REMOTE_CODE_PREFIX)) {
        R.string.error_conversion_replacement_pending
    } else {
        message.stringResource()
    }

private const val REMOTE_CODE_PREFIX = "REMOTE."
