package com.locatedo.locatedo.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.appstatus.AppStatusDocument
import com.locatedo.locatedo.core.billing.PaywallTrigger
import com.locatedo.locatedo.feature.account.AccountScreen
import com.locatedo.locatedo.feature.appstatus.UpdateRequiredScreen
import com.locatedo.locatedo.feature.categories.CategoriesScreen
import com.locatedo.locatedo.feature.home.HomeScreen
import com.locatedo.locatedo.feature.map.MapScreen
import com.locatedo.locatedo.feature.onboarding.OnboardingScreen
import com.locatedo.locatedo.feature.onboarding.ReminderSetupNeed
import com.locatedo.locatedo.feature.onboarding.ReminderSetupSheet
import com.locatedo.locatedo.feature.paywall.PaywallScreen
import com.locatedo.locatedo.feature.place.PlaceCategoryScreen
import com.locatedo.locatedo.feature.place.PlaceDetailScreen
import com.locatedo.locatedo.feature.place.PlaceEditorScreen
import com.locatedo.locatedo.feature.place.PlaceEditorViewModel
import com.locatedo.locatedo.feature.place.PlacePickScreen
import com.locatedo.locatedo.feature.place.PlaceSearchScreen
import com.locatedo.locatedo.feature.place.PlaceTodosScreen
import com.locatedo.locatedo.feature.promotions.PromotionsConsentSheet
import com.locatedo.locatedo.feature.settings.SettingsScreen
import com.locatedo.locatedo.feature.sharing.AcceptInviteScreen
import com.locatedo.locatedo.feature.sharing.SharingScreen
import com.locatedo.locatedo.feature.todos.TodoListScreen
import com.locatedo.locatedo.ui.analytics.LocalAnalytics
import com.locatedo.locatedo.ui.appstatus.LocalAppStatus
import com.locatedo.locatedo.ui.appstatus.MaintenanceBanner
import com.locatedo.locatedo.ui.navigation.AcceptInviteKey
import com.locatedo.locatedo.ui.navigation.AccountKey
import com.locatedo.locatedo.ui.navigation.CategoriesKey
import com.locatedo.locatedo.ui.navigation.HomeKey
import com.locatedo.locatedo.ui.navigation.MapKey
import com.locatedo.locatedo.ui.navigation.PaywallKey
import com.locatedo.locatedo.ui.navigation.PlaceDetailKey
import com.locatedo.locatedo.ui.navigation.PlaceCategoryKey
import com.locatedo.locatedo.ui.navigation.PlaceEditorKey
import com.locatedo.locatedo.ui.navigation.PlacePickKey
import com.locatedo.locatedo.ui.navigation.PlaceSearchKey
import com.locatedo.locatedo.ui.navigation.PlaceTodosKey
import com.locatedo.locatedo.ui.navigation.SettingsKey
import com.locatedo.locatedo.ui.navigation.SharingKey
import com.locatedo.locatedo.ui.navigation.TodosKey
import java.util.UUID
import kotlinx.coroutines.flow.collectLatest

private data class Tab(val key: NavKey, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(HomeKey, R.string.tab_home, Icons.Filled.Home),
    Tab(MapKey, R.string.tab_map, Icons.Filled.Map),
    Tab(TodosKey, R.string.tab_todos, Icons.Filled.Checklist),
    Tab(SettingsKey, R.string.tab_settings, Icons.Filled.Settings),
)

private val tabKeys = tabs.map { it.key }

@Composable
fun LocateDoApp(appViewModel: AppViewModel = hiltViewModel()) {
    val appState by appViewModel.uiState.collectAsStateWithLifecycle()
    val pendingPlace by appViewModel.pendingPlace.collectAsStateWithLifecycle()
    val pendingInvite by appViewModel.pendingInvite.collectAsStateWithLifecycle()
    val pendingPaywall by appViewModel.pendingPaywall.collectAsStateWithLifecycle()
    val pendingTodos by appViewModel.pendingTodos.collectAsStateWithLifecycle()
    val permissions by appViewModel.currentPermissions.collectAsStateWithLifecycle()
    val appStatus = LocalAppStatus.current

    when {
        appState.isLoading -> Box(modifier = Modifier.fillMaxSize())
        appStatus.requiresUpdate -> UpdateRequiredScreen()
        !appState.hasCompletedOnboarding -> OnboardingScreen()
        else -> Tabs(
            onPlaceAdded = appViewModel::placeAdded,
            pendingPlace = pendingPlace,
            onPlaceConsumed = appViewModel::placeConsumed,
            pendingInvite = pendingInvite,
            onInviteConsumed = appViewModel::inviteConsumed,
            pendingPaywall = pendingPaywall,
            onPaywallConsumed = appViewModel::paywallConsumed,
            pendingTodos = pendingTodos,
            onTodosConsumed = appViewModel::todosConsumed,
            showsMaintenanceBanner = appState.isSignedIn,
            onDismissMaintenanceBanner = appViewModel::dismissMaintenanceBanner,
        )
    }
    val currentPermissions = permissions
    val reminderSetup = appState.reminderSetup
    if (reminderSetup != null && currentPermissions != null) {
        ReminderSetupSheet(
            permissions = currentPermissions,
            asksForPreciseLocation = ReminderSetupNeed.PRECISE_LOCATION in reminderSetup.missing,
            onNotificationsRequested = appViewModel::notificationsRequested,
            onPreciseLocationRequested = appViewModel::preciseLocationRequested,
            onRefresh = appViewModel::refreshPermissions,
            onClose = appViewModel::closeReminderSetup,
        )
    }
    if (appState.isAskingPromotions) {
        PromotionsConsentSheet(onAnswer = appViewModel::answerPromotions)
    }
    appState.notice?.let { notice ->
        NoticeDialog(notice = notice, onDismiss = appViewModel::dismissNotice)
    }
    appStatus.pendingNotice?.let { notice ->
        AnnouncementDialog(notice = notice, onDismiss = { appViewModel.noticeShown(notice) })
    }
}

// An announcement from the app status, shown once per id.
@Composable
private fun AnnouncementDialog(notice: AppStatusDocument.Notice, onDismiss: () -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.announcement_title)) },
        text = { Text(notice.message.text(language) ?: "") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_ok))
            }
        },
    )
}

@Composable
private fun NoticeDialog(notice: AppNotice, onDismiss: () -> Unit) {
    val title = when (notice) {
        AppNotice.REMOVED -> R.string.removed_title
        AppNotice.SESSION_ENDED -> R.string.session_ended_title
    }
    val message = when (notice) {
        AppNotice.REMOVED -> R.string.removed_android_message
        AppNotice.SESSION_ENDED -> R.string.session_ended_android_message
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_ok))
            }
        },
    )
}

@Composable
private fun Tabs(
    onPlaceAdded: () -> Unit,
    pendingPlace: UUID?,
    onPlaceConsumed: (UUID) -> Unit,
    pendingInvite: String?,
    onInviteConsumed: (String) -> Unit,
    pendingPaywall: PaywallTrigger?,
    onPaywallConsumed: (PaywallTrigger) -> Unit,
    pendingTodos: Boolean,
    onTodosConsumed: () -> Unit,
    showsMaintenanceBanner: Boolean,
    onDismissMaintenanceBanner: () -> Unit,
) {
    val backStack = rememberNavBackStack(HomeKey)
    val current = backStack.lastOrNull()
    val currentTab = backStack.lastOrNull { it in tabKeys }
    // The add / edit flow spans three screens, so its draft lives in a ViewModel scoped to the activity.
    val activity = LocalActivity.current as ComponentActivity
    val placeEditor: PlaceEditorViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val analytics = LocalAnalytics.current
    val todoUndo: TodoUndoViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    // A newer delete replaces the snackbar of the one before, as on iOS.
    LaunchedEffect(Unit) {
        todoUndo.offers.collectLatest { deleted ->
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.todo_deleted, deleted.first().title),
                actionLabel = resources.getString(R.string.common_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                todoUndo.undo(deleted)
            }
        }
    }

    LaunchedEffect(pendingPlace) {
        if (pendingPlace != null) {
            backStack.clear()
            backStack.add(HomeKey)
            backStack.add(PlaceDetailKey(pendingPlace.toString()))
            onPlaceConsumed(pendingPlace)
        }
    }
    LaunchedEffect(pendingTodos) {
        if (pendingTodos) {
            backStack.showTab(TodosKey)
            onTodosConsumed()
        }
    }
    LaunchedEffect(pendingInvite) {
        if (pendingInvite != null) {
            backStack.leaveFlow()
            backStack.add(AcceptInviteKey(pendingInvite))
            onInviteConsumed(pendingInvite)
        }
    }
    LaunchedEffect(pendingPaywall) {
        if (pendingPaywall != null) {
            if (backStack.lastOrNull() !is PaywallKey) {
                backStack.add(PaywallKey(pendingPaywall))
            }
            onPaywallConsumed(pendingPaywall)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (current in tabKeys || current is PlaceDetailKey) {
                NavigationBar {
                    for (tab in tabs) {
                        NavigationBarItem(
                            selected = currentTab == tab.key,
                            onClick = {
                                if (current != tab.key) {
                                    backStack.showTab(tab.key)
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
        Column(modifier = Modifier.padding(padding)) {
            // Only the tabs carry the banner; a flow screen or the paywall is not the place for it.
            if (showsMaintenanceBanner && current in tabKeys) {
                MaintenanceBanner(status = LocalAppStatus.current, onDismiss = onDismissMaintenanceBanner)
            }
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.weight(1f),
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
                                backStack.add(PlacePickKey)
                            },
                            onOpenPlace = { placeId -> backStack.add(PlaceDetailKey(placeId.toString())) },
                            onOpenSharing = {
                                analytics.log(AnalyticsEvent.SHARE_TAPPED, mapOf(AnalyticsParameter.SOURCE to "home"))
                                backStack.add(SharingKey)
                            },
                        )
                    }
                    entry<MapKey> {
                        MapScreen(
                            onAddPlace = {
                                placeEditor.start(placeId = null)
                                backStack.add(PlacePickKey)
                            },
                            onOpenPlace = { placeId -> backStack.add(PlaceDetailKey(placeId.toString())) },
                        )
                    }
                    entry<TodosKey> {
                        TodoListScreen(
                            onAddPlace = {
                                placeEditor.start(placeId = null)
                                backStack.add(PlacePickKey)
                            },
                            onOpenPlace = { placeId -> backStack.add(PlaceDetailKey(placeId.toString())) },
                        )
                    }
                    entry<SettingsKey> {
                        SettingsScreen(
                            onOpenAccount = { backStack.add(AccountKey) },
                            onOpenSharing = {
                                analytics.log(AnalyticsEvent.SHARE_TAPPED, mapOf(AnalyticsParameter.SOURCE to "settings"))
                                backStack.add(SharingKey)
                            },
                            onOpenCategories = { backStack.add(CategoriesKey) },
                        )
                    }
                    entry<PlaceDetailKey> { key ->
                        val placeId = UUID.fromString(key.placeId)
                        PlaceDetailScreen(
                            placeId = placeId,
                            onBack = { backStack.removeLastOrNull() },
                            onEdit = {
                                placeEditor.start(placeId)
                                backStack.add(PlaceEditorKey)
                            },
                        )
                    }
                    entry<CategoriesKey> {
                        CategoriesScreen(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<AccountKey> {
                        AccountScreen(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<SharingKey> {
                        SharingScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onAcceptInvite = { backStack.add(AcceptInviteKey()) },
                        )
                    }
                    entry<AcceptInviteKey> { key ->
                        AcceptInviteScreen(
                            token = key.token,
                            onBack = { backStack.removeLastOrNull() },
                            onJoined = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<PaywallKey> { key ->
                        PaywallScreen(trigger = key.trigger, onClose = { backStack.removeLastOrNull() })
                    }
                    entry<PlaceSearchKey> {
                        PlaceSearchScreen(
                            viewModel = placeEditor,
                            onPredictionFetched = { backStack.removeLastOrNull() },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    // A new place starts here and goes on to its details; an edited one comes back to them.
                    entry<PlacePickKey> {
                        PlacePickScreen(
                            viewModel = placeEditor,
                            onOpenSearch = { backStack.add(PlaceSearchKey) },
                            onLocationChosen = {
                                if (placeEditor.uiState.value.draft.isEditing) {
                                    backStack.removeLastOrNull()
                                } else {
                                    backStack.add(PlaceEditorKey)
                                }
                            },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<PlaceEditorKey> {
                        PlaceEditorScreen(
                            viewModel = placeEditor,
                            onChooseOnMap = {
                                if (placeEditor.uiState.value.draft.isEditing) {
                                    placeEditor.pickOnMap()
                                    backStack.add(PlacePickKey)
                                } else {
                                    backStack.removeLastOrNull()
                                }
                            },
                            onManageCategories = { backStack.add(CategoriesKey) },
                            onNext = { backStack.add(PlaceCategoryKey) },
                            onSaved = { isNew ->
                                backStack.leaveFlow()
                                if (isNew) {
                                    onPlaceAdded()
                                }
                            },
                            onCancel = { backStack.leaveFlow() },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<PlaceCategoryKey> {
                        PlaceCategoryScreen(
                            viewModel = placeEditor,
                            onNext = { backStack.add(PlaceTodosKey) },
                            onManageCategories = { backStack.add(CategoriesKey) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<PlaceTodosKey> {
                        PlaceTodosScreen(
                            viewModel = placeEditor,
                            onSaved = {
                                backStack.leaveFlow()
                                onPlaceAdded()
                            },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                },
            )
        }
    }
}

// Home stays at the bottom, so back from another tab returns to Home before leaving the app.
private fun NavBackStack<NavKey>.showTab(tab: NavKey) {
    clear()
    add(HomeKey)
    if (tab != HomeKey) {
        add(tab)
    }
}

// A place edited from its detail returns there; one added from a tab returns to the tab.
private fun NavBackStack<NavKey>.leaveFlow() {
    while (size > 1 && lastOrNull() !in tabKeys && lastOrNull() !is PlaceDetailKey) {
        removeLastOrNull()
    }
}
