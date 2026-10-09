package com.locatedo.locatedo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.data.TodoRepository
import com.locatedo.locatedo.core.data.TodoUndo
import com.locatedo.locatedo.core.model.Todo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TodoUndoViewModel @Inject constructor(
    undo: TodoUndo,
    private val todoRepository: TodoRepository,
) : ViewModel() {
    val offers: SharedFlow<List<Todo>> = undo.offers

    fun undo(deleted: List<Todo>) {
        viewModelScope.launch {
            todoRepository.restore(deleted)
        }
    }
}
