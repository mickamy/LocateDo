package com.locatedo.locatedo.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.locatedo.locatedo.R
import com.locatedo.locatedo.feature.home.HomeScreen
import com.locatedo.locatedo.feature.place.PlaceEditorScreen
import com.locatedo.locatedo.feature.place.PlaceEditorViewModel
import com.locatedo.locatedo.feature.place.PlacePickScreen
import com.locatedo.locatedo.feature.place.PlaceSearchScreen
import com.locatedo.locatedo.feature.settings.SettingsScreen
import com.locatedo.locatedo.feature.todos.TodoListScreen
import com.locatedo.locatedo.ui.navigation.HomeKey
import com.locatedo.locatedo.ui.navigation.PlaceEditorKey
import com.locatedo.locatedo.ui.navigation.PlacePickKey
import com.locatedo.locatedo.ui.navigation.PlaceSearchKey
import com.locatedo.locatedo.ui.navigation.SettingsKey
import com.locatedo.locatedo.ui.navigation.TodosKey

private data class Tab(val key: NavKey, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(HomeKey, R.string.tab_home, Icons.Filled.Home),
    Tab(TodosKey, R.string.tab_todos, Icons.Filled.Checklist),
    Tab(SettingsKey, R.string.tab_settings, Icons.Filled.Settings),
)

private val tabKeys = tabs.map { it.key }

@Composable
fun LocateDoApp() {
    val backStack = rememberNavBackStack(HomeKey)
    val current = backStack.lastOrNull()
    // The add / edit flow spans three screens, so its draft lives in a ViewModel scoped to the activity.
    val activity = LocalActivity.current as ComponentActivity
    val placeEditor: PlaceEditorViewModel = hiltViewModel(viewModelStoreOwner = activity)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (current in tabKeys) {
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
                entry<HomeKey> {
                    HomeScreen(
                        onAddPlace = {
                            placeEditor.start(placeId = null)
                            backStack.add(PlaceSearchKey)
                        },
                        onEditPlace = { placeId ->
                            placeEditor.start(placeId)
                            backStack.add(PlaceEditorKey)
                        },
                    )
                }
                entry<TodosKey> {
                    TodoListScreen(
                        onAddPlace = {
                            placeEditor.start(placeId = null)
                            backStack.add(PlaceSearchKey)
                        },
                        onOpenPlace = {
                            backStack.clear()
                            backStack.add(HomeKey)
                        },
                    )
                }
                entry<SettingsKey> { SettingsScreen() }
                entry<PlaceSearchKey> {
                    PlaceSearchScreen(
                        viewModel = placeEditor,
                        onChooseOnMap = { backStack.add(PlacePickKey) },
                        onLocationChosen = { backStack.add(PlaceEditorKey) },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<PlacePickKey> {
                    PlacePickScreen(
                        viewModel = placeEditor,
                        onLocationChosen = {
                            backStack.removeLastOrNull()
                            if (backStack.lastOrNull() != PlaceEditorKey) {
                                backStack.add(PlaceEditorKey)
                            }
                        },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<PlaceEditorKey> {
                    PlaceEditorScreen(
                        viewModel = placeEditor,
                        onChooseOnMap = { backStack.add(PlacePickKey) },
                        onSaved = { backStack.leaveFlow() },
                        onCancel = { backStack.leaveFlow() },
                    )
                }
            },
        )
    }
}

private fun NavBackStack<NavKey>.leaveFlow() {
    while (size > 1 && lastOrNull() !in tabKeys) {
        removeLastOrNull()
    }
}
