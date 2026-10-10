package com.locatedo.locatedo.core.geofence

import com.locatedo.locatedo.core.common.Geo
import com.locatedo.locatedo.core.common.di.ApplicationScope
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Keeps the registered fences equal to the plan: nearest places while location is "always", nothing otherwise.
@OptIn(FlowPreview::class)
@Singleton
class GeofenceSync @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val permissions: PermissionsRepository,
    private val locationRepository: LocationRepository,
    private val registrar: GeofenceRegistrar,
    private val preferences: AppPreferences,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun start() {
        scope.launch {
            merge(placeRepository.observeAll().map { }, permissions.observe().map { }, requests)
                .debounce(DEBOUNCE_MILLIS)
                .collect { sync() }
        }
    }

    fun requestSync() {
        requests.tryEmit(Unit)
    }

    suspend fun sync() {
        mutex.withLock {
            val registered = GeofenceRecord.decode(preferences.registeredGeofences.first())
            val here = locationRepository.lastCoordinate()
            val desired = if (permissions.observe().first().location == LocationAuth.ALWAYS) {
                GeofencePlan.regions(placeRepository.observeAll().first(), here)
            } else {
                emptyList()
            }
            val changes = GeofencePlan.changes(registered, desired)
            var current = registered
            if (changes.remove.isNotEmpty() && registrar.remove(changes.remove)) {
                current = current - changes.remove.toSet()
            }
            if (changes.add.isNotEmpty() && registrar.add(changes.add)) {
                current = current + changes.add.associateBy { it.requestId }
                forgetEntriesOutside(changes.add, here)
            }
            if (current != registered) {
                preferences.setRegisteredGeofences(GeofenceRecord.encode(current.values))
            }
        }
    }

    // A fence registered while outside reports nothing, so a time it was entered from before would make a later stay
    // look long enough for a departure.
    private suspend fun forgetEntriesOutside(regions: List<GeofenceRegion>, here: Coordinate?) {
        if (here == null) {
            return
        }
        for (region in regions) {
            if (Geo.distanceMeters(here, Coordinate(region.latitude, region.longitude)) > region.radiusMeters) {
                placeRepository.setEnteredAt(region.id, null)
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 1_000L
    }
}
