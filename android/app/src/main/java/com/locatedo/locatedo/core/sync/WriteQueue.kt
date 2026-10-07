package com.locatedo.locatedo.core.sync

import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.database.PendingWriteDao
import com.locatedo.locatedo.core.database.PendingWriteEntity
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

// Writes wait here, in order, until the engine sends them. Nothing is queued while signed out: local data goes up
// with CreateHousehold at sign-in instead.
@Singleton
class WriteQueue @Inject constructor(
    private val dao: PendingWriteDao,
    private val authenticator: Authenticator,
    private val clock: Clock,
) {
    private val _queued = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val queued: SharedFlow<Unit> = _queued

    val size: Flow<Int> = dao.observeCount()

    // Meant to run inside the transaction that made the local change, so the two land together.
    suspend fun enqueue(writes: List<Write>) {
        if (writes.isEmpty() || authenticator.current() == null) {
            return
        }
        val now = clock.instant().toEpochMilli()
        for (write in writes) {
            dao.insert(PendingWriteEntity(kind = write.kind.key, payload = write.serialized(), createdAt = now))
        }
        _queued.tryEmit(Unit)
    }

    suspend fun enqueue(write: Write) = enqueue(listOf(write))

    suspend fun isEmpty(): Boolean = dao.count() == 0
}
