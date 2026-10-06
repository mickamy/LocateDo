package com.locatedo.locatedo.feature.home

import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.location.GeocodedPlace
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.testing.FakeCategoryRepository
import com.locatedo.locatedo.testing.FakeGeocodingRepository
import com.locatedo.locatedo.testing.FakeLocationRepository
import com.locatedo.locatedo.testing.FakePlaceRepository
import com.locatedo.locatedo.testing.FakeTodoRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val places = FakePlaceRepository()
    private val categories = FakeCategoryRepository()
    private val todos = FakeTodoRepository()
    private val location = FakeLocationRepository(coordinate = Coordinate(35.6896, 139.7006))
    private val geocoding = FakeGeocodingRepository(GeocodedPlace(name = null, address = "1 Main St"))
    private val grocery = Category(id = uuidV7(now), name = "Grocery", icon = "cart", color = "green", sortOrder = 0, updatedAt = now)
    private val store = Place(id = uuidV7(now), name = "Store", latitude = 35.6580, longitude = 139.7016, categoryId = grocery.id, createdAt = now)
    private val milk = Todo(id = uuidV7(now), title = "Milk", placeId = store.id, createdAt = now)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        categories.state.value = listOf(grocery)
        places.state.value = listOf(PlaceWithTodos(store, listOf(milk)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startsLoadingThenShowsPlacesWithTheirCategories() = runTest(dispatcher) {
        places.state.value = emptyList()
        val viewModel = HomeViewModel(places, categories, todos, location, geocoding)
        assertTrue(viewModel.uiState.value.isLoading)

        places.state.value = listOf(PlaceWithTodos(store, listOf(milk)))
        subscribe(viewModel)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(store), state.places.map { it.place })
        assertEquals(grocery, state.categories[grocery.id])
        assertEquals(1, state.openTodoCount)
        assertNull(state.selected)
    }

    @Test
    fun selectingAPlaceBuildsItsDetail() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.select(store.id)

        val detail = viewModel.uiState.value.selected
        assertNotNull(detail)
        assertEquals(store, detail?.place)
        assertEquals(listOf(milk), detail?.openTodos)
        assertEquals(grocery, detail?.category)
        assertEquals("1 Main St", detail?.address)
        assertEquals(3_515.0, detail?.distanceMeters ?: 0.0, 30.0)
    }

    @Test
    fun clearingTheSelectionHidesTheDetail() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.select(store.id)

        viewModel.clearSelection()

        assertNull(viewModel.uiState.value.selected)
    }

    @Test
    fun completingATodoMovesItToTheCompletedList() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.select(store.id)
        todos.state.value = listOf(milk)

        viewModel.setTodoCompleted(milk.id, completed = true)
        places.state.value = listOf(PlaceWithTodos(store, todos.state.value))

        val detail = viewModel.uiState.value.selected
        assertEquals(emptyList<Todo>(), detail?.openTodos)
        assertEquals(listOf(milk.id), detail?.completedTodos?.map { it.id })
    }

    @Test
    fun reopeningOverTheFreeLimitRaisesAnEvent() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        todos.limit = FreeLimit.OPEN_TODOS

        viewModel.setTodoCompleted(milk.id, completed = false)

        assertEquals(listOf(HomeEvent.LimitReached(FreeLimit.OPEN_TODOS)), events)
    }

    @Test
    fun deletingThePlaceClearsTheSelection() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.select(store.id)

        viewModel.deletePlace(store.id)

        assertNull(viewModel.uiState.value.selected)
        assertEquals(emptyList<PlaceWithTodos>(), places.state.value)
    }

    private fun TestScope.viewModel(): HomeViewModel {
        val viewModel = HomeViewModel(places, categories, todos, location, geocoding)
        subscribe(viewModel)
        return viewModel
    }

    private fun TestScope.subscribe(viewModel: HomeViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }
}
