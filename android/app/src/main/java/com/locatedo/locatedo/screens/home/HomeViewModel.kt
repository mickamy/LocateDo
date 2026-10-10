package com.locatedo.locatedo.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.analytics.AlwaysPromptAnswer
import com.locatedo.locatedo.core.analytics.AlwaysPromptTracker
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.common.Nearby
import com.locatedo.locatedo.core.common.NearbyPlace
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import com.locatedo.locatedo.core.sync.SyncEngine
import com.locatedo.locatedo.logic.PermissionBanner
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val nearby: List<NearbyPlace> = emptyList(),
    val categories: Map<UUID, Category> = emptyMap(),
    val here: Coordinate? = null,
    val permissionBanner: PermissionBanner? = null,
    val hasRequestedPreciseLocation: Boolean = false,
    val isSignedIn: Boolean = false,
    val isRefreshing: Boolean = false,
) {
    val hasPlaces: Boolean
        get() = nearby.isNotEmpty()

    val openTodoCount: Int
        get() = nearby.sumOf { it.entry.openTodos.size }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    places: PlaceRepository,
    categories: CategoryRepository,
    private val location: LocationRepository,
    private val permissions: PermissionsRepository,
    authenticator: Authenticator,
    private val sync: SyncEngine,
    private val analytics: Analytics,
    clock: Clock,
) : ViewModel() {
    private val alwaysPrompt = AlwaysPromptTracker(analytics, clock)
    private val here = MutableStateFlow<Coordinate?>(null)
    private val isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        places.observeAllWithTodos(),
        categories.observeAll(),
        here,
        permissions.observe(),
        combine(authenticator.session, isRefreshing) { session, refreshing -> (session != null) to refreshing },
    ) { entries, categories, here, granted, (isSignedIn, refreshing) ->
        HomeUiState(
            isLoading = false,
            nearby = Nearby.sort(entries, here),
            categories = categories.associateBy { it.id },
            here = here,
            permissionBanner = PermissionBanner.of(granted, entries.isNotEmpty()),
            hasRequestedPreciseLocation = granted.hasRequestedPreciseLocation,
            isSignedIn = isSignedIn,
            isRefreshing = refreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    // Back on screen: permissions may have changed in system settings, and the nearest place may have.
    fun resumed() {
        permissions.refresh()
        viewModelScope.launch {
            here.value = location.lastCoordinate() ?: return@launch
        }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                sync.sync()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun permissionBannerTapped(banner: PermissionBanner) {
        analytics.log(AnalyticsEvent.PERMISSION_BANNER_TAPPED, mapOf(AnalyticsParameter.KIND to banner.key))
        if (banner == PermissionBanner.LOCATION_ALWAYS) {
            alwaysPrompt.shown()
        }
    }

    fun alwaysLocationAnswered(answer: AlwaysPromptAnswer) {
        alwaysPrompt.answered(answer)
        permissions.refresh()
    }

    fun preciseLocationRequested() {
        viewModelScope.launch {
            permissions.markPreciseLocationRequested()
            permissions.refresh()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
