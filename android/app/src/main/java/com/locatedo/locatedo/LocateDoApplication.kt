package com.locatedo.locatedo

import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.locatedo.locatedo.core.account.AccountManager
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.common.di.ApplicationScope
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.geofence.GeofenceSync
import com.locatedo.locatedo.core.notifications.ArrivalNotifier
import com.locatedo.locatedo.core.push.DeviceRegistration
import com.locatedo.locatedo.core.push.PushMessages
import com.locatedo.locatedo.core.sync.NetworkMonitor
import com.locatedo.locatedo.core.sync.SyncEngine
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

    @Inject lateinit var syncEngine: SyncEngine

    @Inject lateinit var pushMessages: PushMessages

    @Inject lateinit var networkMonitor: NetworkMonitor

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notifier.prepare()
        applicationScope.launch {
            categories.ensureBuiltins()
        }
        geofenceSync.start()
        syncEngine.start()
        networkMonitor.start()
        collectSyncTriggers()
        applicationScope.launch {
            authenticator.sessionEnded.collect {
                accountManager.endSession()
            }
        }
        // A household whose creation failed last time, and an installation the server has not seen yet.
        applicationScope.launch {
            try {
                accountManager.uploadLocalDataIfNeeded()
            } catch (e: Exception) {
                Log.w(TAG, "Initial upload failed", e)
            }
            deviceRegistration.registerIfSignedIn()
        }
    }

    // Pull on every return to the foreground, when the server says something changed, when the network comes back,
    // and as soon as the device has a household. Being removed from the household starts a fresh one.
    private fun collectSyncTriggers() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    applicationScope.launch { syncEngine.sync() }
                }
            },
        )
        applicationScope.launch {
            pushMessages.received.collect { syncEngine.sync() }
        }
        applicationScope.launch {
            networkMonitor.reconnected.collect { syncEngine.sync() }
        }
        applicationScope.launch {
            accountManager.householdReady.collect { syncEngine.sync() }
        }
        applicationScope.launch {
            syncEngine.removed.collect {
                try {
                    accountManager.startOver()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not start over after being removed from the household", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "LocateDo"
    }
}
