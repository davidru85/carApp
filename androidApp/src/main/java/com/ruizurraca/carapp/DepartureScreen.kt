package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AuthProvider

/** RED stub: the `E4-01` GREEN commit replaces the body. */
@Suppress("UnusedParameter")
internal fun resolveRootDestination(
    session: SessionUiState,
    vehicleCount: Int,
    isReauthenticating: Boolean,
): OnboardingDestination = resolveOnboardingDestination(session.phase, vehicleCount)

/** RED stub: the `E4-01` GREEN commit replaces the body. */
@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
internal fun isReauthenticationStillInFlight(
    isReauthenticating: Boolean,
    session: SessionUiState,
): Boolean = false

/** RED stub: the `E4-01` GREEN commit replaces the body. */
@Suppress("UnusedParameter")
internal fun reauthenticationProviders(providers: List<AuthProvider>): List<AuthProvider> = emptyList()

/** RED stub: the `E4-01` GREEN commit replaces the body. */
@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
internal fun departureRetryExplanationResource(retry: DepartureRetry): Int = 0
