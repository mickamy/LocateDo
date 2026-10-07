package com.locatedo.locatedo.core.notifications

import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.data.TodoRepository
import com.locatedo.locatedo.core.sync.SyncEngine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// To-dos checked off from an arrival notification's button, one or all of them. The notification then shows only
// what is still open of what it listed, without sounding again, and goes away once nothing is left.
@Singleton
class ArrivalCheckOff @Inject constructor(
    private val todoRepository: TodoRepository,
    private val placeRepository: PlaceRepository,
    private val notifier: ArrivalNotifier,
    private val syncEngine: SyncEngine,
) {
    suspend fun checkOff(placeId: UUID, todoIds: List<UUID>, notifiedIds: List<UUID>) {
        for (id in todoIds) {
            todoRepository.checkOff(id)
        }
        val entry = placeRepository.observeWithTodos(placeId).first()
        val remaining = notifiedIds.mapNotNull { id -> entry?.openTodos?.firstOrNull { it.id == id } }
        if (entry == null || remaining.isEmpty()) {
            notifier.cancelArrival(placeId)
        } else {
            notifier.notifyArrival(entry.place, remaining, silent = true)
        }
        // The receiver has a few seconds; what is not sent by then stays queued for the next sync.
        withTimeoutOrNull(SEND_TIMEOUT_MILLIS) { syncEngine.drain() }
    }

    private companion object {
        const val SEND_TIMEOUT_MILLIS = 8_000L
    }
}
