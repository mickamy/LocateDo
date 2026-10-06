package com.locatedo.locatedo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.locatedo.locatedo.R
import com.locatedo.locatedo.feature.home.HomeScreen
import com.locatedo.locatedo.feature.settings.SettingsScreen
import com.locatedo.locatedo.feature.todos.TodoListScreen
import com.locatedo.locatedo.ui.navigation.HomeKey
import com.locatedo.locatedo.ui.navigation.SettingsKey
import com.locatedo.locatedo.ui.navigation.TodosKey

private data class Tab(val key: NavKey, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(HomeKey, R.string.tab_home, Icons.Filled.Home),
    Tab(TodosKey, R.string.tab_todos, Icons.Filled.Checklist),
    Tab(SettingsKey, R.string.tab_settings, Icons.Filled.Settings),
)

@Composable
fun LocateDoApp() {
    val backStack = rememberNavBackStack(HomeKey)
    val current = backStack.lastOrNull()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                for (tab in tabs) {
                    NavigationBarItem(
                        selected = current == tab.key,
                        onClick = {
                            if (current != tab.key) {
                                backStack.clear()
                                backStack.add(tab.key)
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(padding),
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<HomeKey> { HomeScreen() }
                entry<TodosKey> { TodoListScreen() }
                entry<SettingsKey> { SettingsScreen() }
            },
        )
    }
}
