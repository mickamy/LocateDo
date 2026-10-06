package com.locatedo.locatedo.core.model

import java.time.Instant
import java.util.UUID

data class Place(
    val id: UUID,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = DEFAULT_RADIUS_METERS,
    val categoryId: UUID? = null,
    val sortOrder: Int = 0,
    val lastNotifiedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
) {
    companion object {
        const val DEFAULT_RADIUS_METERS = 100.0
        val RADIUS_RANGE = 50.0..500.0
    }
}

data class Todo(
    val id: UUID,
    val title: String,
    val placeId: UUID,
    val assigneeId: UUID? = null,
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
) {
    val isCompleted: Boolean
        get() = completedAt != null
}

data class PlaceWithTodos(
    val place: Place,
    val todos: List<Todo>,
) {
    val openTodos: List<Todo>
        get() = todos.filter { !it.isCompleted }.sortedBy { it.createdAt }

    val completedTodosNewestFirst: List<Todo>
        get() = todos.filter { it.isCompleted }.sortedByDescending { it.completedAt }
}

// `icon` and `color` are app-defined keys shared with iOS and the server (e.g. "cart", "green").
data class Category(
    val id: UUID,
    val builtin: BuiltinCategory? = null,
    val name: String? = null,
    val icon: String,
    val color: String,
    val sortOrder: Int,
    val updatedAt: Instant,
)

enum class BuiltinCategory(val key: String, val icon: String, val color: String) {
    SHOPPING("shopping", "cart", "green"),
    WORK("work", "briefcase", "blue"),
    LIFE("life", "house", "orange"),
    OTHER("other", "mappin", "gray");

    companion object {
        fun fromKey(key: String): BuiltinCategory? = entries.firstOrNull { it.key == key }
    }
}

enum class FreeLimit(val max: Int) {
    PLACES(3),
    OPEN_TODOS(15),
}
