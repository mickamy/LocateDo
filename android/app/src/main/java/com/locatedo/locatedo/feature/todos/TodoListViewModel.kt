package com.locatedo.locatedo.feature.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.common.PlaceSelectionRequests
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.data.TodoRepository
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.Todo
import dagger.hilt.android.lifecycle.HiltViewModel
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

enum class TodoFilter {
    ALL,
    OPEN,
    DONE,
}

data class TodoGroup(val place: Place, val category: Category?, val todos: List<Todo>)

enum class TodoListEmptyState {
    NO_PLACES,
    NO_TODOS,
    NO_MATCHES,
}

data class TodoListUiState(
    val isLoading: Boolean = true,
    val filter: TodoFilter = TodoFilter.OPEN,
    val hasPlaces: Boolean = false,
    val hasTodos: Boolean = false,
    val groups: List<TodoGroup> = emptyList(),
) {
    val emptyState: TodoListEmptyState?
        get() = when {
            isLoading -> null
            !hasPlaces -> TodoListEmptyState.NO_PLACES
            !hasTodos -> TodoListEmptyState.NO_TODOS
            groups.isEmpty() -> TodoListEmptyState.NO_MATCHES
            else -> null
        }
}

sealed interface TodoListEvent {
    data class LimitReached(val limit: FreeLimit) : TodoListEvent
}

@HiltViewModel
class TodoListViewModel @Inject constructor(
    placeRepository: PlaceRepository,
    categoryRepository: CategoryRepository,
    private val todoRepository: TodoRepository,
    private val selectionRequests: PlaceSelectionRequests,
) : ViewModel() {
    private val filter = MutableStateFlow(TodoFilter.OPEN)
    private val _events = MutableSharedFlow<TodoListEvent>()

    val events: SharedFlow<TodoListEvent> = _events

    val uiState: StateFlow<TodoListUiState> = combine(
        placeRepository.observeAllWithTodos(),
        categoryRepository.observeAll(),
        filter,
    ) { places, categories, filter ->
        val byId = categories.associateBy { it.id }
        TodoListUiState(
            isLoading = false,
            filter = filter,
            hasPlaces = places.isNotEmpty(),
            hasTodos = places.any { it.todos.isNotEmpty() },
            groups = places.mapNotNull { entry ->
                val todos = when (filter) {
                    TodoFilter.ALL -> entry.openTodos + entry.completedTodosNewestFirst
                    TodoFilter.OPEN -> entry.openTodos
                    TodoFilter.DONE -> entry.completedTodosNewestFirst
                }
                if (todos.isEmpty()) null else TodoGroup(entry.place, byId[entry.place.categoryId], todos)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TodoListUiState())

    fun setFilter(filter: TodoFilter) {
        this.filter.value = filter
    }

    fun setTodoCompleted(todoId: UUID, completed: Boolean) {
        viewModelScope.launch {
            val limit = todoRepository.setCompleted(todoId, completed) ?: return@launch
            _events.emit(TodoListEvent.LimitReached(limit))
        }
    }

    fun deleteTodo(todoId: UUID) {
        viewModelScope.launch {
            todoRepository.delete(listOf(todoId))
        }
    }

    // The home map shows the place once the caller switches to that tab.
    fun requestPlace(placeId: UUID) {
        selectionRequests.request(placeId)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
