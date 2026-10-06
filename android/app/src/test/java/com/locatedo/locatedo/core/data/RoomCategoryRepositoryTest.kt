package com.locatedo.locatedo.core.data

import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.sync.Write
import com.locatedo.locatedo.core.sync.WriteQueue
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomCategoryRepositoryTest {
    private lateinit var database: LocateDoDatabase
    private lateinit var repository: RoomCategoryRepository
    private val authenticator = fakeAuthenticator()

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        val queue = WriteQueue(database.pendingWriteDao(), authenticator, fixedClock)
        repository = RoomCategoryRepository(database, database.categoryDao(), queue, fixedClock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ensureBuiltinsSeedsTheFourOnce() = runTest {
        repository.ensureBuiltins()
        repository.ensureBuiltins()

        val categories = repository.observeAll().first()
        assertEquals(BuiltinCategory.entries.toList(), categories.map { it.builtin })
        assertEquals(listOf(0, 1, 2, 3), categories.map { it.sortOrder })
    }

    @Test
    fun ensureBuiltinsLeavesAnExistingTableAlone() = runTest {
        repository.add(category("Kids"))

        repository.ensureBuiltins()

        assertEquals(listOf("Kids"), repository.observeAll().first().map { it.name })
    }

    @Test
    fun addAppendsAfterTheLastSortOrder() = runTest {
        repository.ensureBuiltins()

        repository.add(category("Kids"))

        assertEquals(4, repository.observeAll().first().last().sortOrder)
    }

    @Test
    fun theLastCategoryCannotBeDeleted() = runTest {
        val kids = category("Kids")
        repository.add(kids)

        assertFalse(repository.delete(kids.id))

        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun deletingACategoryUncategorizesItsPlaces() = runTest {
        repository.ensureBuiltins()
        val kids = category("Kids")
        repository.add(kids)
        val places = RoomPlaceRepository(database, database.placeDao(), FakeProStatus(), WriteQueue(database.pendingWriteDao(), authenticator, fixedClock), fixedClock)
        places.add(place("School").copy(categoryId = kids.id))

        assertTrue(repository.delete(kids.id))

        assertNull(places.observeAll().first().single().categoryId)
    }

    @Test
    fun reorderRewritesSortOrder() = runTest {
        repository.ensureBuiltins()
        val before = repository.observeAll().first()

        repository.reorder(before.reversed())

        val after = repository.observeAll().first()
        assertEquals(before.reversed().map { it.id }, after.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), after.map { it.sortOrder })
    }

    @Test
    fun seedingTheBuiltinsIsNotQueued() = runTest {
        authenticator.signIn(testSession)

        repository.ensureBuiltins()

        assertTrue(database.queuedWrites().isEmpty())
    }

    @Test
    fun aNewCategoryGoesLastAndIsQueued() = runTest {
        repository.ensureBuiltins()
        authenticator.signIn(testSession)

        repository.add(category("Gym"))

        val put = database.queuedWrites().single() as Write.PutCategory
        assertEquals("Gym", put.input.name)
        assertEquals(BuiltinCategory.entries.size, put.input.sortOrder)
    }

    @Test
    fun deletingACategoryQueuesTheDelete() = runTest {
        repository.ensureBuiltins()
        authenticator.signIn(testSession)
        val first = repository.observeAll().first().first()

        assertTrue(repository.delete(first.id))

        val delete = database.queuedWrites().single() as Write.DeleteCategory
        assertEquals(first.id.toString(), delete.request.id)
    }

    @Test
    fun reorderingQueuesOnlyTheCategoriesThatMoved() = runTest {
        repository.ensureBuiltins()
        authenticator.signIn(testSession)
        val categories = repository.observeAll().first()

        repository.reorder(listOf(categories[1], categories[0], categories[2], categories[3]))

        val puts = database.queuedWrites().filterIsInstance<Write.PutCategory>().map { it.input.id to it.input.sortOrder }
        assertEquals(listOf(categories[1].id.toString() to 0, categories[0].id.toString() to 1), puts)
    }
}
