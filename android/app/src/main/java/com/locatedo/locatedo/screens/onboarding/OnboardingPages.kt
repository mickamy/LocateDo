package com.locatedo.locatedo.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.common.LegalLinks
import com.locatedo.locatedo.core.notifications.arrivalNotificationText

@Composable
fun IntroPage(onStart: () -> Unit, onReturning: () -> Unit) {
    // Shrinks on small screens before anything else is pushed off.
    val screenHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val animationHeight = (screenHeight * 0.3f).coerceIn(160.dp, 260.dp)
    OnboardingPage(
        actions = {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().testTag("onboarding.start")) {
                Text(stringResource(R.string.onboarding_start))
            }
            TextButton(onClick = onReturning, modifier = Modifier.testTag("onboarding.returning")) {
                Text(stringResource(R.string.onboarding_returning_link))
            }
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppMark()
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        ArrivalAnimation(
            modifier = Modifier
                .fillMaxWidth()
                .height(animationHeight),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.onboarding_headline),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.onboarding_subheadline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun StoreKindPage(onPick: (StoreKind) -> Unit, onLater: () -> Unit) {
    OnboardingPage(
        actions = {
            TextButton(onClick = onLater, modifier = Modifier.testTag("onboarding.later")) {
                Text(stringResource(R.string.onboarding_later))
            }
        },
    ) {
        PageTitle(stringResource(R.string.first_place_kind_title))
        PageMessage(stringResource(R.string.first_place_kind_message))
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (kind in StoreKind.entries) {
                ChoiceCard(
                    title = stringResource(kind.labelRes),
                    icon = kind.icon,
                    onClick = { onPick(kind) },
                    modifier = Modifier.testTag("onboarding.kind.${kind.key}"),
                )
            }
        }
    }
}

// Right before the system asks for location, which is when people wonder where it goes.
@Composable
fun PrivacyPage(reason: String, isRequesting: Boolean, onAllow: () -> Unit) {
    OnboardingPage(
        actions = {
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth().testTag("onboarding.allowLocation"),
                enabled = !isRequesting,
            ) {
                Text(stringResource(R.string.onboarding_allow_location))
            }
        },
    ) {
        Hero(Icons.Filled.Lock)
        PageTitle(stringResource(R.string.onboarding_privacy_title))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Point(Icons.Filled.PhoneAndroid, stringResource(R.string.onboarding_privacy_on_device))
            Point(Icons.Filled.CloudOff, stringResource(R.string.onboarding_privacy_not_sent))
            Point(Icons.Filled.Block, stringResource(R.string.onboarding_privacy_no_ads))
        }
    }
}

// The first place is saved. When reminders still need permissions it says so plainly, rather than promise one that
// cannot arrive yet, and leads on to the setup shown over Home.
@Composable
fun DonePage(placeName: String, needsSetup: Boolean, onContinue: () -> Unit) {
    var message = stringResource(R.string.first_place_done_ready)
    var button = stringResource(R.string.onboarding_start)
    if (needsSetup) {
        message = stringResource(R.string.first_place_done_almost)
        button = stringResource(R.string.first_place_done_continue)
    }
    OnboardingPage(
        actions = {
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().testTag("onboarding.done")) {
                Text(button)
            }
        },
    ) {
        Hero(Icons.Filled.CheckCircle)
        PageTitle(stringResource(R.string.first_place_done_title, placeName))
        PageMessage(message)
    }
}

// For someone who is not starting fresh: joining a household or coming back to an account skips the first place,
// which would only be replaced by the household's.
@Composable
fun ReturningPage(onInvite: () -> Unit, onSignIn: () -> Unit, onBack: () -> Unit) {
    OnboardingPage(
        actions = {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.common_back))
            }
        },
    ) {
        PageTitle(stringResource(R.string.onboarding_returning_title))
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChoiceCard(
                title = stringResource(R.string.onboarding_returning_invite),
                message = stringResource(R.string.onboarding_returning_invite_message),
                icon = Icons.Filled.Group,
                onClick = onInvite,
                modifier = Modifier.testTag("onboarding.returning.invite"),
            )
            ChoiceCard(
                title = stringResource(R.string.onboarding_returning_sign_in),
                message = stringResource(R.string.onboarding_returning_sign_in_message),
                icon = Icons.Filled.AccountCircle,
                onClick = onSignIn,
                modifier = Modifier.testTag("onboarding.returning.signIn"),
            )
        }
    }
}

@Composable
fun AnalyticsConsentPage(onAnswer: (Boolean) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val locale = LocalConfiguration.current.locales[0]
    OnboardingPage(
        actions = {
            // Same size and place, so declining is as easy as agreeing.
            Button(onClick = { onAnswer(true) }, modifier = Modifier.fillMaxWidth().testTag("onboarding.analyticsAllow")) {
                Text(stringResource(R.string.onboarding_analytics_allow))
            }
            OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.fillMaxWidth().testTag("onboarding.analyticsDeny")) {
                Text(stringResource(R.string.onboarding_analytics_deny))
            }
        },
    ) {
        Hero(Icons.Filled.BarChart)
        PageTitle(stringResource(R.string.onboarding_analytics_title))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Point(Icons.Filled.Person, stringResource(R.string.onboarding_analytics_message))
            Point(Icons.Filled.Lock, stringResource(R.string.onboarding_analytics_not_sent))
            Point(Icons.Filled.Settings, stringResource(R.string.onboarding_analytics_settings))
        }
        TextButton(onClick = { uriHandler.openUri(LegalLinks.privacyPolicy(locale)) }) {
            Text(stringResource(R.string.settings_about_privacy_policy))
        }
    }
}

// The example the notification preview shows while nothing is written: the store's first ideas, as the real
// notification would list them.
@Composable
fun firstTodosExample(store: FirstStore): String {
    val resources = LocalResources.current
    val ideas = store.ideas(resources).take(3)
    if (ideas.isEmpty()) {
        return stringResource(R.string.place_editor_todo_example_shopping)
    }
    return stringResource(R.string.place_editor_preview_example, arrivalNotificationText(resources, ideas))
}
