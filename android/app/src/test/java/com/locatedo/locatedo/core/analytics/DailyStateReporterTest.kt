package com.locatedo.locatedo.core.analytics

import com.locatedo.locatedo.core.billing.Entitlements
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.FakeCategoryRepository
import com.locatedo.locatedo.testing.FakeEntitlementSource
import com.locatedo.locatedo.testing.FakeLocationRepository
import com.locatedo.locatedo.testing.FakeMembershipRepository
import com.locatedo.locatedo.testing.FakePermissionsRepository
import com.locatedo.locatedo.testing.FakePlaceRepository
import com.locatedo.locatedo.testing.FakeSyncStateRepository
import com.locatedo.locatedo.testing.SettableClock
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testPreferences
import com.locatedo.locatedo.testing.testSession
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DailyStateReporterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = Instant.parse("2026-10-06T03:00:00Z")
    private val clock = SettableClock(now)
    private val analytics = FakeAnalytics()
    private val places = FakePlaceRepository()
    private val permissions = FakePermissionsRepository(LocationAuth.ALWAYS, NotificationAuth.AUTHORIZED)

    @Test
    fun setsUserPropertiesEveryTimeAndTheDailyStateOncePerDay() = runTest {
        val store = Place(id = uuidV7(now), name = "Store", latitude = 35.0, longitude = 139.0, createdAt = now)
        places.state.value = listOf(PlaceWithTodos(store, listOf(Todo(id = uuidV7(now), title = "Milk", placeId = store.id, createdAt = now))))
        val reporter = reporter()

        reporter.report()

        val values = analytics.values(AnalyticsEvent.DAILY_STATE)
        assertEquals(1L, values["place_count"])
        assertEquals(1L, values["open_todo_count"])
        assertEquals(1L, values["signed_in"])
        assertEquals("always", values["location_auth"])
        assertEquals("1", analytics.userProperties[AnalyticsUserProperty.PLACE_COUNT])
        assertEquals("free", analytics.userProperties[AnalyticsUserProperty.PLAN])
        assertEquals(1, permissions.refreshCount)

        reporter.report()
        assertEquals(1, analytics.count(AnalyticsEvent.DAILY_STATE))

        clock.now = now.plus(Duration.ofDays(1))
        reporter.report()
        assertEquals(2, analytics.count(AnalyticsEvent.DAILY_STATE))
    }

    @Test
    fun aChangeOfLocationPermissionIsReportedOnce() = runTest {
        val reporter = reporter()
        reporter.report()
        assertTrue(analytics.names.none { it == AnalyticsEvent.LOCATION_AUTH_CHANGED })

        permissions.state.value = permissions.state.value.copy(location = LocationAuth.WHEN_IN_USE)
        reporter.report()

        val values = analytics.values(AnalyticsEvent.LOCATION_AUTH_CHANGED)
        assertEquals("always", values["from"])
        assertEquals("when_in_use", values["to"])
        assertEquals("when_in_use", analytics.userProperties[AnalyticsUserProperty.LOCATION_AUTH])

        reporter.report()
        assertEquals(1, analytics.count(AnalyticsEvent.LOCATION_AUTH_CHANGED))
    }

    private fun TestScope.reporter(): DailyStateReporter = DailyStateReporter(
        placeRepository = places,
        categoryRepository = FakeCategoryRepository(),
        membershipRepository = FakeMembershipRepository(),
        syncStateRepository = FakeSyncStateRepository(),
        entitlements = Entitlements(FakeEntitlementSource(), analytics, this),
        authenticator = fakeAuthenticator(testSession),
        permissions = permissions,
        locationRepository = FakeLocationRepository(),
        preferences = testPreferences(folder.root, backgroundScope),
        analytics = analytics,
        clock = clock,
    )
}
