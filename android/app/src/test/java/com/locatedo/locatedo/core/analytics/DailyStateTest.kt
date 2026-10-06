package com.locatedo.locatedo.core.analytics

import com.locatedo.locatedo.core.billing.PlanKind
import com.locatedo.locatedo.core.billing.ProSubscription
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.MemberRole
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Plan
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyStateTest {
    private val now = Instant.parse("2026-10-06T00:00:00Z")

    @Test
    fun countsWhatIsStoredOnTheDevice() {
        val grocery = place("Grocery")
        val pharmacy = place("Pharmacy")
        val places = listOf(
            PlaceWithTodos(grocery, listOf(todo("Milk", grocery), todo("Eggs", grocery))),
            PlaceWithTodos(
                pharmacy,
                listOf(
                    todo("Bread", pharmacy).copy(completedAt = now.minus(Duration.ofDays(2))),
                    todo("Stamps", pharmacy).copy(completedAt = now.minus(Duration.ofDays(8))),
                ),
            ),
        )
        val categories = listOf(
            Category(id = uuidV7(now), builtin = BuiltinCategory.SHOPPING, icon = "cart", color = "green", sortOrder = 0, updatedAt = now),
            Category(id = uuidV7(now), name = "Kids", icon = "figure", color = "orange", sortOrder = 10, updatedAt = now),
        )
        val members = listOf(
            Membership(uuidV7(now), MemberRole.OWNER, "A", now, now),
            Membership(uuidV7(now), MemberRole.MEMBER, "B", now, now),
        )

        val counts = DailyState.counts(places, categories, members, now)

        assertEquals(
            DailyState.Counts(
                places = 2,
                openTodos = 2,
                completedTodosLast7Days = 1,
                placesWithOpenTodos = 1,
                customCategories = 1,
                householdMembers = 2,
            ),
            counts,
        )
    }

    @Test
    fun aDeviceWithoutAHouseholdCountsAsOneMember() {
        val counts = DailyState.counts(emptyList(), emptyList(), emptyList(), now)

        assertEquals(1, counts.householdMembers)
        assertEquals(0, counts.customCategories)
    }

    @Test
    fun aTrialIsToldApartFromAPaidPlan() {
        assertEquals(DailyState.PlanState.TRIAL, DailyState.plan(subscription(isTrial = true), Plan.FREE))
        assertEquals(DailyState.PlanState.PRO, DailyState.plan(subscription(isTrial = false), Plan.FREE))
        assertEquals(DailyState.PlanState.PRO, DailyState.plan(null, Plan.PRO))
        assertEquals(DailyState.PlanState.FREE, DailyState.plan(null, Plan.FREE))
        assertEquals(DailyState.PlanState.FREE, DailyState.plan(null, null))
    }

    @Test
    fun permissionsMapToTheirReportedNames() {
        assertEquals("always", LocationAuth.ALWAYS.analyticsKey)
        assertEquals("when_in_use", LocationAuth.WHEN_IN_USE.analyticsKey)
        assertEquals("denied", LocationAuth.DENIED.analyticsKey)
        assertEquals("not_determined", LocationAuth.NOT_DETERMINED.analyticsKey)
        assertEquals("authorized", NotificationAuth.AUTHORIZED.analyticsKey)
        assertEquals("not_determined", NotificationAuth.NOT_DETERMINED.analyticsKey)
    }

    @Test
    fun userPropertiesCapTheCounts() {
        val properties = state.copy(counts = state.counts.copy(places = 25, openTodos = 30)).userProperties

        assertEquals("20+", properties[AnalyticsUserProperty.PLACE_COUNT])
        assertEquals("30+", properties[AnalyticsUserProperty.OPEN_TODO_COUNT])
    }

    @Test
    fun userPropertiesKeepCountsBelowTheCap() {
        val properties = state.userProperties

        assertEquals("3", properties[AnalyticsUserProperty.PLACE_COUNT])
        assertEquals("14", properties[AnalyticsUserProperty.OPEN_TODO_COUNT])
        assertEquals("2", properties[AnalyticsUserProperty.HOUSEHOLD_MEMBERS])
        assertEquals("free", properties[AnalyticsUserProperty.PLAN])
        assertEquals("always", properties[AnalyticsUserProperty.LOCATION_AUTH])
        assertEquals("1", properties[AnalyticsUserProperty.SIGNED_IN])
    }

    @Test
    fun theEventCarriesRawCounts() {
        val values = state.parameters.wireValues()

        assertEquals(3L, values["place_count"])
        assertEquals(14L, values["open_todo_count"])
        assertEquals(5L, values["completed_todo_count_7d"])
        assertEquals(12L, values["days_since_install"])
        assertEquals(1L, values["precise_location"])
        assertEquals("authorized", values["notification_auth"])
    }

    private val state = DailyState(
        counts = DailyState.Counts(
            places = 3,
            openTodos = 14,
            completedTodosLast7Days = 5,
            placesWithOpenTodos = 2,
            customCategories = 0,
            householdMembers = 2,
        ),
        daysSinceInstall = 12,
        plan = DailyState.PlanState.FREE,
        signedIn = true,
        locationAuth = LocationAuth.ALWAYS,
        preciseLocation = true,
        notificationAuth = NotificationAuth.AUTHORIZED,
    )

    private fun subscription(isTrial: Boolean) =
        ProSubscription(term = PlanKind.ANNUAL, expiresAt = null, willRenew = true, isTrial = isTrial, hasBillingIssue = false)

    private fun place(name: String) =
        Place(id = uuidV7(now), name = name, latitude = 35.0, longitude = 139.0, createdAt = now)

    private fun todo(title: String, place: Place) =
        Todo(id = uuidV7(now), title = title, placeId = place.id, createdAt = now)
}
