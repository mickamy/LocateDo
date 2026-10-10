package com.locatedo.locatedo.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryDraft(
    val category: Category? = null,
    val builtinTitle: String? = null,
    val name: String = "",
    val icon: String = "mappin",
    val color: String = "blue",
) {
    val isEditing: Boolean
        get() = category != null

    val canSave: Boolean
        get() = name.isNotBlank()
}

sealed interface CategoryEditorEvent {
    data object Saved : CategoryEditorEvent
}

@HiltViewModel
class CategoryEditorViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _draft = MutableStateFlow(CategoryDraft())
    private val _events = MutableSharedFlow<CategoryEditorEvent>()

    val draft: StateFlow<CategoryDraft> = _draft

    val events: SharedFlow<CategoryEditorEvent> = _events

    // A built-in's title comes from the translations, so the screen passes it in.
    fun start(category: Category?, builtinTitle: String?) {
        if (category == null) {
            _draft.value = CategoryDraft()
            return
        }
        _draft.value = CategoryDraft(
            category = category,
            builtinTitle = builtinTitle,
            name = category.name ?: builtinTitle ?: "",
            icon = category.icon,
            color = category.color,
        )
    }

    fun setName(name: String) = _draft.update { it.copy(name = name.take(MAX_NAME_LENGTH)) }

    fun setIcon(icon: String) = _draft.update { it.copy(icon = icon) }

    fun setColor(color: String) = _draft.update { it.copy(color = color) }

    fun save() {
        val current = _draft.value
        val name = current.name.trim()
        if (name.isEmpty()) {
            return
        }
        viewModelScope.launch {
            val existing = current.category
            if (existing == null) {
                val now = clock.instant()
                categoryRepository.add(
                    Category(id = uuidV7(now), name = name, icon = current.icon, color = current.color, sortOrder = 0, updatedAt = now),
                )
            } else {
                categoryRepository.update(
                    existing.copy(
                        name = storedName(name, existing.builtin, current.builtinTitle),
                        icon = current.icon,
                        color = current.color,
                    ),
                )
            }
            _events.emit(CategoryEditorEvent.Saved)
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 50

        // A built-in keeps no name while it shows its translated title, so it follows the device language.
        fun storedName(name: String, builtin: BuiltinCategory?, builtinTitle: String?): String? {
            if (builtin != null && name == builtinTitle) {
                return null
            }
            return name
        }
    }
}
