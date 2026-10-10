package com.locatedo.locatedo.core.analytics

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.locatedo.locatedo.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Collection starts off from the manifest. Reports from before the answer stay on the device until it is known.
@Singleton
class CrashReporting @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val analytics: Analytics,
) {
    suspend fun apply(decision: Boolean?) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            return
        }
        val crashlytics = FirebaseCrashlytics.getInstance()
        if (decision == null) {
            crashlytics.setCrashlyticsCollectionEnabled(false)
            return
        }
        // Debug builds are not minified, so their reports would only add noise.
        if (!decision || !BuildConfig.CRASH_REPORTS) {
            crashlytics.setCrashlyticsCollectionEnabled(false)
            crashlytics.deleteUnsentReports()
            return
        }
        crashlytics.sendUnsentReports()
        crashlytics.setCrashlyticsCollectionEnabled(true)
        // The app instance id is the Support ID in support mail, so reports can be found from it.
        val id = analytics.appInstanceId() ?: return
        crashlytics.setUserId(id)
    }
}
