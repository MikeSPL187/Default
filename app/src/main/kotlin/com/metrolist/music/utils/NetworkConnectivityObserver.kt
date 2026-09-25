/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple NetworkConnectivityObserver based on OuterTune's implementation
 * Provides network connectivity monitoring for auto-play functionality
 */
class NetworkConnectivityObserver(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = MutableStateFlow(isCurrentlyConnected())
    val networkStatus = _networkStatus.asStateFlow()

    /** The network the system routes traffic through; null while there is none. */
    @Volatile
    private var defaultNetwork: Network? = null

    // Follows the system's default network rather than asking "is anything connected" on each
    // event: when the last network goes away, that question can still be answered from the one
    // being lost, and no later event corrects it.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            defaultNetwork = network
            _networkStatus.value = reachesInternet(networkCapabilities)
        }

        override fun onLost(network: Network) {
            if (defaultNetwork == null || defaultNetwork == network) {
                defaultNetwork = null
                _networkStatus.value = false
            }
        }
    }

    // A VPN's own callbacks may not follow the networks beneath it, so the real networks (a
    // request leaves VPNs out by default) are watched as well.
    private val underlyingCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = recheckVpn(lost = null)

        override fun onLost(network: Network) = recheckVpn(lost = network)
    }

    private fun recheckVpn(lost: Network?) {
        val default = defaultNetwork ?: return
        val capabilities = connectivityManager.getNetworkCapabilities(default) ?: return
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            _networkStatus.value = reachesInternet(capabilities, lost)
        }
    }

    init {
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                underlyingCallback,
            )
        } catch (e: Exception) {
            // Fallback: assume connected if registration fails
            _networkStatus.value = true
        }
    }

    /**
     * A VPN can stay up as the default network after Wi-Fi and mobile data are both off, so it
     * counts only while some real network carries it.
     */
    @Suppress("DEPRECATION")
    private fun reachesInternet(
        capabilities: NetworkCapabilities,
        lost: Network? = null,
    ): Boolean {
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true
        return connectivityManager.allNetworks.any { network ->
            if (network == lost) return@any false
            val other = connectivityManager.getNetworkCapabilities(network) ?: return@any false
            !other.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && other.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    fun unregister() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        runCatching { connectivityManager.unregisterNetworkCallback(underlyingCallback) }
    }
    
    /**
     * Check current connectivity state synchronously
     */
    fun isCurrentlyConnected(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            networkCapabilities != null && reachesInternet(networkCapabilities)
        } catch (e: Exception) {
            false
        }
    }
}
