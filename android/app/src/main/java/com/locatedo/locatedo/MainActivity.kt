package com.locatedo.locatedo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.locatedo.locatedo.core.common.PlaceSelectionRequests
import com.locatedo.locatedo.ui.LocateDoApp
import com.locatedo.locatedo.ui.theme.LocateDoTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var selectionRequests: PlaceSelectionRequests

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openRequestedPlace(intent)
        setContent {
            LocateDoTheme {
                LocateDoApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequestedPlace(intent)
    }

    // An arrival notification carries its place; the extra is cleared so a recreated activity does not reopen it.
    private fun openRequestedPlace(intent: Intent?) {
        val raw = intent?.getStringExtra(EXTRA_PLACE_ID) ?: return
        intent.removeExtra(EXTRA_PLACE_ID)
        val placeId = runCatching { UUID.fromString(raw) }.getOrNull() ?: return
        selectionRequests.request(placeId)
    }

    companion object {
        private const val EXTRA_PLACE_ID = "placeId"

        fun placeIntent(context: Context, placeId: UUID): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_PLACE_ID, placeId.toString())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
