package com.locatedo.locatedo.core.appstatus

import android.util.Log
import com.locatedo.locatedo.core.common.di.ApplicationScope
import com.locatedo.locatedo.core.datastore.AppPreferences
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AppStatusConfig(val url: String?, val currentVersion: String)

// What the last status says, evaluated at `now`; the store re-evaluates it on every refresh and at each window edge.
data class AppStatusState(
    val document: AppStatusDocument? = null,
    val now: Instant = Instant.EPOCH,
    val requiresUpdate: Boolean = false,
    val dismissedMaintenance: String? = null,
    val shownNotices: Set<String> = emptySet(),
) {
    val maintenancePhase: MaintenancePhase
        get() = MaintenancePhase.of(document?.maintenance, now)

    val activeMaintenance: AppStatusDocument.Maintenance?
        get() = (maintenancePhase as? MaintenancePhase.Active)?.maintenance

    val showsUpcomingBanner: Boolean
        get() {
            val upcoming = maintenancePhase as? MaintenancePhase.Upcoming ?: return false
            return upcoming.maintenance.key != dismissedMaintenance
        }

    val pendingNotice: AppStatusDocument.Notice?
        get() {
            val notice = document?.notice ?: return null
            if (!now.isBefore(notice.until) || notice.id in shownNotices) {
                return null
            }
            return notice
        }
}

// Reads the app status at launch and on every return to the foreground. A status that cannot be read leaves the last
// one in force; a device that never read one is not restricted at all.
@Singleton
class AppStatusStore @Inject constructor(
    private val config: AppStatusConfig,
    private val fetcher: AppStatusFetcher,
    private val preferences: AppPreferences,
    private val gate: MaintenanceGate,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AppStatusState())
    private val _maintenanceEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val refreshes = Mutex()
    private var isLoaded = false
    private var boundary: Job? = null

    val state: StateFlow<AppStatusState> = _state

    // The maintenance window closed while the app was running; sync can go again.
    val maintenanceEnded: SharedFlow<Unit> = _maintenanceEnded

    suspend fun refresh() = refreshes.withLock {
        loadStoredOnce()
        val url = config.url
        if (url != null) {
            try {
                val text = fetcher.fetch(url)
                val document = AppStatusDocument.decode(text)
                preferences.setAppStatusDocument(text)
                _state.update { it.copy(document = document) }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the app status", e)
            }
        }
        apply()
    }

    suspend fun dismissUpcomingBanner() {
        val upcoming = _state.value.maintenancePhase as? MaintenancePhase.Upcoming ?: return
        preferences.setDismissedMaintenance(upcoming.maintenance.key)
        _state.update { it.copy(dismissedMaintenance = upcoming.maintenance.key) }
    }

    suspend fun markNoticeShown(notice: AppStatusDocument.Notice) {
        val shown = _state.value.shownNotices + notice.id
        preferences.setShownNotices(shown)
        _state.update { it.copy(shownNotices = shown) }
    }

    private suspend fun loadStoredOnce() {
        if (isLoaded) {
            return
        }
        isLoaded = true
        val stored = preferences.appStatus.first()
        val document = stored.document?.let { text ->
            try {
                AppStatusDocument.decode(text)
            } catch (e: Exception) {
                Log.w(TAG, "Dropping an unreadable stored app status", e)
                null
            }
        }
        _state.update {
            it.copy(document = document, dismissedMaintenance = stored.dismissedMaintenance, shownNotices = stored.shownNotices)
        }
    }

    private fun apply() {
        val now = clock.instant()
        val document = _state.value.document
        val minimum = document?.minimumVersion?.android
        val requiresUpdate = minimum != null && AppVersion.isOlder(config.currentVersion, minimum)
        _state.update { it.copy(now = now, requiresUpdate = requiresUpdate) }
        gate.update(document?.maintenance, requiresUpdate)
        scheduleBoundary(document?.maintenance, now)
    }

    // Wakes up when the window starts or ends, so the phase changes without another fetch.
    private fun scheduleBoundary(maintenance: AppStatusDocument.Maintenance?, now: Instant) {
        boundary?.cancel()
        val next = listOfNotNull(maintenance?.startsAt, maintenance?.endsAt).filter { it.isAfter(now) }.minOrNull() ?: return
        boundary = scope.launch {
            delay(Duration.between(now, next).toMillis())
            crossBoundary()
        }
    }

    private suspend fun crossBoundary() {
        refreshes.withLock {
            val wasActive = _state.value.maintenancePhase is MaintenancePhase.Active
            apply()
            if (wasActive && _state.value.maintenancePhase is MaintenancePhase.None) {
                _maintenanceEnded.tryEmit(Unit)
            }
        }
    }

    private companion object {
        const val TAG = "AppStatus"
    }
}
