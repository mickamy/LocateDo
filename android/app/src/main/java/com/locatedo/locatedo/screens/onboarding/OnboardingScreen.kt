package com.locatedo.locatedo.screens.onboarding

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.analytics.ScreenEntry
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.logic.ReminderSetup
import com.locatedo.locatedo.navigation.LocalNavigator
import com.locatedo.locatedo.navigation.Navigator
import com.locatedo.locatedo.screens.placeeditor.PlaceEditorEvent
import com.locatedo.locatedo.screens.placeeditor.PlaceEditorViewModel
import com.locatedo.locatedo.screens.placeeditor.PlacePickScreen
import com.locatedo.locatedo.screens.placeeditor.PlaceSearchScreen
import com.locatedo.locatedo.ui.analytics.TrackScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data object IntroKey : NavKey

@Serializable
private data object ReturningKey : NavKey

@Serializable
private data object StoreKindKey : NavKey

@Serializable
private data object StoreNameKey : NavKey

@Serializable
private data class FirstTodosKey(val store: FirstStore) : NavKey

// With no store, location is asked on its own: for "Not now" or a way that skips the first place.
@Serializable
private data class PrivacyKey(val store: FirstStore?) : NavKey

@Serializable
private data object StoreSearchKey : NavKey

@Serializable
private data class StorePickKey(val store: FirstStore) : NavKey

@Serializable
private data class DoneKey(val placeName: String, val kind: StoreKind) : NavKey

@Serializable
private data object AnalyticsKey : NavKey

// The first page, then the first place asked the other way round from adding one: the kind of store, what to do
// there, and only then the store itself, picked on the map around you or searched for by name. Location is asked
// right before the map needs it. Someone invited or coming back skips the first place.
@Composable
fun OnboardingScreen(
    pendingInvite: String?,
    onFinished: (OnboardingResult) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val activity = LocalActivity.current as ComponentActivity
    // The search and the map are the ones adding a place uses, with the same draft.
    val placeEditor: PlaceEditorViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val backStack = rememberNavBackStack(IntroKey)
    val navigator = remember(backStack) { Navigator(backStack) }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val result by viewModel.result.collectAsStateWithLifecycle()
    val permissions by viewModel.currentPermissions.collectAsStateWithLifecycle()
    var isRequestingLocation by rememberSaveable { mutableStateOf(false) }

    // Done and the usage data question start over as the only page, so going back never saves the place twice.
    fun replaceWith(key: NavKey) {
        backStack.clear()
        backStack.add(key)
    }

    fun finish() {
        scope.launch {
            if (viewModel.finish()) {
                replaceWith(AnalyticsKey)
            }
        }
    }

    fun choose(choice: OnboardingChoice) {
        viewModel.choose(choice)
        scope.launch {
            if (viewModel.needsLocation()) {
                navigator.push(PrivacyKey(store = null))
            } else {
                finish()
            }
        }
    }

    fun startTodos(store: FirstStore) {
        if (viewModel.startsNewTodos(store)) {
            placeEditor.start(placeId = null, entry = ScreenEntry.ONBOARDING)
        }
        navigator.push(FirstTodosKey(store))
    }

    fun pickStore(store: FirstStore) {
        navigator.push(StorePickKey(store))
    }

    fun todosDone(store: FirstStore) {
        scope.launch {
            if (viewModel.needsLocation()) {
                navigator.push(PrivacyKey(store))
            } else {
                pickStore(store)
            }
        }
    }

    // Any other store is filed by what was picked, as adding a place does; the kinds are all shopping.
    fun save(store: FirstStore) {
        var category = BuiltinCategory.SHOPPING
        if (store.kind == StoreKind.OTHER) {
            category = placeEditor.uiState.value.draft.suggestion ?: BuiltinCategory.SHOPPING
        }
        placeEditor.saveFirstPlace(defaultName = store.name(resources), category = category)
    }

    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        isRequestingLocation = false
        viewModel.locationRequested()
        val privacy = backStack.lastOrNull() as? PrivacyKey ?: return@rememberLauncherForActivityResult
        val store = privacy.store
        if (store == null) {
            finish()
            return@rememberLauncherForActivityResult
        }
        backStack.removeAt(backStack.lastIndex)
        pickStore(store)
    }

    LaunchedEffect(result) {
        result?.let(onFinished)
    }
    LaunchedEffect(Unit) {
        placeEditor.events.collect { event ->
            val store = backStack.filterIsInstance<StorePickKey>().lastOrNull()?.store ?: return@collect
            when (event) {
                PlaceEditorEvent.PredictionFetched -> navigator.pop()
                PlaceEditorEvent.LocationChosen -> save(store)
                // Only a place already saved, which a fresh install does not have.
                is PlaceEditorEvent.OpenSavedPlace -> choose(OnboardingChoice.LATER)
                is PlaceEditorEvent.Saved -> {
                    val name = placeEditor.uiState.value.draft.name.trim()
                    viewModel.firstPlaceSaved(store.kind, name)
                    replaceWith(DoneKey(name, store.kind))
                }
            }
        }
    }
    // An invite link opened mid-onboarding: the household's places replace a first place, so skip to it.
    LaunchedEffect(pendingInvite) {
        val top = backStack.lastOrNull()
        val isDeciding = top !is DoneKey && top != AnalyticsKey && !(top is PrivacyKey && top.store == null)
        if (pendingInvite != null && isDeciding) {
            choose(OnboardingChoice.INVITE)
        }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        Surface(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = navigator::pop,
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                entryProvider = entryProvider {
                    entry<IntroKey> {
                        TrackStep(OnboardingStep.INTRO)
                        IntroPage(onStart = { navigator.push(StoreKindKey) }, onReturning = { navigator.push(ReturningKey) })
                    }
                    entry<StoreKindKey> {
                        TrackStep(OnboardingStep.STORE_KIND)
                        StoreKindPage(
                            onPick = { kind ->
                                if (kind == StoreKind.OTHER) {
                                    navigator.push(StoreNameKey)
                                } else {
                                    startTodos(FirstStore(kind))
                                }
                            },
                            onLater = { choose(OnboardingChoice.LATER) },
                        )
                    }
                    entry<StoreNameKey> {
                        TrackStep(OnboardingStep.STORE_NAME)
                        StoreNamePage(onNext = { name -> startTodos(FirstStore(StoreKind.OTHER, name)) })
                    }
                    entry<FirstTodosKey> { key ->
                        TrackStep(OnboardingStep.TODOS)
                        FirstTodosPage(store = key.store, placeEditor = placeEditor, onNext = { todosDone(key.store) })
                    }
                    entry<PrivacyKey> { key ->
                        TrackStep(OnboardingStep.PRIVACY)
                        var reason = stringResource(R.string.first_place_permissions)
                        if (key.store == null) {
                            reason = stringResource(R.string.first_place_later_permissions)
                        }
                        PrivacyPage(
                            reason = reason,
                            isRequesting = isRequestingLocation,
                            onAllow = {
                                isRequestingLocation = true
                                requestLocation.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                                )
                            },
                        )
                    }
                    entry<StorePickKey> { key ->
                        PlacePickScreen(
                            viewModel = placeEditor,
                            onOpenSearch = { navigator.push(StoreSearchKey) },
                            heading = key.store.storeTitle(resources),
                        )
                    }
                    entry<StoreSearchKey> {
                        PlaceSearchScreen(viewModel = placeEditor)
                    }
                    entry<DoneKey> { key ->
                        TrackStep(OnboardingStep.DONE)
                        var needsSetup = false
                        permissions?.let { current -> needsSetup = ReminderSetup.missing(current).isNotEmpty() }
                        DonePage(placeName = key.placeName, needsSetup = needsSetup, onContinue = ::finish)
                    }
                    entry<ReturningKey> {
                        TrackStep(OnboardingStep.RETURNING)
                        ReturningPage(
                            onInvite = { choose(OnboardingChoice.INVITE) },
                            onSignIn = { choose(OnboardingChoice.SIGN_IN) },
                            onBack = navigator::pop,
                        )
                    }
                    entry<AnalyticsKey> {
                        TrackStep(OnboardingStep.ANALYTICS)
                        AnalyticsConsentPage(onAnswer = viewModel::answerAnalytics)
                    }
                },
            )
        }
    }
}

@Composable
private fun TrackStep(step: OnboardingStep) {
    TrackScreen(AnalyticsScreen.ONBOARDING, mapOf(AnalyticsParameter.STEP to step.key))
}
