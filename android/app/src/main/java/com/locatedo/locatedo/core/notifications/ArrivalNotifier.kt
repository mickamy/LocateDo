package com.locatedo.locatedo.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.icu.text.ListFormatter
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.locatedo.locatedo.MainActivity
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.model.Place
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface ArrivalNotifier {
    fun prepare()
    fun notifyArrival(place: Place, todoTitles: List<String>)
}

@Singleton
class AndroidArrivalNotifier @Inject constructor(@param:ApplicationContext private val context: Context) : ArrivalNotifier {
    private val manager = NotificationManagerCompat.from(context)

    override fun prepare() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.notification_channel_arrivals))
            .build()
        manager.createNotificationChannel(channel)
    }

    // One notification per place, replaced on the next arrival; tapping it opens the place on the home map.
    override fun notifyArrival(place: Place, todoTitles: List<String>) {
        if (!manager.areNotificationsEnabled()) {
            return
        }
        val text = bodyText(todoTitles)
        val contentIntent = PendingIntent.getActivity(
            context,
            place.id.hashCode(),
            MainActivity.placeIntent(context, place.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_arrived_title, place.name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(place.id.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not post the arrival notification", e)
        }
    }

    private fun bodyText(todoTitles: List<String>): String {
        val body = NotificationPolicy.body(todoTitles)
        val locale = context.resources.configuration.locales[0]
        val shown = ListFormatter.getInstance(locale).format(body.titles)
        if (body.more == 0) {
            return shown
        }
        return shown + "\n" + context.resources.getQuantityString(R.plurals.notification_more, body.more, body.more)
    }

    companion object {
        const val CHANNEL_ID = "arrivals"
        private const val TAG = "Notifications"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds
    abstract fun arrivalNotifier(notifier: AndroidArrivalNotifier): ArrivalNotifier
}
