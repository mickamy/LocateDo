package com.locatedo.locatedo.core.push

import android.util.Log
import com.connectrpc.ConnectException
import com.google.firebase.installations.FirebaseInstallations
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

interface InstallationIdSource {
    suspend fun installationId(): String?
}

// Registers the installation with FCM and reads its id. Firebase is absent from builds without
// google-services.json, which is a missing id, not a crash.
class FirebaseInstallationIdSource @Inject constructor() : InstallationIdSource {
    override suspend fun installationId(): String? = try {
        FirebaseMessaging.getInstance().register().await()
        FirebaseInstallations.getInstance().id.await()
    } catch (e: Exception) {
        Log.w(TAG, "Could not register with FCM", e)
        null
    }

    private companion object {
        const val TAG = "Push"
    }
}

// Tells the server which installation to wake once the user is signed in; the messaging service reports the id at
// startup and whenever it changes.
@Singleton
class DeviceRegistration @Inject constructor(
    private val devices: DeviceServiceClientInterface,
    private val authenticator: Authenticator,
    private val source: InstallationIdSource,
) {
    private val _installationId = MutableStateFlow<String?>(null)

    val installationId: StateFlow<String?> = _installationId

    suspend fun received(installationId: String) {
        _installationId.value = installationId
        registerIfSignedIn()
    }

    suspend fun registerIfSignedIn() {
        if (authenticator.current() == null) {
            return
        }
        val id = _installationId.value ?: source.installationId()?.also { _installationId.value = it } ?: return
        try {
            authenticator.authorized {
                devices.registerDevice(
                    registerDeviceRequest {
                        platform = Platform.PLATFORM_ANDROID
                        pushToken = id
                    },
                )
            }
        } catch (e: ConnectException) {
            Log.w(TAG, "Could not register the installation", e)
        } catch (e: SignedOutException) {
            Log.w(TAG, "Could not register the installation", e)
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
    abstract fun installationIdSource(source: FirebaseInstallationIdSource): InstallationIdSource
}
