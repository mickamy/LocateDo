package com.locatedo.locatedo.feature.place

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.locatedo.locatedo.BuildConfig
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.common.CategoryStyle
import com.locatedo.locatedo.core.common.DistanceFormatting
import com.locatedo.locatedo.core.common.categoryName
import com.locatedo.locatedo.core.common.zoomForRadius
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.model.TodoDeletionVia
import com.locatedo.locatedo.feature.todos.TodoEditorSheet
import com.locatedo.locatedo.feature.todos.TodoRow
import com.locatedo.locatedo.ui.analytics.TrackScreen
import com.locatedo.locatedo.ui.components.AssigneeChoice
import com.locatedo.locatedo.ui.components.CategoryMarker
import com.locatedo.locatedo.ui.components.assigneeChoices
import com.locatedo.locatedo.ui.components.todoDetail
import java.time.Duration
import java.util.UUID

// Mirrors the iOS place detail: the area on a map, what the place is, then its to-dos.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
    placeId: UUID,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: PlaceDetailViewModel = hiltViewModel<PlaceDetailViewModel, PlaceDetailViewModel.Factory>(
        key = placeId.toString(),
        creationCallback = { factory -> factory.create(placeId) },
    ),
) {
    TrackScreen(AnalyticsScreen.PLACE_DETAIL)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val detail = uiState.detail
    var isConfirmingDelete by remember { mutableStateOf(false) }
    var isAddingTodo by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<UUID?>(null) }

    LaunchedEffect(uiState.isLoading, detail == null) {
        if (!uiState.isLoading && detail == null) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.place?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_done))
                    }
                },
                actions = {
                    PlaceMenu(
                        onEdit = onEdit,
                        onDelete = { isConfirmingDelete = true },
                        showsDebugTools = BuildConfig.DEBUG_TOOLS,
                        onSimulateArrival = viewModel::simulateArrival,
                    )
                },
            )
        },
    ) { padding ->
        if (detail == null) {
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            AreaMap(detail)
            PlaceSummary(detail)
            Todos(
                detail = detail,
                members = uiState.members,
                onAddTodo = { isAddingTodo = true },
                onToggle = viewModel::setTodoCompleted,
                onEdit = { editingTodo = it },
                onDelete = viewModel::deleteTodo,
                onAssign = viewModel::setAssignee,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (isConfirmingDelete && detail != null) {
        AlertDialog(
            onDismissRequest = { isConfirmingDelete = false },
            title = { Text(stringResource(R.string.place_detail_delete_confirm_title, detail.place.name)) },
            text = { Text(stringResource(R.string.place_detail_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        isConfirmingDelete = false
                        viewModel.deletePlace()
                    },
                ) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmingDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    if (isAddingTodo) {
        TodoEditorSheet(placeId = placeId, onDismiss = { isAddingTodo = false })
    }
    editingTodo?.let { todoId ->
        TodoEditorSheet(placeId = null, onDismiss = { editingTodo = null }, editingId = todoId)
    }
}

@Composable
private fun AreaMap(detail: PlaceDetail) {
    val place = detail.place
    val center = LatLng(place.latitude, place.longitude)
    val cameraPositionState = rememberCameraPositionState(key = "${place.id}-${place.radiusMeters}") {
        position = CameraPosition.fromLatLngZoom(center, zoomForRadius(place.radiusMeters))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp)),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                compassEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false,
                rotationGesturesEnabled = false,
                scrollGesturesEnabled = false,
                tiltGesturesEnabled = false,
                zoomControlsEnabled = false,
                zoomGesturesEnabled = false,
            ),
        ) {
            Circle(
                center = center,
                radius = place.radiusMeters,
                fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                strokeColor = MaterialTheme.colorScheme.primary,
                strokeWidth = 2f,
            )
            MarkerComposable(
                detail.category?.icon.orEmpty(),
                detail.category?.color.orEmpty(),
                state = rememberUpdatedMarkerState(position = center),
                anchor = Offset(0.5f, 0.5f),
            ) {
                CategoryMarker(icon = detail.category?.icon, color = detail.category?.color)
            }
        }
    }
}

@Composable
private fun PlaceSummary(detail: PlaceDetail) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                CategoryStyle.icon(detail.category?.icon),
                contentDescription = null,
                tint = CategoryStyle.tint(detail.category?.color),
            )
            Text(categoryName(detail.category), style = MaterialTheme.typography.bodyMedium)
        }
        detail.address?.let { address ->
            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        detail.distanceMeters?.let { meters ->
            Text(
                text = stringResource(R.string.place_detail_distance, DistanceFormatting.string(meters)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Todos(
    detail: PlaceDetail,
    members: List<Membership>,
    onAddTodo: () -> Unit,
    onToggle: (UUID, Boolean) -> Unit,
    onEdit: (UUID) -> Unit,
    onDelete: (UUID, TodoDeletionVia) -> Unit,
    onAssign: (UUID, UUID?) -> Unit,
) {
    val assignees = assigneeChoices(members)
    Text(
        text = stringResource(R.string.place_detail_todos_label),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    for (todo in detail.openTodos) {
        TodoRow(
            todo = todo,
            onToggle = { onToggle(todo.id, it) },
            onEdit = { onEdit(todo.id) },
            onDelete = { onDelete(todo.id, it) },
            detail = todoDetail(members, todo),
            assignees = assignees,
            onAssign = { onAssign(todo.id, it) },
        )
    }
    ListItem(
        headlineContent = {
            Text(stringResource(R.string.place_detail_add_todo), color = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier.clickable(onClick = onAddTodo),
        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
    )
    if (detail.completedTodos.isNotEmpty()) {
        CompletedTodos(
            todos = detail.completedTodos,
            members = members,
            assignees = assignees,
            onToggle = onToggle,
            onEdit = onEdit,
            onDelete = onDelete,
            onAssign = onAssign,
        )
    }
}

// Deleting is rare and cannot be undone, so it sits behind the menu with editing, as on iOS.
@Composable
private fun PlaceMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showsDebugTools: Boolean,
    onSimulateArrival: (Duration) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isExpanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.common_more))
        }
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_edit)) },
                onClick = {
                    isExpanded = false
                    onEdit()
                },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.place_detail_delete)) },
                onClick = {
                    isExpanded = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            )
            // Debug and staging builds: an arrival now, or in 10 seconds to lock the screen first.
            if (showsDebugTools) {
                DropdownMenuItem(
                    text = { Text("Simulate arrival (debug)") },
                    onClick = {
                        isExpanded = false
                        onSimulateArrival(Duration.ZERO)
                    },
                    leadingIcon = { Icon(Icons.Filled.MyLocation, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("Simulate arrival in 10 s (debug)") },
                    onClick = {
                        isExpanded = false
                        onSimulateArrival(Duration.ofSeconds(10))
                    },
                    leadingIcon = { Icon(Icons.Filled.Timer, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun CompletedTodos(
    todos: List<Todo>,
    members: List<Membership>,
    assignees: List<AssigneeChoice>,
    onToggle: (UUID, Boolean) -> Unit,
    onEdit: (UUID) -> Unit,
    onDelete: (UUID, TodoDeletionVia) -> Unit,
    onAssign: (UUID, UUID?) -> Unit,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${stringResource(R.string.todo_completed_section)} (${todos.size})",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
    }
    if (isExpanded) {
        for (todo in todos) {
            TodoRow(
                todo = todo,
                onToggle = { onToggle(todo.id, it) },
                onEdit = { onEdit(todo.id) },
                onDelete = { onDelete(todo.id, it) },
                detail = todoDetail(members, todo),
                assignees = assignees,
                onAssign = { onAssign(todo.id, it) },
            )
        }
    }
}
