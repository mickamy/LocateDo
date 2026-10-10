package com.locatedo.locatedo.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AlwaysPromptAnswer
import com.locatedo.locatedo.core.analytics.ScreenEntry
import com.locatedo.locatedo.logic.ReminderSetupNeed
import com.locatedo.locatedo.navigation.AcceptInviteKey
import com.locatedo.locatedo.navigation.AccountKey
import com.locatedo.locatedo.navigation.AllTodosKey
import com.locatedo.locatedo.navigation.CategoriesKey
import com.locatedo.locatedo.navigation.HomeKey
import com.locatedo.locatedo.navigation.LocalNavigator
import com.locatedo.locatedo.navigation.MapKey
import com.locatedo.locatedo.navigation.Navigator
import com.locatedo.locatedo.navigation.Overlay
import com.locatedo.locatedo.navigation.PaywallKey
import com.locatedo.locatedo.navigation.PlaceCategoryKey
import com.locatedo.locatedo.navigation.PlaceDetailsKey
import com.locatedo.locatedo.navigation.PlaceKey
import com.locatedo.locatedo.navigation.PlacePickKey
import com.locatedo.locatedo.navigation.PlaceSearchKey
import com.locatedo.locatedo.navigation.PlaceTodosKey
import com.locatedo.locatedo.navigation.SettingsKey
import com.locatedo.locatedo.navigation.SharingKey
import com.locatedo.locatedo.navigation.rememberNavigator
import com.locatedo.locatedo.screens.account.AccountScreen
import com.locatedo.locatedo.screens.categories.CategoriesScreen
import com.locatedo.locatedo.screens.components.MaintenanceBanner
import com.locatedo.locatedo.screens.components.activityTodoEditor
import com.locatedo.locatedo.screens.home.HomeScreen
import com.locatedo.locatedo.screens.map.MapScreen
import com.locatedo.locatedo.screens.onboarding.AlwaysLocationSheet
import com.locatedo.locatedo.screens.onboarding.ReminderSetupSheet
import com.locatedo.locatedo.screens.paywall.PaywallScreen
import com.locatedo.locatedo.screens.place.PlaceScreen
import com.locatedo.locatedo.screens.placeeditor.PlaceCategoryScreen
import com.locatedo.locatedo.screens.placeeditor.PlaceDetailsScreen
import com.locatedo.locatedo.screens.placeeditor.PlaceEditorEvent
import com.locatedo.locatedo.screens.placeeditor.PlaceEditorViewModel
import com.locatedo.locatedo.screens.placeeditor.PlacePickScreen
import com.locatedo.locatedo.screens.placeeditor.PlaceSearchScreen
import com.locatedo.locatedo.screens.placeeditor.PlaceTodosScreen
import com.locatedo.locatedo.screens.promotions.PromotionsConsentSheet
import com.locatedo.locatedo.screens.settings.SettingsScreen
import com.locatedo.locatedo.screens.sharing.AcceptInviteScreen
import com.locatedo.locatedo.screens.sharing.SharingScreen
import com.locatedo.locatedo.screens.todos.AllTodosScreen
import com.locatedo.locatedo.screens.todos.TodoEditorEvent
import com.locatedo.locatedo.screens.todos.TodoEditorSheet
import com.locatedo.locatedo.screens.todos.TodoEditorViewModel
import com.locatedo.locatedo.ui.analytics.LocalPresentationLevel
import com.locatedo.locatedo.ui.analytics.LocalScreenTracker
import com.locatedo.locatedo.ui.appstatus.LocalAppStatus
import java.util.UUID
import kotlinx.coroutines.flow.collectLatest

// Home and the screens pushed over it, with what floats above them all: the add button, the Undo snackbar, the
// sheets and dialogs, and the requests that arrive from outside.
@Composable
fun MainScreen(appViewModel: AppViewModel) {
    val screenTracker = LocalScreenTracker.current
    val navigator = rememberNavigator(onOverlayClosed = { screenTracker.closed(level = 1) })
    val activity = LocalActivity.current as ComponentActivity
    // Adding or editing a place spans several screens, and the to-do sheet steps aside while one is added from it,
    // so both drafts live with the activity.
    val placeEditor: PlaceEditorViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val todoEditor = activityTodoEditor()
    val appState by appViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    fun addTodo(placeId: UUID?, entry: ScreenEntry) {
        todoEditor.start(placeId, entry)
        navigator.present(Overlay.TodoEditor(entry))
    }

    fun addPlace(entry: ScreenEntry) {
        placeEditor.start(placeId = null, isForTodo = entry == ScreenEntry.TODO_EDITOR, entry = entry)
        navigator.push(PlacePickKey)
    }

    fun editPlace(placeId: UUID) {
        placeEditor.start(placeId = placeId)
        navigator.push(PlaceDetailsKey)
    }

    FromOutside(navigator, appViewModel)
    LaunchedEffect(Unit) {
        placeEditor.events.collect { event ->
            handlePlaceEditor(event, navigator, placeEditor, todoEditor, appViewModel)
        }
    }
    LaunchedEffect(Unit) {
        todoEditor.events.collect { event ->
            navigator.dismissOverlay()
            if (event is TodoEditorEvent.Saved && event.landOn != null) {
                navigator.push(PlaceKey(event.landOn.toString(), ScreenEntry.NEW_PLACE))
            }
        }
    }
    // A newer delete replaces the snackbar of the one before.
    LaunchedEffect(Unit) {
        appViewModel.undoOffers.collectLatest { deleted ->
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.todo_deleted, deleted.first().title),
                actionLabel = resources.getString(R.string.common_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                appViewModel.restore(deleted)
            }
        }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
            floatingActionButton = {
                if (navigator.visibleOverlay == null) {
                    Box(modifier = Modifier.navigationBarsPadding()) {
                        AddButton(top = navigator.top, onAddTodo = ::addTodo, onAddPlace = { addPlace(ScreenEntry.HOME_MENU) })
                    }
                }
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                if (appState.isSignedIn && (navigator.top == HomeKey || navigator.top == MapKey || navigator.top is AllTodosKey)) {
                    MaintenanceBanner(status = LocalAppStatus.current, onDismiss = appViewModel::dismissMaintenanceBanner)
                }
                NavDisplay(
                    backStack = navigator.backStack,
                    modifier = Modifier.weight(1f),
                    onBack = navigator::pop,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        entry<HomeKey> { HomeScreen(onAddPlace = { addPlace(ScreenEntry.HOME_EMPTY) }) }
                        entry<MapKey> { MapScreen() }
                        entry<AllTodosKey> { key ->
                            AllTodosScreen(entry = key.entry, onAddTodo = { addTodo(null, ScreenEntry.ALL_TODOS_EMPTY) })
                        }
                        entry<PlaceKey> { key ->
                            val placeId = UUID.fromString(key.placeId)
                            PlaceScreen(placeId = placeId, entry = key.entry, rank = key.rank, onEdit = { editPlace(placeId) })
                        }
                        entry<SettingsKey> { SettingsScreen() }
                        entry<CategoriesKey> { CategoriesScreen() }
                        entry<AccountKey> { AccountScreen() }
                        entry<SharingKey> { SharingScreen() }
                        entry<AcceptInviteKey> { key -> AcceptInviteScreen(token = key.token) }
                        entry<PaywallKey> { key -> PaywallScreen(trigger = key.trigger) }
                        entry<PlacePickKey> {
                            PlacePickScreen(viewModel = placeEditor, onOpenSearch = { navigator.push(PlaceSearchKey) })
                        }
                        entry<PlaceSearchKey> { PlaceSearchScreen(viewModel = placeEditor) }
                        entry<PlaceDetailsKey> {
                            PlaceDetailsScreen(
                                viewModel = placeEditor,
                                // A new place goes back to its map; an edited one opens the map to move the pin.
                                onChooseOnMap = {
                                    if (placeEditor.uiState.value.draft.isEditing) {
                                        placeEditor.pickOnMap()
                                        navigator.push(PlacePickKey)
                                    } else {
                                        navigator.pop()
                                    }
                                },
                                onNext = { navigator.push(PlaceCategoryKey) },
                                onManageCategories = { navigator.push(CategoriesKey) },
                                onCancel = navigator::leavePlaceEditor,
                            )
                        }
                        entry<PlaceCategoryKey> {
                            PlaceCategoryScreen(
                                viewModel = placeEditor,
                                onNext = { navigator.push(PlaceTodosKey) },
                                onManageCategories = { navigator.push(CategoriesKey) },
                            )
                        }
                        entry<PlaceTodosKey> { PlaceTodosScreen(viewModel = placeEditor) }
                    },
                )
            }
        }
        Overlays(navigator, appViewModel, todoEditor, onNewPlaceForTodo = { addPlace(ScreenEntry.TODO_EDITOR) })
    }
}

// What the draft decided; where to go next is decided here, in one place.
private fun handlePlaceEditor(
    event: PlaceEditorEvent,
    navigator: Navigator,
    placeEditor: PlaceEditorViewModel,
    todoEditor: TodoEditorViewModel,
    appViewModel: AppViewModel,
) {
    val draft = placeEditor.uiState.value.draft
    when (event) {
        PlaceEditorEvent.PredictionFetched -> navigator.pop()
        // A new place goes on to its details; an edited one returns to them.
        PlaceEditorEvent.LocationChosen -> if (draft.isEditing) navigator.pop() else navigator.push(PlaceDetailsKey)
        is PlaceEditorEvent.OpenSavedPlace -> {
            navigator.leavePlaceEditor()
            if (draft.isForTodo) {
                todoEditor.placePicked(event.placeId)
            } else {
                navigator.push(PlaceKey(event.placeId.toString(), ScreenEntry.DUPLICATE_PROMPT))
            }
        }
        is PlaceEditorEvent.Saved -> {
            navigator.leavePlaceEditor()
            if (event.isNew) {
                if (draft.isForTodo) {
                    todoEditor.placeAdded(event.placeId)
                }
                appViewModel.placeAdded(draft.name.trim())
            }
        }
    }
}

// Screens asked for from a notification, a link, or a refused write. They are pushed right away; a sheet that was
// up steps aside and comes back with them.
@Composable
private fun FromOutside(navigator: Navigator, appViewModel: AppViewModel) {
    val pendingPlace by appViewModel.pendingPlace.collectAsStateWithLifecycle()
    val pendingTodos by appViewModel.pendingTodos.collectAsStateWithLifecycle()
    val pendingInvite by appViewModel.pendingInvite.collectAsStateWithLifecycle()
    val pendingPaywall by appViewModel.pendingPaywall.collectAsStateWithLifecycle()
    val reminderSetup by appViewModel.reminderSetup.collectAsStateWithLifecycle()
    val isAskingPromotions by appViewModel.isAskingPromotions.collectAsStateWithLifecycle()
    val afterOnboarding by appViewModel.afterOnboarding.collectAsStateWithLifecycle()

    LaunchedEffect(pendingPlace) {
        pendingPlace?.let {
            navigator.showPlace(it)
            appViewModel.placeConsumed(it)
        }
    }
    LaunchedEffect(pendingTodos) {
        if (pendingTodos) {
            navigator.showAllTodos()
            appViewModel.todosConsumed()
        }
    }
    LaunchedEffect(pendingInvite) {
        pendingInvite?.let {
            navigator.push(AcceptInviteKey(it))
            appViewModel.inviteConsumed(it)
        }
    }
    LaunchedEffect(afterOnboarding) {
        when (afterOnboarding) {
            AfterOnboarding.INVITE -> navigator.push(AcceptInviteKey())
            AfterOnboarding.ACCOUNT -> navigator.push(AccountKey)
            null -> return@LaunchedEffect
        }
        appViewModel.afterOnboardingConsumed()
    }
    LaunchedEffect(pendingPaywall) {
        pendingPaywall?.let {
            if (navigator.top !is PaywallKey) {
                navigator.push(PaywallKey(it))
            }
            appViewModel.paywallConsumed(it)
        }
    }
    // The prompts wait for whatever sheet is up to close.
    val isFree = navigator.overlay == null
    LaunchedEffect(reminderSetup, isFree) {
        reminderSetup?.let { if (isFree) navigator.present(Overlay.ReminderSetup(it)) }
        if (reminderSetup == null && navigator.overlay is Overlay.ReminderSetup) {
            navigator.dismissOverlay()
        }
    }
    LaunchedEffect(isAskingPromotions, isFree) {
        if (isAskingPromotions && isFree) {
            navigator.present(Overlay.Promotions)
        }
    }
    LaunchedEffect(navigator.overlay != null) {
        appViewModel.presentingChanged(navigator.overlay != null)
    }
}

@Composable
private fun Overlays(
    navigator: Navigator,
    appViewModel: AppViewModel,
    todoEditor: TodoEditorViewModel,
    onNewPlaceForTodo: () -> Unit,
) {
    CompositionLocalProvider(LocalPresentationLevel provides 1) {
        OverlayContent(navigator.visibleOverlay, navigator, appViewModel, todoEditor, onNewPlaceForTodo)
    }
}

@Composable
private fun OverlayContent(
    overlay: Overlay?,
    navigator: Navigator,
    appViewModel: AppViewModel,
    todoEditor: TodoEditorViewModel,
    onNewPlaceForTodo: () -> Unit,
) {
    val permissions by appViewModel.currentPermissions.collectAsStateWithLifecycle()
    when (overlay) {
        is Overlay.TodoEditor -> TodoEditorSheet(
            overlay = overlay,
            viewModel = todoEditor,
            onDismiss = navigator::dismissOverlay,
            onNewPlace = onNewPlaceForTodo,
        )
        is Overlay.AlwaysLocation -> AlwaysLocationSheet(
            onAnswer = overlay.onAnswer,
            onDismiss = navigator::dismissOverlay,
        )
        is Overlay.ReminderSetup -> permissions?.let { current ->
            ReminderSetupSheet(
                placeName = overlay.request.placeName,
                permissions = current,
                asksForPreciseLocation = ReminderSetupNeed.PRECISE_LOCATION in overlay.request.missing,
                onNotificationsRequested = appViewModel::notificationsRequested,
                onPreciseLocationRequested = appViewModel::preciseLocationRequested,
                onRefresh = appViewModel::refreshPermissions,
                onClose = { answer: AlwaysPromptAnswer ->
                    navigator.dismissOverlay()
                    appViewModel.closeReminderSetup(answer)
                },
            )
        }
        Overlay.Promotions -> PromotionsConsentSheet(
            onAnswer = { answer ->
                navigator.dismissOverlay()
                appViewModel.answerPromotions(answer)
            },
        )
        is Overlay.Confirmation -> AlertDialog(
            onDismissRequest = navigator::dismissOverlay,
            title = { Text(overlay.title) },
            text = overlay.message?.let { message -> { Text(message) } },
            confirmButton = {
                TextButton(
                    onClick = {
                        navigator.dismissOverlay()
                        overlay.action()
                    },
                ) {
                    Text(overlay.actionLabel, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = navigator::dismissOverlay) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
        null -> Unit
    }
}
