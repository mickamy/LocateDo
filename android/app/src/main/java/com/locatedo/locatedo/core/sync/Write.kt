package com.locatedo.locatedo.core.sync

import com.locatedo.category.v1.CategoryInput
import com.locatedo.category.v1.DeleteCategoryRequest
import com.locatedo.category.v1.deleteCategoryRequest
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.place.v1.DeletePlaceRequest
import com.locatedo.place.v1.PlaceInput
import com.locatedo.place.v1.deletePlaceRequest
import com.locatedo.todo.v1.DeleteTodoRequest
import com.locatedo.todo.v1.SetTodoCompletionRequest
import com.locatedo.todo.v1.TodoInput
import com.locatedo.todo.v1.deleteTodoRequest
import com.locatedo.todo.v1.setTodoCompletionRequest
import java.time.Instant
import java.util.UUID

enum class WriteKind(val key: String) {
    PUT_PLACE("putPlace"),
    DELETE_PLACE("deletePlace"),
    PUT_TODO("putTodo"),
    SET_TODO_COMPLETION("setTodoCompletion"),
    DELETE_TODO("deleteTodo"),
    PUT_CATEGORY("putCategory"),
    DELETE_CATEGORY("deleteCategory");

    companion object {
        fun fromKey(key: String): WriteKind? = entries.firstOrNull { it.key == key }
    }
}

// One queued call to a write RPC, stored as the request's protobuf bytes until it is sent.
sealed interface Write {
    data class PutPlace(val input: PlaceInput) : Write
    data class DeletePlace(val request: DeletePlaceRequest) : Write
    data class PutTodo(val input: TodoInput) : Write
    data class SetTodoCompletion(val request: SetTodoCompletionRequest) : Write
    data class DeleteTodo(val request: DeleteTodoRequest) : Write
    data class PutCategory(val input: CategoryInput) : Write
    data class DeleteCategory(val request: DeleteCategoryRequest) : Write

    val kind: WriteKind
        get() = when (this) {
            is PutPlace -> WriteKind.PUT_PLACE
            is DeletePlace -> WriteKind.DELETE_PLACE
            is PutTodo -> WriteKind.PUT_TODO
            is SetTodoCompletion -> WriteKind.SET_TODO_COMPLETION
            is DeleteTodo -> WriteKind.DELETE_TODO
            is PutCategory -> WriteKind.PUT_CATEGORY
            is DeleteCategory -> WriteKind.DELETE_CATEGORY
        }

    fun serialized(): ByteArray = when (this) {
        is PutPlace -> input.toByteArray()
        is DeletePlace -> request.toByteArray()
        is PutTodo -> input.toByteArray()
        is SetTodoCompletion -> request.toByteArray()
        is DeleteTodo -> request.toByteArray()
        is PutCategory -> input.toByteArray()
        is DeleteCategory -> request.toByteArray()
    }

    companion object {
        fun put(place: Place): Write = PutPlace(ProtoInput.place(place))

        fun put(todo: Todo): Write = PutTodo(ProtoInput.todo(todo))

        fun put(category: Category): Write = PutCategory(ProtoInput.category(category))

        // Unset completedAt reopens the todo.
        fun completion(todoId: UUID, completedAt: Instant?): Write = SetTodoCompletion(
            setTodoCompletionRequest {
                id = ProtoInput.id(todoId)
                completedAt?.let { this.completedAt = it.toTimestamp() }
            },
        )

        fun deletePlace(id: UUID): Write = DeletePlace(deletePlaceRequest { this.id = ProtoInput.id(id) })

        fun deleteTodo(id: UUID): Write = DeleteTodo(deleteTodoRequest { this.id = ProtoInput.id(id) })

        fun deleteCategory(id: UUID): Write = DeleteCategory(deleteCategoryRequest { this.id = ProtoInput.id(id) })

        // Throws InvalidProtocolBufferException when the payload is not that kind's request.
        fun decode(kind: WriteKind, payload: ByteArray): Write = when (kind) {
            WriteKind.PUT_PLACE -> PutPlace(PlaceInput.parseFrom(payload))
            WriteKind.DELETE_PLACE -> DeletePlace(DeletePlaceRequest.parseFrom(payload))
            WriteKind.PUT_TODO -> PutTodo(TodoInput.parseFrom(payload))
            WriteKind.SET_TODO_COMPLETION -> SetTodoCompletion(SetTodoCompletionRequest.parseFrom(payload))
            WriteKind.DELETE_TODO -> DeleteTodo(DeleteTodoRequest.parseFrom(payload))
            WriteKind.PUT_CATEGORY -> PutCategory(CategoryInput.parseFrom(payload))
            WriteKind.DELETE_CATEGORY -> DeleteCategory(DeleteCategoryRequest.parseFrom(payload))
        }
    }
}
