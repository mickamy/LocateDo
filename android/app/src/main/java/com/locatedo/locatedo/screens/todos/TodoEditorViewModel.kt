package com.locatedo.locatedo.screens.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.analytics.PlacePreset
import com.locatedo.locatedo.core.analytics.ScreenEntry
import com.locatedo.locatedo.core.analytics.TodoAddOrigin
import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.paywallTrigger
import com.locatedo.locatedo.core.common.Geo
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.data.MembershipRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.data.TodoRepository
import com.locatedo.locatedo.core.data.TodoUndo
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceEvent
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.model.TodoDeletionVia
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodoDraft(
    val title: String = "",
    val placeId: UUID? = null,
    val assigneeId: UUID? = null,
    val isEditing: Boolean = false,
    // A place made from the picker here; saving a to-do at it lands on that place.
    val newPlaceId: UUID? = null,
    val remindsOnLeave: Boolean = false,
) {
    val canSave: Boolean
        get() = title.isNotBlank() && placeId != null
}

data class TodoEditorUiState(
    val draft: TodoDraft = TodoDraft(),
    val places: List<Place> = emptyList(),
    val members: List<Membership> = emptyList(),
)

sealed interface TodoEditorEvent {
    // landOn is a place just made for this to-do, opened once the sheet closes.
    data class Saved(val landOn: UUID?) : TodoEditorEvent

    data object Deleted : TodoEditorEvent
}

// Scoped to the activity, not the sheet: the sheet steps aside while a new place is added from it, and comes back
// with what was typed.
@HiltViewModel
class TodoEditorViewModel @Inject constructor(
    private val todos: TodoRepository,
    places: PlaceRepository,
    memberships: MembershipRepository,
    private val paywallRequests: PaywallRequests,
    private val undo: TodoUndo,
    private val location: LocationRepository,
    private val clock: Clock,
) : ViewModel() {
    // Where a new to-do was started and the place it started with, for the analytics.
    private data class Preset(val entry: ScreenEntry, val kind: PlacePreset, val placeId: UUID?)

    private val draft = MutableStateFlow(TodoDraft())
    private var editing: Todo? = null
    private var preset: Preset? = null
    private val _events = MutableSharedFlow<TodoEditorEvent>()

    val events: SharedFlow<TodoEditorEvent> = _events

    val uiState: StateFlow<TodoEditorUiState> = combine(draft, places.observeAll(), memberships.observeAll()) { draft, places, members ->
        TodoEditorUiState(draft = draft, places = places, members = members)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TodoEditorUiState())

    // A new to-do starts at the place given; from Home's plus menu at the nearest one, as far as the last known
    // location tells; or else at the first one.
    fun start(placeId: UUID?, entry: ScreenEntry) {
        editing = null
        draft.value = TodoDraft(placeId = placeId)
        if (placeId != null) {
            preset = Preset(entry, PlacePreset.PLACE, placeId)
            return
        }
        preset = Preset(entry, PlacePreset.NONE, null)
        viewModelScope.launch {
            val places = uiState.first { it.places.isNotEmpty() || it.draft.placeId != null }.places
            val (chosen, kind) = choose(places, entry) ?: return@launch
            if (draft.value.placeId != null) {
                return@launch
            }
            draft.update { it.copy(placeId = chosen.id) }
            preset = Preset(entry, kind, chosen.id)
        }
    }

    private suspend fun choose(places: List<Place>, entry: ScreenEntry): Pair<Place, PlacePreset>? {
        if (places.isEmpty()) {
            return null
        }
        if (entry == ScreenEntry.HOME_MENU) {
            val here = location.lastCoordinate()
            if (here != null) {
                val nearest = places.minBy { Geo.distanceMeters(here, Coordinate(it.latitude, it.longitude)) }
                return nearest to PlacePreset.NEAREST
            }
        }
        return places.first() to PlacePreset.FIRST
    }

    fun startEditing(todoId: UUID) {
        editing = null
        preset = null
        draft.value = TodoDraft(isEditing = true)
        viewModelScope.launch {
            val todo = todos.observeAll().first().firstOrNull { it.id == todoId } ?: return@launch
            editing = todo
            draft.value = TodoDraft(
                title = todo.title,
                placeId = todo.placeId,
                assigneeId = todo.assigneeId,
                isEditing = true,
                remindsOnLeave = todo.placeEvent == PlaceEvent.DEPARTURE,
            )
        }
    }

    fun setTitle(title: String) = draft.update { it.copy(title = title) }

    fun setPlace(placeId: UUID) = draft.update { it.copy(placeId = placeId) }

    fun setAssignee(userId: UUID?) = draft.update { it.copy(assigneeId = userId) }

    fun setRemindsOnLeave(remindsOnLeave: Boolean) = draft.update { it.copy(remindsOnLeave = remindsOnLeave) }

    // Back from adding a place from the picker: it becomes the to-do's place.
    fun placeAdded(placeId: UUID) = draft.update { it.copy(placeId = placeId, newPlaceId = placeId) }

    // Back from picking a place already saved, from the duplicate prompt.
    fun placePicked(placeId: UUID) = draft.update { it.copy(placeId = placeId) }

    fun save() {
        val current = draft.value
        val placeId = current.placeId
        if (!current.canSave || placeId == null) {
            return
        }
        val edited = editing
        var placeEvent = PlaceEvent.ARRIVAL
        if (current.remindsOnLeave) {
            placeEvent = PlaceEvent.DEPARTURE
        }
        viewModelScope.launch {
            if (edited != null) {
                todos.update(
                    edited.copy(
                        title = current.title.trim(),
                        placeId = placeId,
                        assigneeId = current.assigneeId,
                        placeEvent = placeEvent,
                    ),
                )
                _events.emit(TodoEditorEvent.Saved(landOn = null))
                return@launch
            }
            val now = clock.instant()
            val todo = Todo(
                id = uuidV7(now),
                title = current.title.trim(),
                placeId = placeId,
                assigneeId = current.assigneeId,
                placeEvent = placeEvent,
                createdAt = now,
            )
            val limit = todos.add(todo, origin = origin(placeId))
            if (limit != null) {
                paywallRequests.request(limit.paywallTrigger)
                return@launch
            }
            _events.emit(TodoEditorEvent.Saved(landOn = current.newPlaceId?.takeIf { it == placeId }))
        }
    }

    private fun origin(placeId: UUID): TodoAddOrigin? {
        val preset = preset ?: return null
        return TodoAddOrigin(preset.entry, preset.kind, placeChanged = placeId != preset.placeId)
    }

    fun delete() {
        val edited = editing ?: return
        viewModelScope.launch {
            undo.offer(todos.delete(listOf(edited.id), TodoDeletionVia.EDITOR))
            _events.emit(TodoEditorEvent.Deleted)
        }
    }
}
