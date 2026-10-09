package com.locatedo.locatedo.feature.todos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.common.CategoryStyle
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.ui.analytics.TrackScreen
import com.locatedo.locatedo.ui.components.assigneeChoices
import com.locatedo.locatedo.ui.components.todoDetail
import java.util.UUID

// Google Maps' saved lists: filter chips on top, one section per place, and a button to add more.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    onAddPlace: () -> Unit,
    onOpenPlace: (UUID) -> Unit,
    viewModel: TodoListViewModel = hiltViewModel(),
) {
    TrackScreen(AnalyticsScreen.TODOS)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isAddingTodo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_todos)) }) },
        floatingActionButton = {
            if (uiState.hasPlaces) {
                ExtendedFloatingActionButton(
                    onClick = { isAddingTodo = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.todo_editor_title)) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChipFor(TodoFilter.ALL, R.string.todo_filter_all, uiState.filter, viewModel::setFilter)
                FilterChipFor(TodoFilter.OPEN, R.string.todo_filter_open, uiState.filter, viewModel::setFilter)
                FilterChipFor(TodoFilter.DONE, R.string.todo_filter_done, uiState.filter, viewModel::setFilter)
            }
            // Pulling down syncs, as on iOS; without an account there is nothing to pull.
            val content: @Composable () -> Unit = {
                when (uiState.emptyState) {
                    TodoListEmptyState.NO_PLACES -> EmptyState(
                        title = R.string.todo_list_no_places_title,
                        message = R.string.todo_list_no_places_message,
                        action = R.string.home_add_place,
                        onAction = onAddPlace,
                    )
                    TodoListEmptyState.NO_TODOS -> EmptyState(
                        title = R.string.todo_list_empty_title,
                        message = R.string.todo_list_empty_message,
                        action = R.string.todo_editor_title,
                        onAction = { isAddingTodo = true },
                    )
                    TodoListEmptyState.NO_MATCHES -> EmptyState(title = R.string.todo_list_filter_empty)
                    null -> TodoGroups(
                        groups = uiState.groups,
                        members = uiState.members,
                        onOpenPlace = onOpenPlace,
                        onToggle = viewModel::setTodoCompleted,
                        onDelete = viewModel::deleteTodo,
                        onAssign = viewModel::setAssignee,
                    )
                }
            }
            if (uiState.isSignedIn) {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    content()
                }
            } else {
                content()
            }
        }
    }
    if (isAddingTodo) {
        TodoEditorSheet(placeId = null, onDismiss = { isAddingTodo = false })
    }
}

@Composable
private fun FilterChipFor(filter: TodoFilter, label: Int, selected: TodoFilter, onSelect: (TodoFilter) -> Unit) {
    FilterChip(
        selected = selected == filter,
        onClick = { onSelect(filter) },
        label = { Text(stringResource(label)) },
    )
}

@Composable
private fun TodoGroups(
    groups: List<TodoGroup>,
    members: List<Membership>,
    onOpenPlace: (UUID) -> Unit,
    onToggle: (UUID, Boolean) -> Unit,
    onDelete: (UUID) -> Unit,
    onAssign: (UUID, UUID?) -> Unit,
) {
    val assignees = assigneeChoices(members)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        for (group in groups) {
            item(key = "place-${group.place.id}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPlace(group.place.id) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        CategoryStyle.icon(group.category?.icon),
                        contentDescription = null,
                        tint = CategoryStyle.tint(group.category?.color),
                    )
                    Text(group.place.name, style = MaterialTheme.typography.titleMedium)
                }
            }
            items(group.todos, key = { it.id }) { todo ->
                TodoRow(
                    todo = todo,
                    onToggle = { onToggle(todo.id, it) },
                    onDelete = { onDelete(todo.id) },
                    detail = todoDetail(members, todo),
                    assignees = assignees,
                    onAssign = { onAssign(todo.id, it) },
                )
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun EmptyState(title: Int, message: Int? = null, action: Int? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        if (message != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) {
                Text(stringResource(action))
            }
        }
    }
}
