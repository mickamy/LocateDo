package com.locatedo.locatedo.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

// Reports when the network comes back after a loss; the first network seen is not a reconnect.
@Singleton
class NetworkMonitor @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val _reconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var wasSatisfied: Boolean? = null

    val reconnected: SharedFlow<Unit> = _reconnected

    fun start() {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = pathChanged(satisfied = true)

                override fun onLost(network: Network) = pathChanged(satisfied = false)
            },
        )
    }

    @Synchronized
    fun pathChanged(satisfied: Boolean) {
        if (satisfied && wasSatisfied == false) {
            _reconnected.tryEmit(Unit)
        }
        wasSatisfied = satisfied
    }
}
