package com.locatedo.locatedo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.WriteAnalytics
import com.locatedo.locatedo.core.appstatus.AppStatusStore
import com.locatedo.locatedo.core.common.PlaceSelectionRequests
import com.locatedo.locatedo.core.sharing.InviteLink
import com.locatedo.locatedo.core.sharing.InviteRequests
import com.locatedo.locatedo.ui.LocateDoApp
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openRequestedPlace(intent)
        openInviteLink(intent)
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
        openInviteLink(intent)
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

        fun placeIntent(context: Context, placeId: UUID, notifiedAt: Instant): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_PLACE_ID, placeId.toString())
                .putExtra(EXTRA_NOTIFIED_AT, notifiedAt.toEpochMilli())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
