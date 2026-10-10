package com.locatedo.locatedo.core.data

import com.locatedo.locatedo.core.model.Todo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

// A screen that deleted one to-do hands it here; the app shell offers Undo in a snackbar, whichever screen it was.
@Singleton
class TodoUndo @Inject constructor() {
    private val _offers = MutableSharedFlow<List<Todo>>(extraBufferCapacity = 1)

    val offers: SharedFlow<List<Todo>> = _offers

    fun offer(deleted: List<Todo>) {
        if (deleted.isNotEmpty()) {
            _offers.tryEmit(deleted)
        }
    }
}
