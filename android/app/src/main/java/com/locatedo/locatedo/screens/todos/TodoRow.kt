package com.locatedo.locatedo.screens.todos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
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
import androidx.compose.ui.Alignment
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
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.PlaceEvent
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.model.TodoDeletionVia
import com.locatedo.locatedo.screens.components.AssigneeChoice
import com.locatedo.locatedo.screens.components.SwipeToDeleteBackground
import com.locatedo.locatedo.screens.components.assigneeChoices
import com.locatedo.locatedo.screens.components.todoDetail
import java.util.UUID

private enum class RowMenu { NONE, ACTIONS, ASSIGNEES }

// What a row reports back; the screen's view model does the work.
interface TodoRowHandler {
    fun toggle(todo: Todo)

    fun edit(todo: Todo)

    fun delete(todo: Todo, via: TodoDeletionVia)

    fun assign(todo: Todo, userId: UUID?)
}

// A to-do that toggles as one checkbox row; swiping it left deletes it. The trailing button and a long press open
// the same menu (edit, assignee when shared, delete), which accessibility reaches as custom actions.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoRow(todo: Todo, members: List<Membership>, handler: TodoRowHandler) {
    val editLabel = stringResource(R.string.common_edit)
    val deleteLabel = stringResource(R.string.common_delete)
    val assignLabel = stringResource(R.string.todo_assignee_change)
    val assignees = assigneeChoices(members)
    val detail = todoDetail(members, todo)
    var menu by remember { mutableStateOf(RowMenu.NONE) }

    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(),
        backgroundContent = { SwipeToDeleteBackground() },
        enableDismissFromStartToEnd = false,
        onDismiss = { handler.delete(todo, TodoDeletionVia.SWIPE) },
    ) {
        // The row's start padding sits outside ListItem's own background, so the box paints it; otherwise the red
        // swipe background shows through as a strip.
        Box(modifier = Modifier.background(ListItemDefaults.containerColor)) {
            ListItem(
                headlineContent = {
                    // A to-do for leaving is marked, not listed apart, so a place keeps one list.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = todo.title,
                            modifier = Modifier.weight(1f, fill = false),
                            textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null,
                            color = if (todo.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        if (todo.placeEvent == PlaceEvent.DEPARTURE) {
                            Icon(
                                Icons.AutoMirrored.Filled.DirectionsWalk,
                                contentDescription = stringResource(R.string.todo_editor_remind_on_leave),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                modifier = Modifier
                    .combinedClickable(
                        role = Role.Checkbox,
                        onClick = { handler.toggle(todo) },
                        onLongClick = { menu = RowMenu.ACTIONS },
                    )
                    .semantics {
                        toggleableState = ToggleableState(todo.isCompleted)
                        customActions = buildList {
                            add(CustomAccessibilityAction(editLabel) { handler.edit(todo); true })
                            if (assignees.isNotEmpty()) {
                                add(CustomAccessibilityAction(assignLabel) { menu = RowMenu.ASSIGNEES; true })
                            }
                            add(CustomAccessibilityAction(deleteLabel) { handler.delete(todo, TodoDeletionVia.MENU); true })
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
                                    handler.edit(todo)
                                },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            )
                            if (assignees.isNotEmpty()) {
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
                                    handler.delete(todo, TodoDeletionVia.MENU)
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
                                handler.assign(todo, userId)
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
