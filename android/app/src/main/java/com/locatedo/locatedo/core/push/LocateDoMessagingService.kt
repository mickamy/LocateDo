package com.locatedo.locatedo.core.push

import android.annotation.SuppressLint
import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.locatedo.locatedo.core.common.di.ApplicationScope
import com.locatedo.locatedo.core.notifications.CampaignHandler
import com.locatedo.locatedo.core.notifications.CampaignNotification
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PushEntryPoint {
    fun deviceRegistration(): DeviceRegistration
    fun pushMessages(): PushMessages
    fun campaignHandler(): CampaignHandler

    @ApplicationScope
    fun applicationScope(): CoroutineScope

    companion object {
        fun from(context: Context): PushEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, PushEntryPoint::class.java)
    }
}

// Registration goes through installation ids (onRegistered); the lint check still asks for the deprecated onNewToken.
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class LocateDoMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) {
        val graph = PushEntryPoint.from(this)
        graph.applicationScope().launch {
            graph.deviceRegistration().received(installationId)
        }
    }

    // A campaign is posted before returning, since the process may be stopped right after; anything else asks for a
    // sync.
    override fun onMessageReceived(message: RemoteMessage) {
        val graph = PushEntryPoint.from(this)
        val campaign = CampaignNotification.from(message.data)
        if (campaign == null) {
            graph.pushMessages().notifyReceived()
            return
        }
        runBlocking {
            graph.campaignHandler().received(campaign, Instant.ofEpochMilli(message.sentTime))
        }
    }
}
