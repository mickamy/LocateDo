package com.locatedo.locatedo.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceSource
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.SyncState
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.notifications.ArrivalNotifier
import com.locatedo.locatedo.core.notifications.CampaignNotification
import com.locatedo.locatedo.core.notifications.CampaignNotifier
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
    val updated = mutableListOf<Place>()
    var limit: FreeLimit? = null

    override fun observeAll(): Flow<List<Place>> = state.map { entries -> entries.map { it.place } }

    override fun observeAllWithTodos(): Flow<List<PlaceWithTodos>> = state

    override fun observeWithTodos(id: UUID): Flow<PlaceWithTodos?> =
        state.map { entries -> entries.firstOrNull { it.place.id == id } }

    override suspend fun add(place: Place, source: PlaceSource?): FreeLimit? {
        limit?.let { return it }
        added += place
        sources += source
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

    override suspend fun markNotified(id: UUID, at: Instant) {
        state.value = state.value.map { if (it.place.id == id) it.copy(place = it.place.copy(lastNotifiedAt = at)) else it }
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
    var allowed = true

    override fun prepare() = Unit

    override fun canNotify(): Boolean = allowed

    override fun notifyArrival(place: Place, todoTitles: List<String>) {
        notified += place to todoTitles
    }
}

class FakeCampaignNotifier : CampaignNotifier {
    val notified = mutableListOf<Pair<CampaignNotification, Instant>>()

    override fun prepare() = Unit

    override fun notify(campaign: CampaignNotification, sentAt: Instant) {
        notified += campaign to sentAt
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

    override fun observe(): Flow<Permissions> = state

    override fun refresh() {
        refreshCount += 1
    }

    override suspend fun markLocationRequested() {
        locationRequested = true
    }

    override suspend fun markNotificationsRequested() {
        notificationsRequested = true
    }
}

class FakeTodoRepository : TodoRepository {
    val state = MutableStateFlow<List<Todo>>(emptyList())
    val added = mutableListOf<Todo>()
    val updated = mutableListOf<Todo>()
    val deleted = mutableListOf<UUID>()
    var limit: FreeLimit? = null

    override fun observeAll(): Flow<List<Todo>> = state

    override suspend fun add(todo: Todo): FreeLimit? {
        limit?.let { return it }
        added += todo
        state.value = state.value + todo
        return null
    }

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

    override suspend fun delete(ids: List<UUID>) {
        deleted += ids
        state.value = state.value.filter { it.id !in ids }
    }
}

class FakeSyncEngine : SyncEngine {
    override val limitRejected = MutableSharedFlow<FreeLimit>()
    override val removed = MutableSharedFlow<Unit>()
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

    override suspend fun autocomplete(query: String, near: Coordinate?): List<PlacePrediction> {
        queries += query
        return predictions
    }

    override suspend fun fetch(id: String): PlaceCandidate? = candidates[id]
}

class FakeGeocodingRepository(var result: GeocodedPlace? = null) : GeocodingRepository {
    override suspend fun reverse(coordinate: Coordinate): GeocodedPlace? = result
}

fun testPreferences(directory: File, scope: CoroutineScope): AppPreferences =
    AppPreferences(PreferenceDataStoreFactory.create(scope = scope) { File(directory, "settings.preferences_pb") })
