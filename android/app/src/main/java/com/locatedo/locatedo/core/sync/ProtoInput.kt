package com.locatedo.locatedo.core.sync

import com.google.protobuf.Timestamp
import com.locatedo.category.v1.CategoryInput
import com.locatedo.category.v1.categoryInput
import com.locatedo.household.v1.CreateHouseholdRequest
import com.locatedo.household.v1.createHouseholdRequest
import com.locatedo.household.v1.initialTodo
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.Plan
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.place.v1.PlaceInput
import com.locatedo.place.v1.placeInput
import com.locatedo.todo.v1.TodoInput
import com.locatedo.todo.v1.todoInput
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

fun Instant.toTimestamp(): Timestamp = Timestamp.newBuilder().setSeconds(epochSecond).setNanos(nano).build()

fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanos.toLong())

fun com.locatedo.household.v1.Plan.toModel(): Plan =
    if (this == com.locatedo.household.v1.Plan.PLAN_PRO) Plan.PRO else Plan.FREE

// The request shapes of the write RPCs, built from the local models.
object ProtoInput {
    fun id(id: UUID): String = id.toString()

    fun category(category: Category): CategoryInput = categoryInput {
        id = ProtoInput.id(category.id)
        builtin = builtin(category.builtin)
        category.name?.let { name = it }
        icon = category.icon
        color = category.color
        sortOrder = category.sortOrder
    }

    fun place(place: Place): PlaceInput = placeInput {
        id = ProtoInput.id(place.id)
        name = place.name
        lat = place.latitude
        lng = place.longitude
        radiusM = place.radiusMeters.roundToInt()
        place.categoryId?.let { categoryId = ProtoInput.id(it) }
        sortOrder = place.sortOrder
    }

    fun todo(todo: Todo): TodoInput = todoInput {
        id = ProtoInput.id(todo.id)
        placeId = ProtoInput.id(todo.placeId)
        title = todo.title
        todo.assigneeId?.let { assigneeId = ProtoInput.id(it) }
    }

    private fun builtin(builtin: BuiltinCategory?): com.locatedo.category.v1.BuiltinCategory = when (builtin) {
        BuiltinCategory.SHOPPING -> com.locatedo.category.v1.BuiltinCategory.BUILTIN_CATEGORY_SHOPPING
        BuiltinCategory.WORK -> com.locatedo.category.v1.BuiltinCategory.BUILTIN_CATEGORY_WORK
        BuiltinCategory.LIFE -> com.locatedo.category.v1.BuiltinCategory.BUILTIN_CATEGORY_LIFE
        BuiltinCategory.OTHER -> com.locatedo.category.v1.BuiltinCategory.BUILTIN_CATEGORY_OTHER
        null -> com.locatedo.category.v1.BuiltinCategory.BUILTIN_CATEGORY_UNSPECIFIED
    }
}

// Everything the device built while signed out, handed to CreateHousehold in one request.
object InitialUpload {
    fun request(householdId: UUID, categories: List<Category>, places: List<Place>, todos: List<Todo>): CreateHouseholdRequest =
        createHouseholdRequest {
            id = ProtoInput.id(householdId)
            this.categories += categories.map(ProtoInput::category)
            this.places += places.map(ProtoInput::place)
            this.todos += todos.map { todo ->
                initialTodo {
                    this.todo = ProtoInput.todo(todo)
                    todo.completedAt?.let { completedAt = it.toTimestamp() }
                }
            }
        }
}
