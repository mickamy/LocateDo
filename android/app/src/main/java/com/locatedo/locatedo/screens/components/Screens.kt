package com.locatedo.locatedo.screens.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.locatedo.locatedo.R
import com.locatedo.locatedo.navigation.LocalNavigator
import com.locatedo.locatedo.navigation.Overlay
import com.locatedo.locatedo.screens.todos.TodoEditorViewModel
import java.util.UUID

// Room below a list's last row, so it can scroll above the floating add button.
val AddButtonClearance = 88.dp

// A pushed screen's bar: its title and the way back.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, actions: @Composable () -> Unit = {}) {
    val navigator = LocalNavigator.current
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = navigator::pop) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
        },
        actions = { actions() },
    )
}

// The to-do sheet's state lives with the activity, so the sheet can step aside and come back.
@Composable
fun activityTodoEditor(): TodoEditorViewModel =
    hiltViewModel(viewModelStoreOwner = LocalActivity.current as ComponentActivity)

@Composable
fun rememberTodoEditing(): (UUID) -> Unit {
    val navigator = LocalNavigator.current
    val editor = activityTodoEditor()
    return { id ->
        editor.startEditing(id)
        navigator.present(Overlay.TodoEditor(entry = null))
    }
}

// Deletes what was already checked off, after a confirmation and without Undo. Deleting syncs, so a shared household
// loses them too, which the confirmation says.
@Composable
fun DeleteCompletedButton(count: Int, isShared: Boolean, onConfirm: () -> Unit) {
    val navigator = LocalNavigator.current
    val title = pluralStringResource(R.plurals.todo_delete_completed_confirm, count, count)
    val message = if (isShared) stringResource(R.string.todo_delete_completed_shared_message) else null
    val delete = stringResource(R.string.common_delete)
    TextButton(onClick = { navigator.present(Overlay.Confirmation(title, message, delete, onConfirm)) }) {
        Text(stringResource(R.string.todo_delete_completed), color = MaterialTheme.colorScheme.error)
    }
}

// Pulling down syncs; without an account there is nothing to pull.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Refreshable(isSignedIn: Boolean, isRefreshing: Boolean, onRefresh: () -> Unit, content: @Composable () -> Unit) {
    if (isSignedIn) {
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            content()
        }
    } else {
        content()
    }
}
