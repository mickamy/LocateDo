package com.locatedo.locatedo.core.database

import androidx.room.Dao
import androidx.room.Insert
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

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun get(id: String): PlaceEntity?

    @Query("SELECT id FROM places")
    suspend fun ids(): List<String>

    @Query("SELECT COUNT(*) FROM places")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(place: PlaceEntity)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE places SET lastNotifiedAt = :at WHERE id = :id")
    suspend fun setLastNotifiedAt(id: String, at: Long?)

    @Query("DELETE FROM places")
    suspend fun deleteAll()
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY createdAt")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun get(id: String): TodoEntity?

    @Query("SELECT COUNT(*) FROM todos WHERE completedAt IS NULL")
    suspend fun countOpen(): Int

    @Query("SELECT COUNT(*) FROM todos WHERE placeId = :placeId AND completedAt IS NULL")
    suspend fun countOpen(placeId: String): Int

    @Upsert
    suspend fun upsert(todo: TodoEntity)

    @Query("DELETE FROM todos WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("DELETE FROM todos")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Query("SELECT id FROM categories")
    suspend fun ids(): List<String>

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

    @Query("SELECT COUNT(*) FROM categories WHERE builtin IS NULL")
    suspend fun countCustom(): Int

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface MembershipDao {
    @Query("SELECT * FROM memberships ORDER BY joinedAt")
    fun observeAll(): Flow<List<MembershipEntity>>

    @Query("SELECT * FROM memberships ORDER BY joinedAt")
    suspend fun all(): List<MembershipEntity>

    @Upsert
    suspend fun upsert(membership: MembershipEntity)

    @Query("DELETE FROM memberships WHERE userId = :userId")
    suspend fun delete(userId: String)

    @Query("DELETE FROM memberships")
    suspend fun deleteAll()
}

@Dao
interface PendingWriteDao {
    @Insert
    suspend fun insert(write: PendingWriteEntity): Long

    @Query("SELECT * FROM pending_writes ORDER BY sequence LIMIT 1")
    suspend fun head(): PendingWriteEntity?

    @Query("SELECT * FROM pending_writes ORDER BY sequence")
    suspend fun all(): List<PendingWriteEntity>

    @Query("SELECT COUNT(*) FROM pending_writes")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pending_writes")
    fun observeCount(): Flow<Int>

    @Query("UPDATE pending_writes SET attempts = attempts + 1 WHERE sequence = :sequence")
    suspend fun recordAttempt(sequence: Long)

    @Query("DELETE FROM pending_writes WHERE sequence = :sequence")
    suspend fun delete(sequence: Long)

    @Query("DELETE FROM pending_writes")
    suspend fun deleteAll()
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE id = 1")
    fun observe(): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun get(): SyncStateEntity?

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Query("DELETE FROM sync_state")
    suspend fun clear()
}
