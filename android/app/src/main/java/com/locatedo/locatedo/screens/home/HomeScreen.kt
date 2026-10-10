package com.locatedo.locatedo.screens.home

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.common.CategoryStyle
import com.locatedo.locatedo.core.common.DistanceFormatting
import com.locatedo.locatedo.core.common.NearbyPlace
import com.locatedo.locatedo.core.common.SystemSettings
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.logic.PermissionBanner
import com.locatedo.locatedo.navigation.AllTodosKey
import com.locatedo.locatedo.navigation.LocalNavigator
import com.locatedo.locatedo.navigation.MapKey
import com.locatedo.locatedo.navigation.Overlay
import com.locatedo.locatedo.navigation.PlaceKey
import com.locatedo.locatedo.navigation.SettingsKey
import com.locatedo.locatedo.navigation.SharingKey
import com.locatedo.locatedo.screens.components.AddButtonClearance
import com.locatedo.locatedo.screens.components.PermissionBannerCard
import com.locatedo.locatedo.screens.components.Refreshable
import com.locatedo.locatedo.screens.components.rememberPreciseLocationRequest
import com.locatedo.locatedo.ui.analytics.LocalAnalytics
import com.locatedo.locatedo.ui.analytics.TrackScreen

private const val PREVIEW_ZOOM = 14f
private const val PREVIEW_TODOS = 2

// The root: a glance at the map, every open to-do, then the places, nearest first.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onAddPlace: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    TrackScreen(AnalyticsScreen.HOME)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val analytics = LocalAnalytics.current
    val context = LocalContext.current
    val requestPreciseLocation = rememberPreciseLocationRequest(
        hasRequested = uiState.hasRequestedPreciseLocation,
        onRequested = viewModel::preciseLocationRequested,
    )
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.resumed()
    }

    fun bannerTapped(banner: PermissionBanner) {
        viewModel.permissionBannerTapped(banner)
        when (banner) {
            PermissionBanner.LOCATION_ALWAYS -> navigator.present(Overlay.AlwaysLocation(viewModel::alwaysLocationAnswered))
            PermissionBanner.LOCATION_DENIED -> SystemSettings.openAppDetails(context)
            PermissionBanner.PRECISE_LOCATION -> requestPreciseLocation()
            PermissionBanner.NOTIFICATIONS -> SystemSettings.openNotifications(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(
                        onClick = {
                            analytics.log(AnalyticsEvent.SHARE_TAPPED, mapOf(AnalyticsParameter.SOURCE to "home"))
                            navigator.push(SharingKey)
                        },
                    ) {
                        Icon(Icons.Filled.Group, contentDescription = stringResource(R.string.sharing_title))
                    }
                    IconButton(onClick = { navigator.push(SettingsKey) }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.tab_settings))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Refreshable(isSignedIn = uiState.isSignedIn, isRefreshing = uiState.isRefreshing, onRefresh = viewModel::refresh) {
                when {
                    uiState.isLoading -> Box(Modifier.fillMaxSize())
                    !uiState.hasPlaces -> EmptyPlaces(onAddPlace = onAddPlace)
                    else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = AddButtonClearance)) {
                        uiState.permissionBanner?.let { banner ->
                            item { PermissionBannerCard(banner = banner, onClick = { bannerTapped(banner) }) }
                        }
                        item { MapPreview(uiState, onClick = { navigator.push(MapKey) }) }
                        item { AllTodosRow(count = uiState.openTodoCount, onClick = { navigator.push(AllTodosKey) }) }
                        items(uiState.nearby, key = { it.entry.place.id }) { nearby ->
                            NearbyRow(
                                nearby = nearby,
                                category = uiState.categories[nearby.entry.place.categoryId],
                                onClick = { navigator.push(PlaceKey(nearby.entry.place.id.toString())) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// A glance at where you are; a tap opens the full map, where it can be moved around.
@Composable
private fun MapPreview(uiState: HomeUiState, onClick: () -> Unit) {
    val label = stringResource(R.string.tab_map)
    val nearest = uiState.nearby.firstOrNull()?.entry?.place
    val center = uiState.here?.let { LatLng(it.latitude, it.longitude) } ?: nearest?.let { LatLng(it.latitude, it.longitude) }
    val cameraPositionState = rememberCameraPositionState()
    LaunchedEffect(center) {
        if (center != null) {
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(center, PREVIEW_ZOOM))
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                onClick {
                    onClick()
                    true
                }
            },
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
            onMapClick = { onClick() },
            onPOIClick = { onClick() },
            onMyLocationClick = { onClick() },
        )
    }
}

@Composable
private fun AllTodosRow(count: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.home_all_todos)) },
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(Icons.Filled.Checklist, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Text(count.toString(), style = MaterialTheme.typography.bodyLarge) },
    )
}

// A place with up to two open to-dos and how far it is.
@Composable
private fun NearbyRow(nearby: NearbyPlace, category: Category?, onClick: () -> Unit) {
    val place = nearby.entry.place
    val openTodos = nearby.entry.openTodos
    ListItem(
        headlineContent = { Text(place.name) },
        modifier = Modifier.clickable(onClick = onClick),
        supportingContent = openTodos.takeIf { it.isNotEmpty() }?.let { todos ->
            {
                Column {
                    for (todo in todos.take(PREVIEW_TODOS)) {
                        Text(todo.title, maxLines = 1)
                    }
                    val more = todos.size - PREVIEW_TODOS
                    if (more > 0) {
                        Text(pluralStringResource(R.plurals.home_more_todos, more, more))
                    }
                }
            }
        },
        leadingContent = {
            Icon(CategoryStyle.icon(category?.icon), contentDescription = null, tint = CategoryStyle.tint(category?.color))
        },
        trailingContent = nearby.distanceMeters?.let { meters ->
            { Text(DistanceFormatting.string(meters), style = MaterialTheme.typography.bodyMedium) }
        },
    )
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
        Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
