package com.locatedo.locatedo.core.analytics

import com.locatedo.locatedo.core.billing.Entitlements
import com.locatedo.locatedo.core.billing.ProSubscription
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.PlaceEvent
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Plan
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

val LocationAuth.analyticsKey: String
    get() = when (this) {
        LocationAuth.ALWAYS -> "always"
        LocationAuth.WHEN_IN_USE -> "when_in_use"
        LocationAuth.DENIED -> "denied"
        LocationAuth.NOT_DETERMINED -> "not_determined"
    }

val NotificationAuth.analyticsKey: String
    get() = when (this) {
        NotificationAuth.AUTHORIZED -> "authorized"
        NotificationAuth.DENIED -> "denied"
        NotificationAuth.NOT_DETERMINED -> "not_determined"
    }

data class DailyState(
    val counts: Counts,
    val daysSinceInstall: Int,
    val plan: PlanState,
    val signedIn: Boolean,
    val locationAuth: LocationAuth,
    val preciseLocation: Boolean,
    val notificationAuth: NotificationAuth,
    val promotionsConsent: Boolean,
    val batteryOptimizationExempt: Boolean,
) {
    enum class PlanState(val key: String) {
        FREE("free"),
        PRO("pro"),
        TRIAL("trial"),
    }

    data class Counts(
        val places: Int = 0,
        val openTodos: Int = 0,
        val openDepartureTodos: Int = 0,
        val completedTodosLast7Days: Int = 0,
        val placesWithOpenTodos: Int = 0,
        val customCategories: Int = 0,
        val householdMembers: Int = 1,
    )

    val parameters: AnalyticsParameters
        get() = mapOf(
            AnalyticsParameter.PLACE_COUNT to counts.places,
            AnalyticsParameter.OPEN_TODO_COUNT to counts.openTodos,
            AnalyticsParameter.OPEN_DEPARTURE_TODOS to counts.openDepartureTodos,
            AnalyticsParameter.COMPLETED_TODO_COUNT_7D to counts.completedTodosLast7Days,
            AnalyticsParameter.PLACES_WITH_OPEN_TODOS to counts.placesWithOpenTodos,
            AnalyticsParameter.CUSTOM_CATEGORY_COUNT to counts.customCategories,
            AnalyticsParameter.HOUSEHOLD_MEMBERS to counts.householdMembers,
            AnalyticsParameter.DAYS_SINCE_INSTALL to daysSinceInstall,
            AnalyticsParameter.PLAN to plan.key,
            AnalyticsParameter.SIGNED_IN to signedIn,
            AnalyticsParameter.LOCATION_AUTH to locationAuth.analyticsKey,
            AnalyticsParameter.PRECISE_LOCATION to preciseLocation,
            AnalyticsParameter.NOTIFICATION_AUTH to notificationAuth.analyticsKey,
            AnalyticsParameter.PROMOTIONS_CONSENT to promotionsConsent,
            AnalyticsParameter.BATTERY_OPTIMIZATION_EXEMPT to batteryOptimizationExempt,
        )

    val userProperties: Map<AnalyticsUserProperty, String>
        get() = mapOf(
            AnalyticsUserProperty.PLAN to plan.key,
            AnalyticsUserProperty.LOCATION_AUTH to locationAuth.analyticsKey,
            AnalyticsUserProperty.HOUSEHOLD_MEMBERS to counts.householdMembers.toString(),
            AnalyticsUserProperty.PLACE_COUNT to capped(counts.places, PLACE_COUNT_CAP),
            AnalyticsUserProperty.OPEN_TODO_COUNT to capped(counts.openTodos, OPEN_TODO_COUNT_CAP),
            AnalyticsUserProperty.SIGNED_IN to flag(signedIn),
            AnalyticsUserProperty.PROMOTIONS_CONSENT to flag(promotionsConsent),
        )

    companion object {
        const val PLACE_COUNT_CAP = 20
        const val OPEN_TODO_COUNT_CAP = 30

        fun flag(value: Boolean): String {
            if (value) {
                return "1"
            }
            return "0"
        }

        fun capped(count: Int, cap: Int): String {
            if (count >= cap) {
                return "$cap+"
            }
            return count.toString()
        }

        fun plan(subscription: ProSubscription?, householdPlan: Plan?): PlanState {
            if (subscription?.isTrial == true) {
                return PlanState.TRIAL
            }
            if (Entitlements.isPro(subscription != null, householdPlan)) {
                return PlanState.PRO
            }
            return PlanState.FREE
        }

        fun counts(
            places: List<PlaceWithTodos>,
            categories: List<Category>,
            members: List<Membership>,
            now: Instant,
        ): Counts {
            val weekAgo = now.minus(Duration.ofDays(7))
            val todos = places.flatMap { it.todos }
            val openTodos = todos.filter { !it.isCompleted }
            return Counts(
                places = places.size,
                openTodos = openTodos.size,
                openDepartureTodos = openTodos.count { it.placeEvent == PlaceEvent.DEPARTURE },
                completedTodosLast7Days = todos.count { todo -> todo.completedAt?.isAfter(weekAgo) == true },
                placesWithOpenTodos = places.count { it.openTodos.isNotEmpty() },
                customCategories = categories.count { it.builtin == null },
                householdMembers = members.size.coerceAtLeast(1),
            )
        }
    }
}

// Sent on the first foreground of each local calendar day.
object DailyStateSchedule {
    fun isDue(reportedOn: String?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        reportedOn != day(now, zone)

    fun day(now: Instant, zone: ZoneId = ZoneId.systemDefault()): String = now.atZone(zone).toLocalDate().toString()
}

object NotificationAuthHistory {
    fun change(lastReported: String?, current: NotificationAuth): Pair<NotificationAuth, NotificationAuth>? {
        val previous = NotificationAuth.entries.firstOrNull { it.analyticsKey == lastReported } ?: return null
        if (previous == current) {
            return null
        }
        return previous to current
    }
}

object LocationAuthHistory {
    fun change(lastReported: String?, current: LocationAuth): Pair<LocationAuth, LocationAuth>? {
        val previous = LocationAuth.entries.firstOrNull { it.analyticsKey == lastReported } ?: return null
        if (previous == current) {
            return null
        }
        return previous to current
    }
}
