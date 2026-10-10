package com.locatedo.locatedo.core.notifications

import com.locatedo.locatedo.core.model.Todo
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class NotificationBody(val titles: List<String>, val more: Int)

enum class ArrivalSuppression(val key: String) {
    NO_OPEN_TODOS("no_open_todos"),
    ASSIGNED_TO_OTHERS("assigned_to_others"),
    RECENTLY_NOTIFIED("recently_notified"),
    NOTIFICATIONS_OFF("notifications_off"),

    // Left within five minutes of going in; decided by PlacePresence, not by suppression().
    SHORT_STAY("short_stay"),
}

object NotificationPolicy {
    val COOLDOWN: Duration = Duration.ofMinutes(30)
    const val MAX_TITLES = 3

    // Why a reminder goes unannounced, checked in this order; null means notify.
    fun suppression(
        openTodoCount: Int,
        notifiableTodoCount: Int,
        lastNotifiedAt: Instant?,
        notificationsAllowed: Boolean,
        now: Instant,
    ): ArrivalSuppression? {
        if (openTodoCount <= 0) {
            return ArrivalSuppression.NO_OPEN_TODOS
        }
        if (notifiableTodoCount <= 0) {
            return ArrivalSuppression.ASSIGNED_TO_OTHERS
        }
        if (lastNotifiedAt != null && Duration.between(lastNotifiedAt, now) < COOLDOWN) {
            return ArrivalSuppression.RECENTLY_NOTIFIED
        }
        if (!notificationsAllowed) {
            return ArrivalSuppression.NOTIFICATIONS_OFF
        }
        return null
    }

    fun notifiableTodos(todos: List<Todo>, userId: UUID?): List<Todo> {
        if (userId == null) {
            return todos
        }
        return todos.filter { it.assigneeId == null || it.assigneeId == userId }
    }

    fun body(todoTitles: List<String>): NotificationBody =
        NotificationBody(todoTitles.take(MAX_TITLES), (todoTitles.size - MAX_TITLES).coerceAtLeast(0))
}
