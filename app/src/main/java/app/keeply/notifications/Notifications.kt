package app.keeply.notifications

import kotlinx.coroutines.launch
import app.keeply.data.db.NotificationLogEntity
import app.keeply.KeeplyApp
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.keeply.MainActivity
import app.keeply.R
import app.keeply.data.settings.NotificationCategory
import app.keeply.security.IntentToken
import app.keeply.notifications.ReminderActionReceiver.Companion.putAlert

object Notifications {

    /** Fixed id so repeated previews replace each other instead of piling up. */
    const val PREVIEW_ID = 0x4B50 // "KP"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        NotificationCategory.entries.forEach { c ->
            manager.createNotificationChannel(
                // Timed reminders (an interview in 15 minutes) should pop up; the rest can wait in the shade.
                NotificationChannel(
                    c.channelId, c.label,
                    if (c == NotificationCategory.REMINDERS) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = c.description
                }
            )
        }
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Notification text contains only the item title and timing — never amounts of
     * documents, ID numbers or notes — and is hidden on the lock screen (VISIBILITY_PRIVATE).
     */
    fun post(
        context: Context, category: NotificationCategory, notificationId: Int, itemId: String?, title: String, body: String,
        /** Shown when the notification is expanded, e.g. the reminder's note. Hidden on the lock screen (private). */
        details: String? = null,
        /** Adds Snooze and Mark as done; tapping opens the in-app sheet with more snooze choices. */
        withActions: Boolean = false,
        /** A test from "Preview reminder": same buttons, but Done isn't recorded in history. */
        preview: Boolean = false,
        occurrenceMinute: Long? = null,
    ) {
        val alert = ReminderAlert(notificationId, itemId, category, title, body, details, preview, occurrenceMinute)
        // Real reminders go into the Notifications page history (previews are only tests).
        if (withActions && !preview) {
            val app = context.applicationContext as KeeplyApp
            app.appScope.launch {
                app.container.vault.logNotification(
                    NotificationLogEntity(
                        notificationId, itemId, category.name, title, body, details, occurrenceMinute, System.currentTimeMillis(),
                    )
                )
            }
        }
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            if (withActions) putAlert(alert) else if (itemId != null) putExtra(MainActivity.EXTRA_OPEN_ITEM_ID, itemId)
            IntentToken.sign(context, this)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notificationId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(R.drawable.ic_stat_keeply)
            .setContentTitle(title)
            .setContentText(body)
            .apply {
                if (!details.isNullOrBlank()) setStyle(NotificationCompat.BigTextStyle().bigText("$body\n\n${details.trim()}"))
            }
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .apply {
                if (withActions) {
                    val snoozeLabel = if (category == NotificationCategory.REMINDERS) "Snooze ${ReminderActionReceiver.SNOOZE_MINUTES} min" else "Remind me tomorrow"
                    addAction(0, snoozeLabel, ReminderActionReceiver.intent(context, ReminderActionReceiver.ACTION_SNOOZE, alert))
                    addAction(0, "Mark as done", ReminderActionReceiver.intent(context, ReminderActionReceiver.ACTION_DONE, alert))
                }
            }
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (ignored: SecurityException) {
            // Permission revoked between check and post; nothing to do.
        }
    }
}
