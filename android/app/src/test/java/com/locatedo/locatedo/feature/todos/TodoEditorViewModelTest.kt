package com.locatedo.locatedo.feature.todos

import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.PaywallTrigger
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.data.TodoUndo
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.model.TodoDeletionVia
import com.locatedo.locatedo.testing.FakeMembershipRepository
import com.locatedo.locatedo.testing.FakePlaceRepository
import com.locatedo.locatedo.testing.FakeTodoRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoEditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val todos = FakeTodoRepository()
    private val places = FakePlaceRepository()
    private val paywalls = PaywallRequests()
    private val undo = TodoUndo()
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
    fun anAssigneeIsSavedWithTheTodo() = runTest(dispatcher) {
        val viewModel = viewModel()
        val assignee = UUID.randomUUID()
        viewModel.start(store.id)
        viewModel.setTitle("Milk")
        viewModel.setAssignee(assignee)

        viewModel.save()

        assertEquals(assignee, todos.added.single().assigneeId)
    }

    @Test
    fun theFreeLimitOpensThePaywallAndKeepsTheSheet() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        todos.limit = FreeLimit.OPEN_TODOS
        viewModel.start(store.id)
        viewModel.setTitle("Milk")

        viewModel.save()

        assertEquals(PaywallTrigger.TODO_LIMIT, paywalls.pending.value)
        assertTrue(events.isEmpty())
        assertTrue(todos.added.isEmpty())
    }

    @Test
    fun editingFillsTheDraftAndSavesTheChangesToTheSameTodo() = runTest(dispatcher) {
        val pharmacy = Place(id = uuidV7(now), name = "Pharmacy", latitude = 35.0, longitude = 139.0, createdAt = now)
        val milk = Todo(id = uuidV7(now), title = "Milk", placeId = store.id, createdAt = now)
        todos.state.value = listOf(milk)
        val viewModel = viewModel()
        val events = events(viewModel)

        viewModel.startEditing(milk.id)
        val draft = viewModel.uiState.value.draft
        assertTrue(draft.isEditing)
        assertFalse(draft.isPlaceFixed)
        assertEquals("Milk", draft.title)
        viewModel.setTitle("Sunscreen ")
        viewModel.setPlace(pharmacy.id)
        viewModel.save()

        assertEquals(listOf(milk.copy(title = "Sunscreen", placeId = pharmacy.id)), todos.updated)
        assertTrue(todos.added.isEmpty())
        assertEquals(listOf(TodoEditorEvent.Saved), events)
    }

    @Test
    fun deletingFromTheEditorOffersUndo() = runTest(dispatcher) {
        val milk = Todo(id = uuidV7(now), title = "Milk", placeId = store.id, createdAt = now)
        todos.state.value = listOf(milk)
        val viewModel = viewModel()
        val events = events(viewModel)
        val offers = mutableListOf<List<Todo>>()
        backgroundScope.launch { undo.offers.collect { offers += it } }

        viewModel.startEditing(milk.id)
        viewModel.delete()

        assertEquals(listOf(milk.id), todos.deleted)
        assertEquals(listOf(TodoDeletionVia.EDITOR), todos.deletedVia)
        assertEquals(listOf(listOf(milk)), offers)
        assertEquals(listOf(TodoEditorEvent.Saved), events)
    }

    private fun TestScope.viewModel(): TodoEditorViewModel {
        val viewModel = TodoEditorViewModel(todos, places, FakeMembershipRepository(), paywalls, undo, Clock.fixed(now, ZoneOffset.UTC))
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun TestScope.events(viewModel: TodoEditorViewModel): List<TodoEditorEvent> {
        val events = mutableListOf<TodoEditorEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        return events
    }
}
