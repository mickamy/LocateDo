package com.locatedo.locatedo.screens.onboarding

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import com.locatedo.locatedo.R
import kotlinx.serialization.Serializable

// The kinds of store onboarding starts from, the same four the map offers nearby, and any other store.
enum class StoreKind(
    val key: String,
    @param:StringRes val nameRes: Int,
    val icon: ImageVector,
    @param:StringRes val todosTitleRes: Int?,
    @param:StringRes val storeTitleRes: Int?,
    val ideaRes: List<Int>,
) {
    GROCERY(
        "grocery",
        R.string.first_place_grocery_name,
        Icons.Filled.ShoppingCart,
        R.string.first_place_grocery_todos_title,
        R.string.first_place_grocery_store_title,
        listOf(
            R.string.first_place_grocery_idea_1,
            R.string.first_place_grocery_idea_2,
            R.string.first_place_grocery_idea_3,
            R.string.first_place_grocery_idea_4,
            R.string.first_place_grocery_idea_5,
        ),
    ),
    DRUGSTORE(
        "drugstore",
        R.string.first_place_drugstore_name,
        Icons.Filled.LocalPharmacy,
        R.string.first_place_drugstore_todos_title,
        R.string.first_place_drugstore_store_title,
        listOf(
            R.string.first_place_drugstore_idea_1,
            R.string.first_place_drugstore_idea_2,
            R.string.first_place_drugstore_idea_3,
            R.string.first_place_drugstore_idea_4,
            R.string.first_place_drugstore_idea_5,
        ),
    ),
    CONVENIENCE(
        "convenience",
        R.string.first_place_convenience_name,
        Icons.Filled.Storefront,
        R.string.first_place_convenience_todos_title,
        R.string.first_place_convenience_store_title,
        listOf(
            R.string.first_place_convenience_idea_1,
            R.string.first_place_convenience_idea_2,
            R.string.first_place_convenience_idea_3,
            R.string.first_place_convenience_idea_4,
            R.string.first_place_convenience_idea_5,
        ),
    ),
    HARDWARE(
        "hardware",
        R.string.first_place_hardware_name,
        Icons.Filled.Handyman,
        R.string.first_place_hardware_todos_title,
        R.string.first_place_hardware_store_title,
        listOf(
            R.string.first_place_hardware_idea_1,
            R.string.first_place_hardware_idea_2,
            R.string.first_place_hardware_idea_3,
            R.string.first_place_hardware_idea_4,
            R.string.first_place_hardware_idea_5,
        ),
    ),
    OTHER("other", R.string.first_place_other_name, Icons.Filled.ShoppingBag, null, null, emptyList()),
    ;

    @get:StringRes
    val labelRes: Int
        get() {
            if (this == OTHER) {
                return R.string.first_place_other_label
            }
            return nameRes
        }
}

// The store onboarding is about: one of the kinds, or any other store by what was typed for it.
@Serializable
data class FirstStore(val kind: StoreKind, val customName: String? = null) {
    fun name(resources: Resources): String = customName ?: resources.getString(kind.nameRes)

    fun todosTitle(resources: Resources): String =
        kind.todosTitleRes?.let(resources::getString) ?: resources.getString(R.string.place_editor_todos_title, name(resources))

    fun storeTitle(resources: Resources): String =
        kind.storeTitleRes?.let(resources::getString) ?: resources.getString(R.string.first_place_other_store_title, name(resources))


    fun ideas(resources: Resources): List<String> = kind.ideaRes.map(resources::getString)
}
