package com.locatedo.locatedo.feature.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.common.categoryName
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.ui.analytics.TrackScreen
import com.locatedo.locatedo.ui.components.CategoryBadge
import com.locatedo.locatedo.ui.components.SwipeToDeleteBackground
import com.locatedo.locatedo.ui.components.dragToReorderHandle
import com.locatedo.locatedo.ui.components.rememberDragToReorderState
import kotlinx.coroutines.launch

// A plain Material list: tap to edit, drag the handle to reorder, swipe left to delete.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoryListViewModel = hiltViewModel(),
) {
    TrackScreen(AnalyticsScreen.CATEGORIES)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Category?>(null) }
    var isAdding by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val lastOneMessage = stringResource(R.string.category_last_one)
    val listState = rememberLazyListState()
    val reorder = rememberDragToReorderState(listState, onMove = viewModel::move, onDrop = viewModel::commitOrder)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_done))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { isAdding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.category_add)) },
            )
        },
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
            itemsIndexed(uiState.items, key = { _, item -> item.category.id }) { index, item ->
                val isDragging = reorder.draggingKey == item.category.id
                CategoryRow(
                    item = item,
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) reorder.draggedDistance else 0f }
                        .then(if (isDragging) Modifier else Modifier.animateItem()),
                    isDragging = isDragging,
                    handleModifier = Modifier.dragToReorderHandle(reorder, item.category.id),
                    onClick = { editing = item.category },
                    onMoveUp = if (index == 0) {
                        null
                    } else {
                        {
                            viewModel.move(index, index - 1)
                            viewModel.commitOrder()
                        }
                    },
                    onMoveDown = if (index == uiState.items.lastIndex) {
                        null
                    } else {
                        {
                            viewModel.move(index, index + 1)
                            viewModel.commitOrder()
                        }
                    },
                    onDelete = {
                        val deleted = viewModel.delete(item.category.id)
                        if (!deleted) {
                            snackbarHostState.showSnackbar(lastOneMessage)
                        }
                        deleted
                    },
                )
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    if (isAdding) {
        CategoryEditorSheet(category = null, onDismiss = { isAdding = false })
    }
    editing?.let { category ->
        CategoryEditorSheet(category = category, onDismiss = { editing = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryRow(
    item: CategoryListItem,
    modifier: Modifier,
    isDragging: Boolean,
    handleModifier: Modifier,
    onClick: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: suspend () -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val dismissState = rememberSwipeToDismissBoxState()
    var isConfirmingDelete by remember { mutableStateOf(false) }
    val name = categoryName(item.category)
    val moveUpLabel = stringResource(R.string.category_move_up)
    val moveDownLabel = stringResource(R.string.category_move_down)
    val deleteLabel = stringResource(R.string.common_delete)

    fun delete() {
        scope.launch {
            if (!onDelete()) {
                dismissState.reset()
            }
        }
    }

    fun requestDelete() {
        if (item.placeCount == 0) {
            delete()
        } else {
            isConfirmingDelete = true
        }
    }

    fun keep() {
        isConfirmingDelete = false
        scope.launch { dismissState.reset() }
    }

    // Dragging and swiping have no accessibility equivalent, so the row offers them as actions.
    val accessibilityActions = buildList {
        if (onMoveUp != null) {
            add(CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true })
        }
        if (onMoveDown != null) {
            add(CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true })
        }
        add(CustomAccessibilityAction(deleteLabel) { requestDelete(); true })
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { SwipeToDeleteBackground() },
        enableDismissFromStartToEnd = false,
        onDismiss = { requestDelete() },
    ) {
        Surface(
            tonalElevation = if (isDragging) 4.dp else 0.dp,
            shadowElevation = if (isDragging) 4.dp else 0.dp,
        ) {
            ListItem(
                headlineContent = { Text(name) },
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .semantics { customActions = accessibilityActions },
                leadingContent = { CategoryBadge(icon = item.category.icon, color = item.category.color, size = 36.dp) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = pluralStringResource(R.plurals.category_place_count, item.placeCount, item.placeCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(modifier = handleModifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
            )
        }
    }

    if (isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = ::keep,
            title = { Text(stringResource(R.string.category_delete_confirm_title, name)) },
            text = { Text(pluralStringResource(R.plurals.category_delete_confirm_message, item.placeCount, item.placeCount)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        isConfirmingDelete = false
                        delete()
                    },
                ) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = ::keep) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
