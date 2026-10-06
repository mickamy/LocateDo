package com.locatedo.locatedo.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryListItem(val category: Category, val placeCount: Int)

data class CategoryListUiState(
    val isLoading: Boolean = true,
    val items: List<CategoryListItem> = emptyList(),
)

@HiltViewModel
class CategoryListViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    placeRepository: PlaceRepository,
) : ViewModel() {
    // While a row is dragged the order lives here; it is written once the row is dropped.
    private val draggedOrder = MutableStateFlow<List<UUID>?>(null)

    val uiState: StateFlow<CategoryListUiState> = combine(
        categoryRepository.observeAll(),
        placeRepository.observeAll(),
        draggedOrder,
    ) { categories, places, order ->
        val counts = places.groupingBy { it.categoryId }.eachCount()
        val ordered = if (order == null) categories else categories.sortedBy { category -> order.indexOf(category.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        CategoryListUiState(
            isLoading = false,
            items = ordered.map { CategoryListItem(it, counts[it.id] ?: 0) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CategoryListUiState())

    init {
        viewModelScope.launch {
            categoryRepository.observeAll().collect { categories ->
                if (draggedOrder.value != null && categories.map { it.id } == draggedOrder.value) {
                    draggedOrder.value = null
                }
            }
        }
    }

    fun move(from: Int, to: Int) {
        val ids = uiState.value.items.map { it.category.id }.toMutableList()
        if (from !in ids.indices || to !in ids.indices || from == to) {
            return
        }
        ids.add(to, ids.removeAt(from))
        draggedOrder.value = ids
    }

    fun commitOrder() {
        if (draggedOrder.value == null) {
            return
        }
        val ordered = uiState.value.items.map { it.category }
        viewModelScope.launch {
            categoryRepository.reorder(ordered)
        }
    }

    suspend fun delete(id: UUID): Boolean = categoryRepository.delete(id)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
