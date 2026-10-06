package com.locatedo.locatedo.core.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.Place
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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

        notifier.notifyArrival(store, listOf("Milk", "Bread", "Eggs", "Butter"))
        notifier.notifyArrival(store, listOf("Milk"))

        val manager = shadowOf(application.getSystemService(NotificationManager::class.java))
        assertEquals(listOf(AndroidArrivalNotifier.CHANNEL_ID), manager.notificationChannels.map { (it as android.app.NotificationChannel).id })
        val posted = manager.allNotifications.single()
        assertEquals("You’re at Store", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Milk", posted.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
    }

    @Test
    fun tappingOpensThePlaceAndSaysWhenItWasPosted() {
        val notifier = AndroidArrivalNotifier(application, clock)
        notifier.prepare()

        notifier.notifyArrival(store, listOf("Milk"))

        val manager = shadowOf(application.getSystemService(NotificationManager::class.java))
        val intent = shadowOf(manager.allNotifications.single().contentIntent).savedIntent
        assertEquals(store.id.toString(), intent.getStringExtra("placeId"))
        assertEquals(now.toEpochMilli(), intent.getLongExtra("notifiedAt", -1))
    }

    @Test
    fun theBodyListsThreeTitlesAndCountsTheRest() {
        val notifier = AndroidArrivalNotifier(application, clock)
        notifier.prepare()

        notifier.notifyArrival(store, listOf("Milk", "Bread", "Eggs", "Butter", "Jam"))

        val manager = shadowOf(application.getSystemService(NotificationManager::class.java))
        val text = manager.allNotifications.single().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertTrue(text, text.contains("Milk"))
        assertTrue(text, text.contains("Eggs"))
        assertFalse(text, text.contains("Butter"))
        assertTrue(text, text.endsWith("+2 more"))
    }
}
