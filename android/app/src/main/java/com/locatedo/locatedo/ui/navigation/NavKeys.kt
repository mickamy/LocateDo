package com.locatedo.locatedo.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.locatedo.locatedo.core.billing.PaywallTrigger
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data object MapKey : NavKey

@Serializable
data object TodosKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object CategoriesKey : NavKey

@Serializable
data class PlaceDetailKey(val placeId: String) : NavKey

@Serializable
data object AccountKey : NavKey

@Serializable
data object SharingKey : NavKey

@Serializable
data class PaywallKey(val trigger: PaywallTrigger) : NavKey

// A token arrives with an opened invite link; from the sharing screen the field starts empty.
@Serializable
data class AcceptInviteKey(val token: String? = null) : NavKey

// The add / edit flow: the map (with the search on top), then the form. Its draft lives in PlaceEditorViewModel.
@Serializable
data object PlaceSearchKey : NavKey

@Serializable
data object PlacePickKey : NavKey

@Serializable
data object PlaceEditorKey : NavKey

// A new place goes on to pick its category, then its to-dos.
@Serializable
data object PlaceCategoryKey : NavKey

@Serializable
data object PlaceTodosKey : NavKey
