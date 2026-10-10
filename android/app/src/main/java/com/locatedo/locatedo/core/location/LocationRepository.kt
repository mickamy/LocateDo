package com.locatedo.locatedo.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.locatedo.locatedo.core.model.Coordinate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

interface LocationRepository {
    fun hasForegroundPermission(): Boolean
    fun hasPrecisePermission(): Boolean
    suspend fun lastCoordinate(): Coordinate?
}

@Singleton
class FusedLocationRepository @Inject constructor(@param:ApplicationContext private val context: Context) : LocationRepository {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    override fun hasForegroundPermission(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    override fun hasPrecisePermission(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    override suspend fun lastCoordinate(): Coordinate? {
        if (!hasForegroundPermission()) {
            return null
        }
        // Nothing has asked for a fix yet right after location is first allowed, so one is taken then.
        val location = try {
            client.lastLocation.await() ?: withTimeoutOrNull(CURRENT_FIX_TIMEOUT_MILLIS) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            }
        } catch (e: SecurityException) {
            null
        } catch (e: ApiException) {
            null
        }
        return location?.let { Coordinate(it.latitude, it.longitude) }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CURRENT_FIX_TIMEOUT_MILLIS = 5_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds
    abstract fun locationRepository(repository: FusedLocationRepository): LocationRepository
}
