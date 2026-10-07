package com.locatedo.locatedo.feature.promotions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.push.PromotionsConsent

// Asked once, after the app has delivered its first arrival reminder; swiping it away counts as an answer too.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromotionsConsentSheet(onAnswer: (PromotionsConsent.Answer) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onAnswer(PromotionsConsent.Answer.DISMISSED) }) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Filled.Campaign,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.promotions_prompt_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.promotions_prompt_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = { onAnswer(PromotionsConsent.Answer.ACCEPTED) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.promotions_prompt_accept))
            }
            TextButton(onClick = { onAnswer(PromotionsConsent.Answer.DECLINED) }) {
                Text(stringResource(R.string.promotions_prompt_decline))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
