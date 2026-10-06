package com.locatedo.locatedo.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val builtin: String?,
    val name: String?,
    val icon: String,
    val color: String,
    val sortOrder: Int,
    val updatedAt: Long,
)

@Entity(
    tableName = "places",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId")],
)
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val categoryId: String?,
    val sortOrder: Int,
    val lastNotifiedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "todos",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("placeId")],
)
data class TodoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val placeId: String,
    val assigneeId: String?,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val householdId: String?,
    val cursor: Long,
    val plan: String,
)

@Entity(tableName = "memberships")
data class MembershipEntity(
    @PrimaryKey val userId: String,
    val role: String,
    val displayName: String,
    val joinedAt: Long,
    val updatedAt: Long,
)

// Not a data class: a ByteArray payload has no structural equality to offer.
@Entity(tableName = "pending_writes")
class PendingWriteEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val kind: String,
    val payload: ByteArray,
    val createdAt: Long,
    val attempts: Int = 0,
)

data class PlaceAndTodos(
    @Embedded val place: PlaceEntity,
    @Relation(parentColumn = "id", entityColumn = "placeId")
    val todos: List<TodoEntity>,
)
