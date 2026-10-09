package com.locatedo.locatedo.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.Place
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

data class MapUiState(
    val isLoading: Boolean = true,
    val places: List<Place> = emptyList(),
    val categories: Map<UUID, Category> = emptyMap(),
)

@HiltViewModel
class MapViewModel @Inject constructor(
    placeRepository: PlaceRepository,
    categoryRepository: CategoryRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {
    private val _cameraTargets = MutableSharedFlow<Coordinate>()

    val uiState: StateFlow<MapUiState> = combine(
        placeRepository.observeAll(),
        categoryRepository.observeAll(),
    ) { places, categories ->
        MapUiState(isLoading = false, places = places, categories = categories.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MapUiState())

    // One-off camera moves, like the Google Maps "my location" button.
    val cameraTargets: SharedFlow<Coordinate> = _cameraTargets

    fun hasLocationPermission(): Boolean = locationRepository.hasForegroundPermission()

    fun locateMe() {
        viewModelScope.launch {
            val coordinate = locationRepository.lastCoordinate() ?: return@launch
            _cameraTargets.emit(coordinate)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
