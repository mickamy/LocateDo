package com.locatedo.locatedo.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.locatedo.locatedo.core.common.di.ApplicationScope
import com.locatedo.locatedo.core.model.PlaceEvent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ArrivalActionEntryPoint {
    fun arrivalCheckOff(): ArrivalCheckOff

    @ApplicationScope
    fun applicationScope(): CoroutineScope
}

// A check-off button on a reminder, for one to-do or all of them; works from the lock screen without
// opening the app.
class ArrivalActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val placeId = uuid(intent.getStringExtra(EXTRA_PLACE_ID)) ?: return
        val event = PlaceEvent.fromKey(intent.getStringExtra(EXTRA_PLACE_EVENT).orEmpty())
        val todoIds = intent.getStringArrayExtra(EXTRA_TODO_IDS).orEmpty().mapNotNull(::uuid)
        val notifiedIds = intent.getStringArrayExtra(EXTRA_NOTIFIED_IDS).orEmpty().mapNotNull(::uuid)
        val graph = EntryPointAccessors.fromApplication(context.applicationContext, ArrivalActionEntryPoint::class.java)
        val result = goAsync()
        graph.applicationScope().launch {
            try {
                graph.arrivalCheckOff().checkOff(placeId, event, todoIds, notifiedIds)
            } finally {
                result.finish()
            }
        }
    }

    private fun uuid(raw: String?): UUID? = raw?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    companion object {
        const val EXTRA_PLACE_ID = "placeId"
        const val EXTRA_PLACE_EVENT = "placeEvent"
        const val EXTRA_TODO_IDS = "todoIds"
        const val EXTRA_NOTIFIED_IDS = "notifiedIds"
    }
}
