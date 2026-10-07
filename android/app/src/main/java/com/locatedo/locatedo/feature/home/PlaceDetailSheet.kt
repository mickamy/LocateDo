package com.locatedo.locatedo.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.common.CategoryStyle
import com.locatedo.locatedo.core.common.DistanceFormatting
import com.locatedo.locatedo.core.common.categoryName
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.feature.todos.TodoRow
import com.locatedo.locatedo.ui.analytics.TrackScreen
import com.locatedo.locatedo.ui.components.AssigneeChoice
import com.locatedo.locatedo.ui.components.assigneeChoices
import com.locatedo.locatedo.ui.components.todoDetail
import java.util.UUID

// Google Maps' place sheet: a header, a row of action chips, then the content; here the content is the to-dos.
@Composable
fun PlaceDetailSheet(
    detail: PlaceDetail,
    onClose: () -> Unit,
    onAddTodo: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleTodo: (UUID, Boolean) -> Unit,
    onDeleteTodo: (UUID) -> Unit,
    members: List<Membership> = emptyList(),
    onAssignTodo: (UUID, UUID?) -> Unit = { _, _ -> },
) {
    TrackScreen(AnalyticsScreen.PLACE_DETAIL)
    val assignees = assigneeChoices(members)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 24.dp, end = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(detail.place.name, style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        CategoryStyle.icon(detail.category?.icon),
                        contentDescription = null,
                        tint = CategoryStyle.tint(detail.category?.color),
                    )
                    Text(categoryName(detail.category), style = MaterialTheme.typography.bodyMedium)
                    detail.distanceMeters?.let { meters ->
                        Text(
                            text = stringResource(R.string.place_detail_distance, DistanceFormatting.string(meters)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                detail.address?.let { address ->
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PlaceMenu(onDelete = onDelete)
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_done))
            }
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = onAddTodo,
                label = { Text(stringResource(R.string.place_detail_add_todo)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            )
            AssistChip(
                onClick = onEdit,
                label = { Text(stringResource(R.string.common_edit)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            )
        }
        Text(
            text = stringResource(R.string.place_detail_todos_label),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (detail.todos.isEmpty()) {
            Text(
                text = stringResource(R.string.place_detail_no_todos),
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (todo in detail.openTodos) {
            TodoRow(
                todo = todo,
                onToggle = { onToggleTodo(todo.id, it) },
                onDelete = { onDeleteTodo(todo.id) },
                detail = todoDetail(members, todo),
                assignees = assignees,
                onAssign = { onAssignTodo(todo.id, it) },
            )
        }
        if (detail.completedTodos.isNotEmpty()) {
            CompletedTodos(
                todos = detail.completedTodos,
                members = members,
                assignees = assignees,
                onToggle = onToggleTodo,
                onDelete = onDeleteTodo,
                onAssign = onAssignTodo,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

// Deleting is rare and cannot be undone, so it sits behind the menu instead of next to the everyday actions.
@Composable
private fun PlaceMenu(onDelete: () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isExpanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.common_more))
        }
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.place_detail_delete)) },
                onClick = {
                    isExpanded = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun CompletedTodos(
    todos: List<Todo>,
    members: List<Membership>,
    assignees: List<AssigneeChoice>,
    onToggle: (UUID, Boolean) -> Unit,
    onDelete: (UUID) -> Unit,
    onAssign: (UUID, UUID?) -> Unit,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${stringResource(R.string.todo_completed_section)} (${todos.size})",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
    }
    if (isExpanded) {
        for (todo in todos) {
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
}
