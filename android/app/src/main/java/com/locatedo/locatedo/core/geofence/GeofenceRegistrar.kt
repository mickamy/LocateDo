package com.locatedo.locatedo.core.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

interface GeofenceRegistrar {
    suspend fun add(regions: List<GeofenceRegion>): Boolean
    suspend fun remove(requestIds: List<String>): Boolean
}

// Play services evaluates the fences; a dwell of two minutes filters out driving past, as the spec asks. Going in and
// out are reported too, for when the device went inside and for departures.
@Singleton
class PlayGeofenceRegistrar @Inject constructor(@param:ApplicationContext private val context: Context) : GeofenceRegistrar {
    private val client = LocationServices.getGeofencingClient(context)

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    override suspend fun add(regions: List<GeofenceRegion>): Boolean {
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL or GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(regions.map { it.asGeofence() })
            .build()
        return try {
            client.addGeofences(request, pendingIntent).await()
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not add geofences", e)
            false
        } catch (e: ApiException) {
            Log.w(TAG, "Could not add geofences", e)
            false
        }
    }

    override suspend fun remove(requestIds: List<String>): Boolean = try {
        client.removeGeofences(requestIds).await()
        true
    } catch (e: ApiException) {
        Log.w(TAG, "Could not remove geofences", e)
        false
    }

    private fun GeofenceRegion.asGeofence(): Geofence = Geofence.Builder()
        .setRequestId(requestId)
        .setCircularRegion(latitude, longitude, radiusMeters.toFloat())
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .setTransitionTypes(
            Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL or Geofence.GEOFENCE_TRANSITION_EXIT,
        )
        .setLoiteringDelay(LOITERING_DELAY_MILLIS)
        .build()

    private companion object {
        const val TAG = "Geofence"
        const val LOITERING_DELAY_MILLIS = 2 * 60 * 1000
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GeofenceModule {
    @Binds
    abstract fun geofenceRegistrar(registrar: PlayGeofenceRegistrar): GeofenceRegistrar
}
