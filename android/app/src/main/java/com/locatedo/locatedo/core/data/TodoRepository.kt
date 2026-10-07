package com.locatedo.locatedo.core.data

import androidx.room.withTransaction
import com.locatedo.locatedo.core.analytics.WriteAnalytics
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.database.TodoDao
import com.locatedo.locatedo.core.database.TodoEntity
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.sync.Write
import com.locatedo.locatedo.core.sync.WriteQueue
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TodoRepository {
    fun observeAll(): Flow<List<Todo>>

    // Returns the limit that stopped the write on a free plan, or null when it went through.
    suspend fun add(todo: Todo): FreeLimit?
    suspend fun update(todo: Todo)
    suspend fun setCompleted(id: UUID, completed: Boolean): FreeLimit?
    suspend fun delete(ids: List<UUID>)
}

@Singleton
class RoomTodoRepository @Inject constructor(
    private val database: LocateDoDatabase,
    private val todoDao: TodoDao,
    private val proStatus: ProStatus,
    private val queue: WriteQueue,
    private val authenticator: Authenticator,
    private val analytics: WriteAnalytics,
    private val clock: Clock,
) : TodoRepository {
    // What a completion write found and did, so the analytics can be sent once the transaction is over.
    private class Completion(val before: TodoEntity, val limit: FreeLimit?)

    override fun observeAll(): Flow<List<Todo>> =
        todoDao.observeAll().map { todos -> todos.map(TodoEntity::asModel) }

    override suspend fun add(todo: Todo): FreeLimit? {
        val limit = database.withTransaction {
            if (openLimitReached()) {
                return@withTransaction FreeLimit.OPEN_TODOS
            }
            todoDao.upsert(todo.asEntity())
            queue.enqueue(Write.put(todo))
            null
        }
        if (limit != null) {
            analytics.limitReached(limit)
            return limit
        }
        analytics.todoAdded(todo, todoDao.countOpen(), todoDao.countOpen(todo.placeId.toString()))
        reportCounts()
        return null
    }

    override suspend fun update(todo: Todo) {
        database.withTransaction {
            val updated = todo.copy(updatedAt = clock.instant())
            todoDao.upsert(updated.asEntity())
            queue.enqueue(Write.put(updated))
        }
    }

    // Reopening counts against the free limit just like adding, so the server and the device agree. The server
    // records who checked it off; the device notes itself too, so the row is right before the next pull.
    override suspend fun setCompleted(id: UUID, completed: Boolean): FreeLimit? {
        val userId = authenticator.current()?.userId
        val completion = database.withTransaction {
            val todo = todoDao.get(id.toString()) ?: return@withTransaction null
            val reopening = !completed && todo.completedAt != null
            if (reopening && openLimitReached()) {
                return@withTransaction Completion(todo, FreeLimit.OPEN_TODOS)
            }
            val now = clock.instant()
            val completedAt = if (completed) now else null
            val completerId = if (completed) userId?.toString() else null
            todoDao.upsert(
                todo.copy(
                    completedAt = completedAt?.toEpochMilli(),
                    completerId = completerId,
                    updatedAt = now.toEpochMilli(),
                ),
            )
            queue.enqueue(Write.completion(id, completedAt))
            Completion(todo, null)
        } ?: return null
        if (completion.limit != null) {
            analytics.limitReached(completion.limit)
            return completion.limit
        }
        if (completed && completion.before.completedAt == null) {
            analytics.todoCompleted(completion.before.asModel(), todoDao.countOpen())
        }
        reportCounts()
        return null
    }

    override suspend fun delete(ids: List<UUID>) {
        database.withTransaction {
            todoDao.delete(ids.map(UUID::toString))
            queue.enqueue(ids.map { Write.deleteTodo(it) })
        }
        reportCounts()
    }

    private suspend fun openLimitReached(): Boolean =
        !proStatus.isPro() && todoDao.countOpen() >= FreeLimit.OPEN_TODOS.max

    private suspend fun reportCounts() {
        analytics.countsChanged(database.placeDao().count(), todoDao.countOpen())
    }
}
