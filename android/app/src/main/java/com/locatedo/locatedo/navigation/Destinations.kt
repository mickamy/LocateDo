package com.locatedo.locatedo.navigation

import androidx.navigation3.runtime.NavKey
import com.locatedo.locatedo.core.analytics.ScreenEntry
import com.locatedo.locatedo.core.billing.PaywallTrigger
import kotlinx.serialization.Serializable

// Home is the only root; everything else is pushed over it.
@Serializable
data object HomeKey : NavKey

@Serializable
data object MapKey : NavKey

// Where a screen was opened from rides on its key, for the analytics.
@Serializable
data class AllTodosKey(val entry: ScreenEntry) : NavKey

@Serializable
data class PlaceKey(val placeId: String, val entry: ScreenEntry, val rank: Int? = null) : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object CategoriesKey : NavKey

@Serializable
data object AccountKey : NavKey

@Serializable
data object SharingKey : NavKey

@Serializable
data class PaywallKey(val trigger: PaywallTrigger) : NavKey

// A token arrives with an opened invite link; from the sharing screen the field starts empty.
@Serializable
data class AcceptInviteKey(val token: String? = null) : NavKey

// Adding or editing a place. A new place goes map → details → category → to-dos; one added from the to-do sheet stops
// at the category; an existing one is edited in the details alone. The draft lives in PlaceEditorViewModel.
@Serializable
data object PlacePickKey : NavKey

@Serializable
data object PlaceSearchKey : NavKey

@Serializable
data object PlaceDetailsKey : NavKey

@Serializable
data object PlaceCategoryKey : NavKey

@Serializable
data object PlaceTodosKey : NavKey

val placeEditorKeys: Set<NavKey> = setOf(PlacePickKey, PlaceSearchKey, PlaceDetailsKey, PlaceCategoryKey, PlaceTodosKey)
