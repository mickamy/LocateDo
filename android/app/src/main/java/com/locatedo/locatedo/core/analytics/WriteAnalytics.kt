package com.locatedo.locatedo.core.analytics

import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceSource
import com.locatedo.locatedo.core.model.Todo
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

// The events behind local writes, reported by the repositories once a write has gone through.
@Singleton
class WriteAnalytics @Inject constructor(
    private val analytics: Analytics,
    private val preferences: AppPreferences,
    private val clock: Clock,
) {
    @Volatile
    private var lastArrivalOpen: ArrivalOpen? = null

    suspend fun placeAdded(place: Place, category: Category?, placeCount: Int, source: PlaceSource?) {
        val parameters = mutableMapOf<AnalyticsParameter, Any>(
            AnalyticsParameter.PLACE_COUNT to placeCount,
            AnalyticsParameter.CATEGORY to analyticsCategory(category),
            AnalyticsParameter.RADIUS_M to place.radiusMeters.toInt(),
            AnalyticsParameter.DAYS_SINCE_INSTALL to daysSinceInstall(),
        )
        if (source != null) {
            parameters[AnalyticsParameter.SOURCE] = source.key
        }
        analytics.log(AnalyticsEvent.PLACE_ADDED, parameters)
    }

    fun placeDeleted(place: Place, openTodos: Int, placeCount: Int) {
        val ageDays = Duration.between(place.createdAt, clock.instant()).toDays().coerceAtLeast(0)
        analytics.log(
            AnalyticsEvent.PLACE_DELETED,
            mapOf(
                AnalyticsParameter.PLACE_COUNT to placeCount,
                AnalyticsParameter.AGE_DAYS to ageDays,
                AnalyticsParameter.OPEN_TODOS to openTodos,
            ),
        )
    }

    suspend fun todoAdded(todo: Todo, openTodoCount: Int, placeOpenTodos: Int) {
        analytics.log(
            AnalyticsEvent.TODO_ADDED,
            mapOf(
                AnalyticsParameter.OPEN_TODO_COUNT to openTodoCount,
                AnalyticsParameter.PLACE_OPEN_TODOS to placeOpenTodos,
                AnalyticsParameter.ASSIGNED to (todo.assigneeId != null),
                AnalyticsParameter.DAYS_SINCE_INSTALL to daysSinceInstall(),
            ),
        )
    }

    fun todoCompleted(todo: Todo, openTodoCount: Int) {
        val now = clock.instant()
        val via = lastArrivalOpen?.via(todo.placeId, now) ?: CompletionVia.APP
        val ageHours = Duration.between(todo.createdAt, now).toHours().coerceAtLeast(0)
        analytics.log(
            AnalyticsEvent.TODO_COMPLETED,
            mapOf(
                AnalyticsParameter.VIA to via.key,
                AnalyticsParameter.AGE_HOURS to ageHours,
                AnalyticsParameter.OPEN_TODO_COUNT to openTodoCount,
            ),
        )
    }

    suspend fun limitReached(limit: FreeLimit) {
        analytics.log(
            AnalyticsEvent.LIMIT_REACHED,
            mapOf(
                AnalyticsParameter.KIND to limit.analyticsKind,
                AnalyticsParameter.DAYS_SINCE_INSTALL to daysSinceInstall(),
            ),
        )
    }

    fun countsChanged(placeCount: Int, openTodoCount: Int) {
        analytics.setUserProperty(AnalyticsUserProperty.PLACE_COUNT, DailyState.capped(placeCount, DailyState.PLACE_COUNT_CAP))
        analytics.setUserProperty(AnalyticsUserProperty.OPEN_TODO_COUNT, DailyState.capped(openTodoCount, DailyState.OPEN_TODO_COUNT_CAP))
    }

    // Opened from an arrival notification; notifiedAt is null when the intent came from somewhere else.
    fun arrivalOpened(placeId: UUID, notifiedAt: Instant?) {
        val now = clock.instant()
        lastArrivalOpen = ArrivalOpen(placeId, now)
        if (notifiedAt == null) {
            return
        }
        val latency = Duration.between(notifiedAt, now).seconds.coerceAtLeast(0)
        analytics.log(AnalyticsEvent.ARRIVAL_OPENED, mapOf(AnalyticsParameter.LATENCY_S to latency))
    }

    private suspend fun daysSinceInstall(): Int =
        InstallDate.daysSinceInstall(preferences.analytics.first().firstLaunchedAt, clock.instant())
}
