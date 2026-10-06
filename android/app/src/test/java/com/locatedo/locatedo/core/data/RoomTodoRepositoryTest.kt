package com.locatedo.locatedo.core.data

import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.sync.Write
import com.locatedo.locatedo.core.sync.WriteQueue
import com.locatedo.locatedo.core.sync.toInstant
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testSession
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomTodoRepositoryTest {
    private lateinit var database: LocateDoDatabase
    private lateinit var places: RoomPlaceRepository
    private lateinit var repository: RoomTodoRepository
    private val proStatus = FakeProStatus()
    private val authenticator = fakeAuthenticator()
    private val store: Place = place("Store")

    @Before
    fun setUp() = runTest {
        database = inMemoryDatabase()
        val queue = WriteQueue(database.pendingWriteDao(), authenticator, fixedClock)
        places = RoomPlaceRepository(database, database.placeDao(), proStatus, queue, fixedClock)
        repository = RoomTodoRepository(database, database.todoDao(), proStatus, queue, fixedClock)
        places.add(store)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addingATodoAttachesItToThePlace() = runTest {
        val milk = todo("Milk", store.id)

        assertNull(repository.add(milk))

        val withTodos = places.observeWithTodos(store.id).first()
        assertEquals(listOf(milk), withTodos?.openTodos)
    }

    @Test
    fun theSixteenthOpenTodoHitsTheFreeLimit() = runTest {
        repeat(FreeLimit.OPEN_TODOS.max) { index ->
            assertNull(repository.add(todo("Todo $index", store.id)))
        }

        assertEquals(FreeLimit.OPEN_TODOS, repository.add(todo("One too many", store.id)))
    }

    @Test
    fun completingFreesASlotAndReopeningTakesItBack() = runTest {
        val first = todo("First", store.id)
        repository.add(first)
        repeat(FreeLimit.OPEN_TODOS.max - 1) { index ->
            repository.add(todo("Todo $index", store.id))
        }

        assertNull(repository.setCompleted(first.id, completed = true))
        assertNull(repository.add(todo("Fits now", store.id)))
        assertEquals(FreeLimit.OPEN_TODOS, repository.setCompleted(first.id, completed = false))

        val stored = repository.observeAll().first().single { it.id == first.id }
        assertTrue(stored.isCompleted)
        assertEquals(fixedNow, stored.completedAt)
    }

    @Test
    fun completingAnUnknownTodoDoesNothing() = runTest {
        assertNull(repository.setCompleted(todo("Ghost", store.id).id, completed = true))

        assertEquals(emptyList<Any>(), repository.observeAll().first())
    }

    @Test
    fun deleteRemovesTheGivenTodos() = runTest {
        val milk = todo("Milk", store.id)
        val eggs = todo("Eggs", store.id)
        repository.add(milk)
        repository.add(eggs)

        repository.delete(listOf(milk.id))

        assertEquals(listOf(eggs), repository.observeAll().first())
    }

    @Test
    fun togglingCompletionQueuesTheCompletionEachTime() = runTest {
        authenticator.signIn(testSession)
        val milk = todo("Milk", store.id)
        repository.add(milk)

        repository.setCompleted(milk.id, true)
        repository.setCompleted(milk.id, false)

        val completions = database.queuedWrites().filterIsInstance<Write.SetTodoCompletion>().map { it.request }
        assertEquals(listOf(milk.id.toString(), milk.id.toString()), completions.map { it.id })
        assertEquals(listOf(true, false), completions.map { it.hasCompletedAt() })
        assertEquals(fixedNow, completions[0].completedAt.toInstant())
    }

    @Test
    fun updatingQueuesAPutWithTheAssignee() = runTest {
        authenticator.signIn(testSession)
        val milk = todo("Milk", store.id)
        repository.add(milk)
        val assignee = UUID.randomUUID()

        repository.update(milk.copy(assigneeId = assignee))

        val put = database.queuedWrites().last() as Write.PutTodo
        assertEquals(assignee.toString(), put.input.assigneeId)
    }

    @Test
    fun deletingSeveralTodosQueuesADeleteForEach() = runTest {
        authenticator.signIn(testSession)
        val milk = todo("Milk", store.id)
        val bread = todo("Bread", store.id)
        repository.add(milk)
        repository.add(bread)

        repository.delete(listOf(milk.id, bread.id))

        val deletes = database.queuedWrites().filterIsInstance<Write.DeleteTodo>().map { it.request.id }
        assertEquals(listOf(milk.id.toString(), bread.id.toString()), deletes)
        assertEquals(listOf(1L, 2L, 3L, 4L), database.pendingWriteDao().all().map { it.sequence })
    }
}
