package com.locatedo.locatedo.testing

import android.location.Location
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.location.GeocodedPlace
import com.locatedo.locatedo.core.location.GeocodingRepository
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.places.PlaceCandidate
import com.locatedo.locatedo.core.places.PlacePrediction
import com.locatedo.locatedo.core.places.PlacesRepository
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePlaceRepository : PlaceRepository {
    val state = MutableStateFlow<List<PlaceWithTodos>>(emptyList())
    val added = mutableListOf<Place>()
    val updated = mutableListOf<Place>()
    var limit: FreeLimit? = null

    override fun observeAll(): Flow<List<Place>> = state.map { entries -> entries.map { it.place } }

    override fun observeAllWithTodos(): Flow<List<PlaceWithTodos>> = state

    override fun observeWithTodos(id: UUID): Flow<PlaceWithTodos?> =
        state.map { entries -> entries.firstOrNull { it.place.id == id } }

    override suspend fun add(place: Place): FreeLimit? {
        limit?.let { return it }
        added += place
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

    override suspend fun markNotified(id: UUID, at: Instant) = Unit
}

class FakeCategoryRepository : CategoryRepository {
    val state = MutableStateFlow<List<Category>>(emptyList())

    override fun observeAll(): Flow<List<Category>> = state
    override suspend fun ensureBuiltins() = Unit
    override suspend fun add(category: Category) = Unit
    override suspend fun update(category: Category) = Unit
    override suspend fun delete(id: UUID): Boolean = true
    override suspend fun reorder(categories: List<Category>) = Unit
}

class FakeLocationRepository : LocationRepository {
    override fun hasForegroundPermission(): Boolean = false
    override fun hasPrecisePermission(): Boolean = false
    override suspend fun lastLocation(): Location? = null
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
