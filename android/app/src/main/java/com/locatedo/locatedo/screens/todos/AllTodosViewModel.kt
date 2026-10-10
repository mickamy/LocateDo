package com.locatedo.locatedo.screens.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.MembershipRepository
import com.locatedo.locatedo.core.data.PlaceRepository
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

data class AllTodosUiState(
    val isLoading: Boolean = true,
    val filter: TodoFilter = TodoFilter.OPEN,
    val hasTodos: Boolean = false,
    val groups: List<TodoGroup> = emptyList(),
    val completedTodoIds: List<UUID> = emptyList(),
    val members: List<Membership> = emptyList(),
    val isSignedIn: Boolean = false,
    val isRefreshing: Boolean = false,
)

// Every to-do, one section per place, filtered to open (the default, as the count on Home), done, or all.
@HiltViewModel
class AllTodosViewModel @Inject constructor(
    places: PlaceRepository,
    categories: CategoryRepository,
    memberships: MembershipRepository,
    authenticator: Authenticator,
    private val sync: SyncEngine,
    private val actions: TodoActions,
) : ViewModel() {
    private val filter = MutableStateFlow(TodoFilter.OPEN)
    private val isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<AllTodosUiState> = combine(
        places.observeAllWithTodos(),
        categories.observeAll(),
        memberships.observeAll(),
        filter,
        combine(authenticator.session, isRefreshing) { session, refreshing -> (session != null) to refreshing },
    ) { entries, categories, members, filter, (isSignedIn, refreshing) ->
        val byId = categories.associateBy { it.id }
        AllTodosUiState(
            isLoading = false,
            filter = filter,
            hasTodos = entries.any { it.todos.isNotEmpty() },
            groups = entries.mapNotNull { entry ->
                val todos = when (filter) {
                    TodoFilter.ALL -> entry.openTodos + entry.completedTodosNewestFirst
                    TodoFilter.OPEN -> entry.openTodos
                    TodoFilter.DONE -> entry.completedTodosNewestFirst
                }
                todos.takeIf { it.isNotEmpty() }?.let { TodoGroup(entry.place, byId[entry.place.categoryId], it) }
            },
            completedTodoIds = entries.flatMap { entry -> entry.completedTodosNewestFirst.map { it.id } },
            members = members,
            isSignedIn = isSignedIn,
            isRefreshing = refreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AllTodosUiState())

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

    fun toggle(todo: Todo) {
        viewModelScope.launch { actions.setCompleted(todo.id, !todo.isCompleted) }
    }

    fun delete(todo: Todo, via: TodoDeletionVia) {
        viewModelScope.launch { actions.delete(todo.id, via) }
    }

    fun assign(todo: Todo, userId: UUID?) {
        viewModelScope.launch { actions.assign(todo, userId) }
    }

    fun deleteCompleted() {
        val ids = uiState.value.completedTodoIds
        viewModelScope.launch { actions.deleteCompleted(ids) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
