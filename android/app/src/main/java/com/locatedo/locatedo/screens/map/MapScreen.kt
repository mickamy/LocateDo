package com.locatedo.locatedo.screens.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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
import com.locatedo.locatedo.core.analytics.ScreenEntry
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.navigation.LocalNavigator
import com.locatedo.locatedo.navigation.PlaceKey
import com.locatedo.locatedo.screens.components.BackTopBar
import com.locatedo.locatedo.screens.components.CategoryMarker
import com.locatedo.locatedo.ui.analytics.TrackScreen
import com.locatedo.locatedo.ui.analytics.parameters
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val PLACE_ZOOM = 14f

data class MapUiState(val places: List<Place> = emptyList(), val categories: Map<UUID, Category> = emptyMap())

@HiltViewModel
class MapViewModel @Inject constructor(
    places: PlaceRepository,
    categories: CategoryRepository,
    private val location: LocationRepository,
) : ViewModel() {
    private val _cameraTargets = MutableSharedFlow<Coordinate>()

    val uiState: StateFlow<MapUiState> = combine(places.observeAll(), categories.observeAll()) { places, categories ->
        MapUiState(places = places, categories = categories.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MapUiState())

    // One-off camera moves, like the Google Maps "my location" button.
    val cameraTargets: SharedFlow<Coordinate> = _cameraTargets

    fun hasLocationPermission(): Boolean = location.hasForegroundPermission()

    fun locateMe() {
        viewModelScope.launch {
            _cameraTargets.emit(location.lastCoordinate() ?: return@launch)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

// Every place as a category marker; a marker opens the place. Finding yourself is the map's own button.
@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    TrackScreen(AnalyticsScreen.MAP, opening = ScreenEntry.HOME.parameters)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
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
    // The first fix jumps there, so the map never opens on the whole world; the button animates.
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

    Scaffold(topBar = { BackTopBar(stringResource(R.string.tab_map)) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                            navigator.push(PlaceKey(place.id.toString(), ScreenEntry.MAP_PIN))
                            true
                        },
                    ) {
                        CategoryMarker(icon = category?.icon, color = category?.color)
                    }
                }
            }
            FloatingActionButton(
                onClick = {
                    if (hasLocationPermission) {
                        viewModel.locateMe()
                    } else {
                        requestPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
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
