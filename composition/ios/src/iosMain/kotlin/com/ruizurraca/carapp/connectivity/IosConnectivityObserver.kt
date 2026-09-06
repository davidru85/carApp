package com.ruizurraca.carapp.connectivity

import com.ruizurraca.carapp.core.common.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Publishes real iOS network reachability onto the `ConnectivityObserver` contract.
 *
 * The platform registration is supplied as [registerListener] so the observer's own behaviour is
 * exercisable from the canonical `iosSimulatorArm64Test` route, the same source-reuse topology
 * `D-109` established for `IosLocaleProvider`. Story `E2-06` implements the behaviour; this
 * declaration exists so its failing tests compile and execute.
 */
internal class IosConnectivityObserver(
    initiallyOnline: Boolean,
    @Suppress("UnusedPrivateProperty")
    private val registerListener: ((Boolean) -> Unit) -> Unit,
) : ConnectivityObserver {
    private val state = MutableStateFlow(initiallyOnline)

    override val isOnline: StateFlow<Boolean> = state
}
