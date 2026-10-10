package com.locatedo.locatedo.screens.todos

import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.PaywallTrigger
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.data.TodoUndo
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.model.TodoDeletionVia
import com.locatedo.locatedo.testing.FakeCategoryRepository
import com.locatedo.locatedo.testing.FakeMembershipRepository
import com.locatedo.locatedo.testing.FakePlaceRepository
import com.locatedo.locatedo.testing.FakeSyncEngine
import com.locatedo.locatedo.testing.FakeTodoRepository
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testSession
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AllTodosViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val places = FakePlaceRepository()
    private val categories = FakeCategoryRepository()
    private val todos = FakeTodoRepository()
    private val memberships = FakeMembershipRepository()
    private val paywalls = PaywallRequests()
    private val store = Place(id = uuidV7(now), name = "Store", latitude = 35.0, longitude = 139.0, createdAt = now)
    private val pharmacy = Place(id = uuidV7(now), name = "Pharmacy", latitude = 35.1, longitude = 139.1, createdAt = now)
    private val milk = Todo(id = uuidV7(now), title = "Milk", placeId = store.id, createdAt = now)
    private val bread = Todo(id = uuidV7(now), title = "Bread", placeId = store.id, createdAt = now.plusSeconds(1), completedAt = now.plusSeconds(60))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun opensOnOpenToDos() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertEquals(TodoFilter.OPEN, viewModel.uiState.value.filter)
    }

    @Test
    fun withNoTodosItSaysSo() = runTest(dispatcher) {
        places.state.value = listOf(PlaceWithTodos(store, emptyList()))
        val viewModel = viewModel()

        assertFalse(viewModel.uiState.value.hasTodos)
    }

    @Test
    fun theOpenFilterGroupsOpenTodosByPlace() = runTest(dispatcher) {
        places.state.value = listOf(PlaceWithTodos(store, listOf(milk, bread)), PlaceWithTodos(pharmacy, emptyList()))
        val viewModel = viewModel()

        val state = viewModel.uiState.value
        assertTrue(state.hasTodos)
        assertEquals(listOf("Store"), state.groups.map { it.place.name })
        assertEquals(listOf("Milk"), state.groups.single().todos.map { it.title })
    }

    @Test
    fun theDoneFilterShowsCompletedTodosAndReportsNoMatches() = runTest(dispatcher) {
        places.state.value = listOf(PlaceWithTodos(store, listOf(milk, bread)))
        val viewModel = viewModel()

        viewModel.setFilter(TodoFilter.DONE)
        assertEquals(listOf("Bread"), viewModel.uiState.value.groups.single().todos.map { it.title })

        places.state.value = listOf(PlaceWithTodos(store, listOf(milk)))
        assertTrue(viewModel.uiState.value.hasTodos)
        assertTrue(viewModel.uiState.value.groups.isEmpty())
    }

    @Test
    fun theAllFilterListsOpenThenCompleted() = runTest(dispatcher) {
        places.state.value = listOf(PlaceWithTodos(store, listOf(bread, milk)))
        val viewModel = viewModel()

        viewModel.setFilter(TodoFilter.ALL)

        assertEquals(listOf("Milk", "Bread"), viewModel.uiState.value.groups.single().todos.map { it.title })
    }

    @Test
    fun deletingCompletedTakesThemFromEveryPlace() = runTest(dispatcher) {
        val sunscreen = Todo(id = uuidV7(now), title = "Sunscreen", placeId = pharmacy.id, createdAt = now, completedAt = now)
        places.state.value = listOf(PlaceWithTodos(store, listOf(milk, bread)), PlaceWithTodos(pharmacy, listOf(sunscreen)))
        todos.state.value = listOf(milk, bread, sunscreen)
        val viewModel = viewModel()

        viewModel.deleteCompleted()

        assertEquals(setOf(bread.id, sunscreen.id), todos.deleted.toSet())
        assertEquals(listOf(TodoDeletionVia.COMPLETED_BULK), todos.deletedVia)
    }

    @Test
    fun deletingOneTodoOffersUndo() = runTest(dispatcher) {
        places.state.value = listOf(PlaceWithTodos(store, listOf(milk)))
        todos.state.value = listOf(milk)
        val undo = TodoUndo()
        val offers = mutableListOf<List<Todo>>()
        backgroundScope.launch { undo.offers.collect { offers += it } }
        val viewModel = viewModel(undo)

        viewModel.delete(milk, TodoDeletionVia.MENU)

        assertEquals(listOf(listOf(milk)), offers)
        assertEquals(listOf(TodoDeletionVia.MENU), todos.deletedVia)
    }

    @Test
    fun reopeningOverTheFreeLimitAsksForThePaywall() = runTest(dispatcher) {
        places.state.value = listOf(PlaceWithTodos(store, listOf(bread)))
        val viewModel = viewModel()
        todos.limit = FreeLimit.OPEN_TODOS

        viewModel.toggle(bread)

        assertEquals(PaywallTrigger.TODO_LIMIT, paywalls.pending.value)
    }

    private fun TestScope.viewModel(undo: TodoUndo = TodoUndo()): AllTodosViewModel {
        val viewModel = AllTodosViewModel(places, categories, memberships, fakeAuthenticator(), FakeSyncEngine(), TodoActions(todos, paywalls, undo))
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun assigningWritesTheTodoBack() = runTest(dispatcher) {
        places.state.value = listOf(PlaceWithTodos(store, listOf(milk)))
        val viewModel = viewModel()
        val assignee = java.util.UUID.randomUUID()

        viewModel.assign(milk, assignee)

        assertEquals(assignee, todos.updated.single().assigneeId)
        assertEquals(milk.id, todos.updated.single().id)
    }

    @Test
    fun pullingToRefreshSyncsOnceSignedIn() = runTest(dispatcher) {
        val sync = FakeSyncEngine()
        val viewModel = AllTodosViewModel(places, categories, memberships, fakeAuthenticator(testSession), sync, TodoActions(todos, paywalls, TodoUndo()))
        backgroundScope.launch { viewModel.uiState.collect {} }
        assertTrue(viewModel.uiState.value.isSignedIn)

        viewModel.refresh()

        assertEquals(1, sync.syncs)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }
}
