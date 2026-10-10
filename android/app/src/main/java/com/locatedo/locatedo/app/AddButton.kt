package com.locatedo.locatedo.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.locatedo.locatedo.R
import com.locatedo.locatedo.navigation.AllTodosKey
import com.locatedo.locatedo.navigation.HomeKey
import com.locatedo.locatedo.navigation.PlaceKey
import java.util.UUID

// Changes with the screen: on Home a plus that opens "add a to-do" and "add a place" above it (Material's FAB menu),
// on a place or the list of to-dos a button that adds a to-do, and nothing elsewhere.
@Composable
fun AddButton(top: NavKey?, onAddTodo: (UUID?) -> Unit, onAddPlace: () -> Unit) {
    when (top) {
        HomeKey -> HomeMenu(onAddTodo = { onAddTodo(null) }, onAddPlace = onAddPlace)
        is PlaceKey -> AddTodoButton(onClick = { onAddTodo(UUID.fromString(top.placeId)) })
        AllTodosKey -> AddTodoButton(onClick = { onAddTodo(null) })
        else -> Unit
    }
}

@Composable
private fun HomeMenu(onAddTodo: () -> Unit, onAddPlace: () -> Unit) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = isExpanded) { isExpanded = false }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuItem(label = R.string.todo_editor_title, icon = { Icon(Icons.Filled.Checklist, contentDescription = null) }) {
                    isExpanded = false
                    onAddTodo()
                }
                MenuItem(label = R.string.home_add_place, icon = { Icon(Icons.Filled.Place, contentDescription = null) }) {
                    isExpanded = false
                    onAddPlace()
                }
            }
        }
        FloatingActionButton(onClick = { isExpanded = !isExpanded }) {
            if (isExpanded) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel))
            } else {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.common_add))
            }
        }
    }
}

@Composable
private fun MenuItem(label: Int, icon: @Composable () -> Unit, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = icon,
        text = { Text(stringResource(label)) },
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
private fun AddTodoButton(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.todo_editor_title)) },
    )
}
