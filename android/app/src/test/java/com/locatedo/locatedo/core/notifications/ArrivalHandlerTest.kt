package com.locatedo.locatedo.core.notifications

import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.geofence.GeofenceTransition
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceEvent
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.FakeArrivalNotifier
import com.locatedo.locatedo.testing.FakeCategoryRepository
import com.locatedo.locatedo.testing.FakePlaceRepository
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testPreferences
import com.locatedo.locatedo.testing.testSession
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArrivalHandlerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val places = FakePlaceRepository()
    private val notifier = FakeArrivalNotifier()
    private val authenticator = fakeAuthenticator()
    private val analytics = FakeAnalytics()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences by lazy { testPreferences(folder.root, scope) }
    private val handler by lazy {
        ArrivalHandler(
            places,
            FakeCategoryRepository(),
            notifier,
            authenticator,
            analytics,
            preferences,
            Clock.fixed(now, ZoneOffset.UTC),
        )
    }
    @After
    fun tearDown() {
        scope.cancel()
    }

    private val store = Place(id = uuidV7(now), name = "Store", latitude = 35.0000, longitude = 139.0, createdAt = now)
    private val office = Place(id = uuidV7(now), name = "Office", latitude = 35.0100, longitude = 139.0, createdAt = now)
    private val here = Coordinate(35.0001, 139.0)

    @Test
    fun notifiesTheNearestPlaceWithOpenTodosAndRemembersWhen() = runTest {
        places.state.value = listOf(
            PlaceWithTodos(store, listOf(todo("Milk", store), todo("Bread", store).copy(completedAt = now))),
            PlaceWithTodos(office, listOf(todo("Report", office))),
        )

        handler.handle(GeofenceTransition.DWELL, listOf(office.id, store.id), near = here)

        val (place, titles) = notifier.notified.single()
        assertEquals(store.id, place.id)
        assertEquals(listOf("Milk"), titles)
        assertEquals(now, places.state.value.first { it.place.id == store.id }.place.lastArrivalNotifiedAt)
        assertNull(places.state.value.first { it.place.id == office.id }.place.lastArrivalNotifiedAt)
        val values = analytics.values(AnalyticsEvent.REMINDER_NOTIFIED)
        assertEquals(1L, values["open_todos"])
        assertEquals("none", values["category"])
        assertEquals(100L, values["radius_m"])
        assertTrue(preferences.promotions.first().hasReceivedArrivalNotification)
    }

    @Test
    fun aPlaceWithOnlyTodosForLeavingIsNotAnnouncedOnArrival() = runTest {
        places.state.value = listOf(
            PlaceWithTodos(store, listOf(todo("Umbrella", store).copy(placeEvent = PlaceEvent.DEPARTURE))),
        )

        handler.handle(GeofenceTransition.DWELL, listOf(store.id), near = here)

        assertTrue(notifier.notified.isEmpty())
        assertEquals(0L, analytics.values(AnalyticsEvent.REMINDER_SUPPRESSED)["open_todos"])
    }

    @Test
    fun fallsBackToTheNextPlaceWhenTheNearestHasNothingToDo() = runTest {
        places.state.value = listOf(
            PlaceWithTodos(store, emptyList()),
            PlaceWithTodos(office, listOf(todo("Report", office))),
        )

        handler.handle(GeofenceTransition.DWELL, listOf(store.id, office.id), near = here)

        assertEquals(listOf(office.id), notifier.notified.map { it.first.id })
        val suppressed = analytics.values(AnalyticsEvent.REMINDER_SUPPRESSED)
        assertEquals("no_open_todos", suppressed["reason"])
        assertEquals(0L, suppressed["open_todos"])
        assertEquals(1, analytics.count(AnalyticsEvent.REMINDER_SUPPRESSED))
    }

    @Test
    fun staysQuietWithinTheCooldown() = runTest {
        places.state.value = listOf(
            PlaceWithTodos(store.copy(lastArrivalNotifiedAt = now.minus(Duration.ofMinutes(10))), listOf(todo("Milk", store))),
        )

        handler.handle(GeofenceTransition.DWELL, listOf(store.id), near = here)

        assertTrue(notifier.notified.isEmpty())
        assertFalse(preferences.promotions.first().hasReceivedArrivalNotification)
        assertEquals(
            mapOf(
                "place_event" to "arrival",
                "reason" to "recently_notified",
                "open_todos" to 1L,
                "category" to "none",
                "radius_m" to 100L,
            ),
            analytics.values(AnalyticsEvent.REMINDER_SUPPRESSED),
        )
    }

    @Test
    fun onlyTodosForSomeoneElseAreReportedAsSuch() = runTest {
        authenticator.signIn(testSession)
        places.state.value = listOf(
            PlaceWithTodos(store, listOf(todo("Milk", store).copy(assigneeId = UUID.randomUUID()))),
        )

        handler.handle(GeofenceTransition.DWELL, listOf(store.id), near = here)

        assertTrue(notifier.notified.isEmpty())
        val suppressed = analytics.values(AnalyticsEvent.REMINDER_SUPPRESSED)
        assertEquals("assigned_to_others", suppressed["reason"])
        assertEquals(1L, suppressed["open_todos"])
    }

    @Test
    fun withNotificationsOffNothingIsShownOrCountedAsNotified() = runTest {
        notifier.allowed = false
        places.state.value = listOf(PlaceWithTodos(store, listOf(todo("Milk", store))))

        handler.handle(GeofenceTransition.DWELL, listOf(store.id), near = here)

        assertTrue(notifier.notified.isEmpty())
        assertEquals(0, analytics.count(AnalyticsEvent.REMINDER_NOTIFIED))
        assertEquals("notifications_off", analytics.values(AnalyticsEvent.REMINDER_SUPPRESSED)["reason"])
        assertNull(places.state.value.single().place.lastArrivalNotifiedAt)
        assertFalse(preferences.promotions.first().hasReceivedArrivalNotification)
    }

    @Test
    fun overlappingReasonsReportOnlyTheFirst() = runTest {
        notifier.allowed = false
        places.state.value = listOf(
            PlaceWithTodos(store.copy(lastArrivalNotifiedAt = now.minus(Duration.ofMinutes(10))), listOf(todo("Milk", store))),
        )

        handler.handle(GeofenceTransition.DWELL, listOf(store.id), near = here)

        assertEquals(1, analytics.count(AnalyticsEvent.REMINDER_SUPPRESSED))
        assertEquals("recently_notified", analytics.values(AnalyticsEvent.REMINDER_SUPPRESSED)["reason"])
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

        handler.handle(GeofenceTransition.DWELL, listOf(store.id), near = here)

        assertEquals(listOf("Bread", "Eggs"), notifier.notified.single().second)
        assertEquals(0, analytics.count(AnalyticsEvent.REMINDER_SUPPRESSED))
    }

    @Test
    fun leavingAfterAStayAnnouncesOnlyTheTodosForLeaving() = runTest {
        places.state.value = listOf(
            PlaceWithTodos(
                store.copy(enteredAt = now.minus(Duration.ofMinutes(10))),
                listOf(todo("Milk", store), todo("Umbrella", store).copy(placeEvent = PlaceEvent.DEPARTURE)),
            ),
        )

        handler.handle(GeofenceTransition.EXIT, listOf(store.id), near = here)

        assertEquals(listOf("Umbrella"), notifier.notified.single().second)
        assertEquals(listOf(PlaceEvent.DEPARTURE), notifier.events)
        val stored = places.state.value.single().place
        assertEquals(now, stored.lastDepartureNotifiedAt)
        assertNull(stored.lastArrivalNotifiedAt)
        assertNull(stored.enteredAt)
        assertEquals("departure", analytics.values(AnalyticsEvent.REMINDER_NOTIFIED)["place_event"])
    }

    @Test
    fun leavingSoonAfterGoingInIsReportedAsAShortStay() = runTest {
        places.state.value = listOf(
            PlaceWithTodos(
                store.copy(enteredAt = now.minus(Duration.ofMinutes(2))),
                listOf(todo("Umbrella", store).copy(placeEvent = PlaceEvent.DEPARTURE)),
            ),
        )

        handler.handle(GeofenceTransition.EXIT, listOf(store.id), near = here)

        assertTrue(notifier.notified.isEmpty())
        assertEquals(
            mapOf(
                "place_event" to "departure",
                "reason" to "short_stay",
                "stay_min" to 2L,
                "open_todos" to 1L,
                "category" to "none",
                "radius_m" to 100L,
            ),
            analytics.values(AnalyticsEvent.REMINDER_SUPPRESSED),
        )
        assertNull(places.state.value.single().place.enteredAt)
    }

    @Test
    fun goingInRecordsTheTimeOnceAndAnnouncesNothing() = runTest {
        val earlier = now.minus(Duration.ofMinutes(3))
        places.state.value = listOf(
            PlaceWithTodos(store, listOf(todo("Milk", store))),
            PlaceWithTodos(office.copy(enteredAt = earlier), listOf(todo("Report", office))),
        )

        handler.handle(GeofenceTransition.ENTER, listOf(store.id, office.id), near = here)

        assertTrue(notifier.notified.isEmpty())
        assertEquals(now, places.state.value.first { it.place.id == store.id }.place.enteredAt)
        assertEquals(earlier, places.state.value.first { it.place.id == office.id }.place.enteredAt)
    }

    @Test
    fun ignoresPlacesThatNoLongerExist() = runTest {
        places.state.value = emptyList()

        handler.handle(GeofenceTransition.DWELL, listOf(store.id), near = null)

        assertTrue(notifier.notified.isEmpty())
    }

    private fun todo(title: String, place: Place) = Todo(id = uuidV7(now), title = title, placeId = place.id, createdAt = now)
}
