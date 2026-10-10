package com.locatedo.locatedo.feature.todos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.model.TodoDeletionVia
import com.locatedo.locatedo.ui.components.AssigneeChoice
import com.locatedo.locatedo.ui.components.SwipeToDeleteBackground
import java.util.UUID

private enum class RowMenu { NONE, ACTIONS, ASSIGNEES }

// A to-do that toggles as one checkbox row; swiping it left deletes it. The trailing button and a long press open
// the same menu (edit, assignee when shared, delete), which accessibility reaches as custom actions.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoRow(
    todo: Todo,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: (TodoDeletionVia) -> Unit,
    detail: String? = null,
    assignees: List<AssigneeChoice> = emptyList(),
    onAssign: (UUID?) -> Unit = {},
) {
    val editLabel = stringResource(R.string.common_edit)
    val deleteLabel = stringResource(R.string.common_delete)
    val assignLabel = stringResource(R.string.todo_assignee_change)
    var menu by remember { mutableStateOf(RowMenu.NONE) }
    val canAssign = assignees.isNotEmpty()

    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(),
        backgroundContent = { SwipeToDeleteBackground() },
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete(TodoDeletionVia.SWIPE) },
    ) {
        // The row's start padding sits outside ListItem's own background, so the box paints it; otherwise the red
        // swipe background shows through as a strip.
        Box(modifier = Modifier.background(ListItemDefaults.containerColor)) {
            ListItem(
                headlineContent = {
                    Text(
                        text = todo.title,
                        textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null,
                        color = if (todo.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                },
                modifier = Modifier
                    .combinedClickable(
                        role = Role.Checkbox,
                        onClick = { onToggle(!todo.isCompleted) },
                        onLongClick = { menu = RowMenu.ACTIONS },
                    )
                    .semantics {
                        toggleableState = ToggleableState(todo.isCompleted)
                        customActions = buildList {
                            add(
                                CustomAccessibilityAction(editLabel) {
                                    onEdit()
                                    true
                                },
                            )
                            if (canAssign) {
                                add(
                                    CustomAccessibilityAction(assignLabel) {
                                        menu = RowMenu.ASSIGNEES
                                        true
                                    },
                                )
                            }
                            add(
                                CustomAccessibilityAction(deleteLabel) {
                                    onDelete(TodoDeletionVia.MENU)
                                    true
                                },
                            )
                        }
                    }
                    .padding(start = 8.dp),
                supportingContent = detail?.let { text -> { Text(text) } },
                leadingContent = { Checkbox(checked = todo.isCompleted, onCheckedChange = null) },
                trailingContent = {
                    Box {
                        IconButton(onClick = { menu = RowMenu.ACTIONS }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.common_more))
                        }
                        DropdownMenu(expanded = menu == RowMenu.ACTIONS, onDismissRequest = { menu = RowMenu.NONE }) {
                            DropdownMenuItem(
                                text = { Text(editLabel) },
                                onClick = {
                                    menu = RowMenu.NONE
                                    onEdit()
                                },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            )
                            if (canAssign) {
                                DropdownMenuItem(
                                    text = { Text(assignLabel) },
                                    onClick = { menu = RowMenu.ASSIGNEES },
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(deleteLabel) },
                                onClick = {
                                    menu = RowMenu.NONE
                                    onDelete(TodoDeletionVia.MENU)
                                },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            )
                        }
                        AssigneeMenu(
                            expanded = menu == RowMenu.ASSIGNEES,
                            assignees = assignees,
                            selectedId = todo.assigneeId,
                            onDismiss = { menu = RowMenu.NONE },
                            onSelect = { userId ->
                                menu = RowMenu.NONE
                                onAssign(userId)
                            },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun AssigneeMenu(
    expanded: Boolean,
    assignees: List<AssigneeChoice>,
    selectedId: UUID?,
    onDismiss: () -> Unit,
    onSelect: (UUID?) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        for (choice in assignees) {
            DropdownMenuItem(
                text = { Text(choice.name) },
                onClick = { onSelect(choice.userId) },
                trailingIcon = if (choice.userId == selectedId) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else {
                    null
                },
            )
        }
    }
}
