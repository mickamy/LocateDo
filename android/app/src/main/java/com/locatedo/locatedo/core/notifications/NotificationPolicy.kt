package com.locatedo.locatedo.core.notifications

import com.locatedo.locatedo.core.model.Todo
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class NotificationBody(val titles: List<String>, val more: Int)

object NotificationPolicy {
    val COOLDOWN: Duration = Duration.ofMinutes(30)
    const val MAX_TITLES = 3

    fun shouldNotify(openTodoCount: Int, lastNotifiedAt: Instant?, now: Instant): Boolean {
        if (openTodoCount <= 0) {
            return false
        }
        if (lastNotifiedAt == null) {
            return true
        }
        return Duration.between(lastNotifiedAt, now) >= COOLDOWN
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
