package com.locatedo.locatedo.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.common.SystemSettings
import com.locatedo.locatedo.feature.onboarding.AlwaysLocationSheet
import com.locatedo.locatedo.feature.onboarding.rememberPreciseLocationRequest
import com.locatedo.locatedo.ui.analytics.TrackScreen
import java.util.UUID

private const val PREVIEW_ZOOM = 14f
private val FAB_CLEARANCE = 88.dp

// Mirrors the iOS home: what is waiting where, closest first; the map has its own tab.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddPlace: () -> Unit,
    onOpenPlace: (UUID) -> Unit,
    onOpenSharing: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    TrackScreen(AnalyticsScreen.HOME)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isExplainingAlwaysLocation by viewModel.isExplainingAlwaysLocation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestPreciseLocation = rememberPreciseLocationRequest(
        hasRequested = uiState.hasRequestedPreciseLocation,
        onRequested = viewModel::preciseLocationRequested,
    )

    val listState = rememberLazyListState()
    val isAtTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
        viewModel.refreshLocation()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_home)) },
                navigationIcon = {
                    IconButton(onClick = onOpenSharing) {
                        Icon(Icons.Filled.Group, contentDescription = stringResource(R.string.sharing_title))
                    }
                },
            )
        },
        // Adding a place is the screen's main action, as a FAB that shrinks to its icon once the list scrolls.
        floatingActionButton = {
            if (!uiState.isLoading && uiState.places.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddPlace,
                    expanded = isAtTop,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.home_add_place)) },
                )
            }
        },
    ) { padding ->
        val content: @Composable () -> Unit = {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize())
                uiState.places.isEmpty() -> EmptyPlaces(onAddPlace = onAddPlace)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = FAB_CLEARANCE),
                ) {
                    uiState.permissionBanner?.let { banner ->
                        item {
                            PermissionBannerCard(
                                banner = banner,
                                onClick = {
                                    viewModel.permissionBannerTapped(banner)
                                    when (banner) {
                                        PermissionBanner.LOCATION_ALWAYS -> Unit
                                        PermissionBanner.LOCATION_DENIED -> SystemSettings.openAppDetails(context)
                                        PermissionBanner.PRECISE_LOCATION -> requestPreciseLocation()
                                        PermissionBanner.NOTIFICATIONS -> SystemSettings.openNotifications(context)
                                    }
                                },
                            )
                        }
                    }
                    item {
                        MapPreview(uiState)
                    }
                    if (uiState.openTodoCount > 0) {
                        item {
                            Text(
                                text = pluralStringResource(R.plurals.home_open_summary, uiState.openTodoCount, uiState.openTodoCount),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    nearbyItems(uiState = uiState, onSelect = onOpenPlace)
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isSignedIn) {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    content()
                }
            } else {
                content()
            }
        }
    }

    if (isExplainingAlwaysLocation) {
        AlwaysLocationSheet(
            onAnswer = viewModel::alwaysLocationAnswered,
            onDismiss = viewModel::dismissAlwaysLocation,
        )
    }
}

// Where you are at a glance, centered on the nearest place until the location is known.
@Composable
private fun MapPreview(uiState: HomeUiState) {
    val nearest = uiState.nearby.firstOrNull()?.entry?.place
    val center = uiState.here?.let { LatLng(it.latitude, it.longitude) }
        ?: nearest?.let { LatLng(it.latitude, it.longitude) }
    val cameraPositionState = rememberCameraPositionState()
    LaunchedEffect(center) {
        if (center != null) {
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(center, PREVIEW_ZOOM))
        }
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
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp)),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = uiState.here != null),
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
        )
    }
}

@Composable
private fun EmptyPlaces(onAddPlace: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Place,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleLarge)
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
    }
}
