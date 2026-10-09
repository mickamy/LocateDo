package com.locatedo.locatedo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.analytics.AlwaysPromptAnswer
import com.locatedo.locatedo.core.analytics.AlwaysPromptTracker
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.common.Nearby
import com.locatedo.locatedo.core.common.NearbyPlace
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
    val permissionBanner: PermissionBanner? = null,
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
    private val analytics: Analytics,
    clock: Clock,
) : ViewModel() {
    private val alwaysPrompt = AlwaysPromptTracker(analytics, clock)
    private val _isExplainingAlwaysLocation = MutableStateFlow(false)
    private val currentCoordinate = MutableStateFlow<Coordinate?>(null)
    private val _cameraTargets = MutableSharedFlow<Coordinate>()

    val isExplainingAlwaysLocation: StateFlow<Boolean> = _isExplainingAlwaysLocation

    private val content = combine(
        placeRepository.observeAllWithTodos(),
        categoryRepository.observeAll(),
        currentCoordinate,
    ) { places, categories, here ->
        HomeUiState(
            isLoading = false,
            places = places,
            nearby = Nearby.sort(places, here),
            categories = categories.associateBy { it.id },
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(content, permissions.observe()) { state, granted ->
        state.copy(permissionBanner = PermissionBanner.of(granted.location, granted.notifications, state.places.isNotEmpty()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    // One-off camera moves, like the Google Maps "my location" button.
    val cameraTargets: SharedFlow<Coordinate> = _cameraTargets

    fun refreshPermissions() = permissions.refresh()

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

    fun locateMe() {
        viewModelScope.launch {
            val coordinate = refreshCurrentCoordinate() ?: return@launch
            _cameraTargets.emit(coordinate)
        }
    }

    private suspend fun refreshCurrentCoordinate(): Coordinate? {
        val coordinate = locationRepository.lastCoordinate()
        if (coordinate != null) {
            currentCoordinate.value = coordinate
        }
        return coordinate
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
