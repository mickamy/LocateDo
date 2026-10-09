package com.locatedo.locatedo.feature.place

import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.PaywallTrigger
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.data.TodoUndo
import com.locatedo.locatedo.core.location.GeocodedPlace
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.testing.FakeArrivalSimulator
import com.locatedo.locatedo.testing.FakeCategoryRepository
import com.locatedo.locatedo.testing.FakeGeocodingRepository
import com.locatedo.locatedo.testing.FakeLocationRepository
import com.locatedo.locatedo.testing.FakeMembershipRepository
import com.locatedo.locatedo.testing.FakePlaceRepository
import com.locatedo.locatedo.testing.FakeTodoRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val places = FakePlaceRepository()
    private val categories = FakeCategoryRepository()
    private val todos = FakeTodoRepository()
    private val memberships = FakeMembershipRepository()
    private val location = FakeLocationRepository(coordinate = Coordinate(35.6896, 139.7006))
    private val geocoding = FakeGeocodingRepository(GeocodedPlace(name = null, address = "1 Main St"))
    private val paywalls = PaywallRequests()
    private val arrivals = FakeArrivalSimulator()
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
    fun buildsTheDetailWithItsCategoryAddressAndDistance() = runTest(dispatcher) {
        val viewModel = viewModel(store.id)

        val detail = viewModel.uiState.value.detail
        assertEquals(store, detail?.place)
        assertEquals(listOf(milk), detail?.openTodos)
        assertEquals(grocery, detail?.category)
        assertEquals("1 Main St", detail?.address)
        assertEquals(3_515.0, detail?.distanceMeters ?: 0.0, 30.0)
    }

    @Test
    fun completingATodoMovesItToTheCompletedList() = runTest(dispatcher) {
        val viewModel = viewModel(store.id)
        todos.state.value = listOf(milk)

        viewModel.setTodoCompleted(milk.id, completed = true)
        places.state.value = listOf(PlaceWithTodos(store, todos.state.value))

        val detail = viewModel.uiState.value.detail
        assertEquals(emptyList<Todo>(), detail?.openTodos)
        assertEquals(listOf(milk.id), detail?.completedTodos?.map { it.id })
    }

    @Test
    fun reopeningOverTheFreeLimitAsksForThePaywall() = runTest(dispatcher) {
        val viewModel = viewModel(store.id)
        todos.limit = FreeLimit.OPEN_TODOS

        viewModel.setTodoCompleted(milk.id, completed = false)

        assertEquals(PaywallTrigger.TODO_LIMIT, paywalls.pending.value)
    }

    @Test
    fun deletingThePlaceLeavesNoDetail() = runTest(dispatcher) {
        val viewModel = viewModel(store.id)

        viewModel.deletePlace()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.detail)
        assertEquals(emptyList<PlaceWithTodos>(), places.state.value)
    }

    @Test
    fun anUnknownPlaceLoadsWithoutADetail() = runTest(dispatcher) {
        val viewModel = viewModel(uuidV7(now))

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.detail)
    }

    @Test
    fun aDebugArrivalIsHandedOnWithItsDelay() = runTest(dispatcher) {
        val viewModel = viewModel(store.id)

        viewModel.simulateArrival(Duration.ofSeconds(10))

        assertEquals(listOf(store.id to Duration.ofSeconds(10)), arrivals.arrivals)
    }

    private fun TestScope.viewModel(placeId: UUID): PlaceDetailViewModel {
        val viewModel = PlaceDetailViewModel(placeId, places, categories, memberships, todos, location, geocoding, paywalls, arrivals, TodoUndo())
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }
}
