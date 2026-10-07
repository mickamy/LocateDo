package com.locatedo.locatedo.core.api

import com.locatedo.locatedo.BuildConfig
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The server's plain HTTP health endpoint, for the debug section's connection check.
interface HealthCheck {
    suspend fun statusCode(): Int
}

class HttpHealthCheck @Inject constructor() : HealthCheck {
    override suspend fun statusCode(): Int = withContext(Dispatchers.IO) {
        val connection = URL("${BuildConfig.API_BASE_URL}/healthz").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.useCaches = false
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthCheckModule {
    @Binds
    abstract fun healthCheck(check: HttpHealthCheck): HealthCheck
}
