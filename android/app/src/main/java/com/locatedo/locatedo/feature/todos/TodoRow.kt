package com.locatedo.locatedo.feature.todos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.locatedo.locatedo.ui.components.AssigneeChoice
import com.locatedo.locatedo.ui.components.SwipeToDeleteBackground
import java.util.UUID

// A to-do that toggles as one checkbox row; swiping it left deletes it, and so does the accessibility action.
// With assignees to choose from, a long press opens the menu, which accessibility reaches as a custom action.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoRow(
    todo: Todo,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    assigneeName: String? = null,
    assignees: List<AssigneeChoice> = emptyList(),
    onAssign: (UUID?) -> Unit = {},
) {
    val deleteLabel = stringResource(R.string.common_delete)
    val assignLabel = stringResource(R.string.todo_assignee_change)
    var isChoosingAssignee by remember { mutableStateOf(false) }
    val canAssign = assignees.isNotEmpty()

    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(),
        backgroundContent = { SwipeToDeleteBackground() },
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete() },
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
                        onLongClick = if (canAssign) {
                            { isChoosingAssignee = true }
                        } else {
                            null
                        },
                    )
                    .semantics {
                        toggleableState = ToggleableState(todo.isCompleted)
                        customActions = buildList {
                            add(
                                CustomAccessibilityAction(deleteLabel) {
                                    onDelete()
                                    true
                                },
                            )
                            if (canAssign) {
                                add(
                                    CustomAccessibilityAction(assignLabel) {
                                        isChoosingAssignee = true
                                        true
                                    },
                                )
                            }
                        }
                    }
                    .padding(start = 8.dp),
                supportingContent = assigneeName?.let { name -> { Text(name) } },
                leadingContent = { Checkbox(checked = todo.isCompleted, onCheckedChange = null) },
            )
            DropdownMenu(expanded = isChoosingAssignee, onDismissRequest = { isChoosingAssignee = false }) {
                for (choice in assignees) {
                    DropdownMenuItem(
                        text = { Text(choice.name) },
                        onClick = {
                            isChoosingAssignee = false
                            onAssign(choice.userId)
                        },
                        trailingIcon = if (choice.userId == todo.assigneeId) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}
