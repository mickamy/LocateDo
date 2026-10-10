package com.locatedo.locatedo.core.notifications

import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.AnalyticsParameters
import com.locatedo.locatedo.core.analytics.analyticsCategory
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.common.Nearby
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.geofence.GeofenceTransition
import com.locatedo.locatedo.core.geofence.PlacePresence
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.PlaceEvent
import com.locatedo.locatedo.core.model.PlaceWithTodos
import java.time.Clock
import java.time.Duration
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
    // Records when the device went inside, then announces what the report calls for.
    suspend fun handle(transition: GeofenceTransition, placeIds: List<UUID>, near: Coordinate?) {
        val now = clock.instant()
        val arrivals = mutableListOf<PlaceWithTodos>()
        val departures = mutableListOf<PlaceWithTodos>()
        for (entry in entries(placeIds)) {
            val outcome = PlacePresence.next(transition, entry.place.enteredAt, now)
            if (outcome.enteredAt != entry.place.enteredAt) {
                placeRepository.setEnteredAt(entry.place.id, outcome.enteredAt)
            }
            when (val event = outcome.event) {
                PlacePresence.Event.Arrival -> arrivals += entry
                is PlacePresence.Event.Departure -> departures += entry
                is PlacePresence.Event.ShortStay -> shortStay(entry, event.stay)
                null -> Unit
            }
        }
        announce(arrivals, PlaceEvent.ARRIVAL, near)
        announce(departures, PlaceEvent.DEPARTURE, near)
    }

    // As if the fence had reported it, through the same rules; for the debug menu.
    suspend fun remind(placeIds: List<UUID>, event: PlaceEvent, near: Coordinate?) {
        announce(entries(placeIds), event, near)
    }

    private suspend fun entries(placeIds: List<UUID>): List<PlaceWithTodos> =
        placeIds.mapNotNull { placeRepository.observeWithTodos(it).first() }

    // Overlapping fences (a station, a mall) fire together; only the nearest place that has something to do is announced.
    private suspend fun announce(entries: List<PlaceWithTodos>, event: PlaceEvent, near: Coordinate?) {
        for (nearby in Nearby.sort(entries, near)) {
            if (notify(nearby.entry, event)) {
                return
            }
        }
    }

    // Every place looked at and left unannounced is reported with the reason; reminder_notified counts only
    // notifications that are actually shown. Each kind lists only its own to-dos.
    private suspend fun notify(entry: PlaceWithTodos, event: PlaceEvent): Boolean {
        val openTodos = entry.openTodos.filter { it.placeEvent == event }
        val todos = NotificationPolicy.notifiableTodos(openTodos, userId = authenticator.current()?.userId)
        val now = clock.instant()
        val suppression = NotificationPolicy.suppression(
            openTodoCount = openTodos.size,
            notifiableTodoCount = todos.size,
            lastNotifiedAt = entry.place.lastNotifiedAt(event),
            notificationsAllowed = notifier.canNotify(),
            now = now,
        )
        if (suppression != null) {
            analytics.log(
                AnalyticsEvent.REMINDER_SUPPRESSED,
                parameters(entry, event) + mapOf(
                    AnalyticsParameter.REASON to suppression.key,
                    AnalyticsParameter.OPEN_TODOS to openTodos.size,
                ),
            )
            return false
        }
        // A refused notification is not delivered, so it neither starts the cooldown nor counts as a reminder.
        if (!notifier.notify(event, entry.place, todos)) {
            analytics.log(
                AnalyticsEvent.REMINDER_SUPPRESSED,
                parameters(entry, event) + mapOf(
                    AnalyticsParameter.REASON to ArrivalSuppression.SCHEDULE_FAILED.key,
                    AnalyticsParameter.OPEN_TODOS to openTodos.size,
                ),
            )
            return false
        }
        placeRepository.markNotified(entry.place.id, event, now)
        preferences.setReceivedArrivalNotification()
        analytics.log(
            AnalyticsEvent.REMINDER_NOTIFIED,
            parameters(entry, event) + mapOf(AnalyticsParameter.OPEN_TODOS to todos.size),
        )
        return true
    }

    private suspend fun shortStay(entry: PlaceWithTodos, stay: Duration) {
        analytics.log(
            AnalyticsEvent.REMINDER_SUPPRESSED,
            parameters(entry, PlaceEvent.DEPARTURE) + mapOf(
                AnalyticsParameter.REASON to ArrivalSuppression.SHORT_STAY.key,
                AnalyticsParameter.STAY_MIN to stay.toMinutes(),
                AnalyticsParameter.OPEN_TODOS to entry.openTodos.count { it.placeEvent == PlaceEvent.DEPARTURE },
            ),
        )
    }

    private suspend fun parameters(entry: PlaceWithTodos, event: PlaceEvent): AnalyticsParameters {
        val category = categoryRepository.observeAll().first().firstOrNull { it.id == entry.place.categoryId }
        return mapOf(
            AnalyticsParameter.PLACE_EVENT to event.key,
            AnalyticsParameter.CATEGORY to analyticsCategory(category),
            AnalyticsParameter.RADIUS_M to entry.place.radiusMeters.toInt(),
        )
    }
}
