package com.locatedo.locatedo.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.locatedo.locatedo.core.billing.PaywallTrigger
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigatorTest {
    private val placeId = UUID.fromString("0199bd00-0000-7000-8000-000000000001")
    private val place = PlaceKey(placeId.toString())

    private fun navigator(vararg keys: NavKey) = Navigator(NavBackStack(*keys))

    @Test
    fun anOverlayStepsAsideForAPushedScreenAndComesBack() {
        val navigator = navigator(HomeKey, place)
        navigator.present(Overlay.TodoEditor)

        navigator.push(PlacePickKey)
        assertEquals(Overlay.TodoEditor, navigator.overlay)
        assertNull(navigator.visibleOverlay)

        navigator.leavePlaceEditor()
        assertEquals(Overlay.TodoEditor, navigator.visibleOverlay)
    }

    @Test
    fun anOverlayGoesWithTheScreenItWasOpenedOn() {
        val navigator = navigator(HomeKey, place)
        navigator.present(Overlay.TodoEditor)

        navigator.pop()

        assertNull(navigator.overlay)
    }

    @Test
    fun aPlaceFromOutsideSitsOverHomeAndDropsWhatWasOpen() {
        val navigator = navigator(HomeKey, AllTodosKey, PaywallKey(PaywallTrigger.TODO_LIMIT))
        navigator.present(Overlay.Promotions)

        navigator.showPlace(placeId)

        assertEquals(listOf(HomeKey, place), navigator.backStack.toList())
        assertNull(navigator.overlay)
    }

    @Test
    fun leavingThePlaceEditorStopsAtTheScreenItStartedFrom() {
        val navigator = navigator(HomeKey, place, PlaceDetailsKey, PlacePickKey, PlaceSearchKey)

        navigator.leavePlaceEditor()

        assertEquals(listOf(HomeKey, place), navigator.backStack.toList())
    }

    @Test
    fun homeIsNeverPopped() {
        val navigator = navigator(HomeKey)

        navigator.pop()

        assertEquals(listOf<NavKey>(HomeKey), navigator.backStack.toList())
    }
}
