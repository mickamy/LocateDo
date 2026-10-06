package com.locatedo.locatedo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.locatedo.locatedo.core.common.CategoryStyle

// The category's icon on a filled circle of its color.
@Composable
fun CategoryBadge(icon: String?, color: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(CategoryStyle.tint(color), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            CategoryStyle.icon(icon),
            contentDescription = null,
            modifier = Modifier.size(size * 0.55f),
            tint = Color.White,
        )
    }
}

// The badge with a white rim, drawn onto the map as the place's marker (anchored at its center).
@Composable
fun CategoryMarker(icon: String?, color: String?) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        CategoryBadge(icon = icon, color = color, size = 30.dp)
    }
}
