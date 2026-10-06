package com.locatedo.locatedo.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Transaction
    @Query("SELECT * FROM places ORDER BY sortOrder, createdAt")
    fun observeAllWithTodos(): Flow<List<PlaceAndTodos>>

    @Transaction
    @Query("SELECT * FROM places WHERE id = :id")
    fun observeWithTodos(id: String): Flow<PlaceAndTodos?>

    @Query("SELECT COUNT(*) FROM places")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(place: PlaceEntity)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE places SET lastNotifiedAt = :at WHERE id = :id")
    suspend fun setLastNotifiedAt(id: String, at: Long?)
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY createdAt")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun get(id: String): TodoEntity?

    @Query("SELECT COUNT(*) FROM todos WHERE completedAt IS NULL")
    suspend fun countOpen(): Int

    @Upsert
    suspend fun upsert(todo: TodoEntity)

    @Query("DELETE FROM todos WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT MAX(sortOrder) FROM categories")
    suspend fun maxSortOrder(): Int?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: String)
}
