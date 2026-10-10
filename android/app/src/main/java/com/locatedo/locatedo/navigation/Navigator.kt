package com.locatedo.locatedo.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.locatedo.locatedo.core.analytics.AlwaysPromptAnswer
import com.locatedo.locatedo.logic.ReminderSetupRequest
import java.util.UUID

// What sits over a screen: a bottom sheet or a dialog. Only one at a time.
sealed interface Overlay {
    data object TodoEditor : Overlay

    // Explains background location before the system page; the answer goes to whoever asked, for its analytics.
    data class AlwaysLocation(val onAnswer: (AlwaysPromptAnswer) -> Unit) : Overlay

    data class ReminderSetup(val request: ReminderSetupRequest) : Overlay

    data object Promotions : Overlay

    // A destructive action asked about first.
    data class Confirmation(
        val title: String,
        val message: String?,
        val actionLabel: String,
        val action: () -> Unit,
    ) : Overlay
}

// The one place that moves between screens and puts things over them. An overlay belongs to the screen it was opened
// on: pushing another screen hides it with its state kept, coming back shows it again, and leaving that screen for
// good drops it. So a screen pushed from anywhere (a notification, a link, the paywall) never fights a sheet.
@Stable
class Navigator(val backStack: NavBackStack<NavKey>) {
    private var current by mutableStateOf<Pair<Overlay, NavKey>?>(null)

    val overlay: Overlay?
        get() = current?.first

    val visibleOverlay: Overlay?
        get() = current?.takeIf { (_, host) -> host == backStack.lastOrNull() }?.first

    val top: NavKey?
        get() = backStack.lastOrNull()

    fun push(key: NavKey) {
        backStack.add(key)
    }

    fun pop() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
        dropOrphanedOverlay()
    }

    fun present(overlay: Overlay) {
        current = overlay to (backStack.lastOrNull() ?: HomeKey)
    }

    fun dismissOverlay() {
        current = null
    }

    // A place opened from outside sits over Home, as if it had been opened from there.
    fun showPlace(placeId: UUID) = replaceAboveHome(PlaceKey(placeId.toString()))

    fun showAllTodos() = replaceAboveHome(AllTodosKey)

    // Back out of adding or editing a place, to the screen it started from.
    fun leavePlaceEditor() {
        while (backStack.size > 1 && backStack.lastOrNull() in placeEditorKeys) {
            backStack.removeAt(backStack.lastIndex)
        }
        dropOrphanedOverlay()
    }

    private fun replaceAboveHome(key: NavKey) {
        backStack.clear()
        backStack.add(HomeKey)
        backStack.add(key)
        dropOrphanedOverlay()
    }

    private fun dropOrphanedOverlay() {
        val host = current?.second ?: return
        if (host !in backStack) {
            current = null
        }
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }

@Composable
fun rememberNavigator(): Navigator {
    val backStack = rememberNavBackStack(HomeKey)
    return remember(backStack) { Navigator(backStack) }
}
