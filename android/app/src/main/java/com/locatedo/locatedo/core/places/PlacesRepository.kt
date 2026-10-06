package com.locatedo.locatedo.core.places

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.locatedo.locatedo.BuildConfig
import com.locatedo.locatedo.core.model.Coordinate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class PlacePrediction(val id: String, val primaryText: String, val secondaryText: String?)

data class PlaceCandidate(val name: String?, val address: String?, val coordinate: Coordinate)

interface PlacesRepository {
    suspend fun autocomplete(query: String, near: Coordinate?): List<PlacePrediction>
    suspend fun fetch(id: String): PlaceCandidate?
}

@Singleton
class GooglePlacesRepository @Inject constructor(@param:ApplicationContext private val context: Context) : PlacesRepository {
    private val client: PlacesClient by lazy {
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(context, BuildConfig.MAPS_API_KEY)
        }
        Places.createClient(context)
    }

    // One token per search session so the predictions and the final fetch bill as one session.
    private var sessionToken: AutocompleteSessionToken? = null

    override suspend fun autocomplete(query: String, near: Coordinate?): List<PlacePrediction> {
        if (query.isBlank()) {
            return emptyList()
        }
        val token = sessionToken ?: AutocompleteSessionToken.newInstance().also { sessionToken = it }
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(token)
            .apply {
                if (near != null) {
                    locationBias = CircularBounds.newInstance(LatLng(near.latitude, near.longitude), SEARCH_RADIUS_METERS)
                }
            }
            .build()
        return runCatching { client.findAutocompletePredictions(request).await() }
            .getOrNull()
            ?.autocompletePredictions
            ?.map { PlacePrediction(it.placeId, it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString()) }
            ?: emptyList()
    }

    override suspend fun fetch(id: String): PlaceCandidate? {
        val request = FetchPlaceRequest.builder(id, listOf(Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION))
            .setSessionToken(sessionToken)
            .build()
        sessionToken = null
        val place = runCatching { client.fetchPlace(request).await().place }.getOrNull() ?: return null
        val location = place.location ?: return null
        return PlaceCandidate(
            name = place.displayName,
            address = place.formattedAddress,
            coordinate = Coordinate(location.latitude, location.longitude),
        )
    }

    private companion object {
        const val SEARCH_RADIUS_METERS = 20_000.0
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlacesModule {
    @Binds
    abstract fun placesRepository(repository: GooglePlacesRepository): PlacesRepository
}
