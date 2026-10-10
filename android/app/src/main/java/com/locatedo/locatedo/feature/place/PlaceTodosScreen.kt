package com.locatedo.locatedo.feature.place

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.notifications.arrivalNotificationText
import com.locatedo.locatedo.feature.onboarding.ArrivalNotificationCard

// Adding a place, last screen: what to do there, asked right before saving since reminders need it. Saving with none
// is fine.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceTodosScreen(viewModel: PlaceEditorViewModel, onSaved: () -> Unit, onBack: () -> Unit) {
    LaunchedEffect(Unit) {
        viewModel.stepShown(NewPlaceStep.TODOS)
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is PlaceEditorEvent.Saved && event.isNew) {
                onSaved()
            }
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = uiState.draft
    val builtin = uiState.categories.firstOrNull { it.id == draft.categoryId }?.builtin

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.place_editor_todos_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save) {
                        Text(stringResource(R.string.common_save))
                    }
                },
            )
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
            ArrivalNotificationPreview(
                placeName = draft.name.trim(),
                titles = draft.todos + draft.todoDraft,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.place_editor_todos_title, draft.name.trim()),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            NewPlaceTodos(
                todos = draft.todos,
                todoDraft = draft.todoDraft,
                todosLeft = uiState.todosLeft,
                placeholder = stringResource(todoPlaceholder(builtin)),
                onTodoChange = viewModel::setTodo,
                onRemove = viewModel::removeTodo,
                onDraftChange = viewModel::setTodoDraft,
                onAddDraft = viewModel::addTodoDraft,
            )
        }
    }
}

// Follows the typing, with the same wording and cut-off as the real notification.
@Composable
private fun ArrivalNotificationPreview(placeName: String, titles: List<String>, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val shown = titles.map(String::trim).filter(String::isNotEmpty)
    var message = stringResource(R.string.place_editor_preview_empty)
    if (shown.isNotEmpty()) {
        message = arrivalNotificationText(resources, shown)
    }
    ArrivalNotificationCard(
        title = placeName,
        message = message,
        modifier = modifier,
        isMessageMuted = shown.isEmpty(),
    )
}

private fun todoPlaceholder(builtin: BuiltinCategory?): Int = when (builtin) {
    BuiltinCategory.SHOPPING -> R.string.place_editor_todo_example_shopping
    BuiltinCategory.LIFE -> R.string.place_editor_todo_example_life
    BuiltinCategory.WORK -> R.string.place_editor_todo_example_work
    BuiltinCategory.OTHER, null -> R.string.place_editor_todo_placeholder
}

// Rows of plain text (a checkbox would look tickable), then "Add to-do", which turns into a field where Next adds the
// row and moves on to the next; an empty Next closes it again.
@Composable
private fun NewPlaceTodos(
    todos: List<String>,
    todoDraft: String,
    todosLeft: Int?,
    placeholder: String,
    onTodoChange: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
    onDraftChange: (String) -> Unit,
    onAddDraft: () -> Unit,
) {
    // Text left half-typed when going back comes back open, so it is never hidden yet saved.
    var isTyping by rememberSaveable { mutableStateOf(todos.isEmpty() || todoDraft.isNotEmpty()) }
    val focusRequester = remember { FocusRequester() }
    todos.forEachIndexed { index, title ->
        ListItem(
            headlineContent = {
                PlainField(value = title, onValueChange = { onTodoChange(index, it) })
            },
            trailingContent = {
                IconButton(onClick = { onRemove(index) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_delete))
                }
            },
        )
    }
    if (todosLeft != 0) {
        if (isTyping) {
            ListItem(
                headlineContent = {
                    PlainField(
                        value = todoDraft,
                        onValueChange = onDraftChange,
                        placeholder = placeholder,
                        modifier = Modifier.focusRequester(focusRequester),
                        onNext = {
                            if (todoDraft.isBlank()) {
                                onDraftChange("")
                                isTyping = false
                            } else {
                                onAddDraft()
                            }
                        },
                    )
                },
                    // Next does the same; the button says that the row can be added and another typed.
                trailingContent = {
                    IconButton(onClick = onAddDraft, enabled = todoDraft.isNotBlank()) {
                        Icon(
                            Icons.Filled.AddCircle,
                            contentDescription = stringResource(R.string.place_detail_add_todo),
                            tint = if (todoDraft.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        } else {
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.place_detail_add_todo), color = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable { isTyping = true },
                leadingContent = {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
            )
        }
    }
    Text(
        text = stringResource(R.string.place_editor_todos_footer),
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (todosLeft != null) {
        Text(
            text = pluralStringResource(R.plurals.place_editor_todos_remaining, todosLeft, todosLeft),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// A borderless field, so a row reads like a to-do rather than a form.
@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    onNext: (() -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = if (onNext == null) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext?.invoke() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}
