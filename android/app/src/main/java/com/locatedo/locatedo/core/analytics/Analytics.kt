package com.locatedo.locatedo.core.analytics

import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.FreeLimit

// Names are shared with iOS; both apps report into the same GA4 property.
enum class AnalyticsEvent(val key: String) {
    ALWAYS_PROMPT_ANSWERED("always_prompt_answered"),
    ARRIVAL_NOTIFIED("arrival_notified"),
    ARRIVAL_OPENED("arrival_opened"),
    ARRIVAL_SUPPRESSED("arrival_suppressed"),
    DAILY_STATE("daily_state"),
    LIMIT_REACHED("limit_reached"),
    LOCATION_AUTH_CHANGED("location_auth_changed"),
    NOTIFICATION_AUTH_CHANGED("notification_auth_changed"),
    ONBOARDING_COMPLETED("onboarding_completed"),
    PERMISSION_ACTION_TAPPED("permission_action_tapped"),
    PERMISSION_BANNER_TAPPED("permission_banner_tapped"),
    PLACE_ADDED("place_added"),
    PLACE_DELETED("place_deleted"),
    TODO_ADDED("todo_added"),
    TODO_COMPLETED("todo_completed"),
    SHARE_TAPPED("share_tapped"),
    INVITE_ACCEPTED("invite_accepted"),
    PAYWALL_SHOWN("paywall_shown"),
    PAYWALL_PURCHASED("paywall_purchased"),
    PAYWALL_DISMISSED("paywall_dismissed"),
    PURCHASE_STARTED("purchase_started"),
    PURCHASE_CANCELED("purchase_cancelled"),
    PURCHASE_FAILED("purchase_failed"),
    RESTORE_COMPLETED("restore_completed"),
    PROMOTIONS_PROMPT_SHOWN("promotions_prompt_shown"),
    PROMOTIONS_PROMPT_ANSWERED("promotions_prompt_answered"),
    PROMOTIONS_CONSENT_CHANGED("promotions_consent_changed"),
    CAMPAIGN_OPENED("campaign_opened"),
    COMPLETION_NOTICES_CHANGED("completion_notices_changed"),
    COMPLETION_NOTICE_OPENED("completion_notice_opened"),
}

enum class AnalyticsParameter(val key: String) {
    ACTION("action"),
    AGE_DAYS("age_days"),
    AGE_HOURS("age_hours"),
    ASSIGNED("assigned"),
    BATTERY_OPTIMIZATION_EXEMPT("battery_optimization_exempt"),
    CAMPAIGN_ID("campaign_id"),
    CATEGORY("category"),
    COUNT("count"),
    COMPLETED_TODO_COUNT_7D("completed_todo_count_7d"),
    CUSTOM_CATEGORY_COUNT("custom_category_count"),
    DAYS_SINCE_INSTALL("days_since_install"),
    DURATION_S("duration_s"),
    FROM("from"),
    HAS_URL("has_url"),
    HOUSEHOLD_MEMBERS("household_members"),
    KIND("kind"),
    LATENCY_S("latency_s"),
    LOCATION_AUTH("location_auth"),
    MODE("mode"),
    NOTIFICATION_AUTH("notification_auth"),
    OPEN_TODO_COUNT("open_todo_count"),
    OPEN_TODOS("open_todos"),
    PLACE_COUNT("place_count"),
    PLACE_OPEN_TODOS("place_open_todos"),
    PLACES_WITH_OPEN_TODOS("places_with_open_todos"),
    PLAN("plan"),
    PRECISE_LOCATION("precise_location"),
    PROMOTIONS_CONSENT("promotions_consent"),
    RADIUS_M("radius_m"),
    REASON("reason"),
    RESULT("result"),
    SIGNED_IN("signed_in"),
    SOURCE("source"),
    STEP("step"),
    TO("to"),
    TRIGGER("trigger"),
    VIA("via"),
}

enum class AnalyticsUserProperty(val key: String) {
    HOUSEHOLD_MEMBERS("household_members"),
    LOCATION_AUTH("location_auth"),
    OPEN_TODO_COUNT("open_todo_count"),
    PLACE_COUNT("place_count"),
    PLAN("plan"),
    PROMOTIONS_CONSENT("promotions_consent"),
    SIGNED_IN("signed_in"),
}

enum class AnalyticsScreen(val key: String) {
    ACCOUNT("account"),
    ACCOUNT_BENEFITS("account_benefits"),
    ACCEPT_INVITE("accept_invite"),
    ALWAYS_LOCATION_PROMPT("always_location_prompt"),
    CATEGORIES("categories"),
    CATEGORY_EDITOR("category_editor"),
    HOME("home"),
    MAP("map"),
    ONBOARDING("onboarding"),
    PAYWALL("paywall"),
    PLACE_DETAIL("place_detail"),
    PLACE_EDITOR("place_editor"),
    PLACE_PICKER("place_picker"),
    PLACE_SEARCH("place_search"),
    SETTINGS("settings"),
    SHARING("sharing"),
    SHARING_INTRO("sharing_intro"),
    TODO_EDITOR("todo_editor"),
    TODOS("todos"),
    UPDATE_REQUIRED("update_required"),
}

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
