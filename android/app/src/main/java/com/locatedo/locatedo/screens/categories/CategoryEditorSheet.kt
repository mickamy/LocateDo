package com.locatedo.locatedo.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.analytics.EditorMode
import com.locatedo.locatedo.core.common.CategoryStyle
import com.locatedo.locatedo.core.common.builtinCategoryName
import com.locatedo.locatedo.core.common.categoryColorName
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.ui.analytics.TrackScreen
import com.locatedo.locatedo.screens.components.CategoryBadge

// Name, then the icon and color grids, with the badge previewing the result.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorSheet(
    category: Category?,
    onDismiss: () -> Unit,
    viewModel: CategoryEditorViewModel = hiltViewModel(),
) {
    val mode = if (category == null) EditorMode.NEW else EditorMode.EDIT
    TrackScreen(AnalyticsScreen.CATEGORY_EDITOR, mapOf(AnalyticsParameter.MODE to mode.key))
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val builtinTitle = category?.builtin?.let { builtinCategoryName(it) }

    LaunchedEffect(Unit) {
        viewModel.start(category, builtinTitle)
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                CategoryEditorEvent.Saved -> onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(if (draft.isEditing) R.string.category_edit_title else R.string.category_new_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryBadge(icon = draft.icon, color = draft.color, size = 44.dp)
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = viewModel::setName,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.category_name)) },
                    placeholder = { Text(stringResource(R.string.category_name_placeholder)) },
                    singleLine = true,
                )
            }
            SectionTitle(stringResource(R.string.category_icon))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (key in CategoryStyle.icons) {
                    IconChoice(key = key, isSelected = key == draft.icon, color = draft.color, onSelect = { viewModel.setIcon(key) })
                }
            }
            SectionTitle(stringResource(R.string.category_color))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (key in CategoryStyle.colors) {
                    ColorChoice(key = key, isSelected = key == draft.color, onSelect = { viewModel.setColor(key) })
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = viewModel::save, enabled = draft.canSave) {
                    Text(stringResource(R.string.common_save))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun IconChoice(key: String, isSelected: Boolean, color: String, onSelect: () -> Unit) {
    val tint = CategoryStyle.tint(color)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (isSelected) tint else MaterialTheme.colorScheme.surfaceVariant)
            .selectable(selected = isSelected, onClick = onSelect),
        contentAlignment = Alignment.Center,
    ) {
        Icon(CategoryStyle.icon(key), contentDescription = null, tint = if (isSelected) Color.White else tint)
    }
}

@Composable
private fun ColorChoice(key: String, isSelected: Boolean, onSelect: () -> Unit) {
    val tint = CategoryStyle.tint(key)
    val name = categoryColorName(key)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .selectable(selected = isSelected, onClick = onSelect)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(Modifier.size(44.dp).border(2.dp, tint, CircleShape))
        }
        Box(Modifier.size(32.dp).background(tint, CircleShape))
    }
}
