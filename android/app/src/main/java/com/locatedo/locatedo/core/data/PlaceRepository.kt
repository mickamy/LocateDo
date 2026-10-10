package com.locatedo.locatedo.core.data

import androidx.room.withTransaction
import com.locatedo.locatedo.core.analytics.ScreenEntry
import com.locatedo.locatedo.core.analytics.WriteAnalytics
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.database.PlaceAndTodos
import com.locatedo.locatedo.core.database.PlaceDao
import com.locatedo.locatedo.core.database.PlaceEntity
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceEvent
import com.locatedo.locatedo.core.model.PlaceSource
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.core.sync.Write
import com.locatedo.locatedo.core.sync.WriteQueue
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface PlaceRepository {
    fun observeAll(): Flow<List<Place>>
    fun observeAllWithTodos(): Flow<List<PlaceWithTodos>>
    fun observeWithTodos(id: UUID): Flow<PlaceWithTodos?>

    // Returns the limit that stopped the write on a free plan, or null when it went through.
    // todoCount is how many to-dos were typed with a new place; they are added through TodoRepository afterwards.
    suspend fun add(
        place: Place,
        source: PlaceSource? = null,
        todoCount: Int = 0,
        suggestedCategory: BuiltinCategory? = null,
        entry: ScreenEntry? = null,
    ): FreeLimit?
    suspend fun update(place: Place)
    suspend fun delete(id: UUID)
    // Null forgets the last notification of that kind, as the debug arrival and departure do.
    suspend fun markNotified(id: UUID, event: PlaceEvent, at: Instant?)
    suspend fun setEnteredAt(id: UUID, at: Instant?)
}

@Singleton
class RoomPlaceRepository @Inject constructor(
    private val database: LocateDoDatabase,
    private val placeDao: PlaceDao,
    private val proStatus: ProStatus,
    private val queue: WriteQueue,
    private val analytics: WriteAnalytics,
    private val clock: Clock,
) : PlaceRepository {
    override fun observeAll(): Flow<List<Place>> =
        placeDao.observeAll().map { places -> places.map(PlaceEntity::asModel) }

    override fun observeAllWithTodos(): Flow<List<PlaceWithTodos>> =
        placeDao.observeAllWithTodos().map { places -> places.map(PlaceAndTodos::asModel) }

    override fun observeWithTodos(id: UUID): Flow<PlaceWithTodos?> =
        placeDao.observeWithTodos(id.toString()).map { it?.asModel() }

    // Counting and inserting in one transaction keeps two quick adds from both slipping under the limit.
    override suspend fun add(
        place: Place,
        source: PlaceSource?,
        todoCount: Int,
        suggestedCategory: BuiltinCategory?,
        entry: ScreenEntry?,
    ): FreeLimit? {
        val limit = database.withTransaction {
            if (!proStatus.isPro() && placeDao.count() >= FreeLimit.PLACES.max) {
                return@withTransaction FreeLimit.PLACES
            }
            placeDao.upsert(place.asEntity())
            queue.enqueue(Write.put(place))
            null
        }
        if (limit != null) {
            analytics.limitReached(limit)
            return limit
        }
        val category = place.categoryId?.let { database.categoryDao().get(it.toString())?.asModel() }
        analytics.placeAdded(place, category, placeDao.count(), source, todoCount, suggestedCategory, entry)
        reportCounts()
        return null
    }

    override suspend fun update(place: Place) {
        database.withTransaction {
            val updated = place.copy(updatedAt = clock.instant())
            placeDao.upsert(updated.asEntity())
            queue.enqueue(Write.put(updated))
        }
    }

    override suspend fun delete(id: UUID) {
        val stored = placeDao.observeWithTodos(id.toString()).first()?.asModel()
        database.withTransaction {
            placeDao.delete(id.toString())
            queue.enqueue(Write.deletePlace(id))
        }
        if (stored == null) {
            return
        }
        analytics.placeDeleted(stored.place, stored.openTodos.size, placeDao.count())
        reportCounts()
    }

    override suspend fun markNotified(id: UUID, event: PlaceEvent, at: Instant?) {
        when (event) {
            PlaceEvent.ARRIVAL -> placeDao.setLastArrivalNotifiedAt(id.toString(), at?.toEpochMilli())
            PlaceEvent.DEPARTURE -> placeDao.setLastDepartureNotifiedAt(id.toString(), at?.toEpochMilli())
        }
    }

    override suspend fun setEnteredAt(id: UUID, at: Instant?) {
        placeDao.setEnteredAt(id.toString(), at?.toEpochMilli())
    }

    private suspend fun reportCounts() {
        analytics.countsChanged(placeDao.count(), database.todoDao().countOpen())
    }
}
