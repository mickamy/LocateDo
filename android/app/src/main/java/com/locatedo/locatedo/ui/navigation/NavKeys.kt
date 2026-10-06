package com.locatedo.locatedo.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data object TodosKey : NavKey

@Serializable
data object SettingsKey : NavKey
