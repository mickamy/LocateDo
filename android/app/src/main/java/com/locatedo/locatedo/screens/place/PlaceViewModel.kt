package com.locatedo.locatedo.screens.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.common.Geo
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.MembershipRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.location.GeocodingRepository
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.model.TodoDeletionVia
import com.locatedo.locatedo.core.notifications.ArrivalSimulator
import com.locatedo.locatedo.core.sync.SyncEngine
import com.locatedo.locatedo.screens.todos.TodoActions
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaceInfo(
    val place: Place,
    val category: Category?,
    val openTodos: List<Todo>,
    val completedTodos: List<Todo>,
    val distanceMeters: Double?,
    val address: String?,
)

// A place deleted here or by someone in the household has no info once loaded.
data class PlaceUiState(
    val isLoading: Boolean = true,
    val info: PlaceInfo? = null,
    val members: List<Membership> = emptyList(),
    val isSignedIn: Boolean = false,
    val isRefreshing: Boolean = false,
)

@HiltViewModel(assistedFactory = PlaceViewModel.Factory::class)
class PlaceViewModel @AssistedInject constructor(
    @Assisted private val placeId: UUID,
    private val places: PlaceRepository,
    categories: CategoryRepository,
    memberships: MembershipRepository,
    authenticator: Authenticator,
    private val location: LocationRepository,
    private val geocoding: GeocodingRepository,
    private val sync: SyncEngine,
    private val actions: TodoActions,
    private val arrivalSimulator: ArrivalSimulator,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(placeId: UUID): PlaceViewModel
    }

    private val address = MutableStateFlow<String?>(null)
    private val here = MutableStateFlow<Coordinate?>(null)
    private val isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<PlaceUiState> = combine(
        places.observeWithTodos(placeId),
        categories.observeAll(),
        memberships.observeAll(),
        combine(address, here) { address, here -> address to here },
        combine(authenticator.session, isRefreshing) { session, refreshing -> (session != null) to refreshing },
    ) { entry, categories, members, (address, here), (isSignedIn, refreshing) ->
        val info = entry?.let {
            val place = it.place
            PlaceInfo(
                place = place,
                category = categories.firstOrNull { category -> category.id == place.categoryId },
                openTodos = it.openTodos,
                completedTodos = it.completedTodosNewestFirst,
                distanceMeters = here?.let { coordinate -> Geo.distanceMeters(coordinate, Coordinate(place.latitude, place.longitude)) },
                address = address,
            )
        }
        PlaceUiState(isLoading = false, info = info, members = members, isSignedIn = isSignedIn, isRefreshing = refreshing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PlaceUiState())

    init {
        viewModelScope.launch {
            here.value = location.lastCoordinate()
        }
        viewModelScope.launch {
            val place = places.observeWithTodos(placeId).filterNotNull().first().place
            address.value = geocoding.reverse(Coordinate(place.latitude, place.longitude))?.address
        }
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
        val ids = uiState.value.info?.completedTodos?.map { it.id }.orEmpty()
        viewModelScope.launch { actions.deleteCompleted(ids) }
    }

    fun deletePlace() {
        viewModelScope.launch { places.delete(placeId) }
    }

    fun simulateArrival(after: Duration) = arrivalSimulator.arrive(placeId, after)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
