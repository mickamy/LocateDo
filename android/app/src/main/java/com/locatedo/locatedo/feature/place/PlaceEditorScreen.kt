package com.locatedo.locatedo.feature.place

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.common.CategoryStyle
import com.locatedo.locatedo.core.common.categoryName
import com.locatedo.locatedo.core.common.zoomForRadius
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.ui.components.RadiusSlider

// Google Maps' "add a place" form: a full page with the save action in the top bar.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditorScreen(
    viewModel: PlaceEditorViewModel,
    onChooseOnMap: () -> Unit,
    onManageCategories: () -> Unit,
    onSaved: (isNew: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.editorShown()
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = uiState.draft

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaceEditorEvent.Saved -> onSaved(event.isNew)
                PlaceEditorEvent.LocationChosen -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (draft.isEditing) R.string.place_editor_title_edit else R.string.place_editor_title_new))
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = draft.canSave) {
                        Text(stringResource(R.string.common_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::setName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.place_editor_name_label)) },
                placeholder = { Text(stringResource(R.string.place_editor_name_placeholder)) },
                singleLine = true,
            )

            SectionTitle(stringResource(R.string.place_editor_location_label))
            val coordinate = draft.coordinate
            if (coordinate != null) {
                LocationPreview(coordinate = coordinate, radiusMeters = draft.radiusMeters)
            } else {
                Text(
                    text = stringResource(R.string.place_editor_no_location),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            draft.address?.let { address ->
                Text(text = address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onChooseOnMap) {
                Icon(Icons.Filled.Map, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.place_editor_choose_on_map))
            }

            SectionTitle(stringResource(R.string.place_editor_radius_label))
            RadiusSlider(
                meters = draft.radiusMeters,
                onChange = viewModel::setRadius,
                label = stringResource(R.string.place_editor_radius_label),
            )

            SectionTitle(stringResource(R.string.place_editor_category_label))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = draft.categoryId == category.id,
                        onClick = { viewModel.setCategory(category.id) },
                        label = { Text(categoryName(category)) },
                        leadingIcon = {
                            Icon(
                                CategoryStyle.icon(category.icon),
                                contentDescription = null,
                                tint = CategoryStyle.tint(category.color),
                            )
                        },
                    )
                }
                item {
                    FilterChip(
                        selected = draft.categoryId == null,
                        onClick = { viewModel.setCategory(null) },
                        label = { Text(categoryName(null)) },
                    )
                }
                item {
                    AssistChip(
                        onClick = onManageCategories,
                        label = { Text(stringResource(R.string.category_manage)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun LocationPreview(coordinate: Coordinate, radiusMeters: Double) {
    val target = coordinate.toLatLng()
    val cameraPositionState = rememberCameraPositionState()
    LaunchedEffect(target, radiusMeters) {
        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(target, zoomForRadius(radiusMeters)))
    }
    GoogleMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(MaterialTheme.shapes.medium),
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
        Marker(state = rememberUpdatedMarkerState(position = target))
        Circle(
            center = target,
            radius = radiusMeters,
            fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            strokeColor = MaterialTheme.colorScheme.primary,
            strokeWidth = 2f,
        )
    }
}
