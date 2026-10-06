package com.locatedo.locatedo.feature.place

import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.location.GeocodedPlace
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceSource
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.places.PlaceCandidate
import com.locatedo.locatedo.core.places.PlacePrediction
import com.locatedo.locatedo.testing.FakeCategoryRepository
import com.locatedo.locatedo.testing.FakeGeocodingRepository
import com.locatedo.locatedo.testing.FakeLocationRepository
import com.locatedo.locatedo.testing.FakePlaceRepository
import com.locatedo.locatedo.testing.FakePlacesRepository
import com.locatedo.locatedo.testing.testPreferences
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceEditorViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val places = FakePlaceRepository()
    private val categories = FakeCategoryRepository()
    private val search = FakePlacesRepository()
    private val geocoding = FakeGeocodingRepository()
    private val other = Category(id = uuidV7(now), builtin = BuiltinCategory.OTHER, icon = "mappin", color = "gray", sortOrder = 3, updatedAt = now)
    private val shopping = Category(id = uuidV7(now), builtin = BuiltinCategory.SHOPPING, icon = "cart", color = "green", sortOrder = 0, updatedAt = now)
    private val store = Coordinate(35.0, 139.0)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        categories.state.value = listOf(shopping, other)
        search.predictions = listOf(PlacePrediction("p1", "Supermarket", "1 Main St"))
        search.candidates["p1"] = PlaceCandidate(name = "Supermarket", address = "1 Main St", coordinate = store)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aNewDraftTakesTheDefaultRadiusAndTheOtherCategory() = runTest(dispatcher) {
        val preferences = preferencesOf()
        preferences.setDefaultRadiusMeters(250.0)
        val viewModel = viewModel(preferences)

        viewModel.start(placeId = null)

        val draft = viewModel.uiState.value.draft
        assertEquals(250.0, draft.radiusMeters, 0.0)
        assertEquals(other.id, draft.categoryId)
        assertFalse(draft.canSave)
    }

    @Test
    fun typingSearchesAfterADebounce() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(placeId = null)

        viewModel.setQuery("sup")
        assertEquals(emptyList<String>(), search.queries)
        advanceTimeBy(301)

        assertEquals(listOf("sup"), search.queries)
        assertEquals(search.predictions, viewModel.uiState.value.predictions)
    }

    @Test
    fun choosingAPredictionFillsTheDraft() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        viewModel.start(placeId = null)

        viewModel.selectPrediction(search.predictions.single())

        val draft = viewModel.uiState.value.draft
        assertEquals(store, draft.coordinate)
        assertEquals("Supermarket", draft.name)
        assertEquals("1 Main St", draft.address)
        assertEquals(PlaceSource.SEARCH, draft.source)
        assertEquals(listOf(PlaceEditorEvent.LocationChosen), events)
        assertTrue(draft.canSave)
    }

    @Test
    fun aTypedNameIsKeptWhenTheLocationIsChosen() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(placeId = null)
        viewModel.setName("Weekly groceries")

        viewModel.selectPrediction(search.predictions.single())

        assertEquals("Weekly groceries", viewModel.uiState.value.draft.name)
    }

    @Test
    fun savingANewPlaceAddsItFromTheDraft() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        viewModel.start(placeId = null)
        viewModel.selectPrediction(search.predictions.single())
        viewModel.setName("  Store  ")
        viewModel.setRadius(200.0)
        viewModel.setCategory(shopping.id)

        viewModel.save()

        val saved = places.added.single()
        assertEquals("Store", saved.name)
        assertEquals(store.latitude, saved.latitude, 0.0)
        assertEquals(200.0, saved.radiusMeters, 0.0)
        assertEquals(shopping.id, saved.categoryId)
        assertEquals(now, saved.createdAt)
        assertEquals(PlaceEditorEvent.Saved, events.last())
    }

    @Test
    fun savingOverTheFreeLimitRaisesTheLimit() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        places.limit = FreeLimit.PLACES
        viewModel.start(placeId = null)
        viewModel.selectPrediction(search.predictions.single())

        viewModel.save()

        assertEquals(PlaceEditorEvent.LimitReached(FreeLimit.PLACES), events.last())
        assertTrue(places.added.isEmpty())
    }

    @Test
    fun editingLoadsTheStoredPlaceAndUpdatesIt() = runTest(dispatcher) {
        val stored = Place(id = uuidV7(now), name = "Store", latitude = 35.0, longitude = 139.0, categoryId = shopping.id, createdAt = now)
        places.state.value = listOf(PlaceWithTodos(stored, emptyList()))
        val viewModel = viewModel()
        val events = events(viewModel)

        viewModel.start(stored.id)
        assertEquals("Store", viewModel.uiState.value.draft.name)
        assertTrue(viewModel.uiState.value.draft.isEditing)
        viewModel.setName("Supermarket")
        viewModel.save()

        assertEquals("Supermarket", places.updated.single().name)
        assertEquals(stored.id, places.updated.single().id)
        assertEquals(PlaceEditorEvent.Saved, events.last())
    }

    @Test
    fun confirmingAPickUsesTheGeocodedAddress() = runTest(dispatcher) {
        geocoding.result = GeocodedPlace(name = "Corner shop", address = "2 Side St")
        val viewModel = viewModel()
        viewModel.start(placeId = null)

        viewModel.previewPick(store)
        advanceTimeBy(401)
        assertEquals("Corner shop", viewModel.uiState.value.pickPreview.name)
        viewModel.confirmPick()

        val draft = viewModel.uiState.value.draft
        assertEquals(store, draft.coordinate)
        assertEquals("Corner shop", draft.name)
        assertEquals("2 Side St", draft.address)
        assertEquals(PlaceSource.MAP, draft.source)
    }

    @Test
    fun aBlankNameCannotBeSaved() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        viewModel.start(placeId = null)
        viewModel.selectPrediction(search.predictions.single())
        viewModel.setName("   ")

        viewModel.save()

        assertNull(events.lastOrNull { it != PlaceEditorEvent.LocationChosen })
        assertTrue(places.added.isEmpty())
    }

    private fun TestScope.viewModel(preferences: AppPreferences = preferencesOf()): PlaceEditorViewModel {
        val viewModel = PlaceEditorViewModel(
            placeRepository = places,
            categoryRepository = categories,
            placesRepository = search,
            geocodingRepository = geocoding,
            locationRepository = FakeLocationRepository(),
            preferences = preferences,
            clock = clock,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun TestScope.events(viewModel: PlaceEditorViewModel): List<PlaceEditorEvent> {
        val events = mutableListOf<PlaceEditorEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        return events
    }

    private fun TestScope.preferencesOf() = testPreferences(folder.root, backgroundScope)
}
