package com.locatedo.locatedo.core.appstatus

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AppStatusFetcher {
    suspend fun fetch(url: String): String
}

// A plain GET that skips every cache: the file is served with must-revalidate and read a few times a day at most.
class HttpAppStatusFetcher @Inject constructor() : AppStatusFetcher {
    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("app status returned HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000
    }
}
