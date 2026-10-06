package com.locatedo.locatedo.feature.todos

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.ui.components.SwipeToDeleteBackground

// A to-do that toggles as one checkbox row; swiping it left deletes it, and so does the accessibility action.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoRow(todo: Todo, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val deleteLabel = stringResource(R.string.common_delete)
    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(),
        backgroundContent = { SwipeToDeleteBackground() },
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete() },
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = todo.title,
                    textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null,
                    color = if (todo.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            },
            modifier = Modifier
                .toggleable(value = todo.isCompleted, role = Role.Checkbox, onValueChange = onToggle)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(deleteLabel) {
                            onDelete()
                            true
                        },
                    )
                }
                .padding(start = 8.dp),
            leadingContent = { Checkbox(checked = todo.isCompleted, onCheckedChange = null) },
        )
    }
}
