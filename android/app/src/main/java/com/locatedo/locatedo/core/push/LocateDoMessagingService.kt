package com.locatedo.locatedo.core.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.locatedo.locatedo.core.common.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PushEntryPoint {
    fun deviceRegistration(): DeviceRegistration
    fun pushMessages(): PushMessages

    @ApplicationScope
    fun applicationScope(): CoroutineScope

    companion object {
        fun from(context: Context): PushEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, PushEntryPoint::class.java)
    }
}

class LocateDoMessagingService : FirebaseMessagingService() {
    // Registration tokens are deprecated in favor of installation ids, which the server does not target yet.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        val graph = PushEntryPoint.from(this)
        graph.applicationScope().launch {
            graph.deviceRegistration().received(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        PushEntryPoint.from(this).pushMessages().notifyReceived()
    }
}
