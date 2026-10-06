package com.locatedo.locatedo.core.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.locatedo.locatedo.core.model.Coordinate
import java.util.UUID
import kotlinx.coroutines.launch

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "Geofence event failed with code ${event.errorCode}")
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_DWELL) {
            return
        }
        val placeIds = event.triggeringGeofences.orEmpty().mapNotNull { fence ->
            runCatching { UUID.fromString(fence.requestId) }.getOrNull()
        }
        if (placeIds.isEmpty()) {
            return
        }
        val here = event.triggeringLocation?.let { Coordinate(it.latitude, it.longitude) }
        val graph = GeofenceEntryPoint.from(context)
        val result = goAsync()
        graph.applicationScope().launch {
            try {
                graph.arrivalHandler().arrived(placeIds, here)
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        const val TAG = "Geofence"
    }
}
