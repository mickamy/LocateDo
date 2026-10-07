package com.locatedo.locatedo.core.sync

import android.util.Log
import androidx.room.withTransaction
import com.connectrpc.Code
import com.connectrpc.ConnectException
import com.google.protobuf.InvalidProtocolBufferException
import com.locatedo.locatedo.core.appstatus.MaintenanceGate
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.auth.SignedOutException
import com.locatedo.locatedo.core.common.di.ApplicationScope
import com.locatedo.locatedo.core.data.ProStatus
import com.locatedo.locatedo.core.data.SyncStateRepository
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.database.PendingWriteDao
import com.locatedo.locatedo.core.database.PendingWriteEntity
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Plan
import com.locatedo.sync.v1.Change
import com.locatedo.sync.v1.SyncServiceClientInterface
import com.locatedo.sync.v1.pullRequest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SyncEngine {
    // A queued insert the server refused on the free plan was undone locally.
    val limitRejected: SharedFlow<FreeLimit>

    // The server denies this user the household while the session is still valid: the member was removed.
    val removed: SharedFlow<Unit>

    // What the last pull brought, for the debug section.
    val lastPullSummary: StateFlow<String?>

    fun start()

    // Sends the queue and stops there.
    suspend fun drain()

    // Sends the queue, then pulls what changed on the server.
    suspend fun sync()
}

// Sends queued writes in order, stopping at the first one the server does not accept, and pulls the household's
// changes. Overlapping requests share one run; a request that arrives during a run makes it go round again.
@Singleton
class DefaultSyncEngine @Inject constructor(
    private val sender: WriteSender,
    private val syncService: SyncServiceClientInterface,
    private val authenticator: Authenticator,
    private val database: LocateDoDatabase,
    private val pendingWrites: PendingWriteDao,
    private val queue: WriteQueue,
    private val syncState: SyncStateRepository,
    private val applier: ChangeApplier,
    private val limitRejection: LimitRejection,
    private val proStatus: ProStatus,
    private val gate: MaintenanceGate,
    @param:ApplicationScope private val scope: CoroutineScope,
) : SyncEngine {
    private class Pulled(val changes: List<Change>, val cursor: Long, val reset: Boolean, val plan: Plan)

    private val _limitRejected = MutableSharedFlow<FreeLimit>(extraBufferCapacity = 1)
    private val _removed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _lastPullSummary = MutableStateFlow<String?>(null)
    private val runs = Mutex()
    private var inFlight: Job? = null
    private var rerunRequested = false
    private var pullRequested = false
    private var permissionDenied = false
    private var scheduled: Job? = null

    override val limitRejected: SharedFlow<FreeLimit> = _limitRejected

    override val removed: SharedFlow<Unit> = _removed

    override val lastPullSummary: StateFlow<String?> = _lastPullSummary

    override fun start() {
        scope.launch {
            queue.queued.collect { scheduleDrain() }
        }
    }

    // Quick successive edits go up in one run.
    @Synchronized
    fun scheduleDrain(delayMillis: Long = DRAIN_DELAY_MILLIS): Job {
        scheduled?.cancel()
        return scope.launch {
            delay(delayMillis)
            drain()
        }.also { scheduled = it }
    }

    override suspend fun drain() = run(pulling = false)

    override suspend fun sync() = run(pulling = true)

    private suspend fun run(pulling: Boolean) {
        val job = runs.withLock {
            rerunRequested = true
            if (pulling) {
                pullRequested = true
            }
            inFlight ?: scope.launch { loop() }.also { inFlight = it }
        }
        job.join()
    }

    private suspend fun loop() {
        while (true) {
            val pulls = runs.withLock {
                if (!rerunRequested) {
                    inFlight = null
                    return
                }
                rerunRequested = false
                val pulls = pullRequested
                pullRequested = false
                pulls
            }
            try {
                // Maintenance, or a build too old for the server: the queue waits, and the app syncs when it reopens.
                if (gate.isClosed()) {
                    continue
                }
                sendQueuedWrites()
                if (pulls) {
                    pullChanges()
                }
                if (permissionDenied) {
                    permissionDenied = false
                    confirmSession()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sync run failed", e)
            }
        }
    }

    private suspend fun sendQueuedWrites() {
        if (authenticator.current() == null) {
            return
        }
        val householdId = syncState.get().householdId ?: return
        var sent = 0
        while (true) {
            val head = pendingWrites.head() ?: break
            if (!deliver(head, ProtoInput.id(householdId))) {
                break
            }
            pendingWrites.delete(head.sequence)
            sent += 1
        }
        if (sent > 0) {
            Log.i(TAG, "Sent $sent queued writes")
        }
    }

    // True when the head is done with, delivered or dropped; false keeps it for the next run.
    private suspend fun deliver(head: PendingWriteEntity, householdId: String): Boolean {
        val write = decode(head)
        if (write == null) {
            Log.e(TAG, "Dropping an unreadable ${head.kind} write")
            return true
        }
        try {
            sender.send(write, householdId)
            return true
        } catch (e: ConnectException) {
            if (e.code in DROPPED) {
                Log.e(TAG, "Dropping a rejected ${head.kind} write", e)
                return true
            }
            if (e.code == Code.FAILED_PRECONDITION && !proStatus.isPro()) {
                return reject(write, head.kind)
            }
            noteIfPermissionDenied(e)
            pendingWrites.recordAttempt(head.sequence)
            Log.w(TAG, "Keeping the ${head.kind} write at the head", e)
            return false
        } catch (e: SignedOutException) {
            return false
        }
    }

    private fun decode(head: PendingWriteEntity): Write? {
        val kind = WriteKind.fromKey(head.kind) ?: return null
        return try {
            Write.decode(kind, head.payload)
        } catch (e: InvalidProtocolBufferException) {
            null
        }
    }

    // With Pro, the webhook that lifts the limit may simply not have landed yet, so the write waits.
    private suspend fun reject(write: Write, kind: String): Boolean {
        Log.w(TAG, "Dropping a $kind write rejected by the free limit")
        val limit = database.withTransaction { limitRejection.revert(write) }
        if (limit != null) {
            _limitRejected.tryEmit(limit)
        }
        return true
    }

    private suspend fun pullChanges() {
        if (authenticator.current() == null) {
            return
        }
        val state = syncState.get()
        val householdId = state.householdId ?: return
        val pulled = try {
            fetchChanges(householdId, state.cursor)
        } catch (e: ConnectException) {
            noteIfPermissionDenied(e)
            _lastPullSummary.value = "failed: ${e.code}"
            Log.w(TAG, "Pull failed", e)
            return
        } catch (e: SignedOutException) {
            return
        }
        database.withTransaction {
            val current = syncState.get()
            if (current.householdId != householdId) {
                return@withTransaction
            }
            applier.apply(pulled.changes, pulled.reset)
            syncState.set(current.copy(cursor = pulled.cursor, plan = pulled.plan))
        }
        var summary = "${pulled.changes.size} changes, cursor ${state.cursor} → ${pulled.cursor}"
        if (pulled.reset) {
            summary += ", reset"
        }
        _lastPullSummary.value = summary
        Log.i(TAG, "Pulled $summary")
    }

    private suspend fun fetchChanges(householdId: UUID, after: Long): Pulled {
        val changes = mutableListOf<Change>()
        var cursor = after
        var reset = false
        var plan = Plan.FREE
        var hasMore = true
        while (hasMore) {
            val request = pullRequest {
                this.householdId = ProtoInput.id(householdId)
                this.cursor = cursor
            }
            val response = authenticator.authorized { syncService.pull(request) }
            changes += response.changesList
            cursor = response.cursor
            if (response.reset) {
                reset = true
            }
            plan = response.household.plan.toModel()
            hasMore = response.hasMore
        }
        return Pulled(changes, cursor, reset, plan)
    }

    // PermissionDenied means either the session ended or the member was removed; a refresh tells them apart.
    private suspend fun confirmSession() {
        try {
            authenticator.refresh()
        } catch (e: ConnectException) {
            Log.w(TAG, "Permission denied and the refresh failed", e)
            return
        } catch (e: SignedOutException) {
            return
        }
        Log.w(TAG, "Permission denied with a valid session; treating it as a removal from the household")
        _removed.tryEmit(Unit)
    }

    private fun noteIfPermissionDenied(e: ConnectException) {
        if (e.code == Code.PERMISSION_DENIED) {
            permissionDenied = true
        }
    }

    companion object {
        const val DRAIN_DELAY_MILLIS = 2_000L
        private const val TAG = "Sync"
        private val DROPPED = setOf(Code.ALREADY_EXISTS, Code.INVALID_ARGUMENT, Code.NOT_FOUND)
    }
}
