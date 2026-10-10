package com.locatedo.locatedo.feature.todos

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
    val isPlaceFixed: Boolean = false,
    val assigneeId: UUID? = null,
    val isEditing: Boolean = false,
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
    data object Saved : TodoEditorEvent
}

@HiltViewModel
class TodoEditorViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    placeRepository: PlaceRepository,
    membershipRepository: MembershipRepository,
    private val paywallRequests: PaywallRequests,
    private val undo: TodoUndo,
    private val clock: Clock,
) : ViewModel() {
    private val draft = MutableStateFlow(TodoDraft())
    private var editing: Todo? = null
    private val _events = MutableSharedFlow<TodoEditorEvent>()

    val events: SharedFlow<TodoEditorEvent> = _events

    val uiState: StateFlow<TodoEditorUiState> = combine(
        draft,
        placeRepository.observeAll(),
        membershipRepository.observeAll(),
    ) { draft, places, members ->
        TodoEditorUiState(draft = draft, places = places, members = members)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TodoEditorUiState())

    // A place given here is fixed (opened from that place); without one the sheet offers a picker.
    fun start(placeId: UUID?) {
        editing = null
        draft.value = TodoDraft(placeId = placeId, isPlaceFixed = placeId != null)
    }

    // Editing can move the to-do to another place, so the picker is always there.
    fun startEditing(todoId: UUID) {
        viewModelScope.launch {
            val todo = todoRepository.observeAll().first().firstOrNull { it.id == todoId } ?: return@launch
            editing = todo
            draft.value = TodoDraft(
                title = todo.title,
                placeId = todo.placeId,
                assigneeId = todo.assigneeId,
                isEditing = true,
            )
        }
    }

    fun setTitle(title: String) = draft.update { it.copy(title = title) }

    fun setPlace(placeId: UUID) = draft.update { it.copy(placeId = placeId) }

    fun setAssignee(userId: UUID?) = draft.update { it.copy(assigneeId = userId) }

    fun save() {
        val current = draft.value
        val placeId = current.placeId
        if (!current.canSave || placeId == null) {
            return
        }
        val edited = editing
        if (edited != null) {
            viewModelScope.launch {
                todoRepository.update(
                    edited.copy(title = current.title.trim(), placeId = placeId, assigneeId = current.assigneeId),
                )
                _events.emit(TodoEditorEvent.Saved)
            }
            return
        }
        viewModelScope.launch {
            val now = clock.instant()
            val todo = Todo(
                id = uuidV7(now),
                title = current.title.trim(),
                placeId = placeId,
                assigneeId = current.assigneeId,
                createdAt = now,
            )
            val limit = todoRepository.add(todo)
            if (limit == null) {
                _events.emit(TodoEditorEvent.Saved)
            } else {
                paywallRequests.request(limit.paywallTrigger)
            }
        }
    }

    fun delete() {
        val edited = editing ?: return
        viewModelScope.launch {
            undo.offer(todoRepository.delete(listOf(edited.id), TodoDeletionVia.EDITOR))
            _events.emit(TodoEditorEvent.Saved)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
