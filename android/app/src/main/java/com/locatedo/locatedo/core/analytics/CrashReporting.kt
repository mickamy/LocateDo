package com.locatedo.locatedo.core.analytics

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// The app instance ID is the Support ID in support mail, so reports can be found from it.
@Singleton
class CrashReporting @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val analytics: Analytics,
) {
    suspend fun tagWithAppInstanceId() {
        if (FirebaseApp.getApps(context).isEmpty()) {
            return
        }
        val id = analytics.appInstanceId() ?: return
        FirebaseCrashlytics.getInstance().setUserId(id)
    }
}
