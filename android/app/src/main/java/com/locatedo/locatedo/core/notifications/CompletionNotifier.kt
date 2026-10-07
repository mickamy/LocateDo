package com.locatedo.locatedo.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.locatedo.locatedo.MainActivity
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.common.TodosRequests
import com.locatedo.locatedo.core.datastore.AppPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

interface CompletionNotifier {
    fun prepare()
    fun notify(notice: CompletionNotice, id: Int)
}

// The household channel, apart from arrival reminders and promotions, so each can be silenced on its own.
@Singleton
class AndroidCompletionNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CompletionNotifier {
    private val manager = NotificationManagerCompat.from(context)

    override fun prepare() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.notification_channel_household))
            .build()
        manager.createNotificationChannel(channel)
    }

    override fun notify(notice: CompletionNotice, id: Int) {
        if (!manager.areNotificationsEnabled()) {
            return
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            id,
            MainActivity.completionIntent(context, notice.count),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentText(notice.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(CHANNEL_ID, id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not post the completion notification", e)
        }
    }

    companion object {
        const val CHANNEL_ID = "household"
        private const val TAG = "Notifications"
    }
}

@Singleton
class CompletionHandler @Inject constructor(
    private val preferences: AppPreferences,
    private val notifier: CompletionNotifier,
    private val todosRequests: TodosRequests,
    private val analytics: Analytics,
) {
    // The switch is checked again here, so a send that crossed turning it off is dropped.
    suspend fun received(notice: CompletionNotice, id: Int) {
        if (!preferences.completionNotices.first()) {
            return
        }
        notifier.notify(notice, id)
    }

    fun opened(count: Int) {
        analytics.log(AnalyticsEvent.COMPLETION_NOTICE_OPENED, mapOf(AnalyticsParameter.COUNT to count))
        todosRequests.request()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CompletionModule {
    @Binds
    abstract fun completionNotifier(notifier: AndroidCompletionNotifier): CompletionNotifier
}
