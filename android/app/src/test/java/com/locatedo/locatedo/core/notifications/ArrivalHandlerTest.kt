package com.locatedo.locatedo.core.notifications

import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.FakeArrivalNotifier
import com.locatedo.locatedo.testing.FakeCategoryRepository
import com.locatedo.locatedo.testing.FakePlaceRepository
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testSession
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalHandlerTest {
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val places = FakePlaceRepository()
    private val notifier = FakeArrivalNotifier()
    private val authenticator = fakeAuthenticator()
    private val analytics = FakeAnalytics()
    private val handler = ArrivalHandler(
        places,
        FakeCategoryRepository(),
        notifier,
        authenticator,
        analytics,
        Clock.fixed(now, ZoneOffset.UTC),
    )
    private val store = Place(id = uuidV7(now), name = "Store", latitude = 35.0000, longitude = 139.0, createdAt = now)
    private val office = Place(id = uuidV7(now), name = "Office", latitude = 35.0100, longitude = 139.0, createdAt = now)
    private val here = Coordinate(35.0001, 139.0)

    @Test
    fun notifiesTheNearestPlaceWithOpenTodosAndRemembersWhen() = runTest {
        places.state.value = listOf(
            PlaceWithTodos(store, listOf(todo("Milk", store), todo("Bread", store).copy(completedAt = now))),
            PlaceWithTodos(office, listOf(todo("Report", office))),
        )

        handler.arrived(listOf(office.id, store.id), near = here)

        val (place, titles) = notifier.notified.single()
        assertEquals(store.id, place.id)
        assertEquals(listOf("Milk"), titles)
        assertEquals(now, places.state.value.first { it.place.id == store.id }.place.lastNotifiedAt)
        assertNull(places.state.value.first { it.place.id == office.id }.place.lastNotifiedAt)
        val values = analytics.values(AnalyticsEvent.ARRIVAL_NOTIFIED)
        assertEquals(1L, values["open_todos"])
        assertEquals("none", values["category"])
        assertEquals(100L, values["radius_m"])
    }

    @Test
    fun fallsBackToTheNextPlaceWhenTheNearestHasNothingToDo() = runTest {
        places.state.value = listOf(
            PlaceWithTodos(store, emptyList()),
            PlaceWithTodos(office, listOf(todo("Report", office))),
        )

        handler.arrived(listOf(store.id, office.id), near = here)

        assertEquals(listOf(office.id), notifier.notified.map { it.first.id })
    }

    @Test
    fun staysQuietWithinTheCooldown() = runTest {
        places.state.value = listOf(
            PlaceWithTodos(store.copy(lastNotifiedAt = now.minus(Duration.ofMinutes(10))), listOf(todo("Milk", store))),
        )

        handler.arrived(listOf(store.id), near = here)

        assertTrue(notifier.notified.isEmpty())
    }

    @Test
    fun todosAssignedToSomeoneElseAreLeftOutOnceSignedIn() = runTest {
        authenticator.signIn(testSession)
        val other = UUID.randomUUID()
        places.state.value = listOf(
            PlaceWithTodos(
                store,
                listOf(
                    todo("Milk", store).copy(assigneeId = other),
                    todo("Bread", store).copy(assigneeId = testSession.userId),
                    todo("Eggs", store),
                ),
            ),
        )

        handler.arrived(listOf(store.id), near = here)

        assertEquals(listOf("Bread", "Eggs"), notifier.notified.single().second)
    }

    @Test
    fun ignoresPlacesThatNoLongerExist() = runTest {
        places.state.value = emptyList()

        handler.arrived(listOf(store.id), near = null)

        assertTrue(notifier.notified.isEmpty())
    }

    private fun todo(title: String, place: Place) = Todo(id = uuidV7(now), title = title, placeId = place.id, createdAt = now)
}
