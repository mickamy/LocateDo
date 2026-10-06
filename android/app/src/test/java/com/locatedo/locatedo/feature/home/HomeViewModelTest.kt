package com.locatedo.locatedo.feature.home

import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Todo
import android.location.Location
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val places = FakePlaceRepository()
    private val categories = FakeCategoryRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startsLoadingThenShowsPlacesWithTheirCategories() = runTest {
        val viewModel = HomeViewModel(places, categories, FakeLocationRepository())
        assertTrue(viewModel.uiState.value.isLoading)

        val grocery = Category(id = uuidV7(now), name = "Grocery", icon = "cart", color = "green", sortOrder = 0, updatedAt = now)
        categories.state.value = listOf(grocery)
        val store = Place(id = uuidV7(now), name = "Store", latitude = 35.0, longitude = 139.0, categoryId = grocery.id, createdAt = now)
        places.state.value = listOf(PlaceWithTodos(store, listOf(Todo(id = uuidV7(now), title = "Milk", placeId = store.id, createdAt = now))))
        // stateIn only runs while someone collects, so subscribe on a dispatcher that starts right away.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(store), state.places.map { it.place })
        assertEquals(grocery, state.categories[grocery.id])
        assertEquals(1, state.openTodoCount)
    }
}

private class FakePlaceRepository : PlaceRepository {
    val state = MutableStateFlow<List<PlaceWithTodos>>(emptyList())

    override fun observeAll(): Flow<List<Place>> = state.map { entries -> entries.map { it.place } }
    override fun observeAllWithTodos(): Flow<List<PlaceWithTodos>> = state
    override fun observeWithTodos(id: UUID): Flow<PlaceWithTodos?> = state.map { entries -> entries.firstOrNull { it.place.id == id } }
    override suspend fun add(place: Place): FreeLimit? = null
    override suspend fun update(place: Place) = Unit
    override suspend fun delete(id: UUID) = Unit
    override suspend fun markNotified(id: UUID, at: Instant) = Unit
}

private class FakeCategoryRepository : CategoryRepository {
    val state = MutableStateFlow<List<Category>>(emptyList())

    override fun observeAll(): Flow<List<Category>> = state
    override suspend fun ensureBuiltins() = Unit
    override suspend fun add(category: Category) = Unit
    override suspend fun update(category: Category) = Unit
    override suspend fun delete(id: UUID): Boolean = true
    override suspend fun reorder(categories: List<Category>) = Unit
}

private class FakeLocationRepository : LocationRepository {
    override fun hasForegroundPermission(): Boolean = false
    override fun hasPrecisePermission(): Boolean = false
    override suspend fun lastLocation(): Location? = null
}
