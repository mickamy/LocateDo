package com.locatedo.locatedo.core.location

import android.content.Context
import android.location.Geocoder
import com.locatedo.locatedo.core.model.Coordinate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GeocodedPlace(val name: String?, val address: String?)

interface GeocodingRepository {
    suspend fun reverse(coordinate: Coordinate): GeocodedPlace?
}

@Singleton
class AndroidGeocodingRepository @Inject constructor(@param:ApplicationContext private val context: Context) :
    GeocodingRepository {
    override suspend fun reverse(coordinate: Coordinate): GeocodedPlace? {
        if (!Geocoder.isPresent()) {
            return null
        }
        return withContext(Dispatchers.IO) {
            // The listener API needs API 33; the blocking call is fine off the main thread on API 31.
            @Suppress("DEPRECATION")
            val address = runCatching {
                Geocoder(context).getFromLocation(coordinate.latitude, coordinate.longitude, 1)?.firstOrNull()
            }.getOrNull() ?: return@withContext null
            val lines = (0..address.maxAddressLineIndex).mapNotNull { address.getAddressLine(it) }
            GeocodedPlace(
                name = address.featureName?.takeIf { it != address.thoroughfare && it != address.subThoroughfare },
                address = lines.firstOrNull(),
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GeocodingModule {
    @Binds
    abstract fun geocodingRepository(repository: AndroidGeocodingRepository): GeocodingRepository
}
