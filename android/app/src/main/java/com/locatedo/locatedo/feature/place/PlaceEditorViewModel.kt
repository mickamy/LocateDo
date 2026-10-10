package com.locatedo.locatedo.feature.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.analytics.EditorMode
import com.locatedo.locatedo.core.analytics.TodoAddVia
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.data.TodoRepository
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.location.GeocodingRepository
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.paywallTrigger
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceSource
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.places.CategoryGuess
import com.locatedo.locatedo.core.places.PlacePrediction
import com.locatedo.locatedo.core.places.PlacesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
    val todos: List<String> = emptyList(),
    val todoDraft: String = "",
    val suggestion: BuiltinCategory? = null,
    val hasChosenCategory: Boolean = false,
    // The name the last pick filled in, so a new pick can replace it but never a name typed by hand.
    val pickedName: String? = null,
) {
    val isEditing: Boolean
        get() = placeId != null

    // What a new place's to-do rows hold once trimmed, the half-typed one included.
    val todoTitles: List<String>
        get() = (todos + todoDraft).map(String::trim).filter(String::isNotEmpty)

    val canSave: Boolean
        get() = name.isNotBlank() && coordinate != null
}

data class PickPreview(
    val coordinate: Coordinate? = null,
    val name: String? = null,
    val address: String? = null,
    val isLoading: Boolean = false,
    val source: PlaceSource = PlaceSource.MAP,
    val suggestion: BuiltinCategory? = null,
)

data class PlaceEditorUiState(
    val draft: PlaceDraft = PlaceDraft(),
    val categories: List<Category> = emptyList(),
    val query: String = "",
    val predictions: List<PlacePrediction> = emptyList(),
    val isSearching: Boolean = false,
    val pickPreview: PickPreview = PickPreview(),
    val remainingOpenTodos: Int? = null,
) {
    // Free-plan room left after the rows already typed; null on Pro.
    val todosLeft: Int?
        get() = remainingOpenTodos?.let { (it - draft.todos.size).coerceAtLeast(0) }
}

sealed interface PlaceEditorEvent {
    data object PredictionFetched : PlaceEditorEvent
    data object LocationChosen : PlaceEditorEvent
    data class Saved(val isNew: Boolean) : PlaceEditorEvent
}

// The screens of adding a new place after the location; an existing place is edited in the details form alone.
enum class NewPlaceStep(val key: String) {
    DETAILS("details"),
    CATEGORY("category"),
    TODOS("todos"),
}

// Scoped to the activity, not a screen: the search, pick, and form screens share one draft.
@OptIn(FlowPreview::class)
@HiltViewModel
class PlaceEditorViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val todoRepository: TodoRepository,
    categoryRepository: CategoryRepository,
    private val placesRepository: PlacesRepository,
    private val geocodingRepository: GeocodingRepository,
    private val locationRepository: LocationRepository,
    private val preferences: AppPreferences,
    private val paywallRequests: PaywallRequests,
    private val analytics: Analytics,
    private val clock: Clock,
) : ViewModel() {
    private val draft = MutableStateFlow(PlaceDraft())
    private val query = MutableStateFlow("")
    private val predictions = MutableStateFlow<List<PlacePrediction>>(emptyList())
    private val isSearching = MutableStateFlow(false)
    private val pickPreview = MutableStateFlow(PickPreview())
    private val remainingOpenTodos = MutableStateFlow<Int?>(null)
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
        remainingOpenTodos,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        PlaceEditorUiState(
            draft = values[0] as PlaceDraft,
            categories = values[1] as List<Category>,
            query = values[2] as String,
            predictions = values[3] as List<PlacePrediction>,
            isSearching = values[4] as Boolean,
            pickPreview = values[5] as PickPreview,
            remainingOpenTodos = values[6] as Int?,
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
                val origin = locationRepository.lastCoordinate()
                val near = draft.value.coordinate ?: origin
                predictions.value = placesRepository.autocomplete(text, near, origin)
                isSearching.value = false
            }
        }
    }

    // Starts a fresh flow: an empty draft for a new place, or the stored place when editing.
    fun start(placeId: UUID?) {
        query.value = ""
        predictions.value = emptyList()
        pickPreview.value = PickPreview()
        draft.value = PlaceDraft(placeId = placeId)
        viewModelScope.launch {
            if (placeId == null) {
                remainingOpenTodos.value = todoRepository.remainingOpen()
                draft.update { it.copy(radiusMeters = preferences.data.first().defaultRadiusMeters) }
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

    // The form screen reports itself here because the draft, not the screen, knows whether this is an edit.
    fun editorShown() {
        if (draft.value.isEditing) {
            analytics.logScreen(AnalyticsScreen.PLACE_EDITOR, mapOf(AnalyticsParameter.MODE to EditorMode.EDIT.key))
            return
        }
        stepShown(NewPlaceStep.DETAILS)
    }

    fun stepShown(step: NewPlaceStep) {
        analytics.logScreen(
            AnalyticsScreen.PLACE_EDITOR,
            mapOf(AnalyticsParameter.MODE to EditorMode.NEW.key, AnalyticsParameter.STEP to step.key),
        )
    }

    fun setName(name: String) = draft.update { it.copy(name = name) }

    fun setRadius(meters: Double) = draft.update { it.copy(radiusMeters = meters) }

    fun setCategory(categoryId: UUID?) = draft.update { it.copy(categoryId = categoryId, hasChosenCategory = true) }

    fun setTodoDraft(text: String) = draft.update { it.copy(todoDraft = text) }

    // Return in the last row turns it into a to-do and leaves an empty row for the next one.
    fun addTodoDraft() {
        val title = draft.value.todoDraft.trim()
        if (title.isEmpty() || uiState.value.todosLeft == 0) {
            return
        }
        draft.update { it.copy(todos = it.todos + title, todoDraft = "") }
    }

    fun setTodo(index: Int, title: String) = draft.update { current ->
        current.copy(todos = current.todos.mapIndexed { i, old -> if (i == index) title else old })
    }

    fun removeTodo(index: Int) = draft.update { current ->
        current.copy(todos = current.todos.filterIndexed { i, _ -> i != index })
    }

    fun setQuery(text: String) {
        query.value = text
    }

    // A search result is shown on the pick map before it is used, so a store with many branches is checked by where it is.
    fun selectPrediction(prediction: PlacePrediction) {
        viewModelScope.launch {
            val candidate = placesRepository.fetch(prediction.id) ?: return@launch
            previewJob?.cancel()
            pickPreview.value = PickPreview(
                coordinate = candidate.coordinate,
                name = candidate.name ?: prediction.primaryText,
                address = candidate.address,
                source = PlaceSource.SEARCH,
                suggestion = CategoryGuess.category(candidate.types),
            )
            _events.emit(PlaceEditorEvent.PredictionFetched)
        }
    }

    fun pickOnMap() {
        previewJob?.cancel()
        pickPreview.value = PickPreview()
    }

    suspend fun lastKnownCoordinate(): Coordinate? = locationRepository.lastCoordinate()

    // Shown on the map like any pick, so it is confirmed the same way.
    fun previewCurrentLocation() {
        viewModelScope.launch {
            val coordinate = locationRepository.lastCoordinate() ?: return@launch
            previewPick(coordinate, source = PlaceSource.CURRENT_LOCATION)
        }
    }

    // A store tapped on the map keeps its own name, and its id tells its kind; the address comes from geocoding
    // either way.
    fun previewPick(
        coordinate: Coordinate,
        name: String? = null,
        placeId: String? = null,
        source: PlaceSource = PlaceSource.MAP,
    ) {
        previewJob?.cancel()
        pickPreview.value = PickPreview(coordinate = coordinate, name = name, isLoading = true, source = source)
        previewJob = viewModelScope.launch {
            val geocoded = geocodingRepository.reverse(coordinate)
            var suggestion: BuiltinCategory? = null
            if (placeId != null) {
                suggestion = CategoryGuess.category(placesRepository.types(placeId))
            }
            pickPreview.update { current ->
                if (current.coordinate != coordinate) {
                    current
                } else {
                    current.copy(
                        name = name ?: geocoded?.name,
                        address = geocoded?.address,
                        isLoading = false,
                        suggestion = suggestion,
                    )
                }
            }
        }
    }

    fun confirmPick() {
        val preview = pickPreview.value
        val coordinate = preview.coordinate ?: return
        viewModelScope.launch {
            choose(coordinate, preview.name, preview.address, preview.source, preview.suggestion)
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
                addPlace(current, coordinate)
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
            if (limit == null) {
                _events.emit(PlaceEditorEvent.Saved(isNew = current.placeId == null))
            } else {
                paywallRequests.request(limit.paywallTrigger)
            }
        }
    }

    // To-dos typed with the place follow it, as many as the free limit leaves room for.
    private suspend fun addPlace(current: PlaceDraft, coordinate: Coordinate): FreeLimit? {
        val now = clock.instant()
        val place = Place(
            id = uuidV7(now),
            name = current.name.trim(),
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            radiusMeters = current.radiusMeters,
            categoryId = current.categoryId ?: uiState.value.categories.firstOrNull { it.builtin == BuiltinCategory.OTHER }?.id,
            createdAt = now,
        )
        var titles = current.todoTitles
        todoRepository.remainingOpen()?.let { titles = titles.take(it) }
        val limit = placeRepository.add(
            place,
            source = current.source,
            todoCount = titles.size,
            suggestedCategory = current.suggestion,
        )
        if (limit != null) {
            return limit
        }
        for (title in titles) {
            val createdAt = clock.instant()
            todoRepository.add(
                Todo(id = uuidV7(createdAt), title = title, placeId = place.id, createdAt = createdAt),
                TodoAddVia.PLACE_EDITOR,
            )
        }
        return null
    }

    // A new pick guesses again, unless the category was already chosen by hand.
    private suspend fun choose(
        coordinate: Coordinate,
        name: String?,
        address: String?,
        source: PlaceSource,
        suggestion: BuiltinCategory?,
    ) {
        val suggested = uiState.value.categories.firstOrNull { suggestion != null && it.builtin == suggestion }
        draft.update { current ->
            var newName = current.name
            var pickedName = current.pickedName
            if (current.name.isBlank() || current.name == current.pickedName) {
                newName = name ?: ""
                pickedName = name
            }
            current.copy(
                coordinate = coordinate,
                address = address,
                source = source,
                name = newName,
                pickedName = pickedName,
                suggestion = suggestion,
                categoryId = if (current.hasChosenCategory || current.isEditing) current.categoryId else suggested?.id,
            )
        }
        query.value = ""
        predictions.value = emptyList()
        _events.emit(PlaceEditorEvent.LocationChosen)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
