package com.locatedo.locatedo.core.push

import android.util.Log
import com.connectrpc.ConnectException
import com.google.firebase.messaging.FirebaseMessaging
import com.locatedo.device.v1.DeviceServiceClientInterface
import com.locatedo.device.v1.Platform
import com.locatedo.device.v1.registerDeviceRequest
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.auth.SignedOutException
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

interface PushTokenSource {
    suspend fun token(): String?
}

// Firebase is absent from builds without google-services.json, which is a missing token, not a crash.
// Registration tokens are deprecated in favor of installation ids, which the server does not target yet.
class FirebasePushTokenSource @Inject constructor() : PushTokenSource {
    @Suppress("DEPRECATION")
    override suspend fun token(): String? = try {
        FirebaseMessaging.getInstance().token.await()
    } catch (e: Exception) {
        Log.w(TAG, "Could not get the FCM token", e)
        null
    }

    private companion object {
        const val TAG = "Push"
    }
}

// Tells the server which device to wake once the user is signed in; the token is refreshed by the messaging service.
@Singleton
class DeviceRegistration @Inject constructor(
    private val devices: DeviceServiceClientInterface,
    private val authenticator: Authenticator,
    private val tokenSource: PushTokenSource,
) {
    private val _pushToken = MutableStateFlow<String?>(null)

    val pushToken: StateFlow<String?> = _pushToken

    suspend fun received(token: String) {
        _pushToken.value = token
        registerIfSignedIn()
    }

    suspend fun registerIfSignedIn() {
        if (authenticator.current() == null) {
            return
        }
        val token = _pushToken.value ?: tokenSource.token()?.also { _pushToken.value = it } ?: return
        try {
            authenticator.authorized {
                devices.registerDevice(
                    registerDeviceRequest {
                        platform = Platform.PLATFORM_ANDROID
                        pushToken = token
                    },
                )
            }
        } catch (e: ConnectException) {
            Log.w(TAG, "Could not register the push token", e)
        } catch (e: SignedOutException) {
            Log.w(TAG, "Could not register the push token", e)
        }
    }

    private companion object {
        const val TAG = "Push"
    }
}

// Data messages carry nothing; the app pulls when one arrives.
@Singleton
class PushMessages @Inject constructor() {
    private val _received = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val received: SharedFlow<Unit> = _received

    fun notifyReceived() {
        _received.tryEmit(Unit)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {
    @Binds
    abstract fun pushTokenSource(source: FirebasePushTokenSource): PushTokenSource
}
