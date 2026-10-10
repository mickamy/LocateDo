package com.locatedo.locatedo.screens.todos

import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.paywallTrigger
import com.locatedo.locatedo.core.data.TodoRepository
import com.locatedo.locatedo.core.data.TodoUndo
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.model.TodoDeletionVia
import java.util.UUID
import javax.inject.Inject

// What every list of to-dos does with a row: check it off (the paywall when a reopen hits the free limit), delete it
// with Undo, assign it.
class TodoActions @Inject constructor(
    private val todos: TodoRepository,
    private val paywallRequests: PaywallRequests,
    private val undo: TodoUndo,
) {
    suspend fun setCompleted(todoId: UUID, completed: Boolean) {
        val limit = todos.setCompleted(todoId, completed) ?: return
        paywallRequests.request(limit.paywallTrigger)
    }

    suspend fun delete(todoId: UUID, via: TodoDeletionVia) {
        undo.offer(todos.delete(listOf(todoId), via))
    }

    // Completed ones go without Undo; the confirmation said so.
    suspend fun deleteCompleted(todoIds: List<UUID>) {
        todos.delete(todoIds, TodoDeletionVia.COMPLETED_BULK)
    }

    suspend fun assign(todo: Todo, userId: UUID?) {
        todos.update(todo.copy(assigneeId = userId))
    }
}
