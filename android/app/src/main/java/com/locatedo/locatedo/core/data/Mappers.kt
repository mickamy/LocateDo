package com.locatedo.locatedo.core.data

import com.locatedo.locatedo.core.database.CategoryEntity
import com.locatedo.locatedo.core.database.MembershipEntity
import com.locatedo.locatedo.core.database.PlaceAndTodos
import com.locatedo.locatedo.core.database.PlaceEntity
import com.locatedo.locatedo.core.database.SyncStateEntity
import com.locatedo.locatedo.core.database.TodoEntity
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.MemberRole
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceEvent
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.model.Plan
import com.locatedo.locatedo.core.model.SyncState
import com.locatedo.locatedo.core.model.Todo
import java.time.Instant
import java.util.UUID

internal fun PlaceEntity.asModel() = Place(
    id = UUID.fromString(id),
    name = name,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    categoryId = categoryId?.let(UUID::fromString),
    sortOrder = sortOrder,
    lastNotifiedAt = lastNotifiedAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

internal fun Place.asEntity() = PlaceEntity(
    id = id.toString(),
    name = name,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    categoryId = categoryId?.toString(),
    sortOrder = sortOrder,
    lastNotifiedAt = lastNotifiedAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

internal fun TodoEntity.asModel() = Todo(
    id = UUID.fromString(id),
    title = title,
    placeId = UUID.fromString(placeId),
    assigneeId = assigneeId?.let(UUID::fromString),
    creatorId = creatorId?.let(UUID::fromString),
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    completerId = completerId?.let(UUID::fromString),
    placeEvent = PlaceEvent.fromKey(placeEvent),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

internal fun Todo.asEntity() = TodoEntity(
    id = id.toString(),
    title = title,
    placeId = placeId.toString(),
    assigneeId = assigneeId?.toString(),
    creatorId = creatorId?.toString(),
    completedAt = completedAt?.toEpochMilli(),
    completerId = completerId?.toString(),
    placeEvent = placeEvent.key,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

internal fun PlaceAndTodos.asModel() = PlaceWithTodos(
    place = place.asModel(),
    todos = todos.map(TodoEntity::asModel),
)

internal fun CategoryEntity.asModel() = Category(
    id = UUID.fromString(id),
    builtin = builtin?.let(BuiltinCategory::fromKey),
    name = name,
    icon = icon,
    color = color,
    sortOrder = sortOrder,
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

internal fun Category.asEntity() = CategoryEntity(
    id = id.toString(),
    builtin = builtin?.key,
    name = name,
    icon = icon,
    color = color,
    sortOrder = sortOrder,
    updatedAt = updatedAt.toEpochMilli(),
)

internal fun MembershipEntity.asModel() = Membership(
    userId = UUID.fromString(userId),
    role = MemberRole.fromKey(role),
    displayName = displayName,
    joinedAt = Instant.ofEpochMilli(joinedAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

internal fun SyncStateEntity.asModel() = SyncState(
    householdId = householdId?.let(UUID::fromString),
    cursor = cursor,
    plan = if (plan == "pro") Plan.PRO else Plan.FREE,
)

internal fun SyncState.asEntity() = SyncStateEntity(
    householdId = householdId?.toString(),
    cursor = cursor,
    plan = if (plan == Plan.PRO) "pro" else "free",
)
