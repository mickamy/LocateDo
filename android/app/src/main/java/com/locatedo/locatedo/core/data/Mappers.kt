package com.locatedo.locatedo.core.data

import com.locatedo.locatedo.core.database.CategoryEntity
import com.locatedo.locatedo.core.database.PlaceAndTodos
import com.locatedo.locatedo.core.database.PlaceEntity
import com.locatedo.locatedo.core.database.TodoEntity
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
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
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

internal fun Todo.asEntity() = TodoEntity(
    id = id.toString(),
    title = title,
    placeId = placeId.toString(),
    assigneeId = assigneeId?.toString(),
    completedAt = completedAt?.toEpochMilli(),
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
