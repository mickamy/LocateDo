package com.locatedo.locatedo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.analytics.AlwaysPromptAnswer
import com.locatedo.locatedo.core.analytics.AlwaysPromptTracker
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.paywallTrigger
import com.locatedo.locatedo.core.common.Geo
import com.locatedo.locatedo.core.common.Nearby
import com.locatedo.locatedo.core.common.NearbyPlace
import com.locatedo.locatedo.core.common.PlaceSelectionRequests
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.MembershipRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.data.TodoRepository
import com.locatedo.locatedo.core.location.GeocodingRepository
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.permissions.PermissionsRepository
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

data class HomeUiState(
    val isLoading: Boolean = true,
    val places: List<PlaceWithTodos> = emptyList(),
    val nearby: List<NearbyPlace> = emptyList(),
    val categories: Map<UUID, Category> = emptyMap(),
    val selected: PlaceDetail? = null,
    val members: List<Membership> = emptyList(),
    val permissionBanner: PermissionBanner? = null,
) {
    val openTodoCount: Int
        get() = places.sumOf { it.openTodos.size }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    categoryRepository: CategoryRepository,
    membershipRepository: MembershipRepository,
    private val todoRepository: TodoRepository,
    private val locationRepository: LocationRepository,
    private val geocodingRepository: GeocodingRepository,
    private val selectionRequests: PlaceSelectionRequests,
    private val paywallRequests: PaywallRequests,
    private val permissions: PermissionsRepository,
    private val analytics: Analytics,
    clock: Clock,
) : ViewModel() {
    private val alwaysPrompt = AlwaysPromptTracker(analytics, clock)
    private val _isExplainingAlwaysLocation = MutableStateFlow(false)
    private val selectedId = MutableStateFlow<UUID?>(null)
    private val addresses = MutableStateFlow<Map<UUID, String?>>(emptyMap())
    private val currentCoordinate = MutableStateFlow<Coordinate?>(null)
    private val _cameraTargets = MutableSharedFlow<Coordinate>()

    val isExplainingAlwaysLocation: StateFlow<Boolean> = _isExplainingAlwaysLocation

    private val content = combine(
        placeRepository.observeAllWithTodos(),
        categoryRepository.observeAll(),
        selectedId,
        combine(addresses, currentCoordinate) { addresses, here -> addresses to here },
        membershipRepository.observeAll(),
    ) { places, categories, selected, (addresses, here), members ->
        val byId = categories.associateBy { it.id }
        HomeUiState(
            isLoading = false,
            places = places,
            nearby = Nearby.sort(places, here),
            categories = byId,
            members = members,
            selected = places.firstOrNull { it.place.id == selected }?.let { entry ->
                PlaceDetail(
                    place = entry.place,
                    todos = entry.todos,
                    category = byId[entry.place.categoryId],
                    distanceMeters = here?.let { Geo.distanceMeters(it, Coordinate(entry.place.latitude, entry.place.longitude)) },
                    address = addresses[entry.place.id],
                )
            },
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(content, permissions.observe()) { state, granted ->
        state.copy(permissionBanner = PermissionBanner.of(granted.location, granted.notifications, state.places.isNotEmpty()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    // One-off camera moves, like the Google Maps "my location" button.
    val cameraTargets: SharedFlow<Coordinate> = _cameraTargets

    init {
        viewModelScope.launch {
            selectionRequests.pending.collect { placeId ->
                if (placeId != null) {
                    select(placeId)
                    selectionRequests.consume(placeId)
                }
            }
        }
    }

    fun refreshPermissions() = permissions.refresh()

    // The "all the time" banner explains first, as the Settings button does; the others go straight to system settings.
    fun permissionBannerTapped(banner: PermissionBanner) {
        analytics.log(AnalyticsEvent.PERMISSION_BANNER_TAPPED, mapOf(AnalyticsParameter.KIND to banner.key))
        if (banner == PermissionBanner.LOCATION_ALWAYS) {
            alwaysPrompt.shown()
            _isExplainingAlwaysLocation.value = true
        }
    }

    fun alwaysLocationAnswered(answer: AlwaysPromptAnswer) = alwaysPrompt.answered(answer)

    fun dismissAlwaysLocation() {
        _isExplainingAlwaysLocation.value = false
        permissions.refresh()
    }

    fun hasLocationPermission(): Boolean = locationRepository.hasForegroundPermission()

    fun locateMe() {
        viewModelScope.launch {
            val coordinate = refreshCurrentCoordinate() ?: return@launch
            _cameraTargets.emit(coordinate)
        }
    }

    fun select(placeId: UUID) {
        selectedId.value = placeId
        viewModelScope.launch {
            refreshCurrentCoordinate()
            if (placeId !in addresses.value) {
                val place = uiState.value.places.firstOrNull { it.place.id == placeId }?.place ?: return@launch
                val geocoded = geocodingRepository.reverse(Coordinate(place.latitude, place.longitude))
                addresses.value = addresses.value + (placeId to geocoded?.address)
            }
        }
    }

    fun clearSelection() {
        selectedId.value = null
    }

    fun setTodoCompleted(todoId: UUID, completed: Boolean) {
        viewModelScope.launch {
            val limit = todoRepository.setCompleted(todoId, completed) ?: return@launch
            paywallRequests.request(limit.paywallTrigger)
        }
    }

    fun deleteTodo(todoId: UUID) {
        viewModelScope.launch {
            todoRepository.delete(listOf(todoId))
        }
    }

    fun setAssignee(todoId: UUID, userId: UUID?) {
        viewModelScope.launch {
            val todo = uiState.value.places.flatMap { it.todos }.firstOrNull { it.id == todoId } ?: return@launch
            todoRepository.update(todo.copy(assigneeId = userId))
        }
    }

    fun deletePlace(placeId: UUID) {
        viewModelScope.launch {
            placeRepository.delete(placeId)
            if (selectedId.value == placeId) {
                selectedId.value = null
            }
        }
    }

    private suspend fun refreshCurrentCoordinate(): Coordinate? {
        val coordinate = locationRepository.lastCoordinate()
        if (coordinate != null) {
            currentCoordinate.value = coordinate
        }
        return coordinate
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
