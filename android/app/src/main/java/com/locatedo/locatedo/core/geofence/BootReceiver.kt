package com.locatedo.locatedo.core.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

// Fences are dropped on reboot and on app updates; this puts them back before the user opens the app.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val graph = GeofenceEntryPoint.from(context)
        val result = goAsync()
        graph.applicationScope().launch {
            try {
                graph.geofenceSync().sync()
            } finally {
                result.finish()
            }
        }
    }
}
