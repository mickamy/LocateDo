package com.locatedo.locatedo.core.notifications

import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.common.Nearby
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.PlaceWithTodos
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class ArrivalHandler @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val notifier: ArrivalNotifier,
    private val authenticator: Authenticator,
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

    private suspend fun notify(entry: PlaceWithTodos): Boolean {
        val todos = NotificationPolicy.notifiableTodos(entry.openTodos, userId = authenticator.current()?.userId)
        val now = clock.instant()
        if (!NotificationPolicy.shouldNotify(todos.size, entry.place.lastNotifiedAt, now)) {
            return false
        }
        notifier.notifyArrival(entry.place, todos.map { it.title })
        placeRepository.markNotified(entry.place.id, now)
        return true
    }
}
