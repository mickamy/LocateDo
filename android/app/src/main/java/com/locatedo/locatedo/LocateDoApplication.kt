package com.locatedo.locatedo

import android.app.Application
import android.util.Log
import com.locatedo.locatedo.core.account.AccountManager
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.common.di.ApplicationScope
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.geofence.GeofenceSync
import com.locatedo.locatedo.core.notifications.ArrivalNotifier
import com.locatedo.locatedo.core.push.DeviceRegistration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class LocateDoApplication : Application() {
    @Inject lateinit var categories: CategoryRepository

    @Inject lateinit var notifier: ArrivalNotifier

    @Inject lateinit var geofenceSync: GeofenceSync

    @Inject lateinit var authenticator: Authenticator

    @Inject lateinit var accountManager: AccountManager

    @Inject lateinit var deviceRegistration: DeviceRegistration

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notifier.prepare()
        applicationScope.launch {
            categories.ensureBuiltins()
        }
        geofenceSync.start()
        applicationScope.launch {
            authenticator.sessionEnded.collect {
                accountManager.endSession()
            }
        }
        // A household whose creation failed last time, and a push token the server has not seen yet.
        applicationScope.launch {
            try {
                accountManager.uploadLocalDataIfNeeded()
            } catch (e: Exception) {
                Log.w(TAG, "Initial upload failed", e)
            }
            deviceRegistration.registerIfSignedIn()
        }
    }

    private companion object {
        const val TAG = "LocateDo"
    }
}
