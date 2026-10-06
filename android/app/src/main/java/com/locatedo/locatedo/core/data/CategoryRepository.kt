package com.locatedo.locatedo.core.data

import androidx.room.withTransaction
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.database.CategoryDao
import com.locatedo.locatedo.core.database.CategoryEntity
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.sync.Write
import com.locatedo.locatedo.core.sync.WriteQueue
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun ensureBuiltins()
    suspend fun add(category: Category)
    suspend fun update(category: Category)

    // The last category stays, because an empty table is re-seeded with the built-ins at launch.
    suspend fun delete(id: UUID): Boolean
    suspend fun reorder(categories: List<Category>)
}

@Singleton
class RoomCategoryRepository @Inject constructor(
    private val database: LocateDoDatabase,
    private val categoryDao: CategoryDao,
    private val queue: WriteQueue,
    private val clock: Clock,
) : CategoryRepository {
    override fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { categories -> categories.map(CategoryEntity::asModel) }

    override suspend fun ensureBuiltins() = database.withTransaction {
        if (categoryDao.count() > 0) {
            return@withTransaction
        }
        val now = clock.instant()
        categoryDao.upsertAll(
            BuiltinCategory.entries.mapIndexed { index, builtin ->
                Category(
                    id = uuidV7(now),
                    builtin = builtin,
                    icon = builtin.icon,
                    color = builtin.color,
                    sortOrder = index,
                    updatedAt = now,
                ).asEntity()
            },
        )
    }

    override suspend fun add(category: Category) = database.withTransaction {
        val next = (categoryDao.maxSortOrder() ?: -1) + 1
        val added = category.copy(sortOrder = next)
        categoryDao.upsert(added.asEntity())
        queue.enqueue(Write.put(added))
    }

    override suspend fun update(category: Category) {
        database.withTransaction {
            val updated = category.copy(updatedAt = clock.instant())
            categoryDao.upsert(updated.asEntity())
            queue.enqueue(Write.put(updated))
        }
    }

    override suspend fun delete(id: UUID): Boolean = database.withTransaction {
        if (categoryDao.count() <= 1) {
            return@withTransaction false
        }
        categoryDao.delete(id.toString())
        queue.enqueue(Write.deleteCategory(id))
        true
    }

    override suspend fun reorder(categories: List<Category>) = database.withTransaction {
        val now = clock.instant()
        val moved = categories.mapIndexedNotNull { index, category ->
            if (category.sortOrder == index) null else category.copy(sortOrder = index, updatedAt = now)
        }
        for (category in moved) {
            categoryDao.upsert(category.asEntity())
        }
        queue.enqueue(moved.map { Write.put(it) })
    }
}
