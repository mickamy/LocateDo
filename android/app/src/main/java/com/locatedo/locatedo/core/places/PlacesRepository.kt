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

data class PlacePrediction(val id: String, val primaryText: String, val secondaryText: String?, val distanceMeters: Int? = null)

data class PlaceCandidate(
    val name: String?,
    val address: String?,
    val coordinate: Coordinate,
    val types: List<String> = emptyList(),
)

interface PlacesRepository {
    suspend fun autocomplete(query: String, near: Coordinate?, origin: Coordinate?): List<PlacePrediction>
    suspend fun fetch(id: String): PlaceCandidate?

    // For a store tapped on the map, which comes with an id and a name only.
    suspend fun types(id: String): List<String>
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

    override suspend fun autocomplete(query: String, near: Coordinate?, origin: Coordinate?): List<PlacePrediction> {
        if (query.isBlank()) {
            return emptyList()
        }
        val token = sessionToken ?: AutocompleteSessionToken.newInstance().also { sessionToken = it }
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(token)
            .apply {
                if (near != null) {
                    locationBias = CircularBounds.newInstance(near.toLatLng(), SEARCH_RADIUS_METERS)
                }
                if (origin != null) {
                    setOrigin(origin.toLatLng())
                }
            }
            .build()
        return runCatching { client.findAutocompletePredictions(request).await() }
            .getOrNull()
            ?.autocompletePredictions
            ?.map { PlacePrediction(it.placeId, it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString(), it.distanceMeters) }
            ?: emptyList()
    }

    override suspend fun fetch(id: String): PlaceCandidate? {
        val fields = listOf(Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION, Place.Field.TYPES)
        val request = FetchPlaceRequest.builder(id, fields)
            .setSessionToken(sessionToken)
            .build()
        sessionToken = null
        val place = runCatching { client.fetchPlace(request).await().place }.getOrNull() ?: return null
        val location = place.location ?: return null
        return PlaceCandidate(
            name = place.displayName,
            address = place.formattedAddress,
            coordinate = Coordinate(location.latitude, location.longitude),
            types = place.placeTypes.orEmpty(),
        )
    }

    // Types alone bill as Place Details Essentials.
    override suspend fun types(id: String): List<String> {
        val request = FetchPlaceRequest.newInstance(id, listOf(Place.Field.TYPES))
        return runCatching { client.fetchPlace(request).await().place.placeTypes }.getOrNull().orEmpty()
    }

    private companion object {
        const val SEARCH_RADIUS_METERS = 20_000.0
    }
}

private fun Coordinate.toLatLng(): LatLng = LatLng(latitude, longitude)

@Module
@InstallIn(SingletonComponent::class)
abstract class PlacesModule {
    @Binds
    abstract fun placesRepository(repository: GooglePlacesRepository): PlacesRepository
}
