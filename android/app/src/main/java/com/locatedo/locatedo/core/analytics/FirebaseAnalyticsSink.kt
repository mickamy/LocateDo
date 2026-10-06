package com.locatedo.locatedo.core.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

// Builds without google-services.json have no Firebase; they report nothing.
@Singleton
class FirebaseAnalyticsSink @Inject constructor(@param:ApplicationContext private val context: Context) : Analytics {
    private val firebase: FirebaseAnalytics? by lazy {
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAnalytics.getInstance(context)
    }

    override fun log(event: AnalyticsEvent, parameters: AnalyticsParameters) {
        firebase?.logEvent(event.key, bundle(parameters))
    }

    override fun logScreen(screen: AnalyticsScreen, parameters: AnalyticsParameters) {
        val bundle = bundle(parameters)
        bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, screen.key)
        bundle.putString(FirebaseAnalytics.Param.SCREEN_CLASS, screen.key)
        firebase?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    override fun setUserProperty(property: AnalyticsUserProperty, value: String?) {
        firebase?.setUserProperty(property.key, value)
    }

    override suspend fun appInstanceId(): String? = try {
        firebase?.appInstanceId?.await()
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the app instance id", e)
        null
    }

    private fun bundle(parameters: AnalyticsParameters): Bundle {
        val bundle = Bundle()
        for ((key, value) in parameters.wireValues()) {
            when (value) {
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        return bundle
    }

    private companion object {
        const val TAG = "Analytics"
    }
}
