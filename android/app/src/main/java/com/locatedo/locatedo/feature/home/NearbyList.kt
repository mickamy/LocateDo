package com.locatedo.locatedo.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.common.CategoryStyle
import com.locatedo.locatedo.core.common.DistanceFormatting
import com.locatedo.locatedo.core.common.NearbyPlace
import com.locatedo.locatedo.core.model.Category
import java.util.UUID

private const val PREVIEW_TODOS = 2

// Closest places first, each with up to two open to-dos, as on the iOS home.
fun LazyListScope.nearbyItems(uiState: HomeUiState, onSelect: (UUID) -> Unit) {
    items(uiState.nearby, key = { it.entry.place.id }) { nearby ->
        NearbyRow(nearby = nearby, category = uiState.categories[nearby.entry.place.categoryId], onClick = { onSelect(nearby.entry.place.id) })
    }
}

@Composable
private fun NearbyRow(nearby: NearbyPlace, category: Category?, onClick: () -> Unit) {
    val place = nearby.entry.place
    val openTodos = nearby.entry.openTodos
    ListItem(
        headlineContent = { Text(place.name) },
        modifier = Modifier.clickable(onClick = onClick),
        supportingContent = if (openTodos.isEmpty()) {
            null
        } else {
            {
                Column {
                    for (todo in openTodos.take(PREVIEW_TODOS)) {
                        Text(todo.title, maxLines = 1)
                    }
                    val more = openTodos.size - PREVIEW_TODOS
                    if (more > 0) {
                        Text(pluralStringResource(R.plurals.home_more_todos, more, more))
                    }
                }
            }
        },
        leadingContent = {
            Icon(
                CategoryStyle.icon(category?.icon),
                contentDescription = null,
                tint = CategoryStyle.tint(category?.color),
            )
        },
        trailingContent = nearby.distanceMeters?.let { meters ->
            { Text(DistanceFormatting.string(meters), style = MaterialTheme.typography.bodyMedium) }
        },
    )
}
