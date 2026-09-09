package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.analytics.AnalyticsTracker
import com.ruizurraca.carapp.core.auth.AuthClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The F-5 sign-out and account-deletion state machine of `docs/SPECIFICATION.md §7 F-5` and
 * `docs/CONTRACTS.md §11.5`.
 *
 * Behavior-free seam: it declares the surface the session holder and its tests need, so the RED
 * specification compiles and fails for the missing behavior rather than for a missing type.
 */
internal class AccountDepartureFlow(
    private val scope: CoroutineScope?,
    private val authClient: AuthClient?,
    private val departure: AccountDepartureHandler?,
    private val analyticsTracker: AnalyticsTracker?,
    private val state: MutableStateFlow<SessionUiState>,
    private val cancelOtherWork: () -> Unit,
) {
    val isRunning: Boolean = false

    val awaitingReauthentication: Boolean = false

    fun ownsState(): Boolean = false

    fun cancel() = Unit

    fun abandon() = Unit

    fun requestSignOut() = Unit

    fun confirmSignOut() = Unit

    fun requestDeleteAccount() = Unit

    fun confirmDeleteAccount() = Unit

    fun resumeAfterReauthentication() = Unit
}
