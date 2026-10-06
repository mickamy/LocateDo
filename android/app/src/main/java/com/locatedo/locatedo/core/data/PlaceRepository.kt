package com.locatedo.locatedo.core.data

import androidx.room.withTransaction
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.database.PlaceAndTodos
import com.locatedo.locatedo.core.database.PlaceDao
import com.locatedo.locatedo.core.database.PlaceEntity
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PlaceRepository {
    fun observeAll(): Flow<List<Place>>
    fun observeAllWithTodos(): Flow<List<PlaceWithTodos>>
    fun observeWithTodos(id: UUID): Flow<PlaceWithTodos?>

    // Returns the limit that stopped the write on a free plan, or null when it went through.
    suspend fun add(place: Place): FreeLimit?
    suspend fun update(place: Place)
    suspend fun delete(id: UUID)
    suspend fun markNotified(id: UUID, at: Instant)
}

@Singleton
class RoomPlaceRepository @Inject constructor(
    private val database: LocateDoDatabase,
    private val placeDao: PlaceDao,
    private val proStatus: ProStatus,
    private val clock: Clock,
) : PlaceRepository {
    override fun observeAll(): Flow<List<Place>> =
        placeDao.observeAll().map { places -> places.map(PlaceEntity::asModel) }

    override fun observeAllWithTodos(): Flow<List<PlaceWithTodos>> =
        placeDao.observeAllWithTodos().map { places -> places.map(PlaceAndTodos::asModel) }

    override fun observeWithTodos(id: UUID): Flow<PlaceWithTodos?> =
        placeDao.observeWithTodos(id.toString()).map { it?.asModel() }

    // Counting and inserting in one transaction keeps two quick adds from both slipping under the limit.
    override suspend fun add(place: Place): FreeLimit? = database.withTransaction {
        if (!proStatus.isPro() && placeDao.count() >= FreeLimit.PLACES.max) {
            return@withTransaction FreeLimit.PLACES
        }
        placeDao.upsert(place.asEntity())
        null
    }

    override suspend fun update(place: Place) {
        placeDao.upsert(place.copy(updatedAt = clock.instant()).asEntity())
    }

    override suspend fun delete(id: UUID) {
        placeDao.delete(id.toString())
    }

    override suspend fun markNotified(id: UUID, at: Instant) {
        placeDao.setLastNotifiedAt(id.toString(), at.toEpochMilli())
    }
}
