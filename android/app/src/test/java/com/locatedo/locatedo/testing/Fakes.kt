package com.locatedo.locatedo.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.locatedo.locatedo.core.analytics.ScreenEntry
import com.locatedo.locatedo.core.analytics.TodoAddOrigin
import com.locatedo.locatedo.core.analytics.TodoAddVia
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.MembershipRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.data.SyncStateRepository
import com.locatedo.locatedo.core.data.TodoRepository
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.geofence.GeofenceRegion
import com.locatedo.locatedo.core.geofence.GeofenceRegistrar
import com.locatedo.locatedo.core.location.GeocodedPlace
import com.locatedo.locatedo.core.location.GeocodingRepository
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceEvent
import com.locatedo.locatedo.core.model.PlaceSource
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.SyncState
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.model.TodoDeletionVia
import com.locatedo.locatedo.core.notifications.ArrivalNotifier
import com.locatedo.locatedo.core.notifications.ArrivalSimulator
import com.locatedo.locatedo.core.notifications.CampaignNotification
import com.locatedo.locatedo.core.notifications.CampaignNotifier
import com.locatedo.locatedo.core.notifications.CompletionNotice
import com.locatedo.locatedo.core.notifications.CompletionNotifier
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.core.permissions.Permissions
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import com.locatedo.locatedo.core.places.PlaceCandidate
import com.locatedo.locatedo.core.places.PlacePrediction
import com.locatedo.locatedo.core.places.PlacesRepository
import com.locatedo.locatedo.core.sharing.HouseholdManager
import com.locatedo.locatedo.core.sharing.Invite
import com.locatedo.locatedo.core.sync.SyncEngine
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePlaceRepository : PlaceRepository {
    val state = MutableStateFlow<List<PlaceWithTodos>>(emptyList())
    val added = mutableListOf<Place>()
    val sources = mutableListOf<PlaceSource?>()
    val todoCounts = mutableListOf<Int>()
    val suggestions = mutableListOf<BuiltinCategory?>()
    val entries = mutableListOf<ScreenEntry?>()
    val updated = mutableListOf<Place>()
    var limit: FreeLimit? = null

    override fun observeAll(): Flow<List<Place>> = state.map { entries -> entries.map { it.place } }

    override fun observeAllWithTodos(): Flow<List<PlaceWithTodos>> = state

    override fun observeWithTodos(id: UUID): Flow<PlaceWithTodos?> =
        state.map { entries -> entries.firstOrNull { it.place.id == id } }

    override suspend fun add(
        place: Place,
        source: PlaceSource?,
        todoCount: Int,
        suggestedCategory: BuiltinCategory?,
        entry: ScreenEntry?,
    ): FreeLimit? {
        limit?.let { return it }
        added += place
        sources += source
        todoCounts += todoCount
        suggestions += suggestedCategory
        entries += entry
        state.value = state.value + PlaceWithTodos(place, emptyList())
        return null
    }

    override suspend fun update(place: Place) {
        updated += place
        state.value = state.value.map { if (it.place.id == place.id) it.copy(place = place) else it }
    }

    override suspend fun delete(id: UUID) {
        state.value = state.value.filter { it.place.id != id }
    }

    override suspend fun markNotified(id: UUID, event: PlaceEvent, at: Instant?) {
        state.value = state.value.map { entry ->
            if (entry.place.id != id) {
                entry
            } else if (event == PlaceEvent.ARRIVAL) {
                entry.copy(place = entry.place.copy(lastArrivalNotifiedAt = at))
            } else {
                entry.copy(place = entry.place.copy(lastDepartureNotifiedAt = at))
            }
        }
    }

    override suspend fun setEnteredAt(id: UUID, at: Instant?) {
        state.value = state.value.map { if (it.place.id == id) it.copy(place = it.place.copy(enteredAt = at)) else it }
    }
}

class FakeGeofenceRegistrar : GeofenceRegistrar {
    val added = mutableListOf<List<GeofenceRegion>>()
    val removed = mutableListOf<List<String>>()
    var failAdd = false

    override suspend fun add(regions: List<GeofenceRegion>): Boolean {
        if (failAdd) {
            return false
        }
        added += regions
        return true
    }

    override suspend fun remove(requestIds: List<String>): Boolean {
        removed += requestIds
        return true
    }
}

class FakeArrivalNotifier : ArrivalNotifier {
    val notified = mutableListOf<Pair<Place, List<String>>>()
    val silent = mutableListOf<Pair<Place, List<String>>>()
    val cancelled = mutableListOf<UUID>()
    var allowed = true

    override fun prepare() = Unit

    override fun canNotify(): Boolean = allowed

    val events = mutableListOf<PlaceEvent>()

    override fun notify(event: PlaceEvent, place: Place, todos: List<Todo>, silent: Boolean) {
        val shown = place to todos.map { it.title }
        events += event
        if (silent) {
            this.silent += shown
        } else {
            notified += shown
        }
    }

    override fun cancel(placeId: UUID, event: PlaceEvent) {
        cancelled += placeId
    }
}

class FakeCampaignNotifier : CampaignNotifier {
    val notified = mutableListOf<Pair<CampaignNotification, Instant>>()

    override fun prepare() = Unit

    override fun notify(campaign: CampaignNotification, sentAt: Instant) {
        notified += campaign to sentAt
    }
}

class FakeArrivalSimulator : ArrivalSimulator {
    val simulated = mutableListOf<Triple<UUID, PlaceEvent, Duration>>()

    override fun simulate(placeId: UUID, event: PlaceEvent, after: Duration) {
        simulated += Triple(placeId, event, after)
    }
}

class FakeCompletionNotifier : CompletionNotifier {
    val notified = mutableListOf<CompletionNotice>()

    override fun prepare() = Unit

    override fun notify(notice: CompletionNotice, id: Int) {
        notified += notice
    }
}

class FakeCategoryRepository : CategoryRepository {
    val state = MutableStateFlow<List<Category>>(emptyList())
    val added = mutableListOf<Category>()
    val updated = mutableListOf<Category>()
    val reordered = mutableListOf<List<Category>>()

    override fun observeAll(): Flow<List<Category>> = state

    override suspend fun ensureBuiltins() = Unit

    override suspend fun add(category: Category) {
        added += category
        state.value = state.value + category.copy(sortOrder = state.value.size)
    }

    override suspend fun update(category: Category) {
        updated += category
        state.value = state.value.map { if (it.id == category.id) category else it }
    }

    override suspend fun delete(id: UUID): Boolean {
        if (state.value.size <= 1) {
            return false
        }
        state.value = state.value.filter { it.id != id }
        return true
    }

    override suspend fun reorder(categories: List<Category>) {
        reordered += categories
        state.value = categories.mapIndexed { index, category -> category.copy(sortOrder = index) }
    }
}

class FakeLocationRepository(var coordinate: Coordinate? = null) : LocationRepository {
    override fun hasForegroundPermission(): Boolean = coordinate != null
    override fun hasPrecisePermission(): Boolean = coordinate != null
    override suspend fun lastCoordinate(): Coordinate? = coordinate
}

class FakePermissionsRepository(
    location: LocationAuth = LocationAuth.NOT_DETERMINED,
    notifications: NotificationAuth = NotificationAuth.NOT_DETERMINED,
) : PermissionsRepository {
    val state = MutableStateFlow(Permissions(location, notifications))
    var refreshCount = 0
    var locationRequested = false
    var notificationsRequested = false
    var preciseLocationRequested = false
    var batteryOptimizationExempt = false

    override fun observe(): Flow<Permissions> = state

    override fun isBatteryOptimizationExempt(): Boolean = batteryOptimizationExempt

    override fun refresh() {
        refreshCount += 1
    }

    override suspend fun markLocationRequested() {
        locationRequested = true
    }

    override suspend fun markNotificationsRequested() {
        notificationsRequested = true
    }

    override suspend fun markPreciseLocationRequested() {
        preciseLocationRequested = true
    }
}

class FakeTodoRepository : TodoRepository {
    val state = MutableStateFlow<List<Todo>>(emptyList())
    val added = mutableListOf<Todo>()
    val updated = mutableListOf<Todo>()
    val deleted = mutableListOf<UUID>()
    val deletedVia = mutableListOf<TodoDeletionVia>()
    val restored = mutableListOf<Todo>()
    val addedVia = mutableListOf<TodoAddVia>()
    val origins = mutableListOf<TodoAddOrigin?>()
    var limit: FreeLimit? = null
    var remaining: Int? = null

    override fun observeAll(): Flow<List<Todo>> = state

    override suspend fun add(todo: Todo, via: TodoAddVia, origin: TodoAddOrigin?): FreeLimit? {
        limit?.let { return it }
        added += todo
        addedVia += via
        origins += origin
        state.value = state.value + todo
        return null
    }

    override suspend fun remainingOpen(): Int? = remaining

    override suspend fun update(todo: Todo) {
        updated += todo
        state.value = state.value.map { if (it.id == todo.id) todo else it }
    }

    override suspend fun setCompleted(id: UUID, completed: Boolean): FreeLimit? {
        if (!completed) {
            limit?.let { return it }
        }
        state.value = state.value.map {
            if (it.id == id) it.copy(completedAt = if (completed) Instant.EPOCH else null) else it
        }
        return null
    }

    override suspend fun checkOff(id: UUID): Boolean {
        val todo = state.value.firstOrNull { it.id == id }
        if (todo == null || todo.isCompleted) {
            return false
        }
        state.value = state.value.map { if (it.id == id) it.copy(completedAt = Instant.EPOCH) else it }
        return true
    }

    override suspend fun delete(ids: List<UUID>, via: TodoDeletionVia): List<Todo> {
        val found = state.value.filter { it.id in ids }
        deleted += found.map { it.id }
        deletedVia += via
        state.value = state.value.filter { it.id !in ids }
        return found
    }

    override suspend fun restore(todos: List<Todo>) {
        restored += todos
        state.value = state.value + todos
    }
}

class FakeSyncEngine : SyncEngine {
    override val limitRejected = MutableSharedFlow<FreeLimit>()
    override val removed = MutableSharedFlow<Unit>()
    override val lastPullSummary = MutableStateFlow<String?>(null)
    var syncs = 0
    var drains = 0

    override fun start() = Unit

    override suspend fun drain() {
        drains += 1
    }

    override suspend fun sync() {
        syncs += 1
    }
}

class FakeMembershipRepository : MembershipRepository {
    val state = MutableStateFlow<List<Membership>>(emptyList())

    override fun observeAll(): Flow<List<Membership>> = state
}

class FakeSyncStateRepository(initial: SyncState = SyncState()) : SyncStateRepository {
    val state = MutableStateFlow(initial)

    override fun observe(): Flow<SyncState> = state

    override suspend fun get(): SyncState = state.value

    override suspend fun set(state: SyncState) {
        this.state.value = state
    }

    override suspend fun clear() {
        state.value = SyncState()
    }
}

class FakeHouseholdManager : HouseholdManager {
    override val isWorking = MutableStateFlow(false)
    val removed = mutableListOf<UUID>()
    val accepted = mutableListOf<String>()
    var leaves = 0
    var removals = 0
    var invite = Invite("https://locatedo.com/i/invite-token-0123456789", Instant.EPOCH)
    var failure: Exception? = null

    override suspend fun remove(userId: UUID) {
        failure?.let { throw it }
        removed += userId
    }

    override suspend fun leave() {
        failure?.let { throw it }
        leaves += 1
    }

    override suspend fun createInvite(): Invite {
        failure?.let { throw it }
        return invite
    }

    override suspend fun accept(token: String) {
        failure?.let { throw it }
        accepted += token
    }

    override suspend fun handleRemoval() {
        removals += 1
    }
}

class FakePlacesRepository : PlacesRepository {
    var predictions: List<PlacePrediction> = emptyList()
    val candidates = mutableMapOf<String, PlaceCandidate>()
    val queries = mutableListOf<String>()
    val origins = mutableListOf<Coordinate?>()

    override suspend fun autocomplete(query: String, near: Coordinate?, origin: Coordinate?): List<PlacePrediction> {
        queries += query
        origins += origin
        return predictions
    }

    val types = mutableMapOf<String, List<String>>()

    override suspend fun fetch(id: String): PlaceCandidate? = candidates[id]

    override suspend fun types(id: String): List<String> = types[id].orEmpty()
}

class FakeGeocodingRepository(var result: GeocodedPlace? = null) : GeocodingRepository {
    override suspend fun reverse(coordinate: Coordinate): GeocodedPlace? = result
}

fun testPreferences(directory: File, scope: CoroutineScope): AppPreferences =
    AppPreferences(PreferenceDataStoreFactory.create(scope = scope) { File(directory, "settings.preferences_pb") })
