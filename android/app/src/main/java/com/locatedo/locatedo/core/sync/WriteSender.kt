package com.locatedo.locatedo.core.sync

import com.locatedo.category.v1.CategoryServiceClientInterface
import com.locatedo.category.v1.putCategoryRequest
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.place.v1.PlaceServiceClientInterface
import com.locatedo.place.v1.putPlaceRequest
import com.locatedo.todo.v1.TodoServiceClientInterface
import com.locatedo.todo.v1.putTodoRequest
import javax.inject.Inject

class WriteSender @Inject constructor(
    private val places: PlaceServiceClientInterface,
    private val todos: TodoServiceClientInterface,
    private val categories: CategoryServiceClientInterface,
    private val authenticator: Authenticator,
) {
    // Throws ConnectException when the server rejects the write and SignedOutException without a session.
    suspend fun send(write: Write, householdId: String) {
        when (write) {
            is Write.PutPlace -> authenticator.authorized {
                places.putPlace(
                    putPlaceRequest {
                        this.householdId = householdId
                        place = write.input
                    },
                )
            }
            is Write.DeletePlace -> authenticator.authorized { places.deletePlace(write.request) }
            is Write.PutTodo -> authenticator.authorized {
                todos.putTodo(
                    putTodoRequest {
                        this.householdId = householdId
                        todo = write.input
                    },
                )
            }
            is Write.SetTodoCompletion -> authenticator.authorized { todos.setTodoCompletion(write.request) }
            is Write.DeleteTodo -> authenticator.authorized { todos.deleteTodo(write.request) }
            is Write.PutCategory -> authenticator.authorized {
                categories.putCategory(
                    putCategoryRequest {
                        this.householdId = householdId
                        category = write.input
                    },
                )
            }
            is Write.DeleteCategory -> authenticator.authorized { categories.deleteCategory(write.request) }
        }
    }
}
