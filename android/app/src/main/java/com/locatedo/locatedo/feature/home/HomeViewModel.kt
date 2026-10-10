package com.locatedo.locatedo.feature.home

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
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import com.locatedo.locatedo.core.sync.SyncEngine
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
    val places: List<PlaceWithTodos> = emptyList(),
    val nearby: List<NearbyPlace> = emptyList(),
    val categories: Map<UUID, Category> = emptyMap(),
    val here: Coordinate? = null,
    val isSignedIn: Boolean = false,
    val isRefreshing: Boolean = false,
    val permissionBanner: PermissionBanner? = null,
    val hasRequestedPreciseLocation: Boolean = false,
) {
    val openTodoCount: Int
        get() = places.sumOf { it.openTodos.size }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    placeRepository: PlaceRepository,
    categoryRepository: CategoryRepository,
    private val locationRepository: LocationRepository,
    private val permissions: PermissionsRepository,
    authenticator: Authenticator,
    private val sync: SyncEngine,
    private val analytics: Analytics,
    clock: Clock,
) : ViewModel() {
    private val alwaysPrompt = AlwaysPromptTracker(analytics, clock)
    private val _isExplainingAlwaysLocation = MutableStateFlow(false)
    private val currentCoordinate = MutableStateFlow<Coordinate?>(null)
    private val isRefreshing = MutableStateFlow(false)

    val isExplainingAlwaysLocation: StateFlow<Boolean> = _isExplainingAlwaysLocation

    private val content = combine(
        placeRepository.observeAllWithTodos(),
        categoryRepository.observeAll(),
        currentCoordinate,
        authenticator.session,
        isRefreshing,
    ) { places, categories, here, session, refreshing ->
        HomeUiState(
            isLoading = false,
            places = places,
            nearby = Nearby.sort(places, here),
            categories = categories.associateBy { it.id },
            here = here,
            isSignedIn = session != null,
            isRefreshing = refreshing,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(content, permissions.observe()) { state, granted ->
        state.copy(
            permissionBanner = PermissionBanner.of(granted, state.places.isNotEmpty()),
            hasRequestedPreciseLocation = granted.hasRequestedPreciseLocation,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    fun refreshPermissions() = permissions.refresh()

    fun preciseLocationRequested() {
        viewModelScope.launch {
            permissions.markPreciseLocationRequested()
            permissions.refresh()
        }
    }

    // The "all the time" banner explains first, as the Settings button does; the others go straight to system settings.
    fun permissionBannerTapped(banner: PermissionBanner) {
        analytics.log(AnalyticsEvent.PERMISSION_BANNER_TAPPED, mapOf(AnalyticsParameter.KIND to banner.key))
        if (banner == PermissionBanner.LOCATION_ALWAYS) {
            alwaysPrompt.shown()
            _isExplainingAlwaysLocation.value = true
        }
    }

    fun alwaysLocationAnswered(answer: AlwaysPromptAnswer) = alwaysPrompt.answered(answer)

    fun dismissAlwaysLocation() {
        _isExplainingAlwaysLocation.value = false
        permissions.refresh()
    }

    fun hasLocationPermission(): Boolean = locationRepository.hasForegroundPermission()

    // Orders the places by distance and centers the small map.
    fun refreshLocation() {
        viewModelScope.launch {
            val coordinate = locationRepository.lastCoordinate() ?: return@launch
            currentCoordinate.value = coordinate
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

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
