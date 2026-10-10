package com.locatedo.locatedo.feature.place

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.common.CategoryStyle
import com.locatedo.locatedo.core.common.categoryName
import com.locatedo.locatedo.core.model.Category

// Adding a place, second screen: the category as colored tiles, preselected from the picked store when possible.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceCategoryScreen(
    viewModel: PlaceEditorViewModel,
    onNext: () -> Unit,
    onManageCategories: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.stepShown(NewPlaceStep.CATEGORY)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = uiState.draft

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.place_editor_category_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = onNext) {
                        Text(stringResource(R.string.place_editor_next))
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (draft.suggestion != null && !draft.hasChosenCategory) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.place_editor_category_suggested),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(uiState.categories, key = { it.id }) { category ->
                CategoryTile(
                    category = category,
                    isSelected = draft.categoryId == category.id,
                    onClick = { viewModel.setCategory(category.id) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = onManageCategories) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text(stringResource(R.string.category_manage), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(category: Category, isSelected: Boolean, onClick: () -> Unit) {
    val tint = CategoryStyle.tint(category.color)
    val shape = RoundedCornerShape(16.dp)
    var border: BorderStroke? = null
    var background = MaterialTheme.colorScheme.surfaceContainer
    if (isSelected) {
        border = BorderStroke(2.dp, tint)
        background = tint.copy(alpha = 0.15f)
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.RadioButton
                selected = isSelected
            },
        shape = shape,
        color = background,
        border = border,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(52.dp).background(tint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CategoryStyle.icon(category.icon), contentDescription = null, tint = Color.White)
            }
            Text(
                text = categoryName(category),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
