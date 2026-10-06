package com.locatedo.locatedo.feature.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.location.GeocodingRepository
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceSource
import com.locatedo.locatedo.core.places.PlacePrediction
import com.locatedo.locatedo.core.places.PlacesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaceDraft(
    val placeId: UUID? = null,
    val name: String = "",
    val coordinate: Coordinate? = null,
    val address: String? = null,
    val radiusMeters: Double = Place.DEFAULT_RADIUS_METERS,
    val categoryId: UUID? = null,
    val source: PlaceSource? = null,
) {
    val isEditing: Boolean
        get() = placeId != null

    val canSave: Boolean
        get() = name.isNotBlank() && coordinate != null
}

data class PickPreview(
    val coordinate: Coordinate? = null,
    val name: String? = null,
    val address: String? = null,
    val isLoading: Boolean = false,
)

data class PlaceEditorUiState(
    val draft: PlaceDraft = PlaceDraft(),
    val categories: List<Category> = emptyList(),
    val query: String = "",
    val predictions: List<PlacePrediction> = emptyList(),
    val isSearching: Boolean = false,
    val pickPreview: PickPreview = PickPreview(),
)

sealed interface PlaceEditorEvent {
    data object LocationChosen : PlaceEditorEvent
    data object Saved : PlaceEditorEvent
    data class LimitReached(val limit: FreeLimit) : PlaceEditorEvent
}

// Scoped to the activity, not a screen: the search, pick, and form screens share one draft.
@OptIn(FlowPreview::class)
@HiltViewModel
class PlaceEditorViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    categoryRepository: CategoryRepository,
    private val placesRepository: PlacesRepository,
    private val geocodingRepository: GeocodingRepository,
    private val locationRepository: LocationRepository,
    private val preferences: AppPreferences,
    private val clock: Clock,
) : ViewModel() {
    private val draft = MutableStateFlow(PlaceDraft())
    private val query = MutableStateFlow("")
    private val predictions = MutableStateFlow<List<PlacePrediction>>(emptyList())
    private val isSearching = MutableStateFlow(false)
    private val pickPreview = MutableStateFlow(PickPreview())
    private val _events = MutableSharedFlow<PlaceEditorEvent>()
    private var previewJob: Job? = null

    val events: SharedFlow<PlaceEditorEvent> = _events

    val uiState: StateFlow<PlaceEditorUiState> = combine(
        draft,
        categoryRepository.observeAll(),
        query,
        predictions,
        isSearching,
        pickPreview,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        PlaceEditorUiState(
            draft = values[0] as PlaceDraft,
            categories = values[1] as List<Category>,
            query = values[2] as String,
            predictions = values[3] as List<PlacePrediction>,
            isSearching = values[4] as Boolean,
            pickPreview = values[5] as PickPreview,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PlaceEditorUiState())

    init {
        viewModelScope.launch {
            query.debounce(SEARCH_DEBOUNCE_MILLIS).collect { text ->
                if (text.isBlank()) {
                    predictions.value = emptyList()
                    return@collect
                }
                isSearching.value = true
                val near = draft.value.coordinate ?: locationRepository.lastLocation()?.let { Coordinate(it.latitude, it.longitude) }
                predictions.value = placesRepository.autocomplete(text, near)
                isSearching.value = false
            }
        }
    }

    // Starts a fresh flow: an empty draft for a new place, or the stored place when editing.
    fun start(placeId: UUID?) {
        query.value = ""
        predictions.value = emptyList()
        pickPreview.value = PickPreview()
        draft.value = PlaceDraft()
        viewModelScope.launch {
            if (placeId == null) {
                val other = uiState.value.categories.firstOrNull { it.builtin == BuiltinCategory.OTHER }
                draft.update {
                    it.copy(
                        radiusMeters = preferences.data.first().defaultRadiusMeters,
                        categoryId = other?.id,
                    )
                }
                return@launch
            }
            val stored = placeRepository.observeWithTodos(placeId).first()?.place ?: return@launch
            draft.value = PlaceDraft(
                placeId = stored.id,
                name = stored.name,
                coordinate = Coordinate(stored.latitude, stored.longitude),
                radiusMeters = stored.radiusMeters,
                categoryId = stored.categoryId,
            )
        }
    }

    fun setName(name: String) = draft.update { it.copy(name = name) }

    fun setRadius(meters: Double) = draft.update { it.copy(radiusMeters = meters) }

    fun setCategory(categoryId: UUID?) = draft.update { it.copy(categoryId = categoryId) }

    fun setQuery(text: String) {
        query.value = text
    }

    fun selectPrediction(prediction: PlacePrediction) {
        viewModelScope.launch {
            val candidate = placesRepository.fetch(prediction.id) ?: return@launch
            choose(candidate.coordinate, candidate.name ?: prediction.primaryText, candidate.address, PlaceSource.SEARCH)
        }
    }

    suspend fun lastKnownCoordinate(): Coordinate? =
        locationRepository.lastLocation()?.let { Coordinate(it.latitude, it.longitude) }

    fun useCurrentLocation() {
        viewModelScope.launch {
            val location = locationRepository.lastLocation() ?: return@launch
            val coordinate = Coordinate(location.latitude, location.longitude)
            val geocoded = geocodingRepository.reverse(coordinate)
            choose(coordinate, geocoded?.name, geocoded?.address, PlaceSource.CURRENT_LOCATION)
        }
    }

    // Called as the pick map settles; the lookup is delayed so a moving map does not geocode every frame.
    fun previewPick(coordinate: Coordinate) {
        previewJob?.cancel()
        pickPreview.value = PickPreview(coordinate = coordinate, isLoading = true)
        previewJob = viewModelScope.launch {
            delay(PICK_PREVIEW_DELAY_MILLIS)
            val geocoded = geocodingRepository.reverse(coordinate)
            pickPreview.update { current ->
                if (current.coordinate != coordinate) current else current.copy(name = geocoded?.name, address = geocoded?.address, isLoading = false)
            }
        }
    }

    fun confirmPick() {
        val preview = pickPreview.value
        val coordinate = preview.coordinate ?: return
        viewModelScope.launch {
            choose(coordinate, preview.name, preview.address, PlaceSource.MAP)
        }
    }

    fun save() {
        val current = draft.value
        val coordinate = current.coordinate
        if (!current.canSave || coordinate == null) {
            return
        }
        viewModelScope.launch {
            val limit = if (current.placeId == null) {
                val now = clock.instant()
                placeRepository.add(
                    Place(
                        id = uuidV7(now),
                        name = current.name.trim(),
                        latitude = coordinate.latitude,
                        longitude = coordinate.longitude,
                        radiusMeters = current.radiusMeters,
                        categoryId = current.categoryId,
                        createdAt = now,
                    ),
                )
            } else {
                val stored = placeRepository.observeWithTodos(current.placeId).first()?.place ?: return@launch
                placeRepository.update(
                    stored.copy(
                        name = current.name.trim(),
                        latitude = coordinate.latitude,
                        longitude = coordinate.longitude,
                        radiusMeters = current.radiusMeters,
                        categoryId = current.categoryId,
                    ),
                )
                null
            }
            _events.emit(if (limit == null) PlaceEditorEvent.Saved else PlaceEditorEvent.LimitReached(limit))
        }
    }

    private suspend fun choose(coordinate: Coordinate, name: String?, address: String?, source: PlaceSource) {
        draft.update { current ->
            current.copy(
                coordinate = coordinate,
                address = address,
                source = source,
                name = current.name.ifBlank { name ?: "" },
            )
        }
        query.value = ""
        predictions.value = emptyList()
        _events.emit(PlaceEditorEvent.LocationChosen)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val PICK_PREVIEW_DELAY_MILLIS = 400L
    }
}
