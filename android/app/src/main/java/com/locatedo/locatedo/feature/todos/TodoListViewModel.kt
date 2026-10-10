package com.locatedo.locatedo.feature.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.paywallTrigger
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.MembershipRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.data.TodoRepository
import com.locatedo.locatedo.core.data.TodoUndo
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.model.TodoDeletionVia
import com.locatedo.locatedo.core.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
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
    val isSignedIn: Boolean = false,
    val isRefreshing: Boolean = false,
    val members: List<Membership> = emptyList(),
    val completedTodoIds: List<UUID> = emptyList(),
) {
    val isShared: Boolean
        get() = members.size > 1

    val emptyState: TodoListEmptyState?
        get() = when {
            isLoading -> null
            !hasPlaces -> TodoListEmptyState.NO_PLACES
            !hasTodos -> TodoListEmptyState.NO_TODOS
            groups.isEmpty() -> TodoListEmptyState.NO_MATCHES
            else -> null
        }
}

@HiltViewModel
class TodoListViewModel @Inject constructor(
    placeRepository: PlaceRepository,
    categoryRepository: CategoryRepository,
    membershipRepository: MembershipRepository,
    private val todoRepository: TodoRepository,
    private val paywallRequests: PaywallRequests,
    authenticator: Authenticator,
    private val sync: SyncEngine,
    private val undo: TodoUndo,
) : ViewModel() {
    private val filter = MutableStateFlow(TodoFilter.OPEN)
    private val isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<TodoListUiState> = combine(
        placeRepository.observeAllWithTodos(),
        categoryRepository.observeAll(),
        filter,
        combine(authenticator.session, isRefreshing) { session, refreshing -> session to refreshing },
        membershipRepository.observeAll(),
    ) { places, categories, filter, (session, refreshing), members ->
        val byId = categories.associateBy { it.id }
        TodoListUiState(
            isLoading = false,
            filter = filter,
            hasPlaces = places.isNotEmpty(),
            hasTodos = places.any { it.todos.isNotEmpty() },
            isSignedIn = session != null,
            isRefreshing = refreshing,
            members = members,
            completedTodoIds = places.flatMap { entry -> entry.completedTodosNewestFirst.map { it.id } },
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

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                sync.sync()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun setTodoCompleted(todoId: UUID, completed: Boolean) {
        viewModelScope.launch {
            val limit = todoRepository.setCompleted(todoId, completed) ?: return@launch
            paywallRequests.request(limit.paywallTrigger)
        }
    }

    fun deleteTodo(todoId: UUID, via: TodoDeletionVia) {
        viewModelScope.launch {
            undo.offer(todoRepository.delete(listOf(todoId), via))
        }
    }

    fun deleteCompleted() {
        val ids = uiState.value.completedTodoIds
        viewModelScope.launch {
            todoRepository.delete(ids, TodoDeletionVia.COMPLETED_BULK)
        }
    }

    fun setAssignee(todoId: UUID, userId: UUID?) {
        viewModelScope.launch {
            val todo = uiState.value.groups.flatMap { it.todos }.firstOrNull { it.id == todoId } ?: return@launch
            todoRepository.update(todo.copy(assigneeId = userId))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
