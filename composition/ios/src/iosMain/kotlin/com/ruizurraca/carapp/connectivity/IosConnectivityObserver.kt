package com.ruizurraca.carapp.connectivity

import com.ruizurraca.carapp.core.common.ConnectivityObserver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_t
import platform.darwin.dispatch_get_global_queue

/**
 * Publishes real iOS network reachability onto the `ConnectivityObserver` contract.
 *
 * The platform registration is supplied as [registerListener] so the observer's own behaviour is
 * exercisable from the canonical `iosSimulatorArm64Test` route, the same source-reuse topology
 * `D-109` established for `IosLocaleProvider`. [fromNetworkPathMonitor] is the thin production
 * adapter over `Network.framework`.
 */
internal class IosConnectivityObserver(
    initiallyOnline: Boolean,
    registerListener: ((Boolean) -> Unit) -> Unit,
) : ConnectivityObserver {
    private val state = MutableStateFlow(initiallyOnline)

    override val isOnline: StateFlow<Boolean> = state

    init {
        registerListener { online -> state.value = online }
    }

    companion object {
        /**
         * Starts an `NWPathMonitor` on a background queue. It begins optimistic and is corrected by
         * the monitor's first update: refusing every network operation until the first callback
         * would be worse than attempting one that fails, because the acquisition path already
         * reports its own failure and stays retryable.
         */
        @OptIn(ExperimentalForeignApi::class)
        fun fromNetworkPathMonitor(): IosConnectivityObserver =
            IosConnectivityObserver(
                initiallyOnline = true,
                registerListener = { publish ->
                    val monitor = nw_path_monitor_create()
                    nw_path_monitor_set_update_handler(monitor) { path: nw_path_t ->
                        publish(nw_path_get_status(path) == nw_path_status_satisfied)
                    }
                    nw_path_monitor_set_queue(monitor, dispatch_get_global_queue(0, 0uL))
                    nw_path_monitor_start(monitor)
                },
            )
    }
}
