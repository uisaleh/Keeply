package app.keeply.ui.features.common

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.keeply.notifications.Notifications
import app.keeply.ui.components.ButtonSize
import app.keeply.ui.components.ButtonVariant
import app.keeply.ui.components.CardTone
import app.keeply.ui.components.IconTile
import app.keeply.ui.theme.Space
import app.keeply.ui.components.KeeplyButton
import app.keeply.ui.components.KeeplyCard
import app.keeply.ui.theme.Keeply

/**
 * Explains why before asking. Shown only where reminders matter (Activity, Settings),
 * never as a cold prompt at launch. Falls back to system settings after a denial.
 */
@Composable
fun NotificationPermissionCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Notifications.canPost(context)) }
    var askedOnce by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = Notifications.canPost(context) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted && Notifications.canPost(context)
        askedOnce = true
    }
    if (allowed) return

    KeeplyCard(modifier, tone = CardTone.Warm, spacing = Space.sm) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            IconTile(Icons.Outlined.NotificationsActive, Keeply.colors.surfaceRaised to Keeply.colors.link)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text("Turn on reminders", style = Keeply.type.bodyMdMedium, color = Keeply.colors.text)
                Text(
                    "Get a heads-up before a subscription renews or a warranty ends. Only for items you've added.",
                    style = Keeply.type.bodySm, color = Keeply.colors.textMuted,
                )
            }
        }
        KeeplyButton(
            text = if (askedOnce || Build.VERSION.SDK_INT < 33) "Open notification settings" else "Allow notifications",
            onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !askedOnce) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Primary, size = ButtonSize.Md,
        )
    }
}
