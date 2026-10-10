package com.locatedo.locatedo.screens.placeeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.places.PlaceDuplicateChoice
import com.locatedo.locatedo.navigation.LocalNavigator
import com.locatedo.locatedo.ui.analytics.TrackScreen
import com.locatedo.locatedo.ui.analytics.parameters

private const val PICK_ZOOM = 16f

// Where the place is: tap the map or a store on it, search, or use where you are, then confirm the pin. Editing a
// place opens it to move the pin.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacePickScreen(viewModel: PlaceEditorViewModel, onOpenSearch: () -> Unit) {
    TrackScreen(AnalyticsScreen.PLACE_PICKER, opening = viewModel.uiState.value.draft.entry?.parameters.orEmpty())
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val preview = uiState.pickPreview
    val initial = preview.coordinate ?: uiState.draft.coordinate
    val cameraPositionState = rememberCameraPositionState {
        if (initial != null) {
            position = CameraPosition.fromLatLngZoom(initial.toLatLng(), PICK_ZOOM)
        }
    }
    val nearbyKinds = listOf(
        stringResource(R.string.place_picker_nearby_grocery),
        stringResource(R.string.place_picker_nearby_drugstore),
        stringResource(R.string.place_picker_nearby_convenience),
        stringResource(R.string.place_picker_nearby_hardware),
    )

    LaunchedEffect(Unit) {
        if (initial == null) {
            viewModel.lastKnownCoordinate()?.let { coordinate ->
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(coordinate.toLatLng(), PICK_ZOOM))
            }
        } else if (preview.coordinate == null) {
            viewModel.previewPick(initial)
        }
    }
    // A pick from the search or from where you are may be off screen.
    LaunchedEffect(preview.coordinate) {
        val coordinate = preview.coordinate ?: return@LaunchedEffect
        cameraPositionState.animate(CameraUpdateFactory.newLatLng(coordinate.toLatLng()))
    }

    uiState.duplicate?.let { existing ->
        DuplicateDialog(name = existing.name, onAnswer = viewModel::answerDuplicate)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { SearchField(query = uiState.query, onClick = onOpenSearch) },
                navigationIcon = {
                    IconButton(onClick = navigator::pop) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_cancel))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
                onMapClick = { latLng -> viewModel.previewPick(Coordinate(latLng.latitude, latLng.longitude)) },
                onPOIClick = { poi ->
                    viewModel.previewPick(Coordinate(poi.latLng.latitude, poi.latLng.longitude), name = poi.name, placeId = poi.placeId)
                },
            ) {
                preview.coordinate?.let { coordinate ->
                    Marker(state = rememberUpdatedMarkerState(position = coordinate.toLatLng()))
                }
            }
            // The kinds of store people add most; each opens the search with that word.
            LazyRow(
                modifier = Modifier.align(Alignment.TopStart),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(nearbyKinds) { kind ->
                    SuggestionChip(
                        onClick = {
                            viewModel.setQuery(kind)
                            onOpenSearch()
                        },
                        label = { Text(kind) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SmallFloatingActionButton(onClick = viewModel::previewCurrentLocation) {
                    Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.place_picker_use_current_location))
                }
                if (preview.coordinate != null) {
                    PickCard(preview = preview, onConfirm = viewModel::confirmPick)
                }
            }
        }
    }
}

// Cancel, add anyway, then the suggested way out on the right, as Material dialogs order them.
@Composable
private fun DuplicateDialog(name: String, onAnswer: (PlaceDuplicateChoice) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(PlaceDuplicateChoice.CANCEL) },
        title = { Text(stringResource(R.string.place_duplicate_title, name)) },
        text = { Text(stringResource(R.string.place_duplicate_message)) },
        confirmButton = {
            TextButton(onClick = { onAnswer(PlaceDuplicateChoice.OPEN) }) {
                Text(stringResource(R.string.place_duplicate_open))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onAnswer(PlaceDuplicateChoice.CANCEL) }) {
                    Text(stringResource(R.string.common_cancel))
                }
                TextButton(onClick = { onAnswer(PlaceDuplicateChoice.ADD) }) {
                    Text(stringResource(R.string.place_duplicate_add))
                }
            }
        },
    )
}

// Looks like a search field and opens the full-screen search, as Google Maps does.
@Composable
private fun SearchField(query: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            var text = query
            var color = MaterialTheme.colorScheme.onSurface
            if (query.isBlank()) {
                text = stringResource(R.string.place_picker_search_placeholder)
                color = MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun PickCard(preview: PickPreview, onConfirm: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = preview.name ?: stringResource(R.string.place_picker_selected), style = MaterialTheme.typography.titleMedium)
            preview.address?.let { address ->
                Text(text = address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                enabled = preview.coordinate != null && !preview.isLoading,
            ) {
                Text(stringResource(R.string.place_picker_use_this_location))
            }
        }
    }
}

internal fun Coordinate.toLatLng(): LatLng = LatLng(latitude, longitude)
