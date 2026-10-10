package com.locatedo.locatedo.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.screens.components.BackTopBar
import com.locatedo.locatedo.screens.placeeditor.DraftTodos
import com.locatedo.locatedo.screens.placeeditor.NotificationPreview
import com.locatedo.locatedo.screens.placeeditor.PlaceEditorViewModel

// "Somewhere else" says what store it is first, a kind or a name, so the to-dos and the search that follow can use it.
@Composable
fun StoreNamePage(onNext: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val trimmed = name.trim()
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Scaffold(topBar = { BackTopBar("") }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PageTitle(stringResource(R.string.first_place_other_name_title))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("onboarding.storeName"),
                placeholder = { Text(stringResource(R.string.first_place_other_name_placeholder)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = {
                        if (trimmed.isNotEmpty()) {
                            onNext(trimmed)
                        }
                    },
                ),
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = { onNext(trimmed) }, modifier = Modifier.fillMaxWidth(), enabled = trimmed.isNotEmpty()) {
                Text(stringResource(R.string.place_editor_next))
            }
        }
    }
}

// What to do at the kind of store picked, under the notification it will make, with ideas to tap.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirstTodosPage(store: FirstStore, placeEditor: PlaceEditorViewModel, onNext: () -> Unit) {
    val uiState by placeEditor.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val draft = uiState.draft
    // Ideas count against the free limit like typed rows, including one still being typed, so none is dropped on save.
    var typing = 0
    if (draft.todoDraft.isNotBlank()) {
        typing = 1
    }
    val remaining = uiState.remainingOpenTodos
    val hasRoom = remaining == null || draft.todos.size + typing < remaining
    val ideasLeft = store.ideas(resources).filter { it !in draft.todos }

    Scaffold(
        topBar = {
            BackTopBar("") {
                TextButton(onClick = onNext, modifier = Modifier.testTag("onboarding.todosNext")) {
                    Text(stringResource(R.string.place_editor_next))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NotificationPreview(
                placeName = store.name(resources),
                titles = draft.todos + draft.todoDraft,
                example = firstTodosExample(store),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = store.todosTitle(resources),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            DraftTodos(
                todos = draft.todos,
                todoDraft = draft.todoDraft,
                todosLeft = uiState.todosLeft,
                placeholder = stringResource(R.string.place_editor_todo_example_shopping),
                onTodoChange = placeEditor::setTodo,
                onRemove = placeEditor::removeTodo,
                onDraftChange = placeEditor::setTodoDraft,
                onAddDraft = placeEditor::addTodoDraft,
            )
            if (hasRoom && ideasLeft.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.first_place_ideas_title),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (idea in ideasLeft) {
                        AssistChip(
                            onClick = { placeEditor.addTodo(idea) },
                            label = { Text(idea) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                            colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}
