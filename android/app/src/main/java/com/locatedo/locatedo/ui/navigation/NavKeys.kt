package com.locatedo.locatedo.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data object TodosKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object CategoriesKey : NavKey

// The add / edit flow: search, pick on the map, then the form. Its draft lives in PlaceEditorViewModel.
@Serializable
data object PlaceSearchKey : NavKey

@Serializable
data object PlacePickKey : NavKey

@Serializable
data object PlaceEditorKey : NavKey
