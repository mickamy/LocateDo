package com.locatedo.locatedo.core.sync

import com.locatedo.locatedo.core.database.PlaceDao
import com.locatedo.locatedo.core.database.TodoDao
import com.locatedo.locatedo.core.model.FreeLimit
import java.time.Clock
import javax.inject.Inject

// Undoes on the device the row a rejected insert created; the server never had it.
class LimitRejection @Inject constructor(
    private val placeDao: PlaceDao,
    private val todoDao: TodoDao,
    private val clock: Clock,
) {
    suspend fun revert(write: Write): FreeLimit? = when (write) {
        is Write.PutPlace -> {
            placeDao.delete(write.input.id)
            FreeLimit.PLACES
        }
        is Write.PutTodo -> {
            todoDao.delete(listOf(write.input.id))
            FreeLimit.OPEN_TODOS
        }
        is Write.SetTodoCompletion -> if (write.request.hasCompletedAt()) null else completeAgain(write.request.id)
        else -> null
    }

    private suspend fun completeAgain(id: String): FreeLimit {
        val todo = todoDao.get(id)
        if (todo != null) {
            val now = clock.instant().toEpochMilli()
            todoDao.upsert(todo.copy(completedAt = now, updatedAt = now))
        }
        return FreeLimit.OPEN_TODOS
    }
}
