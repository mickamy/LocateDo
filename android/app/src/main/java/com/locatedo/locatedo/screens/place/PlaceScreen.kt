package com.locatedo.locatedo.screens.place

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.model.TodoDeletionVia
import com.locatedo.locatedo.navigation.LocalNavigator
import com.locatedo.locatedo.navigation.Overlay
import com.locatedo.locatedo.screens.components.AddButtonClearance
import com.locatedo.locatedo.screens.components.BackTopBar
import com.locatedo.locatedo.screens.components.CategoryMarker
import com.locatedo.locatedo.screens.components.DeleteCompletedButton
import com.locatedo.locatedo.screens.components.Refreshable
import com.locatedo.locatedo.screens.components.rememberTodoEditing
import com.locatedo.locatedo.screens.todos.TodoRow
import com.locatedo.locatedo.screens.todos.TodoRowHandler
import com.locatedo.locatedo.ui.analytics.TrackScreen
import java.time.Duration
import java.util.UUID

// The area on a map, what the place is, then its to-dos; checked-off ones fold away below.
@Composable
fun PlaceScreen(
    placeId: UUID,
    onEdit: () -> Unit,
    viewModel: PlaceViewModel = hiltViewModel<PlaceViewModel, PlaceViewModel.Factory>(
        key = placeId.toString(),
        creationCallback = { factory -> factory.create(placeId) },
    ),
) {
    TrackScreen(AnalyticsScreen.PLACE_DETAIL)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val info = uiState.info
    val navigator = LocalNavigator.current
    val openEditor = rememberTodoEditing()
    val deleteTitle = info?.let { stringResource(R.string.place_detail_delete_confirm_title, it.place.name) }.orEmpty()
    val deleteMessage = stringResource(R.string.place_detail_delete_confirm_message)
    val deleteLabel = stringResource(R.string.common_delete)
    val handler = object : TodoRowHandler {
        override fun toggle(todo: Todo) = viewModel.toggle(todo)

        override fun edit(todo: Todo) = openEditor(todo.id, true)

        override fun delete(todo: Todo, via: TodoDeletionVia) = viewModel.delete(todo, via)

        override fun assign(todo: Todo, userId: UUID?) = viewModel.assign(todo, userId)
    }

    // Gone, here or for the whole household: back to where it was opened from.
    LaunchedEffect(uiState.isLoading, info == null) {
        if (!uiState.isLoading && info == null) {
            navigator.pop()
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(title = info?.place?.name.orEmpty()) {
                PlaceMenu(
                    onEdit = onEdit,
                    onDelete = {
                        navigator.present(Overlay.Confirmation(deleteTitle, deleteMessage, deleteLabel, viewModel::deletePlace))
                    },
                    onSimulateArrival = viewModel::simulateArrival,
                )
            }
        },
    ) { padding ->
        if (info == null) {
            return@Scaffold
        }
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Refreshable(isSignedIn = uiState.isSignedIn, isRefreshing = uiState.isRefreshing, onRefresh = viewModel::refresh) {
                var showsCompleted by rememberSaveable { mutableStateOf(false) }
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = AddButtonClearance)) {
                    item { AreaMap(info) }
                    item { Summary(info) }
                    item {
                        Text(
                            text = stringResource(R.string.place_detail_todos_label),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (info.openTodos.isEmpty() && info.completedTodos.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.place_detail_no_todos),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(info.openTodos, key = { it.id }) { todo ->
                        TodoRow(todo = todo, members = uiState.members, handler = handler)
                    }
                    if (info.completedTodos.isNotEmpty()) {
                        item {
                            CompletedHeader(
                                count = info.completedTodos.size,
                                isShared = uiState.members.size > 1,
                                isExpanded = showsCompleted,
                                onToggle = { showsCompleted = !showsCompleted },
                                onDeleteAll = viewModel::deleteCompleted,
                            )
                        }
                        if (showsCompleted) {
                            items(info.completedTodos, key = { it.id }) { todo ->
                                TodoRow(todo = todo, members = uiState.members, handler = handler)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AreaMap(info: PlaceInfo) {
    val place = info.place
    val center = LatLng(place.latitude, place.longitude)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, zoomForRadius(place.radiusMeters))
    }
    // An edit to the place moves the map along with it.
    LaunchedEffect(center, place.radiusMeters) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(center, zoomForRadius(place.radiusMeters))
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
                info.category?.icon.orEmpty(),
                info.category?.color.orEmpty(),
                state = rememberUpdatedMarkerState(position = center),
                anchor = Offset(0.5f, 0.5f),
            ) {
                CategoryMarker(icon = info.category?.icon, color = info.category?.color)
            }
        }
    }
}

@Composable
private fun Summary(info: PlaceInfo) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(CategoryStyle.icon(info.category?.icon), contentDescription = null, tint = CategoryStyle.tint(info.category?.color))
            Text(categoryName(info.category), style = MaterialTheme.typography.bodyMedium)
        }
        info.address?.let { address ->
            Text(address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        info.distanceMeters?.let { meters ->
            Text(
                text = stringResource(R.string.place_detail_distance, DistanceFormatting.string(meters)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompletedHeader(count: Int, isShared: Boolean, isExpanded: Boolean, onToggle: () -> Unit, onDeleteAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${stringResource(R.string.todo_completed_section)} ($count)",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DeleteCompletedButton(count = count, isShared = isShared, onConfirm = onDeleteAll)
        Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
    }
}

// Deleting is rare and cannot be undone, so it sits behind the menu with editing.
@Composable
private fun PlaceMenu(onEdit: () -> Unit, onDelete: () -> Unit, onSimulateArrival: (Duration) -> Unit) {
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
            if (BuildConfig.DEBUG_TOOLS) {
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
