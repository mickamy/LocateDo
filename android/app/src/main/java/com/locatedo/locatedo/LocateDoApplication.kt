package com.locatedo.locatedo

import android.app.Application
import com.locatedo.locatedo.core.common.di.ApplicationScope
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.geofence.GeofenceSync
import com.locatedo.locatedo.core.notifications.ArrivalNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class LocateDoApplication : Application() {
    @Inject lateinit var categories: CategoryRepository

    @Inject lateinit var notifier: ArrivalNotifier

    @Inject lateinit var geofenceSync: GeofenceSync

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notifier.prepare()
        applicationScope.launch {
            categories.ensureBuiltins()
        }
        geofenceSync.start()
    }
}
