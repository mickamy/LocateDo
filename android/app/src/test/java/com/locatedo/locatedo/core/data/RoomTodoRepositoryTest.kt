package com.locatedo.locatedo.core.data

import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
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
    private val store: Place = place("Store")

    @Before
    fun setUp() = runTest {
        database = inMemoryDatabase()
        places = RoomPlaceRepository(database, database.placeDao(), proStatus, fixedClock)
        repository = RoomTodoRepository(database, database.todoDao(), proStatus, fixedClock)
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
}
