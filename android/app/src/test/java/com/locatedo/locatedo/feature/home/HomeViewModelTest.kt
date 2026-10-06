package com.locatedo.locatedo.feature.home

import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.testing.FakeCategoryRepository
import com.locatedo.locatedo.testing.FakeLocationRepository
import com.locatedo.locatedo.testing.FakePlaceRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
