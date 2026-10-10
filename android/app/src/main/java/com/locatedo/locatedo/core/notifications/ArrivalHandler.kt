package com.locatedo.locatedo.core.notifications

import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.analyticsCategory
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.common.Nearby
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.PlaceEvent
import com.locatedo.locatedo.core.model.PlaceWithTodos
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class ArrivalHandler @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val categoryRepository: CategoryRepository,
    private val notifier: ArrivalNotifier,
    private val authenticator: Authenticator,
    private val analytics: Analytics,
    private val preferences: AppPreferences,
    private val clock: Clock,
) {
    // Overlapping fences (a station, a mall) fire together; only the nearest place that has something to do is announced.
    suspend fun arrived(placeIds: List<UUID>, near: Coordinate?) {
        val entries = placeIds.mapNotNull { placeRepository.observeWithTodos(it).first() }
        for (nearby in Nearby.sort(entries, near)) {
            if (notify(nearby.entry)) {
                return
            }
        }
    }

    // Every place looked at and left unannounced is reported with the reason; arrival_notified counts only
    // notifications that are actually shown.
    // Only to-dos for arriving; those for leaving belong to a departure, which Android does not announce yet.
    private suspend fun notify(entry: PlaceWithTodos): Boolean {
        val openTodos = entry.openTodos.filter { it.placeEvent == PlaceEvent.ARRIVAL }
        val todos = NotificationPolicy.notifiableTodos(openTodos, userId = authenticator.current()?.userId)
        val now = clock.instant()
        val category = categoryRepository.observeAll().first().firstOrNull { it.id == entry.place.categoryId }
        val suppression = NotificationPolicy.suppression(
            openTodoCount = openTodos.size,
            notifiableTodoCount = todos.size,
            lastNotifiedAt = entry.place.lastNotifiedAt,
            notificationsAllowed = notifier.canNotify(),
            now = now,
        )
        if (suppression != null) {
            analytics.log(
                AnalyticsEvent.ARRIVAL_SUPPRESSED,
                mapOf(
                    AnalyticsParameter.REASON to suppression.key,
                    AnalyticsParameter.OPEN_TODOS to openTodos.size,
                    AnalyticsParameter.CATEGORY to analyticsCategory(category),
                    AnalyticsParameter.RADIUS_M to entry.place.radiusMeters.toInt(),
                ),
            )
            return false
        }
        notifier.notifyArrival(entry.place, todos)
        placeRepository.markNotified(entry.place.id, now)
        preferences.setReceivedArrivalNotification()
        analytics.log(
            AnalyticsEvent.ARRIVAL_NOTIFIED,
            mapOf(
                AnalyticsParameter.OPEN_TODOS to todos.size,
                AnalyticsParameter.CATEGORY to analyticsCategory(category),
                AnalyticsParameter.RADIUS_M to entry.place.radiusMeters.toInt(),
            ),
        )
        return true
    }
}
