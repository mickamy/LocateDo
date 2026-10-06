package com.locatedo.locatedo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.PlaceWithTodos
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val places: List<PlaceWithTodos> = emptyList(),
    val categories: Map<UUID, Category> = emptyMap(),
) {
    val openTodoCount: Int
        get() = places.sumOf { it.openTodos.size }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    placeRepository: PlaceRepository,
    categoryRepository: CategoryRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        placeRepository.observeAllWithTodos(),
        categoryRepository.observeAll(),
    ) { places, categories ->
        HomeUiState(isLoading = false, places = places, categories = categories.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    private val _cameraTargets = MutableSharedFlow<LatLng>()

    // One-off camera moves, like the Google Maps "my location" button.
    val cameraTargets: SharedFlow<LatLng> = _cameraTargets

    fun hasLocationPermission(): Boolean = locationRepository.hasForegroundPermission()

    fun locateMe() {
        viewModelScope.launch {
            val location = locationRepository.lastLocation() ?: return@launch
            _cameraTargets.emit(LatLng(location.latitude, location.longitude))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
