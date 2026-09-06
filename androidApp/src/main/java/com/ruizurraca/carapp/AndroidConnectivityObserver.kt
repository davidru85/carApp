package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Publishes real Android network reachability onto the `ConnectivityObserver` contract.
 *
 * The platform registration is supplied as [registerListener] so the observer's own behaviour -
 * the initial value and every subsequent change - is exercisable without `android.jar` runtime
 * behaviour, exactly as `AndroidLocaleProvider` takes its locale source. Story `E2-06` implements
 * the behaviour; this declaration exists so its failing tests compile and execute.
 */
internal class AndroidConnectivityObserver(
    initiallyOnline: Boolean,
    @Suppress("UnusedPrivateProperty")
    private val registerListener: ((Boolean) -> Unit) -> Unit,
) : ConnectivityObserver {
    private val state = MutableStateFlow(initiallyOnline)

    override val isOnline: StateFlow<Boolean> = state
}
