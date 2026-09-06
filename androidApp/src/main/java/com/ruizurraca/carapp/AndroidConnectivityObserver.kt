package com.ruizurraca.carapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Publishes real Android network reachability onto the `ConnectivityObserver` contract.
 *
 * The platform registration is supplied as [registerListener] so the observer's own behaviour - the
 * initial value and every subsequent change - is exercisable without `android.jar` runtime
 * behaviour, exactly as `AndroidLocaleProvider` takes its locale source. [fromSystemService] is the
 * thin production adapter over `ConnectivityManager`.
 */
internal class AndroidConnectivityObserver(
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
         * Observes the default network. A device with no `ConnectivityManager` is treated as online,
         * because refusing every network operation is worse than attempting one that fails: the
         * acquisition path already reports its own failure and stays retryable.
         */
        fun fromSystemService(context: Context): AndroidConnectivityObserver {
            val manager = context.getSystemService<ConnectivityManager>()
            return AndroidConnectivityObserver(
                initiallyOnline = manager?.hasValidatedInternet() ?: true,
                registerListener = { publish ->
                    manager?.registerDefaultNetworkCallback(
                        object : ConnectivityManager.NetworkCallback() {
                            override fun onAvailable(network: Network) = publish(true)

                            override fun onLost(network: Network) = publish(false)

                            override fun onCapabilitiesChanged(
                                network: Network,
                                networkCapabilities: NetworkCapabilities,
                            ) = publish(networkCapabilities.hasValidatedInternet())
                        },
                    )
                },
            )
        }

        private fun ConnectivityManager.hasValidatedInternet(): Boolean =
            getNetworkCapabilities(activeNetwork)?.hasValidatedInternet() ?: false

        private fun NetworkCapabilities.hasValidatedInternet(): Boolean =
            hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
