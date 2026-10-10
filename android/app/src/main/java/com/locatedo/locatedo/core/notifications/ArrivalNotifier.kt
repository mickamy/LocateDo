package com.locatedo.locatedo.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.icu.text.ListFormatter
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.locatedo.locatedo.MainActivity
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.Todo
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface ArrivalNotifier {
    fun prepare()
    fun canNotify(): Boolean
    // A silent post replaces what is shown without sounding again, as after a check-off.
    fun notifyArrival(place: Place, todos: List<Todo>, silent: Boolean = false)
    fun cancelArrival(placeId: UUID)
}

@Singleton
class AndroidArrivalNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : ArrivalNotifier {
    private val manager = NotificationManagerCompat.from(context)

    override fun prepare() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.notification_channel_arrivals))
            .build()
        manager.createNotificationChannel(channel)
    }

    // Off for the whole app, or for the arrivals channel alone; either way nothing would be shown.
    override fun canNotify(): Boolean {
        if (!manager.areNotificationsEnabled()) {
            return false
        }
        return manager.getNotificationChannelCompat(CHANNEL_ID)?.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }

    // One notification per place, replaced on the next arrival; tapping it opens the place on the home map, and each
    // of the first to-dos gets a button that checks it off without opening the app.
    override fun notifyArrival(place: Place, todos: List<Todo>, silent: Boolean) {
        val text = arrivalNotificationText(context.resources, todos.map { it.title })
        val contentIntent = PendingIntent.getActivity(
            context,
            place.id.hashCode(),
            MainActivity.placeIntent(context, place.id, notifiedAt = clock.instant()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            // Titles are cut at one line, so the place name alone keeps long names whole.
            .setContentTitle(place.name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setSilent(silent)
        // Android shows three actions at most; from three to-dos on, the last one checks off all of them.
        var buttons = todos
        if (todos.size >= CHECK_OFF_ALL_FROM) {
            buttons = todos.take(CHECK_OFF_ALL_FROM - 1)
        }
        for (todo in buttons) {
            builder.addAction(0, "✓ ${todo.title}", checkOffIntent(place, listOf(todo), todos, todo.id.hashCode()))
        }
        if (todos.size >= CHECK_OFF_ALL_FROM) {
            val label = context.resources.getQuantityString(R.plurals.notification_android_check_off_all, todos.size, todos.size)
            builder.addAction(0, label, checkOffIntent(place, todos, todos, place.id.hashCode()))
        }
        try {
            manager.notify(place.id.hashCode(), builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not post the arrival notification", e)
        }
    }

    private fun checkOffIntent(place: Place, checkingOff: List<Todo>, notified: List<Todo>, requestCode: Int): PendingIntent {
        val intent = Intent(context, ArrivalActionReceiver::class.java)
            .putExtra(ArrivalActionReceiver.EXTRA_PLACE_ID, place.id.toString())
            .putExtra(ArrivalActionReceiver.EXTRA_TODO_IDS, checkingOff.map { it.id.toString() }.toTypedArray())
            .putExtra(ArrivalActionReceiver.EXTRA_NOTIFIED_IDS, notified.map { it.id.toString() }.toTypedArray())
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun cancelArrival(placeId: UUID) {
        manager.cancel(placeId.hashCode())
    }

    companion object {
        const val CHANNEL_ID = "arrivals"
        const val CHECK_OFF_ALL_FROM = 3
        private const val TAG = "Notifications"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds
    abstract fun arrivalNotifier(notifier: AndroidArrivalNotifier): ArrivalNotifier
}

// The arrival notification's text: the first to-dos as a list, then how many more. Shared with the preview shown
// while adding a place.
fun arrivalNotificationText(resources: Resources, todoTitles: List<String>): String {
    val body = NotificationPolicy.body(todoTitles)
    val locale = resources.configuration.locales[0]
    val shown = ListFormatter.getInstance(locale).format(body.titles)
    if (body.more == 0) {
        return shown
    }
    return shown + "\n" + resources.getQuantityString(R.plurals.notification_more, body.more, body.more)
}
