package com.locatedo.locatedo.feature.home

import com.locatedo.locatedo.core.analytics.AlwaysPromptAnswer
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.core.permissions.Permissions
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.FakeCategoryRepository
import com.locatedo.locatedo.testing.FakeLocationRepository
import com.locatedo.locatedo.testing.FakePermissionsRepository
import com.locatedo.locatedo.testing.FakePlaceRepository
import com.locatedo.locatedo.testing.SettableClock
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
    private val location = FakeLocationRepository(coordinate = Coordinate(35.6896, 139.7006))
    private val permissions = FakePermissionsRepository(LocationAuth.ALWAYS, NotificationAuth.AUTHORIZED)
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
        val viewModel = HomeViewModel(places, categories, location, permissions, analytics, clock)
        assertTrue(viewModel.uiState.value.isLoading)

        places.state.value = listOf(PlaceWithTodos(store, listOf(milk)))
        subscribe(viewModel)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(store), state.places.map { it.place })
        assertEquals(grocery, state.categories[grocery.id])
        assertEquals(1, state.openTodoCount)
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

    private fun TestScope.viewModel(): HomeViewModel {
        val viewModel = HomeViewModel(places, categories, location, permissions, analytics, clock)
        subscribe(viewModel)
        return viewModel
    }

    private fun TestScope.subscribe(viewModel: HomeViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }
}
