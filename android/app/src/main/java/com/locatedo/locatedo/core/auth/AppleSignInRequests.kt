package com.locatedo.locatedo.core.auth

import com.locatedo.locatedo.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppleSignInConfig(val servicesId: String, val redirectUri: String) {
    val isAvailable: Boolean
        get() = servicesId.isNotEmpty() && redirectUri.isNotEmpty()
}

data class AppleSignInCompletion(val result: AppleSignIn.Result, val nonce: String)

// The sign-in leaves the app for a browser tab and comes back through MainActivity, so the state and nonce wait
// here, and the outcome waits until one screen takes it.
@Singleton
class AppleSignInRequests @Inject constructor(private val config: AppleSignInConfig) {
    private data class Pending(val state: String, val nonce: String)

    private var pending: Pending? = null
    private val _completed = MutableStateFlow<AppleSignInCompletion?>(null)

    val isAvailable: Boolean
        get() = config.isAvailable

    val completed: StateFlow<AppleSignInCompletion?> = _completed

    fun start(): String? {
        if (!config.isAvailable) {
            return null
        }
        val request = Pending(state = Nonce.make(STATE_BYTES), nonce = Nonce.make())
        pending = request
        return AppleSignIn.authorizeUrl(config.servicesId, config.redirectUri, request.state, request.nonce)
    }

    // A callback with no sign-in under way is ignored.
    fun received(fragment: String?) {
        val request = pending ?: return
        pending = null
        _completed.value = AppleSignInCompletion(AppleSignIn.result(fragment, request.state), request.nonce)
    }

    fun consume(completion: AppleSignInCompletion): Boolean = _completed.compareAndSet(completion, null)

    private companion object {
        const val STATE_BYTES = 16
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppleSignInModule {
    @Provides
    fun config(): AppleSignInConfig = AppleSignInConfig(BuildConfig.APPLE_SERVICES_ID, BuildConfig.APPLE_REDIRECT_URI)
}
