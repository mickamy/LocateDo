package com.locatedo.locatedo.core.notifications

import com.locatedo.locatedo.core.data.fixedNow
import com.locatedo.locatedo.core.data.place
import com.locatedo.locatedo.core.data.todo
import com.locatedo.locatedo.core.model.PlaceEvent
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.testing.FakeArrivalNotifier
import com.locatedo.locatedo.testing.FakePlaceRepository
import com.locatedo.locatedo.testing.FakeSyncEngine
import com.locatedo.locatedo.testing.FakeTodoRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalCheckOffTest {
    private val store = place("Store")
    private val milk = todo("Milk", store.id)
    private val bread = todo("Bread", store.id)
    private val eggs = todo("Eggs", store.id)
    private val todos = FakeTodoRepository()
    private val places = FakePlaceRepository()
    private val notifier = FakeArrivalNotifier()
    private val sync = FakeSyncEngine()
    private val checkOff = ArrivalCheckOff(todos, places, notifier, sync)

    @Test
    fun checksOffAndQuietlyShowsWhatIsLeftThenSends() = runTest {
        linkPlacesToTodos(milk, bread, eggs)

        checkOff.checkOff(store.id, PlaceEvent.ARRIVAL, listOf(bread.id), listOf(milk.id, bread.id, eggs.id))

        assertTrue(todos.state.value.first { it.id == bread.id }.isCompleted)
        assertEquals(listOf(store to listOf("Milk", "Eggs")), notifier.silent)
        assertTrue(notifier.notified.isEmpty())
        assertEquals(1, sync.drains)
    }

    @Test
    fun whatFamilyCheckedOffMeanwhileIsLeftOut() = runTest {
        linkPlacesToTodos(milk, bread.copy(completedAt = fixedNow), eggs)

        checkOff.checkOff(store.id, PlaceEvent.ARRIVAL, listOf(milk.id), listOf(milk.id, bread.id, eggs.id))

        assertEquals(listOf(store to listOf("Eggs")), notifier.silent)
    }

    @Test
    fun theNotificationGoesAwayWhenNothingIsLeft() = runTest {
        linkPlacesToTodos(milk)

        checkOff.checkOff(store.id, PlaceEvent.ARRIVAL, listOf(milk.id), listOf(milk.id))

        assertEquals(listOf(store.id), notifier.cancelled)
        assertTrue(notifier.silent.isEmpty())
    }

    @Test
    fun checkingOffAllLeavesNothingToShow() = runTest {
        linkPlacesToTodos(milk, bread, eggs)

        checkOff.checkOff(store.id, PlaceEvent.ARRIVAL, listOf(milk.id, bread.id, eggs.id), listOf(milk.id, bread.id, eggs.id))

        assertTrue(todos.state.value.all { it.isCompleted })
        assertEquals(listOf(store.id), notifier.cancelled)
        assertEquals(1, sync.drains)
    }

    // The place's to-dos follow the to-do repository right away, as the database's do.
    private fun TestScope.linkPlacesToTodos(vararg initial: Todo) {
        todos.state.value = initial.toList()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            todos.state.collect { current -> places.state.value = listOf(PlaceWithTodos(store, current)) }
        }
    }
}
