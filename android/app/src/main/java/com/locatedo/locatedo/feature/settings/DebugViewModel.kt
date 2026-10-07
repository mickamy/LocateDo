package com.locatedo.locatedo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.BuildConfig
import com.locatedo.locatedo.core.api.HealthCheck
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.data.SyncStateRepository
import com.locatedo.locatedo.core.sync.SyncEngine
import com.locatedo.locatedo.core.sync.WriteQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DebugUiState(
    val server: String = BuildConfig.API_BASE_URL,
    val userId: String? = null,
    val householdId: String? = null,
    val cursor: Long = 0,
    val queuedWrites: Int = 0,
    val lastPull: String? = null,
    val serverStatus: String? = null,
)

// Debug and staging builds: what the device knows about its account and sync, and a way to poke the server.
@HiltViewModel
class DebugViewModel @Inject constructor(
    authenticator: Authenticator,
    syncStateRepository: SyncStateRepository,
    queue: WriteQueue,
    private val syncEngine: SyncEngine,
    private val healthCheck: HealthCheck,
) : ViewModel() {
    private val serverStatus = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DebugUiState> = combine(
        authenticator.session,
        syncStateRepository.observe(),
        queue.size,
        syncEngine.lastPullSummary,
        serverStatus,
    ) { session, syncState, queued, lastPull, status ->
        DebugUiState(
            userId = session?.userId?.toString(),
            householdId = syncState.householdId?.toString(),
            cursor = syncState.cursor,
            queuedWrites = queued,
            lastPull = lastPull,
            serverStatus = status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DebugUiState())

    fun syncNow() {
        viewModelScope.launch { syncEngine.sync() }
    }

    fun checkConnection() {
        serverStatus.value = "Checking…"
        viewModelScope.launch {
            serverStatus.value = try {
                "HTTP ${healthCheck.statusCode()}"
            } catch (e: IOException) {
                e.toString()
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
