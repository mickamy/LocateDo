package com.locatedo.locatedo.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.common.CategoryStyle
import com.locatedo.locatedo.core.common.DistanceFormatting
import com.locatedo.locatedo.core.common.NearbyPlace
import com.locatedo.locatedo.core.model.Category
import java.util.UUID

private const val PREVIEW_TODOS = 2

// The sheet's list: closest places first, each with up to two open to-dos, like the iOS home.
@Composable
fun NearbyList(uiState: HomeUiState, onSelect: (UUID) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = pluralStringResource(R.plurals.home_open_summary, uiState.openTodoCount, uiState.openTodoCount),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(uiState.nearby, key = { it.entry.place.id }) { nearby ->
            NearbyRow(nearby = nearby, category = uiState.categories[nearby.entry.place.categoryId], onClick = { onSelect(nearby.entry.place.id) })
        }
    }
}

@Composable
private fun NearbyRow(nearby: NearbyPlace, category: Category?, onClick: () -> Unit) {
    val place = nearby.entry.place
    val openTodos = nearby.entry.openTodos
    ListItem(
        headlineContent = { Text(place.name) },
        modifier = Modifier.clickable(onClick = onClick),
        supportingContent = {
            Column {
                nearby.distanceMeters?.let { meters ->
                    Text(stringResource(R.string.place_detail_distance, DistanceFormatting.string(meters)))
                }
                for (todo in openTodos.take(PREVIEW_TODOS)) {
                    Text(todo.title, maxLines = 1)
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
        trailingContent = {
            if (openTodos.isNotEmpty()) {
                Text(openTodos.size.toString(), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
