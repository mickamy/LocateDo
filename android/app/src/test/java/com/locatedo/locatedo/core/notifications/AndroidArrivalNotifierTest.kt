package com.locatedo.locatedo.core.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.Todo
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidArrivalNotifierTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val store = Place(id = uuidV7(now), name = "Store", latitude = 35.0, longitude = 139.0, createdAt = now)

    @Test
    fun postsOneNotificationPerPlaceNamingThreeTodosAndTheRest() {
        val notifier = AndroidArrivalNotifier(application, clock)
        notifier.prepare()

        notifier.notifyArrival(store, todos("Milk", "Bread", "Eggs", "Butter"))
        notifier.notifyArrival(store, todos("Milk"))

        val manager = shadowOf(application.getSystemService(NotificationManager::class.java))
        assertEquals(listOf(AndroidArrivalNotifier.CHANNEL_ID), manager.notificationChannels.map { (it as android.app.NotificationChannel).id })
        val posted = manager.allNotifications.single()
        assertEquals("Store", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Milk", posted.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
    }

    @Test
    fun tappingOpensThePlaceAndSaysWhenItWasPosted() {
        val notifier = AndroidArrivalNotifier(application, clock)
        notifier.prepare()

        notifier.notifyArrival(store, todos("Milk"))

        val manager = shadowOf(application.getSystemService(NotificationManager::class.java))
        val intent = shadowOf(manager.allNotifications.single().contentIntent).savedIntent
        assertEquals(store.id.toString(), intent.getStringExtra("placeId"))
        assertEquals(now.toEpochMilli(), intent.getLongExtra("notifiedAt", -1))
    }

    @Test
    fun oneOrTwoTodosEachGetACheckOffButton() {
        val notifier = AndroidArrivalNotifier(application, clock)
        notifier.prepare()
        val todos = todos("Milk", "Bread")

        notifier.notifyArrival(store, todos)

        val actions = postedActions()
        assertEquals(listOf("✓ Milk", "✓ Bread"), actions.map { it.title.toString() })
        val intent = shadowOf(actions[1].actionIntent).savedIntent
        assertEquals(ArrivalActionReceiver::class.java.name, intent.component?.className)
        assertEquals(store.id.toString(), intent.getStringExtra(ArrivalActionReceiver.EXTRA_PLACE_ID))
        assertEquals(listOf(todos[1].id.toString()), intent.getStringArrayExtra(ArrivalActionReceiver.EXTRA_TODO_IDS)?.toList())
        assertEquals(todos.map { it.id.toString() }, intent.getStringArrayExtra(ArrivalActionReceiver.EXTRA_NOTIFIED_IDS)?.toList())
    }

    @Test
    fun fromThreeTodosTheLastButtonChecksOffAll() {
        val notifier = AndroidArrivalNotifier(application, clock)
        notifier.prepare()
        val todos = todos("Milk", "Bread", "Eggs", "Butter")

        notifier.notifyArrival(store, todos)

        val actions = postedActions()
        assertEquals(listOf("✓ Milk", "✓ Bread", "Mark all 4 as done"), actions.map { it.title.toString() })
        val intent = shadowOf(actions[2].actionIntent).savedIntent
        assertEquals(todos.map { it.id.toString() }, intent.getStringArrayExtra(ArrivalActionReceiver.EXTRA_TODO_IDS)?.toList())
    }

    @Test
    fun cancellingRemovesThePlacesNotification() {
        val notifier = AndroidArrivalNotifier(application, clock)
        notifier.prepare()
        notifier.notifyArrival(store, todos("Milk"))

        notifier.cancelArrival(store.id)

        val manager = shadowOf(application.getSystemService(NotificationManager::class.java))
        assertTrue(manager.allNotifications.isEmpty())
    }

    @Test
    fun theBodyListsThreeTitlesAndCountsTheRest() {
        val notifier = AndroidArrivalNotifier(application, clock)
        notifier.prepare()

        notifier.notifyArrival(store, todos("Milk", "Bread", "Eggs", "Butter", "Jam"))

        val manager = shadowOf(application.getSystemService(NotificationManager::class.java))
        val text = manager.allNotifications.single().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertTrue(text, text.contains("Milk"))
        assertTrue(text, text.contains("Eggs"))
        assertFalse(text, text.contains("Butter"))
        assertTrue(text, text.endsWith("+2 more"))
    }

    private fun todos(vararg titles: String): List<Todo> =
        titles.map { Todo(id = UUID.randomUUID(), title = it, placeId = store.id, createdAt = now) }

    private fun postedActions(): List<Notification.Action> {
        val manager = shadowOf(application.getSystemService(NotificationManager::class.java))
        return manager.allNotifications.single().actions.toList()
    }
}
