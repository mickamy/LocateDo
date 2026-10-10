package com.locatedo.locatedo.debug

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
import com.google.android.gms.location.LocationServices
import com.locatedo.locatedo.core.common.di.ApplicationScope
import com.locatedo.locatedo.core.data.LocalData
import com.locatedo.locatedo.core.database.CategoryDao
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.database.MembershipDao
import com.locatedo.locatedo.core.database.MembershipEntity
import com.locatedo.locatedo.core.database.PlaceDao
import com.locatedo.locatedo.core.database.PlaceEntity
import com.locatedo.locatedo.core.database.TodoDao
import com.locatedo.locatedo.core.database.TodoEntity
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceEvent
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.notifications.ArrivalNotifier
import com.locatedo.locatedo.core.notifications.CompletionNotice
import com.locatedo.locatedo.core.notifications.CompletionNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Replaces local data with a tidy example for Play screenshots, driven over adb by `fastlane screenshots`:
//   am broadcast -n <package>/com.locatedo.locatedo.debug.ScreenshotReceiver -a seed --es language ja
//   am broadcast -n <package>/com.locatedo.locatedo.debug.ScreenshotReceiver -a notify --es language ja
//   am broadcast -n <package>/com.locatedo.locatedo.debug.ScreenshotReceiver -a notify-completion --es language ja
// and `fastlane location_video`, which moves the device into a geofence (the app must be the mock location app):
//   am broadcast -n <package>/com.locatedo.locatedo.debug.ScreenshotReceiver -a move --es latitude 37.77927 --es longitude -122.41924
class ScreenshotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val japanese = intent.getStringExtra(EXTRA_LANGUAGE) == "ja"
        val graph = EntryPointAccessors.fromApplication(context.applicationContext, ScreenshotEntryPoint::class.java)
        val result = goAsync()
        graph.applicationScope().launch {
            try {
                when (intent.action) {
                    ACTION_SEED -> seed(graph, japanese)
                    ACTION_NOTIFY -> {
                        // A replaced notification does not pop up again, so the previous language's one goes first.
                        NotificationManagerCompat.from(context).cancelAll()
                        notifyArrival(graph, japanese)
                    }
                    ACTION_NOTIFY_COMPLETION -> {
                        NotificationManagerCompat.from(context).cancelAll()
                        notifyCompletion(graph, japanese)
                    }
                    ACTION_MOVE -> move(context, intent)
                }
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun seed(graph: ScreenshotEntryPoint, japanese: Boolean) {
        graph.localData().reset()
        val categories = graph.categoryDao().observeAll().first()
        val now = Instant.now().toEpochMilli()
        val center = ScreenshotSeed.center(japanese)
        val household = ScreenshotSeed.household(japanese)
        graph.database().withTransaction {
            ScreenshotSeed.entries(japanese).forEachIndexed { index, entry ->
                val placeId = ScreenshotSeed.placeId(index).toString()
                graph.placeDao().upsert(
                    PlaceEntity(
                        id = placeId,
                        name = entry.name,
                        latitude = center.first + entry.latitudeOffset,
                        longitude = center.second + entry.longitudeOffset,
                        radiusMeters = RADIUS_METERS,
                        categoryId = categories.firstOrNull { it.builtin == entry.builtin.key }?.id,
                        sortOrder = index,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                entry.todos.forEachIndexed { todoIndex, title ->
                    graph.todoDao().upsert(
                        TodoEntity(
                            id = todoId(ScreenshotSeed.placeId(index), todoIndex).toString(),
                            title = title,
                            placeId = placeId,
                            assigneeId = if (index == 0 && title == household.assigned) PARTNER_ID else null,
                            completedAt = null,
                            createdAt = now + todoIndex,
                            updatedAt = now + todoIndex,
                        ),
                    )
                }
            }
            addHousehold(graph, household, now)
        }
        val preferences = graph.preferences()
        preferences.setCompletedOnboarding(true)
        preferences.setRequestedLocation(true)
        preferences.setRequestedNotifications(true)
        preferences.setReminderSetupNever()
        preferences.setShownPromotionsPrompt()
    }

    // Two members, so the grocery store shows an assignee and who checked something off.
    private suspend fun addHousehold(graph: ScreenshotEntryPoint, household: ScreenshotSeed.Household, now: Long) {
        graph.membershipDao().upsert(MembershipEntity(ME_ID, "owner", household.me, now, now))
        graph.membershipDao().upsert(MembershipEntity(PARTNER_ID, "member", household.partner, now + 1, now + 1))
        graph.todoDao().upsert(
            TodoEntity(
                id = todoId(ScreenshotSeed.placeId(0), COMPLETED_INDEX).toString(),
                title = household.completed,
                placeId = ScreenshotSeed.placeId(0).toString(),
                assigneeId = null,
                completedAt = now - COMPLETED_AGO_MILLIS,
                completerId = PARTNER_ID,
                createdAt = now - COMPLETED_AGO_MILLIS,
                updatedAt = now - COMPLETED_AGO_MILLIS,
            ),
        )
    }

    // The server's completion notice for the to-do the partner checked off.
    private fun notifyCompletion(graph: ScreenshotEntryPoint, japanese: Boolean) {
        graph.completionNotifier().prepare()
        graph.completionNotifier().notify(CompletionNotice(ScreenshotSeed.household(japanese).completionNotice, 1), 1)
    }

    private fun notifyArrival(graph: ScreenshotEntryPoint, japanese: Boolean) {
        val entry = ScreenshotSeed.entries(japanese).first()
        val center = ScreenshotSeed.center(japanese)
        val place = Place(
            id = ScreenshotSeed.placeId(0),
            name = entry.name,
            latitude = center.first + entry.latitudeOffset,
            longitude = center.second + entry.longitudeOffset,
            createdAt = Instant.now(),
        )
        val todos = entry.todos.mapIndexed { index, title ->
            Todo(id = todoId(place.id, index), title = title, placeId = place.id, createdAt = place.createdAt)
        }
        graph.arrivalNotifier().prepare()
        graph.arrivalNotifier().notify(PlaceEvent.ARRIVAL, place, todos)
    }

    // Geofences follow the fused provider, which an emulator's `geo fix` does not reach while no app asks for GPS.
    @SuppressLint("MissingPermission")
    private suspend fun move(context: Context, intent: Intent) {
        val location = Location("mock").apply {
            latitude = intent.getStringExtra(EXTRA_LATITUDE)!!.toDouble()
            longitude = intent.getStringExtra(EXTRA_LONGITUDE)!!.toDouble()
            accuracy = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.setMockMode(true).await()
        client.setMockLocation(location).await()
    }

    private fun todoId(placeId: UUID, index: Int): UUID = UUID.nameUUIDFromBytes("$placeId/$index".toByteArray())

    private companion object {
        const val ACTION_SEED = "seed"
        const val ACTION_NOTIFY = "notify"
        const val ACTION_NOTIFY_COMPLETION = "notify-completion"
        const val ACTION_MOVE = "move"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_LANGUAGE = "language"
        const val RADIUS_METERS = 150.0
        const val ME_ID = "0199a6f0-0000-7000-8000-0000000000a1"
        const val PARTNER_ID = "0199a6f0-0000-7000-8000-0000000000a2"
        const val COMPLETED_INDEX = 99
        const val COMPLETED_AGO_MILLIS = 600_000L
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScreenshotEntryPoint {
    fun localData(): LocalData
    fun database(): LocateDoDatabase
    fun placeDao(): PlaceDao
    fun todoDao(): TodoDao
    fun categoryDao(): CategoryDao
    fun preferences(): AppPreferences
    fun arrivalNotifier(): ArrivalNotifier
    fun membershipDao(): MembershipDao
    fun completionNotifier(): CompletionNotifier

    @ApplicationScope
    fun applicationScope(): CoroutineScope
}

// The same places and to-dos as the iOS ScreenshotSeed, so both stores show one story.
object ScreenshotSeed {
    class Entry(
        val name: String,
        val builtin: BuiltinCategory,
        val latitudeOffset: Double,
        val longitudeOffset: Double,
        val todos: List<String>,
    )

    class Household(
        val me: String,
        val partner: String,
        val assigned: String,
        val completed: String,
        val completionNotice: String,
    )

    fun household(japanese: Boolean): Household {
        if (japanese) {
            return Household("たかし", "ゆき", "卵", "トイレットペーパー", "ゆきが「トイレットペーパー」を完了しました")
        }
        return Household("Sam", "Alex", "Eggs", "Paper towels", "Alex checked off \"Paper towels\"")
    }

    fun placeId(index: Int): UUID = UUID.fromString("0199a6f0-0000-7000-8000-00000000000${index + 1}")

    // Chosen so the grocery store, the place the screenshots open, sits on a public landmark and its looked-up address
    // is not someone's home: Tokyo Station and San Francisco City Hall.
    fun center(japanese: Boolean): Pair<Double, Double> {
        if (japanese) {
            return 35.67824 to 139.76712
        }
        return 37.77627 to -122.41924
    }

    fun entries(japanese: Boolean): List<Entry> {
        if (japanese) {
            return listOf(
                Entry("スーパー", BuiltinCategory.SHOPPING, 0.0030, 0.0, listOf("牛乳", "卵", "食パン")),
                Entry("ドラッグストア", BuiltinCategory.SHOPPING, -0.0045, 0.0040, listOf("日焼け止め", "歯みがき粉")),
                Entry("ホームセンター", BuiltinCategory.LIFE, 0.0110, -0.0060, listOf("電球")),
                Entry("会社", BuiltinCategory.WORK, -0.0150, 0.0100, listOf("経費の書類を出す")),
            )
        }
        return listOf(
            Entry("Grocery store", BuiltinCategory.SHOPPING, 0.0030, 0.0, listOf("Milk", "Eggs", "Avocados")),
            Entry("Pharmacy", BuiltinCategory.SHOPPING, -0.0045, 0.0040, listOf("Sunscreen", "Toothpaste")),
            Entry("Hardware store", BuiltinCategory.LIFE, 0.0110, -0.0060, listOf("Light bulbs")),
            Entry("Office", BuiltinCategory.WORK, -0.0150, 0.0100, listOf("Hand in the expense report")),
        )
    }
}
