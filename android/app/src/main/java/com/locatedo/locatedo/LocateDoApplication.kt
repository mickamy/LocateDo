package com.locatedo.locatedo

import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.locatedo.locatedo.core.account.AccountManager
import com.locatedo.locatedo.core.analytics.DailyStateReporter
import com.locatedo.locatedo.core.analytics.WriteAnalytics
import com.locatedo.locatedo.core.appstatus.AppStatusStore
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.billing.Entitlements
import com.locatedo.locatedo.core.common.di.ApplicationScope
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.geofence.GeofenceSync
import com.locatedo.locatedo.core.notifications.ArrivalNotifier
import com.locatedo.locatedo.core.notifications.CampaignNotifier
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import com.locatedo.locatedo.core.push.DeviceRegistration
import com.locatedo.locatedo.core.push.PromotionsConsent
import com.locatedo.locatedo.core.push.PushMessages
import com.locatedo.locatedo.core.sharing.HouseholdManager
import com.locatedo.locatedo.core.sync.NetworkMonitor
import com.locatedo.locatedo.core.sync.SyncEngine
import dagger.hilt.android.HiltAndroidApp
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class LocateDoApplication : Application() {
    @Inject lateinit var preferences: AppPreferences

    @Inject lateinit var categories: CategoryRepository

    @Inject lateinit var notifier: ArrivalNotifier

    @Inject lateinit var campaignNotifier: CampaignNotifier

    @Inject lateinit var geofenceSync: GeofenceSync

    @Inject lateinit var authenticator: Authenticator

    @Inject lateinit var accountManager: AccountManager

    @Inject lateinit var deviceRegistration: DeviceRegistration

    @Inject lateinit var syncEngine: SyncEngine

    @Inject lateinit var pushMessages: PushMessages

    @Inject lateinit var networkMonitor: NetworkMonitor

    @Inject lateinit var householdManager: HouseholdManager

    @Inject lateinit var entitlements: Entitlements

    @Inject lateinit var dailyStateReporter: DailyStateReporter

    @Inject lateinit var writeAnalytics: WriteAnalytics

    @Inject lateinit var promotionsConsent: PromotionsConsent

    @Inject lateinit var permissions: PermissionsRepository

    @Inject lateinit var appStatus: AppStatusStore

    @Inject lateinit var clock: Clock

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notifier.prepare()
        campaignNotifier.prepare()
        applicationScope.launch {
            preferences.recordFirstLaunch(clock.instant())
            categories.ensureBuiltins()
        }
        geofenceSync.start()
        syncEngine.start()
        networkMonitor.start()
        entitlements.start()
        collectSyncTriggers()
        applicationScope.launch {
            authenticator.sessionEnded.collect {
                accountManager.endSession()
            }
        }
        // The app status first, so a maintenance window or an outdated build stops the calls below; then the store's
        // view of the signed-in user, a household whose creation failed last time, and an installation the server
        // has not seen yet.
        applicationScope.launch {
            appStatus.refresh()
            accountManager.linkPurchases()
            try {
                accountManager.uploadLocalDataIfNeeded()
            } catch (e: Exception) {
                Log.w(TAG, "Initial upload failed", e)
            }
            deviceRegistration.registerIfNeeded()
        }
    }

    // Pull on every return to the foreground (after re-reading the app status), when the server says something
    // changed, when the network comes back, when a maintenance window ends, and as soon as the device has a
    // household. Being removed from the household starts a fresh one. A foreground also checks whether the promotions
    // sheet is due.
    private fun collectSyncTriggers() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    applicationScope.launch {
                        appStatus.refresh()
                        if (appStatus.state.value.requiresUpdate) {
                            return@launch
                        }
                        syncEngine.sync()
                        dailyStateReporter.report()
                        promotionsConsent.checkPrompt(
                            notificationAuth = permissions.observe().first().notifications,
                            lastArrivalOpenedAt = writeAnalytics.lastArrivalOpenedAt,
                            now = clock.instant(),
                        )
                    }
                }
            },
        )
        applicationScope.launch {
            appStatus.maintenanceEnded.collect { syncEngine.sync() }
        }
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
            syncEngine.removed.collect { householdManager.handleRemoval() }
        }
    }

    private companion object {
        const val TAG = "LocateDo"
    }
}
