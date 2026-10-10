package com.locatedo.locatedo.screens.todos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.analytics.ScreenEntry
import com.locatedo.locatedo.core.common.CategoryStyle
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.model.TodoDeletionVia
import com.locatedo.locatedo.navigation.LocalNavigator
import com.locatedo.locatedo.navigation.PlaceKey
import com.locatedo.locatedo.screens.components.AddButtonClearance
import com.locatedo.locatedo.screens.components.BackTopBar
import com.locatedo.locatedo.screens.components.DeleteCompletedButton
import com.locatedo.locatedo.screens.components.Refreshable
import com.locatedo.locatedo.screens.components.rememberTodoEditing
import com.locatedo.locatedo.ui.analytics.TrackScreen
import com.locatedo.locatedo.ui.analytics.parameters
import java.util.UUID

@Composable
fun AllTodosScreen(entry: ScreenEntry, onAddTodo: () -> Unit, viewModel: AllTodosViewModel = hiltViewModel()) {
    TrackScreen(AnalyticsScreen.TODOS, opening = entry.parameters)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val openEditor = rememberTodoEditing()
    val handler = object : TodoRowHandler {
        override fun toggle(todo: Todo) = viewModel.toggle(todo)

        override fun edit(todo: Todo) = openEditor(todo.id)

        override fun delete(todo: Todo, via: TodoDeletionVia) = viewModel.delete(todo, via)

        override fun assign(todo: Todo, userId: UUID?) = viewModel.assign(todo, userId)
    }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.home_all_todos)) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Filters(uiState, onSelect = viewModel::setFilter, onDeleteCompleted = viewModel::deleteCompleted)
            Refreshable(isSignedIn = uiState.isSignedIn, isRefreshing = uiState.isRefreshing, onRefresh = viewModel::refresh) {
                when {
                    uiState.isLoading -> Box(Modifier.fillMaxSize())
                    !uiState.hasTodos -> EmptyState(
                        title = R.string.todo_list_empty_title,
                        message = R.string.todo_list_empty_message,
                        action = R.string.todo_editor_title,
                        onAction = onAddTodo,
                    )
                    uiState.groups.isEmpty() -> EmptyState(title = R.string.todo_list_filter_empty)
                    else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = AddButtonClearance)) {
                        for (group in uiState.groups) {
                            item(key = "place-${group.place.id}") {
                                PlaceHeader(group, onClick = { navigator.push(PlaceKey(group.place.id.toString(), ScreenEntry.ALL_TODOS)) })
                            }
                            items(group.todos, key = { it.id }) { todo ->
                                TodoRow(todo = todo, members = uiState.members, handler = handler)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Filters(uiState: AllTodosUiState, onSelect: (TodoFilter) -> Unit, onDeleteCompleted: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((filter, label) in listOf(
            TodoFilter.ALL to R.string.todo_filter_all,
            TodoFilter.OPEN to R.string.todo_filter_open,
            TodoFilter.DONE to R.string.todo_filter_done,
        )) {
            FilterChip(selected = uiState.filter == filter, onClick = { onSelect(filter) }, label = { Text(stringResource(label)) })
        }
        if (uiState.filter == TodoFilter.DONE && uiState.completedTodoIds.isNotEmpty()) {
            Spacer(Modifier.weight(1f))
            DeleteCompletedButton(
                count = uiState.completedTodoIds.size,
                isShared = uiState.members.size > 1,
                onConfirm = onDeleteCompleted,
            )
        }
    }
}

@Composable
private fun PlaceHeader(group: TodoGroup, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
