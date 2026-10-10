package com.locatedo.locatedo.screens.todos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.screens.components.AssigneeChoice
import com.locatedo.locatedo.screens.components.assigneeChoices
import com.locatedo.locatedo.ui.analytics.TrackScreen
import java.util.UUID

// Google Maps' "save to list" sheet: one field, the place (or a new one), who it is for when shared, and save.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditorSheet(viewModel: TodoEditorViewModel, onDismiss: () -> Unit, onNewPlace: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = uiState.draft
    TrackScreen(AnalyticsScreen.TODO_EDITOR, mapOf(AnalyticsParameter.MODE to if (draft.isEditing) "edit" else "add"))
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (!draft.isEditing) {
            focusRequester.requestFocus()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val title = if (draft.isEditing) R.string.todo_editor_edit_title else R.string.todo_editor_title
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = draft.title,
                onValueChange = viewModel::setTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.todo_editor_title_label)) },
                placeholder = { Text(stringResource(R.string.todo_editor_title_placeholder)) },
                singleLine = true,
            )
            PlacePicker(places = uiState.places, selectedId = draft.placeId, onSelect = viewModel::setPlace, onNewPlace = onNewPlace)
            val assignees = assigneeChoices(uiState.members)
            if (assignees.isNotEmpty()) {
                AssigneePicker(choices = assignees, selectedId = draft.assigneeId, onSelect = viewModel::setAssignee)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (draft.isEditing) {
                    TextButton(onClick = viewModel::delete) {
                        Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                }
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

// The places, then "New place…", which adds one without leaving what was typed.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacePicker(places: List<Place>, selectedId: UUID?, onSelect: (UUID) -> Unit, onNewPlace: () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = isExpanded, onExpandedChange = { isExpanded = it }) {
        OutlinedTextField(
            value = places.firstOrNull { it.id == selectedId }?.name.orEmpty(),
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text(stringResource(R.string.todo_editor_place_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
        )
        ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            for (place in places) {
                DropdownMenuItem(
                    text = { Text(place.name) },
                    onClick = {
                        onSelect(place.id)
                        isExpanded = false
                    },
                )
            }
            if (places.isNotEmpty()) {
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.todo_editor_new_place)) },
                onClick = {
                    isExpanded = false
                    onNewPlace()
                },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssigneePicker(choices: List<AssigneeChoice>, selectedId: UUID?, onSelect: (UUID?) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = isExpanded, onExpandedChange = { isExpanded = it }) {
        OutlinedTextField(
            value = choices.firstOrNull { it.userId == selectedId }?.name.orEmpty(),
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text(stringResource(R.string.todo_assignee_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
        )
        ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            for (choice in choices) {
                DropdownMenuItem(
                    text = { Text(choice.name) },
                    onClick = {
                        onSelect(choice.userId)
                        isExpanded = false
                    },
                )
            }
        }
    }
}
