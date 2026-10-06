package com.locatedo.locatedo.core.data

import com.locatedo.locatedo.core.database.SyncStateDao
import com.locatedo.locatedo.core.database.SyncStateEntity
import com.locatedo.locatedo.core.model.SyncState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SyncStateRepository {
    fun observe(): Flow<SyncState>
    suspend fun get(): SyncState
    suspend fun set(state: SyncState)
    suspend fun clear()
}

@Singleton
class RoomSyncStateRepository @Inject constructor(private val dao: SyncStateDao) : SyncStateRepository {
    override fun observe(): Flow<SyncState> = dao.observe().map { it?.asModel() ?: SyncState() }

    override suspend fun get(): SyncState = dao.get()?.asModel() ?: SyncState()

    override suspend fun set(state: SyncState) = dao.upsert(state.asEntity())

    override suspend fun clear() = dao.clear()
}
