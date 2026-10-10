package com.locatedo.locatedo.core.data

import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsUserProperty
import com.locatedo.locatedo.core.analytics.WriteAnalytics
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.PlaceEvent
import com.locatedo.locatedo.core.model.PlaceSource
import com.locatedo.locatedo.core.sync.Write
import com.locatedo.locatedo.core.sync.WriteKind
import com.locatedo.locatedo.core.sync.WriteQueue
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testPreferences
import com.locatedo.locatedo.testing.testSession
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomPlaceRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: LocateDoDatabase
    private lateinit var repository: RoomPlaceRepository
    private lateinit var preferences: AppPreferences
    private val proStatus = FakeProStatus()
    private val authenticator = fakeAuthenticator()
    private val analytics = FakeAnalytics()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        preferences = testPreferences(folder.root, scope)
        val queue = WriteQueue(database.pendingWriteDao(), authenticator, fixedClock)
        val writeAnalytics = WriteAnalytics(analytics, preferences, fixedClock)
        repository = RoomPlaceRepository(database, database.placeDao(), proStatus, queue, writeAnalytics, fixedClock)
    }

    @After
    fun tearDown() {
        database.close()
        scope.cancel()
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

        repository.markNotified(store.id, PlaceEvent.DEPARTURE, fixedNow)

        assertEquals(fixedNow, repository.observeAll().first().single().lastDepartureNotifiedAt)
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

        repository.markNotified(store.id, PlaceEvent.DEPARTURE, fixedNow)

        assertEquals(listOf(WriteKind.PUT_PLACE), database.queuedWrites().map { it.kind })
    }

    @Test
    fun addingAPlaceLogsItsShapeAndWhereItWasPicked() = runTest {
        preferences.recordFirstLaunch(fixedNow.minus(Duration.ofDays(4)))
        val shopping = category("Shopping").copy(builtin = BuiltinCategory.SHOPPING, name = null)
        database.categoryDao().upsert(shopping.asEntity())

        repository.add(place("Grocery").copy(radiusMeters = 150.0, categoryId = shopping.id), PlaceSource.SEARCH)

        val values = analytics.values(AnalyticsEvent.PLACE_ADDED)
        assertEquals(1L, values["place_count"])
        assertEquals("shopping", values["category"])
        assertEquals(150L, values["radius_m"])
        assertEquals(0L, values["todo_count"])
        assertEquals("none", values["suggested_category"])
        assertEquals("search", values["source"])
        assertEquals(4L, values["days_since_install"])
        assertEquals("1", analytics.userProperties[AnalyticsUserProperty.PLACE_COUNT])
    }

    @Test
    fun aPlaceWithoutACategoryOrPickSourceSaysSo() = runTest {
        repository.add(place("Somewhere"))

        val values = analytics.values(AnalyticsEvent.PLACE_ADDED)
        assertEquals("none", values["category"])
        assertNull(values["source"])
    }

    @Test
    fun aPlaceOverTheFreeLimitLogsTheLimitInsteadOfAnAdd() = runTest {
        preferences.recordFirstLaunch(fixedNow.minus(Duration.ofDays(4)))
        repeat(FreeLimit.PLACES.max) { index ->
            repository.add(place("Place $index"))
        }

        repository.add(place("One too many"))

        assertEquals(FreeLimit.PLACES.max, analytics.count(AnalyticsEvent.PLACE_ADDED))
        val values = analytics.values(AnalyticsEvent.LIMIT_REACHED)
        assertEquals("place", values["kind"])
        assertEquals(4L, values["days_since_install"])
    }

    @Test
    fun deletingAPlaceLogsHowOldItWasAndWhatWasLeftOpen() = runTest {
        val store = place("Grocery", createdAt = fixedNow.minus(Duration.ofHours(84)))
        repository.add(store)
        database.todoDao().upsert(todo("Milk", store.id).asEntity())

        repository.delete(store.id)

        val values = analytics.values(AnalyticsEvent.PLACE_DELETED)
        assertEquals(0L, values["place_count"])
        assertEquals(3L, values["age_days"])
        assertEquals(1L, values["open_todos"])
        assertEquals("0", analytics.userProperties[AnalyticsUserProperty.PLACE_COUNT])
        assertEquals("0", analytics.userProperties[AnalyticsUserProperty.OPEN_TODO_COUNT])
    }
}
