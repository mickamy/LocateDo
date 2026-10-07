package com.locatedo.locatedo.core.push

import android.util.Log
import com.connectrpc.ConnectException
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.locatedo.device.v1.DeviceServiceClientInterface
import com.locatedo.device.v1.Platform
import com.locatedo.device.v1.registerDeviceRequest
import com.locatedo.locatedo.core.api.getOrThrow
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.auth.SignedOutException
import com.locatedo.locatedo.core.datastore.AppPreferences
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
import kotlinx.coroutines.flow.first
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

// Tells the server which installation to wake. Signed-in devices always register; signed-out ones only while
// promotions consent is on, plus once right after turning it off so the server drops the anonymous row.
@Singleton
class DeviceRegistration @Inject constructor(
    private val devices: DeviceServiceClientInterface,
    private val authenticator: Authenticator,
    private val source: InstallationIdSource,
    private val preferences: AppPreferences,
    private val language: DisplayLanguage,
) {
    private val _installationId = MutableStateFlow<String?>(null)

    val installationId: StateFlow<String?> = _installationId

    suspend fun received(installationId: String) {
        _installationId.value = installationId
        registerIfNeeded()
    }

    suspend fun registerIfNeeded() {
        val consent = preferences.promotions.first().consent
        if (authenticator.current() == null && !consent) {
            return
        }
        register(consent)
    }

    suspend fun consentChanged() {
        register(preferences.promotions.first().consent)
    }

    private suspend fun register(consent: Boolean) {
        val id = _installationId.value ?: source.installationId()?.also { _installationId.value = it } ?: return
        val request = registerDeviceRequest {
            platform = Platform.PLATFORM_ANDROID
            pushToken = id
            promotionsConsent = consent
            completionNotices = preferences.completionNotices.first()
            language = this@DeviceRegistration.language.current()
        }
        try {
            if (authenticator.current() == null) {
                devices.registerDevice(request).getOrThrow()
            } else {
                authenticator.authorized { devices.registerDevice(request) }
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

    @Binds
    abstract fun displayLanguage(language: ResourcesDisplayLanguage): DisplayLanguage
}
