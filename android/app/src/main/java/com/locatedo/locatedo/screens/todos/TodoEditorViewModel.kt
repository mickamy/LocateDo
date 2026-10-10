package com.locatedo.locatedo.screens.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.paywallTrigger
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.data.MembershipRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.data.TodoRepository
import com.locatedo.locatedo.core.data.TodoUndo
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.Place
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
    private val clock: Clock,
) : ViewModel() {
    private val draft = MutableStateFlow(TodoDraft())
    private var editing: Todo? = null
    private val _events = MutableSharedFlow<TodoEditorEvent>()

    val events: SharedFlow<TodoEditorEvent> = _events

    val uiState: StateFlow<TodoEditorUiState> = combine(draft, places.observeAll(), memberships.observeAll()) { draft, places, members ->
        TodoEditorUiState(draft = draft, places = places, members = members)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TodoEditorUiState())

    // A new to-do starts at the place given, or else the first one.
    fun start(placeId: UUID?) {
        editing = null
        draft.value = TodoDraft(placeId = placeId)
        if (placeId != null) {
            return
        }
        viewModelScope.launch {
            val first = uiState.first { it.places.isNotEmpty() || it.draft.placeId != null }.places.firstOrNull()
            if (first != null) {
                draft.update { if (it.placeId == null) it.copy(placeId = first.id) else it }
            }
        }
    }

    fun startEditing(todoId: UUID) {
        editing = null
        draft.value = TodoDraft(isEditing = true)
        viewModelScope.launch {
            val todo = todos.observeAll().first().firstOrNull { it.id == todoId } ?: return@launch
            editing = todo
            draft.value = TodoDraft(title = todo.title, placeId = todo.placeId, assigneeId = todo.assigneeId, isEditing = true)
        }
    }

    fun setTitle(title: String) = draft.update { it.copy(title = title) }

    fun setPlace(placeId: UUID) = draft.update { it.copy(placeId = placeId) }

    fun setAssignee(userId: UUID?) = draft.update { it.copy(assigneeId = userId) }

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
        viewModelScope.launch {
            if (edited != null) {
                todos.update(edited.copy(title = current.title.trim(), placeId = placeId, assigneeId = current.assigneeId))
                _events.emit(TodoEditorEvent.Saved(landOn = null))
                return@launch
            }
            val now = clock.instant()
            val todo = Todo(
                id = uuidV7(now),
                title = current.title.trim(),
                placeId = placeId,
                assigneeId = current.assigneeId,
                createdAt = now,
            )
            val limit = todos.add(todo)
            if (limit != null) {
                paywallRequests.request(limit.paywallTrigger)
                return@launch
            }
            _events.emit(TodoEditorEvent.Saved(landOn = current.newPlaceId?.takeIf { it == placeId }))
        }
    }

    fun delete() {
        val edited = editing ?: return
        viewModelScope.launch {
            undo.offer(todos.delete(listOf(edited.id), TodoDeletionVia.EDITOR))
            _events.emit(TodoEditorEvent.Deleted)
        }
    }
}
