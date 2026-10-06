package com.locatedo.locatedo.feature.todos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.ui.components.AssigneeChoice
import com.locatedo.locatedo.ui.components.assigneeChoices
import java.util.UUID

// Google Maps' "save to list" sheet: one field, a picker when the place is not already known, and a save button.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditorSheet(
    placeId: UUID?,
    onDismiss: () -> Unit,
    viewModel: TodoEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = uiState.draft
    val focusRequester = remember { FocusRequester() }
    var limitMessage by remember { mutableStateOf<String?>(null) }
    val limitText = stringResource(R.string.paywall_reason_todos)

    LaunchedEffect(Unit) {
        viewModel.start(placeId)
        focusRequester.requestFocus()
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                TodoEditorEvent.Saved -> onDismiss()
                is TodoEditorEvent.LimitReached -> limitMessage = limitText
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.todo_editor_title), style = MaterialTheme.typography.titleLarge)
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
            if (!draft.isPlaceFixed) {
                PlacePicker(
                    places = uiState.places.map { it.id to it.name },
                    selectedId = draft.placeId,
                    onSelect = viewModel::setPlace,
                )
            }
            val assignees = assigneeChoices(uiState.members)
            if (assignees.isNotEmpty()) {
                AssigneePicker(choices = assignees, selectedId = draft.assigneeId, onSelect = viewModel::setAssignee)
            }
            limitMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacePicker(places: List<Pair<UUID, String>>, selectedId: UUID?, onSelect: (UUID) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = isExpanded, onExpandedChange = { isExpanded = it }) {
        OutlinedTextField(
            value = places.firstOrNull { it.first == selectedId }?.second ?: "",
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text(stringResource(R.string.todo_editor_place_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
        )
        ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            for ((id, name) in places) {
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(id)
                        isExpanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssigneePicker(choices: List<AssigneeChoice>, selectedId: UUID?, onSelect: (UUID?) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = isExpanded, onExpandedChange = { isExpanded = it }) {
        OutlinedTextField(
            value = choices.firstOrNull { it.userId == selectedId }?.name ?: "",
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
