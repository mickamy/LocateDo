package com.locatedo.locatedo.core.analytics

import java.util.Collections
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

// The screen last shown on each presentation level: 0 is the screen on top of the back stack, 1 the overlay over it.
class ScreenStack {
    data class Entry(val screen: AnalyticsScreen, val parameters: AnalyticsParameters)

    private val entries = sortedMapOf<Int, Entry>()

    val screens: List<AnalyticsScreen>
        get() = entries.values.map { it.screen }

    fun appeared(entry: Entry, level: Int) {
        entries.keys.retainAll { it < level }
        entries[level] = entry
    }

    // The screen uncovered once the levels from `level` up close, or null when nothing up there was on screen.
    fun closed(level: Int): Entry? {
        if (entries.keys.none { it >= level }) {
            return null
        }
        entries.keys.retainAll { it < level }
        return entries.values.lastOrNull()
    }
}

// An overlay closing does not make the screen below appear again, so it is sent again here; otherwise GA4 would keep
// the overlay as the current screen.
@Singleton
class ScreenTracker @Inject constructor(private val analytics: Analytics) {
    private val stack = ScreenStack()
    private val opened: MutableSet<Any> = Collections.newSetFromMap(WeakHashMap())

    // `opening` goes only with the first appearance, not when the screen is come back to.
    @Synchronized
    fun appeared(screen: AnalyticsScreen, parameters: AnalyticsParameters, opening: AnalyticsParameters, level: Int) {
        analytics.logScreen(screen, opening + parameters)
        stack.appeared(ScreenStack.Entry(screen, parameters), level)
    }

    @Synchronized
    fun closed(level: Int) {
        val uncovered = stack.closed(level) ?: return
        analytics.logScreen(uncovered.screen, uncovered.parameters)
    }

    // True the first time a screen opened by this owner appears; an overlay keeps its instance while it steps aside.
    @Synchronized
    fun isFirstAppearance(owner: Any): Boolean = opened.add(owner)
}
