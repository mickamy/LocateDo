package com.locatedo.locatedo.core.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Work
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.Category

// Maps the app-defined `icon` / `color` keys shared with iOS and the server to Material symbols and colors.
object CategoryStyle {
    val icons: List<String> = listOf(
        "cart", "bag", "fork_knife", "cup", "cross", "dumbbell", "book", "briefcase",
        "building", "house", "car", "fuel", "gift", "pawprint", "leaf", "mappin",
    )

    val colors: List<String> = listOf("blue", "green", "orange", "red", "pink", "purple", "teal", "yellow", "brown", "gray")

    private val symbols: Map<String, ImageVector> = mapOf(
        "cart" to Icons.Filled.ShoppingCart,
        "bag" to Icons.Filled.ShoppingBag,
        "fork_knife" to Icons.Filled.Restaurant,
        "cup" to Icons.Filled.LocalCafe,
        "cross" to Icons.Filled.LocalHospital,
        "dumbbell" to Icons.Filled.FitnessCenter,
        "book" to Icons.Filled.Book,
        "briefcase" to Icons.Filled.Work,
        "building" to Icons.Filled.Business,
        "house" to Icons.Filled.Home,
        "car" to Icons.Filled.DirectionsCar,
        "fuel" to Icons.Filled.LocalGasStation,
        "gift" to Icons.Filled.CardGiftcard,
        "pawprint" to Icons.Filled.Pets,
        "leaf" to Icons.Filled.Eco,
        "mappin" to Icons.Filled.Place,
    )

    private val tints: Map<String, Color> = mapOf(
        "blue" to Color(0xFF1E88E5),
        "green" to Color(0xFF43A047),
        "orange" to Color(0xFFFB8C00),
        "red" to Color(0xFFE53935),
        "pink" to Color(0xFFD81B60),
        "purple" to Color(0xFF8E24AA),
        "teal" to Color(0xFF00897B),
        "yellow" to Color(0xFFFDD835),
        "brown" to Color(0xFF6D4C41),
        "gray" to Color(0xFF757575),
    )

    // Hues for the stock map marker; gray has no hue, so it stays the marker's default red.
    private val hues: Map<String, Float> = mapOf(
        "blue" to BitmapDescriptorFactory.HUE_AZURE,
        "green" to BitmapDescriptorFactory.HUE_GREEN,
        "orange" to BitmapDescriptorFactory.HUE_ORANGE,
        "red" to BitmapDescriptorFactory.HUE_RED,
        "pink" to BitmapDescriptorFactory.HUE_ROSE,
        "purple" to BitmapDescriptorFactory.HUE_VIOLET,
        "teal" to BitmapDescriptorFactory.HUE_CYAN,
        "yellow" to BitmapDescriptorFactory.HUE_YELLOW,
        "brown" to BitmapDescriptorFactory.HUE_ORANGE,
        "gray" to BitmapDescriptorFactory.HUE_RED,
    )

    fun icon(key: String?): ImageVector = symbols[key] ?: Icons.Filled.Place

    fun tint(key: String?): Color = tints[key] ?: tints.getValue("gray")

    fun markerHue(key: String?): Float = hues[key] ?: BitmapDescriptorFactory.HUE_RED
}

// Built-in categories are named by the translations; custom ones carry their own name.
@Composable
fun categoryName(category: Category?): String {
    if (category == null) {
        return stringResource(R.string.category_none)
    }
    category.name?.let { return it }
    return category.builtin?.let { builtinCategoryName(it) } ?: ""
}

@Composable
fun builtinCategoryName(builtin: BuiltinCategory): String = when (builtin) {
    BuiltinCategory.SHOPPING -> stringResource(R.string.category_shopping)
    BuiltinCategory.WORK -> stringResource(R.string.category_work)
    BuiltinCategory.LIFE -> stringResource(R.string.category_life)
    BuiltinCategory.OTHER -> stringResource(R.string.category_other)
}

@Composable
fun categoryColorName(key: String): String = when (key) {
    "blue" -> stringResource(R.string.category_color_blue)
    "green" -> stringResource(R.string.category_color_green)
    "orange" -> stringResource(R.string.category_color_orange)
    "red" -> stringResource(R.string.category_color_red)
    "pink" -> stringResource(R.string.category_color_pink)
    "purple" -> stringResource(R.string.category_color_purple)
    "teal" -> stringResource(R.string.category_color_teal)
    "yellow" -> stringResource(R.string.category_color_yellow)
    "brown" -> stringResource(R.string.category_color_brown)
    else -> stringResource(R.string.category_color_gray)
}
