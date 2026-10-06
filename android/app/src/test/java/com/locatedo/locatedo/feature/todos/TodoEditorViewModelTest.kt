package com.locatedo.locatedo.feature.todos

import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.testing.FakePlaceRepository
import com.locatedo.locatedo.testing.FakeTodoRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoEditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val todos = FakeTodoRepository()
    private val places = FakePlaceRepository()
    private val store = Place(id = uuidV7(now), name = "Store", latitude = 35.0, longitude = 139.0, createdAt = now)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        places.state.value = listOf(PlaceWithTodos(store, emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun savesATodoForTheFixedPlace() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        viewModel.start(store.id)
        viewModel.setTitle("  Milk ")

        viewModel.save()

        val saved = todos.added.single()
        assertEquals("Milk", saved.title)
        assertEquals(store.id, saved.placeId)
        assertEquals(now, saved.createdAt)
        assertEquals(listOf(TodoEditorEvent.Saved), events)
    }

    @Test
    fun withoutAFixedPlaceThePickerOffersThePlaces() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(placeId = null)

        assertFalse(viewModel.uiState.value.draft.isPlaceFixed)
        assertEquals(listOf(store), viewModel.uiState.value.places)
        viewModel.setTitle("Milk")
        assertFalse(viewModel.uiState.value.draft.canSave)
        viewModel.setPlace(store.id)
        assertTrue(viewModel.uiState.value.draft.canSave)
    }

    @Test
    fun theFreeLimitIsReported() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        todos.limit = FreeLimit.OPEN_TODOS
        viewModel.start(store.id)
        viewModel.setTitle("Milk")

        viewModel.save()

        assertEquals(listOf(TodoEditorEvent.LimitReached(FreeLimit.OPEN_TODOS)), events)
        assertTrue(todos.added.isEmpty())
    }

    private fun TestScope.viewModel(): TodoEditorViewModel {
        val viewModel = TodoEditorViewModel(todos, places, Clock.fixed(now, ZoneOffset.UTC))
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun TestScope.events(viewModel: TodoEditorViewModel): List<TodoEditorEvent> {
        val events = mutableListOf<TodoEditorEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        return events
    }
}
