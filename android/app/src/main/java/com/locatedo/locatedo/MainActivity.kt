package com.locatedo.locatedo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.WriteAnalytics
import com.locatedo.locatedo.core.appstatus.AppStatusStore
import com.locatedo.locatedo.core.auth.AppleSignInRequests
import com.locatedo.locatedo.core.common.PlaceSelectionRequests
import com.locatedo.locatedo.core.notifications.CampaignHandler
import com.locatedo.locatedo.core.notifications.CampaignNotification
import com.locatedo.locatedo.core.notifications.CompletionHandler
import com.locatedo.locatedo.core.sharing.InviteLink
import com.locatedo.locatedo.core.sharing.InviteRequests
import com.locatedo.locatedo.app.LocateDoApp
import com.locatedo.locatedo.ui.analytics.LocalAnalytics
import com.locatedo.locatedo.ui.appstatus.LocalAppStatus
import com.locatedo.locatedo.ui.theme.LocateDoTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var selectionRequests: PlaceSelectionRequests

    @Inject lateinit var inviteRequests: InviteRequests

    @Inject lateinit var analytics: Analytics

    @Inject lateinit var writeAnalytics: WriteAnalytics

    @Inject lateinit var appStatus: AppStatusStore

    @Inject lateinit var campaignHandler: CampaignHandler

    @Inject lateinit var completionHandler: CompletionHandler

    @Inject lateinit var appleSignInRequests: AppleSignInRequests

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openRequestedPlace(intent)
        finishAppleSignIn(intent)
        openInviteLink(intent)
        openCampaign(intent)
        openCompletion(intent)
        setContent {
            val status by appStatus.state.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAnalytics provides analytics, LocalAppStatus provides status) {
                LocateDoTheme {
                    LocateDoApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequestedPlace(intent)
        finishAppleSignIn(intent)
        openInviteLink(intent)
        openCampaign(intent)
        openCompletion(intent)
    }

    // Sign in with Apple returns as <applicationId>://auth/apple#…, before any invite link handling clears the data.
    private fun finishAppleSignIn(intent: Intent?) {
        val link = intent?.data ?: return
        if (link.scheme != packageName || link.host != "auth" || link.path != "/apple") {
            return
        }
        intent.data = null
        appleSignInRequests.received(link.encodedFragment)
    }

    // A promotional push; its link, if any, opens in the browser over the app.
    private fun openCampaign(intent: Intent?) {
        val campaignId = intent?.getStringExtra(EXTRA_CAMPAIGN_ID) ?: return
        val url = CampaignNotification.httpsUrl(intent.getStringExtra(EXTRA_CAMPAIGN_URL))
        val sentAt = intent.getLongExtra(EXTRA_SENT_AT, -1).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        intent.removeExtra(EXTRA_CAMPAIGN_ID)
        intent.removeExtra(EXTRA_CAMPAIGN_URL)
        intent.removeExtra(EXTRA_SENT_AT)
        campaignHandler.opened(campaignId, hasUrl = url != null, sentAt = sentAt)
        if (url == null) {
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No browser for the campaign link", e)
        }
    }

    // A household member checked off to-dos this user added; the to-do tab shows them.
    private fun openCompletion(intent: Intent?) {
        val count = intent?.getIntExtra(EXTRA_COMPLETION_COUNT, 0) ?: return
        if (count <= 0) {
            return
        }
        intent.removeExtra(EXTRA_COMPLETION_COUNT)
        completionHandler.opened(count)
    }

    // An invite link through App Links; the data is cleared so a recreated activity does not offer it twice.
    private fun openInviteLink(intent: Intent?) {
        val link = intent?.data ?: return
        intent.data = null
        val token = InviteLink.token(link.toString()) ?: return
        inviteRequests.request(token)
    }

    // An arrival notification carries its place; the extras are cleared so a recreated activity does not reopen it.
    private fun openRequestedPlace(intent: Intent?) {
        val raw = intent?.getStringExtra(EXTRA_PLACE_ID) ?: return
        val notifiedAt = intent.getLongExtra(EXTRA_NOTIFIED_AT, -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)
        intent.removeExtra(EXTRA_PLACE_ID)
        intent.removeExtra(EXTRA_NOTIFIED_AT)
        val placeId = runCatching { UUID.fromString(raw) }.getOrNull() ?: return
        writeAnalytics.arrivalOpened(placeId, notifiedAt)
        selectionRequests.request(placeId)
    }

    companion object {
        private const val EXTRA_PLACE_ID = "placeId"
        private const val EXTRA_NOTIFIED_AT = "notifiedAt"
        private const val EXTRA_CAMPAIGN_ID = "campaignId"
        private const val EXTRA_CAMPAIGN_URL = "campaignUrl"
        private const val EXTRA_SENT_AT = "sentAt"
        private const val EXTRA_COMPLETION_COUNT = "completionCount"
        private const val TAG = "LocateDo"

        fun placeIntent(context: Context, placeId: UUID, notifiedAt: Instant): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_PLACE_ID, placeId.toString())
                .putExtra(EXTRA_NOTIFIED_AT, notifiedAt.toEpochMilli())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun completionIntent(context: Context, count: Int): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_COMPLETION_COUNT, count)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun campaignIntent(context: Context, campaign: CampaignNotification, sentAt: Instant): Intent {
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_CAMPAIGN_ID, campaign.id)
                .putExtra(EXTRA_SENT_AT, sentAt.toEpochMilli())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            campaign.url?.let { intent.putExtra(EXTRA_CAMPAIGN_URL, it) }
            return intent
        }
    }
}
