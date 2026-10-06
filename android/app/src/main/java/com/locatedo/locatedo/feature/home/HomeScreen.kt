package com.locatedo.locatedo.feature.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.common.CategoryStyle

private const val PLACE_ZOOM = 14f
private val sheetPeekHeight = 96.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onAddPlace: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
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
    LaunchedEffect(Unit) {
        viewModel.cameraTargets.collect { target ->
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, PLACE_ZOOM))
        }
    }

    BottomSheetScaffold(
        scaffoldState = rememberBottomSheetScaffoldState(),
        sheetPeekHeight = sheetPeekHeight,
        sheetContent = {
            HomeSheet(uiState = uiState, onAddPlace = onAddPlace)
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
            ) {
                for (entry in uiState.places) {
                    val place = entry.place
                    val category = uiState.categories[place.categoryId]
                    Marker(
                        state = rememberUpdatedMarkerState(position = LatLng(place.latitude, place.longitude)),
                        title = place.name,
                        icon = BitmapDescriptorFactory.defaultMarker(CategoryStyle.markerHue(category?.color)),
                    )
                }
            }
            SearchBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                onSearch = onAddPlace,
                onAccount = {},
            )
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

// The floating bar is the way into adding a place, as the search bar is in Google Maps.
@Composable
private fun SearchBar(modifier: Modifier = Modifier, onSearch: () -> Unit, onAccount: () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onSearch)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.home_add_place),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.Filled.AccountCircle,
                contentDescription = stringResource(R.string.settings_account_title),
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onAccount),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HomeSheet(uiState: HomeUiState, onAddPlace: () -> Unit) {
    if (uiState.isLoading) {
        Spacer(Modifier.height(sheetPeekHeight))
        return
    }
    if (uiState.places.isEmpty()) {
        EmptyPlaces(onAddPlace = onAddPlace)
        return
    }
    Text(
        text = pluralStringResource(R.plurals.home_open_summary, uiState.openTodoCount, uiState.openTodoCount),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun EmptyPlaces(onAddPlace: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Place,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddPlace) {
            Text(stringResource(R.string.home_add_place))
        }
        Spacer(Modifier.height(16.dp))
    }
}
