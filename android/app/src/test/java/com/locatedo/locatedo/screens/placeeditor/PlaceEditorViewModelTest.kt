package com.locatedo.locatedo.screens.placeeditor

import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.analytics.ScreenTracker
import com.locatedo.locatedo.core.analytics.TodoAddVia
import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.PaywallTrigger
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
import com.locatedo.locatedo.core.places.PlaceDuplicateChoice
import com.locatedo.locatedo.core.places.PlacePrediction
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.FakeCategoryRepository
import com.locatedo.locatedo.testing.FakeGeocodingRepository
import com.locatedo.locatedo.testing.FakeLocationRepository
import com.locatedo.locatedo.testing.FakePlaceRepository
import com.locatedo.locatedo.testing.FakePlacesRepository
import com.locatedo.locatedo.testing.FakeTodoRepository
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
    private val todos = FakeTodoRepository()
    private val paywalls = PaywallRequests()
    private val categories = FakeCategoryRepository()
    private val search = FakePlacesRepository()
    private val geocoding = FakeGeocodingRepository()
    private val analytics = FakeAnalytics()
    private val location = FakeLocationRepository()
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
    fun aNewDraftTakesTheDefaultRadiusAndNoCategoryUntilOneIsGuessed() = runTest(dispatcher) {
        val preferences = preferencesOf()
        preferences.setDefaultRadiusMeters(250.0)
        val viewModel = viewModel(preferences)

        viewModel.start(placeId = null)

        val draft = viewModel.uiState.value.draft
        assertEquals(250.0, draft.radiusMeters, 0.0)
        assertNull(draft.categoryId)
        assertFalse(draft.canSave)
    }

    @Test
    fun aSearchedStoreGuessesItsCategory() = runTest(dispatcher) {
        search.candidates["p1"] = search.candidates.getValue("p1").copy(types = listOf("supermarket", "store"))
        val viewModel = viewModel()
        viewModel.start(placeId = null)

        viewModel.selectPrediction(search.predictions.single())
        viewModel.confirmPick()

        val draft = viewModel.uiState.value.draft
        assertEquals(BuiltinCategory.SHOPPING, draft.suggestion)
        assertEquals(shopping.id, draft.categoryId)
    }

    @Test
    fun aStoreTappedOnTheMapGuessesFromItsTypes() = runTest(dispatcher) {
        search.types["poi1"] = listOf("convenience_store")
        val viewModel = viewModel()
        viewModel.start(placeId = null)

        viewModel.previewPick(store, name = "Corner Store", placeId = "poi1")
        viewModel.confirmPick()

        assertEquals(shopping.id, viewModel.uiState.value.draft.categoryId)
        assertEquals("Corner Store", viewModel.uiState.value.draft.name)
    }

    @Test
    fun aNewPickReplacesAPickedNameButNotATypedOne() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(placeId = null)
        viewModel.previewPick(store, name = "Corner Store", placeId = "poi1")
        viewModel.confirmPick()

        viewModel.previewPick(Coordinate(35.1, 139.1), name = "Bakery", placeId = "poi2")
        viewModel.confirmPick()
        assertEquals("Bakery", viewModel.uiState.value.draft.name)

        viewModel.setName("Mom's favorite")
        viewModel.previewPick(store, name = "Corner Store", placeId = "poi1")
        viewModel.confirmPick()
        assertEquals("Mom's favorite", viewModel.uiState.value.draft.name)
    }

    @Test
    fun whereYouAreIsShownOnTheMapBeforeItIsUsed() = runTest(dispatcher) {
        location.coordinate = store
        val viewModel = viewModel()
        val events = events(viewModel)
        viewModel.start(placeId = null)

        viewModel.previewCurrentLocation()

        assertEquals(store, viewModel.uiState.value.pickPreview.coordinate)
        assertTrue(events.none { it is PlaceEditorEvent.LocationChosen })

        viewModel.confirmPick()

        assertEquals(PlaceSource.CURRENT_LOCATION, viewModel.uiState.value.draft.source)
    }

    @Test
    fun aPinOnASavedPlaceAsksBeforeGoingOn() = runTest(dispatcher) {
        val saved = Place(id = uuidV7(now), name = "Store", latitude = store.latitude, longitude = store.longitude, createdAt = now)
        places.state.value = listOf(PlaceWithTodos(saved, emptyList()))
        val viewModel = viewModel()
        val events = events(viewModel)
        viewModel.start(placeId = null)
        viewModel.previewPick(Coordinate(store.latitude + 0.0001, store.longitude))

        viewModel.confirmPick()

        assertEquals(saved, viewModel.uiState.value.duplicate)
        assertTrue(events.none { it is PlaceEditorEvent.LocationChosen })

        viewModel.answerDuplicate(PlaceDuplicateChoice.OPEN)

        assertEquals(PlaceEditorEvent.OpenSavedPlace(saved.id), events.last())
        assertNull(viewModel.uiState.value.duplicate)
        assertEquals(mapOf("choice" to "open"), analytics.values(AnalyticsEvent.PLACE_DUPLICATE_PROMPTED))
    }

    @Test
    fun addingAnotherPlaceAtASavedOneGoesOn() = runTest(dispatcher) {
        val saved = Place(id = uuidV7(now), name = "Store", latitude = store.latitude, longitude = store.longitude, createdAt = now)
        places.state.value = listOf(PlaceWithTodos(saved, emptyList()))
        val viewModel = viewModel()
        val events = events(viewModel)
        viewModel.start(placeId = null)
        viewModel.previewPick(store)
        viewModel.confirmPick()

        viewModel.answerDuplicate(PlaceDuplicateChoice.ADD)

        assertEquals(PlaceEditorEvent.LocationChosen, events.last())
    }

    @Test
    fun movingASavedPlaceNeverAsks() = runTest(dispatcher) {
        val saved = Place(id = uuidV7(now), name = "Store", latitude = store.latitude, longitude = store.longitude, createdAt = now)
        places.state.value = listOf(PlaceWithTodos(saved, emptyList()))
        val viewModel = viewModel()
        val events = events(viewModel)
        viewModel.start(saved.id)
        viewModel.previewPick(store)

        viewModel.confirmPick()

        assertNull(viewModel.uiState.value.duplicate)
        assertEquals(PlaceEditorEvent.LocationChosen, events.last())
    }

    @Test
    fun aPlainMapPointGuessesNothing() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(placeId = null)

        viewModel.previewPick(store)
        viewModel.confirmPick()

        assertNull(viewModel.uiState.value.draft.suggestion)
        assertNull(viewModel.uiState.value.draft.categoryId)
    }

    @Test
    fun aCategoryChosenByHandSurvivesANewPick() = runTest(dispatcher) {
        search.candidates["p1"] = search.candidates.getValue("p1").copy(types = listOf("supermarket"))
        val viewModel = viewModel()
        viewModel.start(placeId = null)
        viewModel.setCategory(other.id)

        viewModel.selectPrediction(search.predictions.single())
        viewModel.confirmPick()

        assertEquals(other.id, viewModel.uiState.value.draft.categoryId)
        assertEquals(BuiltinCategory.SHOPPING, viewModel.uiState.value.draft.suggestion)
    }

    @Test
    fun savingWithoutACategoryUsesOtherAndLogsTheGuess() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(placeId = null)
        viewModel.previewPick(store)
        viewModel.confirmPick()
        viewModel.setName("Home")

        viewModel.save()

        assertEquals(other.id, places.added.single().categoryId)
        assertEquals(listOf<BuiltinCategory?>(null), places.suggestions)
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
    fun searchMeasuresDistancesFromTheCurrentLocation() = runTest(dispatcher) {
        val here = Coordinate(35.5, 139.5)
        location.coordinate = here
        val viewModel = viewModel()
        viewModel.start(placeId = null)

        viewModel.setQuery("sup")
        advanceTimeBy(301)

        assertEquals(listOf<Coordinate?>(here), search.origins)
    }

    @Test
    fun choosingAPredictionShowsItOnTheMapFirst() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        viewModel.start(placeId = null)

        viewModel.selectPrediction(search.predictions.single())

        assertEquals(
            PickPreview(coordinate = store, name = "Supermarket", address = "1 Main St", source = PlaceSource.SEARCH),
            viewModel.uiState.value.pickPreview,
        )
        assertNull(viewModel.uiState.value.draft.coordinate)
        assertEquals(listOf(PlaceEditorEvent.PredictionFetched), events)
    }

    @Test
    fun confirmingAPredictionFillsTheDraft() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        viewModel.start(placeId = null)
        viewModel.selectPrediction(search.predictions.single())

        viewModel.confirmPick()

        val draft = viewModel.uiState.value.draft
        assertEquals(store, draft.coordinate)
        assertEquals("Supermarket", draft.name)
        assertEquals("1 Main St", draft.address)
        assertEquals(PlaceSource.SEARCH, draft.source)
        assertEquals(PlaceEditorEvent.LocationChosen, events.last())
        assertTrue(draft.canSave)
    }

    @Test
    fun tappingTheMapAfterASearchPicksFromTheMap() = runTest(dispatcher) {
        val moved = Coordinate(35.001, 139.001)
        geocoding.result = GeocodedPlace(name = "Corner shop", address = "2 Side St")
        val viewModel = viewModel()
        viewModel.start(placeId = null)
        viewModel.selectPrediction(search.predictions.single())

        viewModel.previewPick(moved)
        viewModel.confirmPick()

        val draft = viewModel.uiState.value.draft
        assertEquals(moved, draft.coordinate)
        assertEquals("Corner shop", draft.name)
        assertEquals(PlaceSource.MAP, draft.source)
    }

    @Test
    fun pickingOnTheMapDropsAnEarlierSearchResult() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(placeId = null)
        viewModel.selectPrediction(search.predictions.single())

        viewModel.pickOnMap()

        assertEquals(PickPreview(), viewModel.uiState.value.pickPreview)
    }

    @Test
    fun aTypedNameIsKeptWhenTheLocationIsChosen() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(placeId = null)
        viewModel.setName("Weekly groceries")

        viewModel.selectPrediction(search.predictions.single())
        viewModel.confirmPick()

        assertEquals("Weekly groceries", viewModel.uiState.value.draft.name)
    }

    @Test
    fun savingANewPlaceAddsItFromTheDraft() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        viewModel.start(placeId = null)
        viewModel.selectPrediction(search.predictions.single())
        viewModel.confirmPick()
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
        assertEquals(listOf(PlaceSource.SEARCH), places.sources)
        assertEquals(PlaceEditorEvent.Saved(placeId = saved.id, isNew = true), events.last())
    }

    @Test
    fun todosTypedWithANewPlaceFollowIt() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(placeId = null)
        viewModel.selectPrediction(search.predictions.single())
        viewModel.confirmPick()
        viewModel.setTodoDraft("Milk")
        viewModel.addTodoDraft()
        viewModel.setTodoDraft(" Eggs ")
        viewModel.addTodoDraft()
        viewModel.setTodo(1, "Bread")
        viewModel.setTodoDraft("  ")
        viewModel.addTodoDraft()
        viewModel.setTodoDraft("Butter")

        viewModel.save()

        val place = places.added.single()
        assertEquals(listOf(3), places.todoCounts)
        assertEquals(listOf("Milk", "Bread", "Butter"), todos.added.map { it.title })
        assertTrue(todos.added.all { it.placeId == place.id && it.assigneeId == null })
        assertEquals(List(3) { TodoAddVia.PLACE_EDITOR }, todos.addedVia)
    }

    @Test
    fun removingATypedTodoLeavesItOut() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(placeId = null)
        viewModel.selectPrediction(search.predictions.single())
        viewModel.confirmPick()
        viewModel.setTodoDraft("Milk")
        viewModel.addTodoDraft()
        viewModel.setTodoDraft("Eggs")
        viewModel.addTodoDraft()

        viewModel.removeTodo(0)
        viewModel.save()

        assertEquals(listOf("Eggs"), todos.added.map { it.title })
    }

    @Test
    fun theFreePlanStopsTypedTodosAtTheLimit() = runTest(dispatcher) {
        todos.remaining = 1
        val viewModel = viewModel()
        viewModel.start(placeId = null)
        viewModel.selectPrediction(search.predictions.single())
        viewModel.confirmPick()
        viewModel.setTodoDraft("Milk")
        viewModel.addTodoDraft()

        assertEquals(0, viewModel.uiState.value.todosLeft)
        viewModel.setTodoDraft("Eggs")
        viewModel.addTodoDraft()
        assertEquals(listOf("Milk"), viewModel.uiState.value.draft.todos)

        viewModel.save()

        assertEquals(listOf("Milk"), todos.added.map { it.title })
        assertEquals(listOf(1), places.todoCounts)
    }

    @Test
    fun aPlaceOverTheLimitAddsNoTodos() = runTest(dispatcher) {
        places.limit = FreeLimit.PLACES
        val viewModel = viewModel()
        viewModel.start(placeId = null)
        viewModel.selectPrediction(search.predictions.single())
        viewModel.confirmPick()
        viewModel.setTodoDraft("Milk")

        viewModel.save()

        assertTrue(todos.added.isEmpty())
    }

    @Test
    fun theFormReportsWhetherItCreatesOrEdits() = runTest(dispatcher) {
        val stored = Place(id = uuidV7(now), name = "Store", latitude = 35.0, longitude = 139.0, createdAt = now)
        places.state.value = listOf(PlaceWithTodos(stored, emptyList()))
        val viewModel = viewModel()

        viewModel.start(placeId = null)
        viewModel.detailsShown()
        viewModel.start(stored.id)
        viewModel.detailsShown()

        assertEquals(
            listOf(
                AnalyticsScreen.PLACE_EDITOR to mapOf("mode" to "new", "step" to "details"),
                AnalyticsScreen.PLACE_EDITOR to mapOf("mode" to "edit"),
            ),
            analytics.screens,
        )
    }

    @Test
    fun savingOverTheFreeLimitOpensThePaywall() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        places.limit = FreeLimit.PLACES
        viewModel.start(placeId = null)
        viewModel.selectPrediction(search.predictions.single())
        viewModel.confirmPick()

        viewModel.save()

        assertEquals(PaywallTrigger.PLACE_LIMIT, paywalls.pending.value)
        assertTrue(events.none { it is PlaceEditorEvent.Saved })
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
        assertEquals(PlaceEditorEvent.Saved(placeId = stored.id, isNew = false), events.last())
    }

    @Test
    fun confirmingAPickUsesTheGeocodedAddress() = runTest(dispatcher) {
        geocoding.result = GeocodedPlace(name = "Corner shop", address = "2 Side St")
        val viewModel = viewModel()
        viewModel.start(placeId = null)

        viewModel.previewPick(store)
        assertEquals("Corner shop", viewModel.uiState.value.pickPreview.name)
        viewModel.confirmPick()

        val draft = viewModel.uiState.value.draft
        assertEquals(store, draft.coordinate)
        assertEquals("Corner shop", draft.name)
        assertEquals("2 Side St", draft.address)
        assertEquals(PlaceSource.MAP, draft.source)
    }

    @Test
    fun aStoreTappedOnTheMapKeepsItsName() = runTest(dispatcher) {
        geocoding.result = GeocodedPlace(name = "1-2 Main St", address = "1-2 Main St")
        val viewModel = viewModel()
        viewModel.start(placeId = null)

        viewModel.previewPick(store, name = "Bakery")
        viewModel.confirmPick()

        val draft = viewModel.uiState.value.draft
        assertEquals("Bakery", draft.name)
        assertEquals("1-2 Main St", draft.address)
        assertEquals(PlaceSource.MAP, draft.source)
    }

    @Test
    fun aBlankNameCannotBeSaved() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        viewModel.start(placeId = null)
        viewModel.selectPrediction(search.predictions.single())
        viewModel.confirmPick()
        viewModel.setName("   ")

        viewModel.save()

        assertTrue(events.none { it is PlaceEditorEvent.Saved })
        assertTrue(places.added.isEmpty())
    }

    @Test
    fun fromTheTodoSheetItIsMarkedSoTheCategoryStepSaves() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.start(placeId = null, isForTodo = true)
        assertTrue(viewModel.uiState.value.draft.isForTodo)

        viewModel.start(placeId = null)
        assertFalse(viewModel.uiState.value.draft.isForTodo)
    }

    private fun TestScope.viewModel(preferences: AppPreferences = preferencesOf()): PlaceEditorViewModel {
        val viewModel = PlaceEditorViewModel(
            places = places,
            todos = todos,
            categories = categories,
            placesSearch = search,
            geocoding = geocoding,
            location = location,
            preferences = preferences,
            paywallRequests = paywalls,
            analytics = analytics,
            screenTracker = ScreenTracker(analytics),
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
