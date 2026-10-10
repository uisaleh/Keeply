package app.keeply.ui.features.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.keeply.quickadd.SuggestionListener
import app.keeply.ui.components.ButtonSize
import app.keeply.ui.components.ButtonVariant
import app.keeply.ui.components.IconTile
import app.keeply.ui.components.KeeplyButton
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Radius
import app.keeply.ui.theme.Space

private data class GuideStep(val title: String, val body: String, val action: String, val open: (Context) -> Unit)

/**
 * Walks people through turning on notification access, which lives deep in Android's settings.
 *
 * Apps installed from a file on Android 13+ are blocked by "restricted settings", and Android only
 * offers "Allow restricted settings" in App info AFTER the person has tried notification access
 * once and seen the block. So that path is three steps in a fixed order: try, unlock, turn on.
 * Each step's button opens the right Android page; coming back moves the guide to the next step,
 * and the sheet closes on its own once access is on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSuggestionsGuide(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = Keeply.colors
    val restricted = remember { SuggestionListener.needsRestrictedUnlock(context) }
    val openAccess: (Context) -> Unit = SuggestionListener::openAccessSettings
    val steps = if (restricted) listOf(
        GuideStep(
            "Try notification access once",
            "Tap Keeply. Android will say \"Restricted setting\". That's expected: just tap OK and come back here.",
            "Open notification access", openAccess,
        ),
        GuideStep(
            "Allow restricted settings",
            "In App info, tap ⋮ (top right), then Allow restricted settings, and confirm with your PIN or fingerprint.",
            "Open app info", ::openAppInfo,
        ),
        GuideStep(
            "Turn on notification access",
            "Tap Keeply, switch it on and tap Allow. Then come back to Keeply.",
            "Open notification access", openAccess,
        ),
    ) else listOf(
        GuideStep(
            "Turn on notification access",
            "Tap Keeply, switch it on and tap Allow. Then come back to Keeply.",
            "Open notification access", openAccess,
        ),
    )
    // The step the person is on, and the one whose page they've just gone to.
    var current by rememberSaveable { mutableIntStateOf(0) }
    var opened by rememberSaveable { mutableIntStateOf(-1) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Access itself is checked by the caller, which closes this sheet once it's on.
        if (opened >= 0) {
            current = (opened + 1).coerceAtMost(steps.lastIndex)
            opened = -1
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceRaised,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.lg).padding(bottom = Space.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                IconTile(Icons.Outlined.AutoAwesome, c.tintViolet)
                Column(Modifier.weight(1f)) {
                    Text("Turn on Smart suggestions", style = Keeply.type.heading3, color = c.text, modifier = Modifier.semantics { heading() })
                    Text(
                        "Keeply spots payments, orders and renewals in your notifications and offers to save them.",
                        style = Keeply.type.bodySm, color = c.textMuted,
                    )
                }
            }

            TrustCard()

            if (restricted) {
                Text(
                    "Why the extra steps? Android adds a safety lock to apps installed from a file instead of the Play Store. It's normal, and you only do it once.",
                    style = Keeply.type.caption, color = c.textMuted,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                steps.forEachIndexed { i, step ->
                    StepRow(
                        number = i + 1, step = step, done = i < current, active = i == current,
                        onOpen = { opened = i; step.open(context) },
                    )
                }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Not now", style = Keeply.type.button, color = c.textMuted)
            }
        }
    }
}

/** Why it's safe to say yes: what's read, where it goes, and what's kept. */
@Composable
private fun TrustCard() {
    val c = Keeply.colors
    val (bg, fg) = c.tintGreen
    Column(
        Modifier.fillMaxWidth().clip(Radius.lg).background(bg).padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Text("Keeply is safe to allow", style = Keeply.type.bodyMdMedium, color = fg)
        }
        listOf(
            "Notifications are checked on your phone only, never uploaded or shared",
            "Nothing is saved unless you tap Save",
            "Codes and OTPs are always ignored",
            "You can turn it off any time in Settings",
        ).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Text(line, style = Keeply.type.bodySm, color = c.text)
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, step: GuideStep, done: Boolean, active: Boolean, onOpen: () -> Unit) {
    val c = Keeply.colors
    Row(
        Modifier.fillMaxWidth().clip(Radius.lg)
            .background(if (active) c.surfaceWarm else c.bgSubtle)
            .padding(Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(
            Modifier.size(28.dp).clip(Radius.pill).background(
                when {
                    done -> c.success
                    active -> c.accent
                    else -> c.borderStrong
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Outlined.Check, contentDescription = "Done", tint = c.onAccent, modifier = Modifier.size(16.dp))
            else Text("$number", style = Keeply.type.captionBold, color = c.onAccent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(step.title, style = Keeply.type.bodyMdMedium, color = c.text)
            Text(step.body, style = Keeply.type.bodySm, color = c.textMuted)
            KeeplyButton(
                step.action, onOpen,
                variant = if (active) ButtonVariant.Primary else ButtonVariant.Outline, size = ButtonSize.Sm,
            )
        }
    }
}

private fun openAppInfo(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
