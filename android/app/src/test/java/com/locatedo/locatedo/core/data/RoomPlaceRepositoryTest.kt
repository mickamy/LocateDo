package com.locatedo.locatedo.core.data

import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.sync.Write
import com.locatedo.locatedo.core.sync.WriteKind
import com.locatedo.locatedo.core.sync.WriteQueue
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testSession
import java.time.Instant
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
class RoomPlaceRepositoryTest {
    private lateinit var database: LocateDoDatabase
    private lateinit var repository: RoomPlaceRepository
    private val proStatus = FakeProStatus()
    private val authenticator = fakeAuthenticator()

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        val queue = WriteQueue(database.pendingWriteDao(), authenticator, fixedClock)
        repository = RoomPlaceRepository(database, database.placeDao(), proStatus, queue, fixedClock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addsAndObserves() = runTest {
        val store = place("Store")

        assertNull(repository.add(store))

        assertEquals(listOf(store), repository.observeAll().first())
    }

    @Test
    fun theFourthPlaceHitsTheFreeLimit() = runTest {
        repeat(FreeLimit.PLACES.max) { index ->
            assertNull(repository.add(place("Place $index")))
        }

        assertEquals(FreeLimit.PLACES, repository.add(place("One too many")))
        assertEquals(FreeLimit.PLACES.max, repository.observeAll().first().size)
    }

    @Test
    fun proHasNoPlaceLimit() = runTest {
        proStatus.pro = true

        repeat(FreeLimit.PLACES.max + 1) { index ->
            assertNull(repository.add(place("Place $index")))
        }

        assertEquals(FreeLimit.PLACES.max + 1, repository.observeAll().first().size)
    }

    @Test
    fun updateStampsUpdatedAt() = runTest {
        val store = place("Store", createdAt = Instant.parse("2026-10-01T00:00:00Z"))
        repository.add(store)

        repository.update(store.copy(name = "Supermarket"))

        val updated = repository.observeAll().first().single()
        assertEquals("Supermarket", updated.name)
        assertEquals(fixedNow, updated.updatedAt)
        assertEquals(store.createdAt, updated.createdAt)
    }

    @Test
    fun deletingAPlaceDropsItsTodos() = runTest {
        val store = place("Store")
        repository.add(store)
        database.todoDao().upsert(todo("Milk", store.id).asEntity())

        repository.delete(store.id)

        assertEquals(emptyList<Any>(), repository.observeAll().first())
        assertEquals(emptyList<Any>(), database.todoDao().observeAll().first())
    }

    @Test
    fun markNotifiedRecordsTheTime() = runTest {
        val store = place("Store")
        repository.add(store)

        repository.markNotified(store.id, fixedNow)

        assertEquals(fixedNow, repository.observeAll().first().single().lastNotifiedAt)
    }

    @Test
    fun signedOutWritesAreNotQueued() = runTest {
        val store = place("Store")

        repository.add(store)
        repository.update(store.copy(name = "Supermarket"))
        repository.delete(store.id)

        assertTrue(database.queuedWrites().isEmpty())
    }

    @Test
    fun addingAndUpdatingAPlaceQueuesPuts() = runTest {
        authenticator.signIn(testSession)
        val store = place("Store")

        repository.add(store)
        repository.update(store.copy(name = "Supermarket"))

        val queue = database.queuedWrites()
        assertEquals(listOf(WriteKind.PUT_PLACE, WriteKind.PUT_PLACE), queue.map { it.kind })
        assertEquals("Supermarket", (queue[1] as Write.PutPlace).input.name)
    }

    @Test
    fun deletingAPlaceQueuesOnlyThePlaceDelete() = runTest {
        authenticator.signIn(testSession)
        val store = place("Store")
        repository.add(store)
        database.todoDao().upsert(todo("Milk", store.id).asEntity())

        repository.delete(store.id)

        val queue = database.queuedWrites()
        assertEquals(listOf(WriteKind.PUT_PLACE, WriteKind.DELETE_PLACE), queue.map { it.kind })
        assertEquals(store.id.toString(), (queue[1] as Write.DeletePlace).request.id)
    }

    @Test
    fun markNotifiedStaysOnTheDevice() = runTest {
        authenticator.signIn(testSession)
        val store = place("Store")
        repository.add(store)

        repository.markNotified(store.id, fixedNow)

        assertEquals(listOf(WriteKind.PUT_PLACE), database.queuedWrites().map { it.kind })
    }
}
