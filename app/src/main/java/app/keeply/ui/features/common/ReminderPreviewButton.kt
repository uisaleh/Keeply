package app.keeply.ui.features.common

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import app.keeply.data.settings.NotificationCategory
import app.keeply.notifications.Notifications
import app.keeply.notifications.ReminderText
import app.keeply.ui.components.ButtonSize
import app.keeply.ui.components.ButtonVariant
import app.keeply.ui.components.KeeplyButton
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space

/**
 * Shows the real reminder notification right now, with the same wording and icon it will
 * have before the date. Asks for notification permission first when needed.
 */
@Composable
fun ReminderPreviewButton(category: NotificationCategory, itemTitle: String, daysBefore: Int, itemId: String?) =
    ReminderPreviewButton(
        category, itemId,
        caption = "See exactly how the reminder will look ${if (daysBefore == 0) "on the day" else "$daysBefore ${if (daysBefore == 1) "day" else "days"} before"}.",
    ) { ReminderText.forCategory(category, itemTitle, daysBefore) }

/** Same button for any wording, e.g. a timed reminder's "In 1 hour · 9:00 AM". */
@Composable
fun ReminderPreviewButton(category: NotificationCategory, itemId: String?, caption: String, details: String? = null, content: () -> Pair<String, String>) {
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }

    fun post() {
        val (title, body) = content()
        Notifications.post(context, category, Notifications.PREVIEW_ID, itemId, title, body, details, withActions = true, preview = true)
        note = "Sent. Pull down the notification shade, or tap it to snooze or mark it done."
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && Notifications.canPost(context)) post()
        else note = "Notifications are off for Keeply, so reminders can't be shown. Turn them on in system settings."
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        KeeplyButton(
            "Preview reminder",
            onClick = {
                when {
                    Notifications.canPost(context) -> post()
                    Build.VERSION.SDK_INT >= 33 -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Soft, size = ButtonSize.Md, leadingIcon = Icons.Outlined.NotificationsActive,
        )
        Text(note ?: caption, style = Keeply.type.caption, color = Keeply.colors.textMuted)
    }
}
