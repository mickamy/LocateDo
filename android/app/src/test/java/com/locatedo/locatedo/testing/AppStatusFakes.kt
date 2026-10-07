package com.locatedo.locatedo.testing

import com.locatedo.locatedo.core.appstatus.AppStatusConfig
import com.locatedo.locatedo.core.appstatus.AppStatusFetcher
import com.locatedo.locatedo.core.appstatus.AppStatusStore
import com.locatedo.locatedo.core.appstatus.MaintenanceGate
import com.locatedo.locatedo.core.data.fixedClock
import com.locatedo.locatedo.core.datastore.AppPreferences
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.CoroutineScope

class FakeAppStatusFetcher(var response: String? = null) : AppStatusFetcher {
    var fetches = 0

    override suspend fun fetch(url: String): String {
        fetches += 1
        return response ?: throw IOException("offline")
    }
}

fun appStatusStore(
    preferences: AppPreferences,
    scope: CoroutineScope,
    fetcher: AppStatusFetcher = FakeAppStatusFetcher(),
    clock: Clock = fixedClock,
    version: String = "1.0",
    gate: MaintenanceGate = MaintenanceGate(clock),
): AppStatusStore = AppStatusStore(
    config = AppStatusConfig(url = "https://locatedo.com/app-status-stg.json", currentVersion = version),
    fetcher = fetcher,
    preferences = preferences,
    gate = gate,
    clock = clock,
    scope = scope,
)
