package com.locatedo.locatedo.feature.home

import com.locatedo.locatedo.core.analytics.AlwaysPromptAnswer
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.PaywallTrigger
import com.locatedo.locatedo.core.common.PlaceSelectionRequests
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.location.GeocodedPlace
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.core.permissions.Permissions
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.FakeArrivalSimulator
import com.locatedo.locatedo.testing.FakeCategoryRepository
import com.locatedo.locatedo.testing.FakeGeocodingRepository
import com.locatedo.locatedo.testing.FakeLocationRepository
import com.locatedo.locatedo.testing.FakeMembershipRepository
import com.locatedo.locatedo.testing.FakePermissionsRepository
import com.locatedo.locatedo.testing.FakePlaceRepository
import com.locatedo.locatedo.testing.FakeTodoRepository
import com.locatedo.locatedo.testing.SettableClock
import java.time.Duration
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
    private val memberships = FakeMembershipRepository()
    private val location = FakeLocationRepository(coordinate = Coordinate(35.6896, 139.7006))
    private val geocoding = FakeGeocodingRepository(GeocodedPlace(name = null, address = "1 Main St"))
    private val requests = PlaceSelectionRequests()
    private val paywalls = PaywallRequests()
    private val permissions = FakePermissionsRepository(LocationAuth.ALWAYS, NotificationAuth.AUTHORIZED)
    private val arrivals = FakeArrivalSimulator()
    private val analytics = FakeAnalytics()
    private val clock = SettableClock(Instant.parse("2026-10-06T00:00:00Z"))
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
        val viewModel = HomeViewModel(places, categories, memberships, todos, location, geocoding, requests, paywalls, permissions, arrivals, analytics, clock)
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
    fun reopeningOverTheFreeLimitAsksForThePaywall() = runTest(dispatcher) {
        val viewModel = viewModel()
        todos.limit = FreeLimit.OPEN_TODOS

        viewModel.setTodoCompleted(milk.id, completed = false)

        assertEquals(PaywallTrigger.TODO_LIMIT, paywalls.pending.value)
    }

    @Test
    fun deletingThePlaceClearsTheSelection() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.select(store.id)

        viewModel.deletePlace(store.id)

        assertNull(viewModel.uiState.value.selected)
        assertEquals(emptyList<PlaceWithTodos>(), places.state.value)
    }

    @Test
    fun nearbyPlacesAreOrderedByDistanceOnceLocated() = runTest(dispatcher) {
        val pharmacy = Place(id = uuidV7(now), name = "Pharmacy", latitude = 35.6890, longitude = 139.7000, createdAt = now)
        places.state.value = listOf(PlaceWithTodos(store, listOf(milk)), PlaceWithTodos(pharmacy, emptyList()))
        val viewModel = viewModel()

        viewModel.locateMe()

        assertEquals(listOf("Pharmacy", "Store"), viewModel.uiState.value.nearby.map { it.entry.place.name })
        assertTrue((viewModel.uiState.value.nearby.first().distanceMeters ?: Double.MAX_VALUE) < 200)
    }

    @Test
    fun aPendingSelectionRequestOpensThePlace() = runTest(dispatcher) {
        requests.request(store.id)

        val viewModel = viewModel()

        assertEquals(store, viewModel.uiState.value.selected?.place)
        assertNull(requests.pending.value)
    }

    @Test
    fun theBannerFollowsThePermissions() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertNull(viewModel.uiState.value.permissionBanner)

        permissions.state.value = Permissions(LocationAuth.WHEN_IN_USE, NotificationAuth.DENIED)

        assertEquals(PermissionBanner.LOCATION_ALWAYS, viewModel.uiState.value.permissionBanner)
    }

    @Test
    fun tappingTheAllTheTimeBannerExplainsFirstAndLogsTheTapAndTheAnswer() = runTest(dispatcher) {
        permissions.state.value = Permissions(LocationAuth.WHEN_IN_USE, NotificationAuth.AUTHORIZED)
        val viewModel = viewModel()

        viewModel.permissionBannerTapped(PermissionBanner.LOCATION_ALWAYS)

        assertTrue(viewModel.isExplainingAlwaysLocation.value)
        assertEquals(mapOf("kind" to "location_always"), analytics.values(AnalyticsEvent.PERMISSION_BANNER_TAPPED))

        clock.now = clock.now.plusSeconds(4)
        viewModel.alwaysLocationAnswered(AlwaysPromptAnswer.DISMISSED)
        viewModel.dismissAlwaysLocation()

        assertFalse(viewModel.isExplainingAlwaysLocation.value)
        assertEquals(
            mapOf("result" to "dismissed", "duration_s" to 4L),
            analytics.values(AnalyticsEvent.ALWAYS_PROMPT_ANSWERED),
        )
    }

    @Test
    fun theOtherBannersOnlyLogTheTap() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.permissionBannerTapped(PermissionBanner.LOCATION_DENIED)
        viewModel.permissionBannerTapped(PermissionBanner.NOTIFICATIONS)

        assertFalse(viewModel.isExplainingAlwaysLocation.value)
        assertEquals(
            listOf(mapOf("kind" to "location_denied"), mapOf("kind" to "notifications")),
            analytics.events.filter { it.first == AnalyticsEvent.PERMISSION_BANNER_TAPPED }.map { it.second },
        )
    }

    @Test
    fun aDebugArrivalIsHandedOnWithItsDelay() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.simulateArrival(store.id, Duration.ofSeconds(10))

        assertEquals(listOf(store.id to Duration.ofSeconds(10)), arrivals.arrivals)
    }

    private fun TestScope.viewModel(): HomeViewModel {
        val viewModel = HomeViewModel(places, categories, memberships, todos, location, geocoding, requests, paywalls, permissions, arrivals, analytics, clock)
        subscribe(viewModel)
        return viewModel
    }

    private fun TestScope.subscribe(viewModel: HomeViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }
}
