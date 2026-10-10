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

// Builds without google-services.json have no Firebase; they report nothing. Collection starts off from the manifest;
// apply turns it on once the consent answer is known.
@Singleton
class FirebaseAnalyticsSink @Inject constructor(@param:ApplicationContext private val context: Context) : Analytics {
    private val firebase: FirebaseAnalytics? by lazy {
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAnalytics.getInstance(context)
    }

    private val queue = AnalyticsQueue()

    // null while the answer is not known yet: nothing is sent, and what is logged waits in memory.
    fun apply(decision: Boolean?) {
        val firebase = firebase ?: return
        val isSending = decision == true
        var status = FirebaseAnalytics.ConsentStatus.DENIED
        if (isSending) {
            status = FirebaseAnalytics.ConsentStatus.GRANTED
        }
        firebase.setConsent(mapOf(FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to status))
        firebase.setAnalyticsCollectionEnabled(isSending)
        val released = synchronized(queue) { queue.decide(decision) }
        for (entry in released) {
            deliver(firebase, entry)
        }
    }

    override fun log(event: AnalyticsEvent, parameters: AnalyticsParameters) {
        send(AnalyticsQueue.Entry.Event(event.key, parameters.wireValues()))
    }

    override fun logScreen(screen: AnalyticsScreen, parameters: AnalyticsParameters) {
        val values = parameters.wireValues().toMutableMap()
        values[FirebaseAnalytics.Param.SCREEN_NAME] = screen.key
        values[FirebaseAnalytics.Param.SCREEN_CLASS] = screen.key
        send(AnalyticsQueue.Entry.Event(FirebaseAnalytics.Event.SCREEN_VIEW, values))
    }

    override fun setUserProperty(property: AnalyticsUserProperty, value: String?) {
        send(AnalyticsQueue.Entry.UserProperty(property.key, value))
    }

    // Only while sending, so the Support ID and RevenueCat never carry it otherwise.
    override suspend fun appInstanceId(): String? {
        val firebase = firebase ?: return null
        if (synchronized(queue) { queue.mode } != AnalyticsQueue.Mode.SENDING) {
            return null
        }
        return try {
            firebase.appInstanceId.await()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the app instance id", e)
            null
        }
    }

    private fun send(entry: AnalyticsQueue.Entry) {
        val firebase = firebase ?: return
        if (synchronized(queue) { queue.submit(entry) }) {
            deliver(firebase, entry)
        }
    }

    private fun deliver(firebase: FirebaseAnalytics, entry: AnalyticsQueue.Entry) {
        when (entry) {
            is AnalyticsQueue.Entry.Event -> firebase.logEvent(entry.name, bundle(entry.parameters))
            is AnalyticsQueue.Entry.UserProperty -> firebase.setUserProperty(entry.name, entry.value)
        }
    }

    private fun bundle(values: Map<String, Any>): Bundle {
        val bundle = Bundle()
        for ((key, value) in values) {
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
