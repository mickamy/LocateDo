package com.locatedo.locatedo.feature.place

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.locatedo.locatedo.ui.analytics.TrackScreen

private const val PICK_ZOOM = 16f

// Like the iOS picker: tap the map or a store on it to drop the pin there.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacePickScreen(
    viewModel: PlaceEditorViewModel,
    onLocationChosen: () -> Unit,
    onBack: () -> Unit,
) {
    TrackScreen(AnalyticsScreen.PLACE_PICKER)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preview = uiState.pickPreview
    val initial = preview.coordinate ?: uiState.draft.coordinate
    val cameraPositionState = rememberCameraPositionState {
        if (initial != null) {
            position = CameraPosition.fromLatLngZoom(initial.toLatLng(), PICK_ZOOM)
        }
    }

    LaunchedEffect(Unit) {
        if (initial == null) {
            viewModel.lastKnownCoordinate()?.let { coordinate ->
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(coordinate.toLatLng(), PICK_ZOOM))
            }
        } else if (preview.coordinate == null) {
            viewModel.previewPick(initial)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is PlaceEditorEvent.LocationChosen) {
                onLocationChosen()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.place_picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                onPOIClick = { poi -> viewModel.previewPick(Coordinate(poi.latLng.latitude, poi.latLng.longitude), name = poi.name) },
            ) {
                preview.coordinate?.let { coordinate ->
                    Marker(state = rememberUpdatedMarkerState(position = coordinate.toLatLng()))
                }
            }
            if (preview.coordinate != null) {
                PickCard(
                    preview = preview,
                    onConfirm = viewModel::confirmPick,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun PickCard(preview: PickPreview, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = preview.name ?: stringResource(R.string.place_picker_selected),
                style = MaterialTheme.typography.titleMedium,
            )
            preview.address?.let { address ->
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
