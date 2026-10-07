package com.locatedo.locatedo.feature.home

import android.Manifest
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.common.SystemSettings
import com.locatedo.locatedo.core.common.zoomForRadius
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.feature.onboarding.AlwaysLocationSheet
import com.locatedo.locatedo.feature.todos.TodoEditorSheet
import com.locatedo.locatedo.ui.analytics.TrackScreen
import com.locatedo.locatedo.ui.components.CategoryMarker
import java.util.UUID

private const val PLACE_ZOOM = 14f
private val sheetPeekHeight = 96.dp
private val emptyPeekHeight = 260.dp
private val detailPeekHeight = 360.dp
private val permissionBannerPeekHeight = 104.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddPlace: () -> Unit,
    onEditPlace: (UUID) -> Unit,
    onOpenSharing: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    TrackScreen(AnalyticsScreen.HOME)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isExplainingAlwaysLocation by viewModel.isExplainingAlwaysLocation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val selected = uiState.selected
    var hasLocationPermission by remember { mutableStateOf(viewModel.hasLocationPermission()) }
    var placeToDelete by remember { mutableStateOf<Place?>(null) }
    var isAddingTodo by remember { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocationPermission = viewModel.hasLocationPermission()
        if (hasLocationPermission) {
            viewModel.locateMe()
        }
    }
    val cameraPositionState = rememberCameraPositionState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }
    BackHandler(enabled = selected != null) {
        viewModel.clearSelection()
    }
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            viewModel.locateMe()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.cameraTargets.collect { target ->
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude, target.longitude), PLACE_ZOOM))
        }
    }
    LaunchedEffect(selected?.place?.id) {
        val place = selected?.place ?: return@LaunchedEffect
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), zoomForRadius(place.radiusMeters)),
        )
    }

    // The collapsed sheet shows one summary line, the whole empty state, or the top of a place's details, with room
    // for the permission banner above the first two.
    var peekHeight = when {
        selected != null -> detailPeekHeight
        !uiState.isLoading && uiState.places.isEmpty() -> emptyPeekHeight
        else -> sheetPeekHeight
    }
    if (selected == null && uiState.permissionBanner != null) {
        peekHeight += permissionBannerPeekHeight
    }

    BottomSheetScaffold(
        scaffoldState = rememberBottomSheetScaffoldState(),
        sheetPeekHeight = peekHeight,
        sheetContent = {
            if (selected != null) {
                PlaceDetailSheet(
                    detail = selected,
                    onClose = viewModel::clearSelection,
                    onAddTodo = { isAddingTodo = true },
                    onEdit = { onEditPlace(selected.place.id) },
                    onDelete = { placeToDelete = selected.place },
                    onToggleTodo = viewModel::setTodoCompleted,
                    onDeleteTodo = viewModel::deleteTodo,
                    members = uiState.members,
                    onAssignTodo = viewModel::setAssignee,
                )
            } else {
                HomeSheet(
                    uiState = uiState,
                    onAddPlace = onAddPlace,
                    onSelect = viewModel::select,
                    onPermissionBanner = { banner ->
                        viewModel.permissionBannerTapped(banner)
                        when (banner) {
                            PermissionBanner.LOCATION_ALWAYS -> Unit
                            PermissionBanner.LOCATION_DENIED -> SystemSettings.openAppDetails(context)
                            PermissionBanner.NOTIFICATIONS -> SystemSettings.openNotifications(context)
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
                onMapClick = { viewModel.clearSelection() },
            ) {
                for (entry in uiState.places) {
                    val place = entry.place
                    val category = uiState.categories[place.categoryId]
                    MarkerComposable(
                        category?.icon.orEmpty(),
                        category?.color.orEmpty(),
                        state = rememberUpdatedMarkerState(position = LatLng(place.latitude, place.longitude)),
                        title = place.name,
                        anchor = Offset(0.5f, 0.5f),
                        onClick = {
                            viewModel.select(place.id)
                            true
                        },
                    ) {
                        CategoryMarker(icon = category?.icon, color = category?.color)
                    }
                }
                selected?.place?.let { place ->
                    Circle(
                        center = LatLng(place.latitude, place.longitude),
                        radius = place.radiusMeters,
                        fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        strokeColor = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2f,
                    )
                }
            }
            if (selected == null) {
                SearchBar(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter),
                    onSearch = onAddPlace,
                    onAccount = onOpenSharing,
                )
            }
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

    placeToDelete?.let { place ->
        AlertDialog(
            onDismissRequest = { placeToDelete = null },
            title = { Text(stringResource(R.string.place_detail_delete_confirm_title, place.name)) },
            text = { Text(stringResource(R.string.place_detail_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlace(place.id)
                        placeToDelete = null
                    },
                ) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { placeToDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    if (isAddingTodo && selected != null) {
        TodoEditorSheet(placeId = selected.place.id, onDismiss = { isAddingTodo = false })
    }
    if (isExplainingAlwaysLocation) {
        AlwaysLocationSheet(
            onAnswer = viewModel::alwaysLocationAnswered,
            onDismiss = viewModel::dismissAlwaysLocation,
        )
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
                .clickable(onClick = onSearch, role = Role.Button)
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
                contentDescription = stringResource(R.string.sharing_title),
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onAccount, role = Role.Button),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HomeSheet(
    uiState: HomeUiState,
    onAddPlace: () -> Unit,
    onSelect: (UUID) -> Unit,
    onPermissionBanner: (PermissionBanner) -> Unit,
) {
    if (uiState.isLoading) {
        Spacer(Modifier.height(sheetPeekHeight))
        return
    }
    Column {
        uiState.permissionBanner?.let { banner ->
            PermissionBannerCard(banner = banner, onClick = { onPermissionBanner(banner) })
        }
        if (uiState.places.isEmpty()) {
            EmptyPlaces(onAddPlace = onAddPlace)
        } else {
            NearbyList(uiState = uiState, onSelect = onSelect)
        }
    }
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
