package com.locatedo.locatedo.feature.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.ui.analytics.TrackScreen
import com.locatedo.locatedo.ui.components.CategoryMarker
import java.util.UUID

private const val PLACE_ZOOM = 14f

// Mirrors the iOS Map tab: every place as a category marker; a marker opens the place.
@Composable
fun MapScreen(
    onAddPlace: () -> Unit,
    onOpenPlace: (UUID) -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    TrackScreen(AnalyticsScreen.MAP)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var hasLocationPermission by remember { mutableStateOf(viewModel.hasLocationPermission()) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocationPermission = viewModel.hasLocationPermission()
        if (hasLocationPermission) {
            viewModel.locateMe()
        }
    }
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            viewModel.locateMe()
        }
    }
    // The first fix jumps there, so the tab never opens on the whole world; the button animates.
    LaunchedEffect(Unit) {
        var hasCentered = false
        viewModel.cameraTargets.collect { target ->
            val update = CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude, target.longitude), PLACE_ZOOM)
            if (hasCentered) {
                cameraPositionState.animate(update)
            } else {
                cameraPositionState.move(update)
                hasCentered = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
        ) {
            for (place in uiState.places) {
                val category = uiState.categories[place.categoryId]
                MarkerComposable(
                    category?.icon.orEmpty(),
                    category?.color.orEmpty(),
                    state = rememberUpdatedMarkerState(position = LatLng(place.latitude, place.longitude)),
                    title = place.name,
                    anchor = Offset(0.5f, 0.5f),
                    onClick = {
                        onOpenPlace(place.id)
                        true
                    },
                ) {
                    CategoryMarker(icon = category?.icon, color = category?.color)
                }
            }
        }
        if (!uiState.isLoading && uiState.places.isEmpty()) {
            EmptyCard(
                onAddPlace = onAddPlace,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        } else {
            FloatingActionButton(
                onClick = {
                    if (hasLocationPermission) {
                        viewModel.locateMe()
                    } else {
                        requestPermission.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.home_my_location))
            }
        }
    }
}

@Composable
private fun EmptyCard(onAddPlace: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.map_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onAddPlace) {
                Text(stringResource(R.string.home_add_place))
            }
        }
    }
}
