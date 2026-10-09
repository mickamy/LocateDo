package com.locatedo.locatedo.feature.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.paywallTrigger
import com.locatedo.locatedo.core.common.Geo
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.MembershipRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.data.TodoRepository
import com.locatedo.locatedo.core.data.TodoUndo
import com.locatedo.locatedo.core.location.GeocodingRepository
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.model.TodoDeletionVia
import com.locatedo.locatedo.core.notifications.ArrivalSimulator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaceDetail(
    val place: Place,
    val todos: List<Todo>,
    val category: Category?,
    val distanceMeters: Double?,
    val address: String?,
) {
    val openTodos: List<Todo>
        get() = todos.filter { !it.isCompleted }.sortedBy { it.createdAt }

    val completedTodos: List<Todo>
        get() = todos.filter { it.isCompleted }.sortedByDescending { it.completedAt }
}

// A place that is gone (deleted here or by someone in the household) has no detail once loaded.
data class PlaceDetailUiState(
    val isLoading: Boolean = true,
    val detail: PlaceDetail? = null,
    val members: List<Membership> = emptyList(),
)

@HiltViewModel(assistedFactory = PlaceDetailViewModel.Factory::class)
class PlaceDetailViewModel @AssistedInject constructor(
    @Assisted private val placeId: UUID,
    private val placeRepository: PlaceRepository,
    categoryRepository: CategoryRepository,
    membershipRepository: MembershipRepository,
    private val todoRepository: TodoRepository,
    private val locationRepository: LocationRepository,
    private val geocodingRepository: GeocodingRepository,
    private val paywallRequests: PaywallRequests,
    private val arrivalSimulator: ArrivalSimulator,
    private val undo: TodoUndo,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(placeId: UUID): PlaceDetailViewModel
    }

    private val address = MutableStateFlow<String?>(null)
    private val here = MutableStateFlow<Coordinate?>(null)

    val uiState: StateFlow<PlaceDetailUiState> = combine(
        placeRepository.observeWithTodos(placeId),
        categoryRepository.observeAll(),
        membershipRepository.observeAll(),
        address,
        here,
    ) { entry, categories, members, address, here ->
        val detail = entry?.let {
            PlaceDetail(
                place = it.place,
                todos = it.todos,
                category = categories.firstOrNull { category -> category.id == it.place.categoryId },
                distanceMeters = here?.let { coordinate -> Geo.distanceMeters(coordinate, it.place.coordinate) },
                address = address,
            )
        }
        PlaceDetailUiState(isLoading = false, detail = detail, members = members)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PlaceDetailUiState())

    init {
        viewModelScope.launch {
            here.value = locationRepository.lastCoordinate()
        }
        viewModelScope.launch {
            val place = placeRepository.observeWithTodos(placeId).first()?.place ?: return@launch
            address.value = geocodingRepository.reverse(place.coordinate)?.address
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

    fun setAssignee(todoId: UUID, userId: UUID?) {
        viewModelScope.launch {
            val todo = uiState.value.detail?.todos?.firstOrNull { it.id == todoId } ?: return@launch
            todoRepository.update(todo.copy(assigneeId = userId))
        }
    }

    fun deletePlace() {
        viewModelScope.launch {
            placeRepository.delete(placeId)
        }
    }

    fun simulateArrival(after: Duration) = arrivalSimulator.arrive(placeId, after)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private val Place.coordinate: Coordinate
    get() = Coordinate(latitude, longitude)
