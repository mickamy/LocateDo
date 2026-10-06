package com.locatedo.locatedo.feature.paywall

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.billing.PaywallPlan
import com.locatedo.locatedo.core.billing.PaywallTrigger
import com.locatedo.locatedo.core.billing.PlanKind
import com.locatedo.locatedo.core.common.LegalLinks

// Why Pro, the two plans, and the store's purchase flow; the reason line says which limit brought the user here.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    trigger: PaywallTrigger,
    onClose: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val uriHandler = LocalUriHandler.current
    val locale = LocalConfiguration.current.locales[0]
    var isShowingRestored by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PaywallEvent.Purchased -> onClose()
                PaywallEvent.Restored -> isShowingRestored = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_done))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Header(trigger = trigger)
            Benefits()
            if (uiState.isMember) {
                Text(
                    text = stringResource(R.string.paywall_member_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Purchase(
                    state = uiState,
                    onSelect = viewModel::select,
                    onPurchase = { activity?.let(viewModel::purchase) },
                    onRestore = viewModel::restore,
                    onOpenTerms = { uriHandler.openUri(LegalLinks.termsOfUse(locale)) },
                    onOpenPrivacy = { uriHandler.openUri(LegalLinks.privacyPolicy(locale)) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (isShowingRestored) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.paywall_restored_title)) },
            text = { Text(stringResource(R.string.paywall_restored_message)) },
            confirmButton = {
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

@Composable
private fun Header(trigger: PaywallTrigger) {
    val reason = when (trigger) {
        PaywallTrigger.PLACE_LIMIT -> R.string.paywall_reason_places
        PaywallTrigger.TODO_LIMIT -> R.string.paywall_reason_todos
        PaywallTrigger.SHARE -> R.string.paywall_reason_share
        PaywallTrigger.SETTINGS -> null
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Text(stringResource(R.string.paywall_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        if (reason != null) {
            Text(stringResource(reason), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Benefits() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Benefit(Icons.Filled.Group, R.string.paywall_benefit_share)
        Benefit(Icons.Filled.AllInclusive, R.string.paywall_benefit_unlimited)
        Benefit(Icons.Filled.Home, R.string.paywall_benefit_household)
    }
}

@Composable
private fun Benefit(icon: ImageVector, text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(text))
    }
}

@Composable
private fun Purchase(
    state: PaywallUiState,
    onSelect: (PlanKind) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    when {
        state.isLoading -> CircularProgressIndicator()
        state.plans.isEmpty() -> Text(
            text = stringResource(R.string.paywall_unavailable),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        else -> {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (plan in state.plans) {
                    PlanRow(plan = plan, isSelected = plan.kind == state.selected, onSelect = { onSelect(plan.kind) })
                }
            }
            Button(onClick = onPurchase, modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy) {
                if (state.isWorking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(if (state.startsTrial) R.string.paywall_start_trial else R.string.paywall_subscribe))
                }
            }
            state.failure?.let { failure ->
                val message = when (failure) {
                    PaywallFailure.PURCHASE_FAILED -> R.string.paywall_failed
                    PaywallFailure.NOTHING_TO_RESTORE -> R.string.paywall_nothing_to_restore
                }
                Text(
                    text = stringResource(message),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = stringResource(R.string.paywall_android_renewal_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRestore, enabled = !state.isBusy) {
                    if (state.isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.paywall_restore))
                    }
                }
                TextButton(onClick = onOpenTerms) {
                    Text(stringResource(R.string.paywall_terms))
                }
                TextButton(onClick = onOpenPrivacy) {
                    Text(stringResource(R.string.settings_about_privacy_policy))
                }
            }
        }
    }
}

@Composable
private fun PlanRow(plan: PaywallPlan, isSelected: Boolean, onSelect: () -> Unit) {
    val title = stringResource(if (plan.kind == PlanKind.ANNUAL) R.string.paywall_annual else R.string.paywall_monthly)
    val detail = when (plan.kind) {
        PlanKind.ANNUAL -> if (plan.trialDays != null) {
            stringResource(R.string.paywall_trial_then, plan.trialDays.toString(), plan.price)
        } else {
            stringResource(R.string.paywall_per_year, plan.price)
        }
        PlanKind.MONTHLY -> stringResource(R.string.paywall_per_month, plan.price)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = isSelected, onClick = null)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (plan.kind == PlanKind.ANNUAL) {
                        Text(
                            text = stringResource(R.string.paywall_best_value),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
