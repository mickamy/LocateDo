package com.locatedo.locatedo.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.locatedo.locatedo.core.datastore.AppPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

enum class LocationAuth { ALWAYS, WHEN_IN_USE, DENIED, NOT_DETERMINED }

enum class NotificationAuth { AUTHORIZED, DENIED, NOT_DETERMINED }

data class Permissions(val location: LocationAuth, val notifications: NotificationAuth)

// Grants happen in system UI, so screens call refresh() when they come back to the foreground.
interface PermissionsRepository {
    fun observe(): Flow<Permissions>
    fun refresh()
    fun isBatteryOptimizationExempt(): Boolean
    suspend fun markLocationRequested()
    suspend fun markNotificationsRequested()
}

@Singleton
class AndroidPermissionsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
) : PermissionsRepository {
    private val refreshes = MutableStateFlow(0)

    override fun observe(): Flow<Permissions> = combine(preferences.data, refreshes) { stored, _ ->
        Permissions(
            location = locationAuth(requested = stored.hasRequestedLocation),
            notifications = notificationAuth(requested = stored.hasRequestedNotifications),
        )
    }

    override fun refresh() = refreshes.update { it + 1 }

    override fun isBatteryOptimizationExempt(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    override suspend fun markLocationRequested() = preferences.setRequestedLocation(true)

    override suspend fun markNotificationsRequested() = preferences.setRequestedNotifications(true)

    // Android cannot tell "never asked" from "denied", so the app remembers whether it has asked.
    private fun locationAuth(requested: Boolean): LocationAuth = when {
        hasForegroundLocation() && granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> LocationAuth.ALWAYS
        hasForegroundLocation() -> LocationAuth.WHEN_IN_USE
        requested -> LocationAuth.DENIED
        else -> LocationAuth.NOT_DETERMINED
    }

    private fun notificationAuth(requested: Boolean): NotificationAuth = when {
        NotificationManagerCompat.from(context).areNotificationsEnabled() -> NotificationAuth.AUTHORIZED
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !requested -> NotificationAuth.NOT_DETERMINED
        else -> NotificationAuth.DENIED
    }

    private fun hasForegroundLocation(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionsModule {
    @Binds
    abstract fun permissionsRepository(repository: AndroidPermissionsRepository): PermissionsRepository
}
