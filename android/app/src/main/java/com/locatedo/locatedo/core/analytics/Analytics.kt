package com.locatedo.locatedo.core.analytics

import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.FreeLimit

enum class EditorMode(val key: String) {
    NEW("new"),
    EDIT("edit"),
}

typealias AnalyticsParameters = Map<AnalyticsParameter, Any>

interface Analytics {
    fun log(event: AnalyticsEvent, parameters: AnalyticsParameters = emptyMap())
    fun logScreen(screen: AnalyticsScreen, parameters: AnalyticsParameters = emptyMap())
    fun setUserProperty(property: AnalyticsUserProperty, value: String?)
    suspend fun appInstanceId(): String?
}

object NoOpAnalytics : Analytics {
    override fun log(event: AnalyticsEvent, parameters: AnalyticsParameters) = Unit

    override fun logScreen(screen: AnalyticsScreen, parameters: AnalyticsParameters) = Unit

    override fun setUserProperty(property: AnalyticsUserProperty, value: String?) = Unit

    override suspend fun appInstanceId(): String? = null
}

// GA4 has no boolean type, so flags go out as 0 / 1.
fun AnalyticsParameters.wireValues(): Map<String, Any> = entries.associate { (parameter, value) ->
    val wire: Any = when (value) {
        is Boolean -> if (value) 1L else 0L
        is Int -> value.toLong()
        is Long, is Double, is String -> value
        else -> value.toString()
    }
    parameter.key to wire
}

fun analyticsCategory(category: Category?): String {
    if (category == null) {
        return "none"
    }
    return category.builtin?.key ?: "custom"
}

val FreeLimit.analyticsKind: String
    get() = when (this) {
        FreeLimit.PLACES -> "place"
        FreeLimit.OPEN_TODOS -> "todo"
    }
