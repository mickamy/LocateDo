package com.locatedo.locatedo.screens.placeeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.analytics.EditorMode
import com.locatedo.locatedo.core.analytics.TodoAddVia
import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.paywallTrigger
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
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceSource
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.places.CategoryGuess
import com.locatedo.locatedo.core.places.PlaceDuplicate
import com.locatedo.locatedo.core.places.PlaceDuplicateChoice
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

// What is being typed and picked for a place, before it is saved.
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
    // Opened from the to-do sheet: it stops at the category and hands the place back, since the to-do being written
    // is the one for it.
    val isForTodo: Boolean = false,
) {
    val isEditing: Boolean
        get() = placeId != null

    // A new place's to-do rows once trimmed, the half-typed one included.
    val todoTitles: List<String>
        get() = (todos + todoDraft).map(String::trim).filter(String::isNotEmpty)

    val canSave: Boolean
        get() = name.isNotBlank() && coordinate != null

    // A new pick guesses the category again, unless one was chosen by hand.
    fun picked(pick: PickPreview, coordinate: Coordinate, categories: List<Category>): PlaceDraft {
        var newName = name
        var newPickedName = pickedName
        if (name.isBlank() || name == pickedName) {
            newName = pick.name.orEmpty()
            newPickedName = pick.name
        }
        val suggested = categories.firstOrNull { pick.suggestion != null && it.builtin == pick.suggestion }
        return copy(
            coordinate = coordinate,
            address = pick.address,
            source = pick.source,
            name = newName,
            pickedName = newPickedName,
            suggestion = pick.suggestion,
            categoryId = if (hasChosenCategory || isEditing) categoryId else suggested?.id,
        )
    }
}

// A point on the pick map, with what is known about it so far.
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
    // A saved place within reach of the confirmed pin, asked about before going on.
    val duplicate: Place? = null,
) {
    // Free-plan room left after the rows already typed; null on Pro.
    val todosLeft: Int?
        get() = remainingOpenTodos?.let { (it - draft.todos.size).coerceAtLeast(0) }
}

sealed interface PlaceEditorEvent {
    data object PredictionFetched : PlaceEditorEvent

    data object LocationChosen : PlaceEditorEvent

    data class OpenSavedPlace(val placeId: UUID) : PlaceEditorEvent

    data class Saved(val placeId: UUID, val isNew: Boolean) : PlaceEditorEvent
}

enum class NewPlaceStep(val key: String) {
    DETAILS("details"),
    CATEGORY("category"),
    TODOS("todos"),
}

// Scoped to the activity: the map, search, details, category, and to-do screens share one draft.
@OptIn(FlowPreview::class)
@HiltViewModel
class PlaceEditorViewModel @Inject constructor(
    private val places: PlaceRepository,
    private val todos: TodoRepository,
    categories: CategoryRepository,
    private val placesSearch: PlacesRepository,
    private val geocoding: GeocodingRepository,
    private val location: LocationRepository,
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
    private val duplicate = MutableStateFlow<Place?>(null)
    private val _events = MutableSharedFlow<PlaceEditorEvent>()
    private var previewJob: Job? = null

    val events: SharedFlow<PlaceEditorEvent> = _events

    val uiState: StateFlow<PlaceEditorUiState> = combine(
        combine(draft, categories.observeAll()) { draft, categories -> draft to categories },
        combine(query, predictions, isSearching) { query, predictions, searching -> Triple(query, predictions, searching) },
        pickPreview,
        remainingOpenTodos,
        duplicate,
    ) { (draft, categories), (query, predictions, searching), preview, remaining, duplicate ->
        PlaceEditorUiState(
            draft = draft,
            categories = categories,
            query = query,
            predictions = predictions,
            isSearching = searching,
            pickPreview = preview,
            remainingOpenTodos = remaining,
            duplicate = duplicate,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlaceEditorUiState())

    init {
        viewModelScope.launch {
            query.debounce(SEARCH_DEBOUNCE_MILLIS).collect { text ->
                if (text.isBlank()) {
                    predictions.value = emptyList()
                    return@collect
                }
                isSearching.value = true
                val origin = location.lastCoordinate()
                val near = draft.value.coordinate ?: origin
                predictions.value = placesSearch.autocomplete(text, near, origin)
                isSearching.value = false
            }
        }
    }

    // A fresh draft: empty for a new place, or the stored place when editing.
    fun start(placeId: UUID?, isForTodo: Boolean = false) {
        query.value = ""
        predictions.value = emptyList()
        previewJob?.cancel()
        pickPreview.value = PickPreview()
        duplicate.value = null
        draft.value = PlaceDraft(placeId = placeId, isForTodo = isForTodo)
        viewModelScope.launch {
            if (placeId == null) {
                remainingOpenTodos.value = todos.remainingOpen()
                draft.update { it.copy(radiusMeters = preferences.data.first().defaultRadiusMeters) }
                return@launch
            }
            val stored = places.observeWithTodos(placeId).first()?.place ?: return@launch
            draft.value = PlaceDraft(
                placeId = stored.id,
                name = stored.name,
                coordinate = Coordinate(stored.latitude, stored.longitude),
                radiusMeters = stored.radiusMeters,
                categoryId = stored.categoryId,
            )
        }
    }

    // The details screen reports itself here because the draft, not the screen, knows whether this is an edit.
    fun detailsShown() {
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
            val candidate = placesSearch.fetch(prediction.id) ?: return@launch
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

    // Choosing on the map again from the details of a place being edited starts with no pin.
    fun pickOnMap() {
        previewJob?.cancel()
        pickPreview.value = PickPreview()
    }

    suspend fun lastKnownCoordinate(): Coordinate? = location.lastCoordinate()

    // Shown on the map like any pick, so it is confirmed the same way.
    fun previewCurrentLocation() {
        viewModelScope.launch {
            previewPick(location.lastCoordinate() ?: return@launch, source = PlaceSource.CURRENT_LOCATION)
        }
    }

    // A store tapped on the map keeps its own name, and its id tells its kind; the address comes from geocoding
    // either way.
    fun previewPick(coordinate: Coordinate, name: String? = null, placeId: String? = null, source: PlaceSource = PlaceSource.MAP) {
        previewJob?.cancel()
        pickPreview.value = PickPreview(coordinate = coordinate, name = name, isLoading = true, source = source)
        previewJob = viewModelScope.launch {
            val geocoded = geocoding.reverse(coordinate)
            val suggestion = placeId?.let { CategoryGuess.category(placesSearch.types(it)) }
            pickPreview.update { current ->
                if (current.coordinate != coordinate) {
                    current
                } else {
                    current.copy(name = name ?: geocoded?.name, address = geocoded?.address, isLoading = false, suggestion = suggestion)
                }
            }
        }
    }

    // A new pin on a place already saved asks first: its to-dos most likely belong there.
    fun confirmPick() {
        val preview = pickPreview.value
        val coordinate = preview.coordinate ?: return
        viewModelScope.launch {
            draft.update { it.picked(preview, coordinate, uiState.value.categories) }
            query.value = ""
            predictions.value = emptyList()
            if (!draft.value.isEditing) {
                val existing = PlaceDuplicate.nearest(coordinate, places.observeAll().first())
                if (existing != null) {
                    duplicate.value = existing
                    return@launch
                }
            }
            _events.emit(PlaceEditorEvent.LocationChosen)
        }
    }

    fun answerDuplicate(choice: PlaceDuplicateChoice) {
        val existing = duplicate.value ?: return
        duplicate.value = null
        analytics.log(AnalyticsEvent.PLACE_DUPLICATE_PROMPTED, mapOf(AnalyticsParameter.CHOICE to choice.key))
        viewModelScope.launch {
            when (choice) {
                PlaceDuplicateChoice.OPEN -> _events.emit(PlaceEditorEvent.OpenSavedPlace(existing.id))
                PlaceDuplicateChoice.ADD -> _events.emit(PlaceEditorEvent.LocationChosen)
                PlaceDuplicateChoice.CANCEL -> Unit
            }
        }
    }

    fun save() {
        val current = draft.value
        val coordinate = current.coordinate
        if (!current.canSave || coordinate == null) {
            return
        }
        viewModelScope.launch {
            val editedId = current.placeId
            if (editedId != null) {
                val stored = places.observeWithTodos(editedId).first()?.place ?: return@launch
                places.update(
                    stored.copy(
                        name = current.name.trim(),
                        latitude = coordinate.latitude,
                        longitude = coordinate.longitude,
                        radiusMeters = current.radiusMeters,
                        categoryId = current.categoryId,
                    ),
                )
                _events.emit(PlaceEditorEvent.Saved(placeId = editedId, isNew = false))
                return@launch
            }
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
            // To-dos typed with the place follow it, as many as the free limit leaves room for.
            var titles = current.todoTitles
            todos.remainingOpen()?.let { titles = titles.take(it) }
            val limit = places.add(place, source = current.source, todoCount = titles.size, suggestedCategory = current.suggestion)
            if (limit != null) {
                paywallRequests.request(limit.paywallTrigger)
                return@launch
            }
            for (title in titles) {
                val createdAt = clock.instant()
                todos.add(Todo(id = uuidV7(createdAt), title = title, placeId = place.id, createdAt = createdAt), TodoAddVia.PLACE_EDITOR)
            }
            _events.emit(PlaceEditorEvent.Saved(placeId = place.id, isNew = true))
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
