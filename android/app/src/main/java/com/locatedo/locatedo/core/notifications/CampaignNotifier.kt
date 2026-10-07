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
import com.locatedo.locatedo.core.datastore.AppPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

interface CampaignNotifier {
    fun prepare()
    fun notify(campaign: CampaignNotification, sentAt: Instant)
}

// Its own quiet channel, so promotions can be turned off in system settings without touching arrival reminders.
@Singleton
class AndroidCampaignNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CampaignNotifier {
    private val manager = NotificationManagerCompat.from(context)

    override fun prepare() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.notification_channel_promotions))
            .build()
        manager.createNotificationChannel(channel)
    }

    override fun notify(campaign: CampaignNotification, sentAt: Instant) {
        if (!manager.areNotificationsEnabled()) {
            return
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            campaign.id.hashCode(),
            MainActivity.campaignIntent(context, campaign, sentAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(campaign.title)
            .setContentText(campaign.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(campaign.body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROMO)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(CHANNEL_ID, campaign.id.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not post the campaign notification", e)
        }
    }

    companion object {
        const val CHANNEL_ID = "promotions"
        private const val TAG = "Notifications"
    }
}

@Singleton
class CampaignHandler @Inject constructor(
    private val preferences: AppPreferences,
    private val notifier: CampaignNotifier,
    private val analytics: Analytics,
    private val clock: Clock,
) {
    // The consent is checked again here, so a send that crossed a withdrawal is dropped.
    suspend fun received(campaign: CampaignNotification, sentAt: Instant) {
        if (!preferences.promotions.first().consent) {
            return
        }
        notifier.notify(campaign, sentAt)
    }

    fun opened(campaignId: String, hasUrl: Boolean, sentAt: Instant?) {
        val parameters = mutableMapOf<AnalyticsParameter, Any>(
            AnalyticsParameter.CAMPAIGN_ID to campaignId,
            AnalyticsParameter.HAS_URL to hasUrl,
        )
        if (sentAt != null) {
            parameters[AnalyticsParameter.LATENCY_S] = Duration.between(sentAt, clock.instant()).seconds.coerceAtLeast(0)
        }
        analytics.log(AnalyticsEvent.CAMPAIGN_OPENED, parameters)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CampaignModule {
    @Binds
    abstract fun campaignNotifier(notifier: AndroidCampaignNotifier): CampaignNotifier
}
