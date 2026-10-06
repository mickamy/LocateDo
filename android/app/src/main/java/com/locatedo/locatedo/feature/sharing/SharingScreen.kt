package com.locatedo.locatedo.feature.sharing

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.model.MemberRole
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.feature.account.AccountFailure
import com.locatedo.locatedo.feature.account.AccountViewModel
import com.locatedo.locatedo.feature.account.GoogleSignInButton
import com.locatedo.locatedo.ui.components.BenefitRow
import com.locatedo.locatedo.ui.components.shownName

// Signed out: why to share, and the Google button. Signed in: the household's state, its members, and the way in or out.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharingScreen(
    onBack: () -> Unit,
    onAcceptInvite: () -> Unit,
    viewModel: SharingViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accountState by accountViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val inviteMessage = stringResource(R.string.sharing_invite_message)
    var memberToRemove by remember { mutableStateOf<Membership?>(null) }
    var isConfirmingLeave by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SharingEvent.InviteCreated -> shareInvite(context, inviteMessage + "\n" + event.invite.url)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sharing_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_done))
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(modifier = Modifier.fillMaxSize().padding(padding))
            !uiState.isSignedIn -> SharingIntro(
                failed = accountState.failure == AccountFailure.SIGN_IN,
                isWorking = accountState.isWorking,
                accountViewModel = accountViewModel,
                modifier = Modifier.padding(padding),
            )
            else -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                SignedIn(
                    state = uiState,
                    onInvite = viewModel::createInvite,
                    onRemove = { memberToRemove = it },
                    onAcceptInvite = onAcceptInvite,
                    onLeave = { isConfirmingLeave = true },
                )
            }
        }
    }

    memberToRemove?.let { membership ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text(stringResource(R.string.sharing_remove_confirm_title, membership.shownName())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        memberToRemove = null
                        viewModel.remove(membership.userId)
                    },
                ) {
                    Text(stringResource(R.string.sharing_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    if (isConfirmingLeave) {
        AlertDialog(
            onDismissRequest = { isConfirmingLeave = false },
            title = { Text(stringResource(R.string.sharing_leave_confirm_title)) },
            text = { Text(stringResource(R.string.sharing_android_leave_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        isConfirmingLeave = false
                        viewModel.leave()
                    },
                ) {
                    Text(stringResource(R.string.sharing_leave), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmingLeave = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private fun shareInvite(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

@Composable
private fun SignedIn(
    state: SharingUiState,
    onInvite: () -> Unit,
    onRemove: (Membership) -> Unit,
    onAcceptInvite: () -> Unit,
    onLeave: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        state.status?.let { status ->
            item(key = "header") {
                SharingHeader(status = status, state = state, onInvite = onInvite)
            }
        }
        item(key = "members") {
            Text(
                text = stringResource(R.string.sharing_members),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        items(state.memberships, key = { it.userId }) { membership ->
            MemberRow(
                membership = membership,
                isCurrentUser = membership.userId == state.currentUserId,
                onRemove = if (state.canRemove(membership) && !state.isWorking) {
                    { onRemove(membership) }
                } else {
                    null
                },
            )
        }
        if (state.hasFailed) {
            item(key = "failure") {
                Text(
                    text = stringResource(R.string.sharing_failed),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (state.canAcceptInvite) {
            item(key = "accept") {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.sharing_accept)) },
                    modifier = Modifier.clickable(enabled = !state.isWorking, onClick = onAcceptInvite),
                    leadingContent = { Icon(Icons.Filled.MailOutline, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                )
            }
        }
        if (state.canLeave) {
            item(key = "leave") {
                HorizontalDivider()
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.sharing_leave), color = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable(enabled = !state.isWorking, onClick = onLeave),
                )
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun SharingHeader(status: SharingStatus, state: SharingUiState, onInvite: () -> Unit) {
    val ownerName = state.owner?.shownName() ?: stringResource(R.string.sharing_unnamed_member)
    val title = when (status) {
        SharingStatus.OWNER_FREE -> stringResource(R.string.sharing_intro_title)
        SharingStatus.OWNER_ALONE -> stringResource(R.string.sharing_invite_header_title)
        SharingStatus.OWNER_SHARING -> pluralStringResource(R.plurals.sharing_shared_header_title, state.memberships.size, state.memberships.size)
        SharingStatus.MEMBER -> stringResource(R.string.sharing_member_header_title, ownerName)
    }
    val message = when (status) {
        SharingStatus.OWNER_FREE -> stringResource(R.string.sharing_pro_required)
        SharingStatus.OWNER_ALONE -> stringResource(R.string.sharing_invite_header_message)
        SharingStatus.OWNER_SHARING -> stringResource(R.string.sharing_android_shared_header_message)
        SharingStatus.MEMBER -> stringResource(R.string.sharing_member_header_message)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HouseholdIcon(size = 72.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        when (status) {
            SharingStatus.OWNER_FREE -> Benefits()
            SharingStatus.OWNER_ALONE, SharingStatus.OWNER_SHARING -> InviteAction(state = state, onInvite = onInvite)
            SharingStatus.MEMBER -> Unit
        }
    }
}

@Composable
private fun InviteAction(state: SharingUiState, onInvite: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onInvite, modifier = Modifier.fillMaxWidth(), enabled = state.seatsLeft > 0 && !state.isWorking) {
            Text(stringResource(R.string.sharing_invite))
        }
        val caption = if (state.seatsLeft == 0) {
            stringResource(R.string.sharing_full)
        } else {
            pluralStringResource(R.plurals.sharing_seats_left, state.seatsLeft, state.seatsLeft) + " " +
                stringResource(R.string.sharing_invite_expiry)
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Benefits() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 8.dp)) {
        BenefitRow(
            icon = Icons.AutoMirrored.Filled.List,
            title = R.string.sharing_benefit_lists_title,
            message = R.string.sharing_android_benefit_lists_message,
        )
        BenefitRow(
            icon = Icons.Filled.Place,
            title = R.string.sharing_benefit_assign_title,
            message = R.string.sharing_benefit_assign_message,
        )
        BenefitRow(
            icon = Icons.Filled.CardGiftcard,
            title = R.string.sharing_benefit_plan_title,
            message = R.string.sharing_benefit_plan_message,
        )
    }
}

@Composable
private fun MemberRow(membership: Membership, isCurrentUser: Boolean, onRemove: (() -> Unit)?) {
    val name = membership.shownName()
    ListItem(
        headlineContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name)
                if (isCurrentUser) {
                    Text(stringResource(R.string.sharing_you), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (membership.role == MemberRole.OWNER) {
                    Text(
                        text = stringResource(R.string.sharing_owner),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.PersonRemove, contentDescription = stringResource(R.string.sharing_remove))
                    }
                }
            }
        },
    )
}

@Composable
private fun SharingIntro(failed: Boolean, isWorking: Boolean, accountViewModel: AccountViewModel, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            HouseholdIcon(size = 88.dp)
            Text(stringResource(R.string.sharing_intro_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                text = stringResource(R.string.sharing_android_intro_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Benefits()
        }
        Text(
            text = stringResource(R.string.sharing_invited_hint),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        if (failed) {
            Text(
                text = stringResource(R.string.settings_account_sign_in_failed),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
        GoogleSignInButton(viewModel = accountViewModel, modifier = Modifier.fillMaxWidth(), enabled = !isWorking)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HouseholdIcon(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Group,
            contentDescription = null,
            modifier = Modifier.size(size / 2),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
