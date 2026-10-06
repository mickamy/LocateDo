package com.locatedo.locatedo.core.geofence

import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.testing.FakeGeofenceRegistrar
import com.locatedo.locatedo.testing.FakeLocationRepository
import com.locatedo.locatedo.testing.FakePermissionsRepository
import com.locatedo.locatedo.testing.FakePlaceRepository
import com.locatedo.locatedo.testing.testPreferences
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GeofenceSyncTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val places = FakePlaceRepository()
    private val permissions = FakePermissionsRepository(location = LocationAuth.ALWAYS)
    private val location = FakeLocationRepository(Coordinate(35.0, 139.0))
    private val registrar = FakeGeofenceRegistrar()
    private val store = place("Store", latitude = 35.001)
    private val office = place("Office", latitude = 35.010)

    @Test
    fun registersTheNearestPlacesAndRemembersThem() = runTest {
        val preferences = testPreferences(folder.root, backgroundScope)
        places.state.value = listOf(office, store).map { PlaceWithTodos(it, emptyList()) }
        val sync = sync(preferences)

        sync.sync()

        assertEquals(listOf(store.id, office.id), registrar.added.single().map { it.id })
        assertTrue(registrar.removed.isEmpty())
        assertEquals(setOf(store.id.toString(), office.id.toString()), GeofenceRecord.decode(preferences.registeredGeofences.first()).keys)
    }

    @Test
    fun onlyTheChangedPlacesAreTouchedOnTheNextSync() = runTest {
        val preferences = testPreferences(folder.root, backgroundScope)
        places.state.value = listOf(store, office).map { PlaceWithTodos(it, emptyList()) }
        val sync = sync(preferences)
        sync.sync()
        registrar.added.clear()

        val resized = store.copy(radiusMeters = 300.0)
        places.state.value = listOf(PlaceWithTodos(resized, emptyList()))
        sync.sync()

        assertEquals(listOf(office.id.toString()), registrar.removed.single())
        assertEquals(listOf(GeofenceRegion(resized)), registrar.added.single())
        assertEquals(mapOf(resized.id.toString() to GeofenceRegion(resized)), GeofenceRecord.decode(preferences.registeredGeofences.first()))
    }

    @Test
    fun withoutAlwaysLocationEverythingIsRemoved() = runTest {
        val preferences = testPreferences(folder.root, backgroundScope)
        places.state.value = listOf(PlaceWithTodos(store, emptyList()))
        val sync = sync(preferences)
        sync.sync()

        permissions.state.value = permissions.state.value.copy(location = LocationAuth.WHEN_IN_USE)
        sync.sync()

        assertEquals(listOf(store.id.toString()), registrar.removed.single())
        assertEquals("", preferences.registeredGeofences.first())
    }

    @Test
    fun aFailedRegistrationIsRetriedOnTheNextSync() = runTest {
        val preferences = testPreferences(folder.root, backgroundScope)
        places.state.value = listOf(PlaceWithTodos(store, emptyList()))
        val sync = sync(preferences)
        registrar.failAdd = true

        sync.sync()
        assertEquals("", preferences.registeredGeofences.first())

        registrar.failAdd = false
        sync.sync()

        assertEquals(listOf(store.id), registrar.added.single().map { it.id })
        assertEquals(setOf(store.id.toString()), GeofenceRecord.decode(preferences.registeredGeofences.first()).keys)
    }

    private fun TestScope.sync(preferences: com.locatedo.locatedo.core.datastore.AppPreferences) =
        GeofenceSync(places, permissions, location, registrar, preferences, TestScope(UnconfinedTestDispatcher(testScheduler)))

    private fun place(name: String, latitude: Double) =
        Place(id = uuidV7(now), name = name, latitude = latitude, longitude = 139.0, createdAt = now)
}
