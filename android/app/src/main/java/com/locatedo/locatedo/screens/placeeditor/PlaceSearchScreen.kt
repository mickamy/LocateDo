package com.locatedo.locatedo.screens.placeeditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.common.DistanceFormatting
import com.locatedo.locatedo.navigation.LocalNavigator
import com.locatedo.locatedo.ui.analytics.TrackScreen

// Google Maps' search, opened from the map: the field is the title and suggestions fill the page. A suggestion goes
// back to the map with its pin.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchScreen(viewModel: PlaceEditorViewModel) {
    TrackScreen(AnalyticsScreen.PLACE_SEARCH)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = uiState.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.place_picker_search_placeholder)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navigator::pop) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_cancel))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            items(uiState.predictions, key = { it.id }) { prediction ->
                ListItem(
                    headlineContent = { Text(prediction.primaryText) },
                    supportingContent = prediction.secondaryText?.let { { Text(it) } },
                    leadingContent = { Icon(Icons.Filled.Place, contentDescription = null) },
                    trailingContent = prediction.distanceMeters?.let { meters -> { Text(DistanceFormatting.string(meters.toDouble())) } },
                    modifier = Modifier.clickable { viewModel.selectPrediction(prediction) },
                )
            }
        }
    }
}
