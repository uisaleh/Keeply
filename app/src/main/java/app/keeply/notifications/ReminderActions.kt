package app.keeply.notifications

import app.keeply.data.repo.localMinuteToDateTime
import app.keeply.core.model.ItemType
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import app.keeply.KeeplyApp
import app.keeply.data.settings.NotificationCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Everything needed to show, snooze or complete one reminder notification. */
data class ReminderAlert(
    val notificationId: Int,
    val itemId: String?,
    val category: NotificationCategory,
    val title: String,
    val body: String,
    val details: String?,
    val preview: Boolean,
    /** Timed reminders: the occurrence (local minute) this alert is about, so Done marks exactly it. */
    val occurrenceMinute: Long? = null,
)

/**
 * Whether a reminder sheet is still worth showing after the person went to its item and came
 * back: not if they marked it paid or done there (that settles the alert), or deleted the item.
 */
suspend fun ReminderAlert.stillWaiting(context: Context): Boolean {
    if (preview) return true
    val vault = (context.applicationContext as KeeplyApp).container.vault
    val outcome = vault.notification(notificationId)?.outcome
    if (outcome == "PAID" || outcome == "DONE") return false
    return itemId == null || vault.item(itemId) != null
}

/** Keeps an open reminder sheet across leaving the screen (e.g. to its item's details) and back. */
val ReminderAlertSaver: androidx.compose.runtime.saveable.Saver<ReminderAlert?, Any> = androidx.compose.runtime.saveable.listSaver(
    save = { a -> if (a == null) emptyList() else listOf(a.notificationId, a.itemId, a.category.name, a.title, a.body, a.details, a.preview, a.occurrenceMinute) },
    restore = { l ->
        if (l.isEmpty()) null
        else ReminderAlert(
            l[0] as Int, l[1] as String?, NotificationCategory.valueOf(l[2] as String), l[3] as String, l[4] as String,
            l[5] as String?, l[6] as Boolean, l[7] as Long?,
        )
    },
)

/**
 * The two buttons on a reminder notification:
 * - Snooze: timed reminders come back in 10 minutes; day-level ones (renewals, warranty ends,
 *   document expiry) come back tomorrow at 9:00.
 * - Mark as done: clears the alert and records "Done" in Activity.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DONE -> {
                val pending = goAsync()
                done(context, alertOf(intent)) { pending.finish() }
            }
            ACTION_SNOOZE -> {
                val alert = alertOf(intent)
                snooze(context, alert, defaultSnoozeAt(alert.category))
            }
            ACTION_REPOST -> {
                val a = alertOf(intent)
                val pending = goAsync()
                val app = context.applicationContext as KeeplyApp
                app.appScope.launch {
                    try {
                        // Don't bring it back if it was paid/done meanwhile, or the item is gone.
                        val vault = app.container.vault
                        val settled = !a.preview && vault.notification(a.notificationId)?.outcome.let { it != null && it != "SNOOZED" }
                        val gone = a.itemId != null && vault.typeOf(a.itemId) == null
                        if (!settled && !gone) Notifications.post(
                            context, a.category, a.notificationId, a.itemId, a.title, a.body,
                            details = a.details, withActions = true, preview = a.preview, occurrenceMinute = a.occurrenceMinute,
                        )
                    } finally { pending.finish() }
                }
            }
        }
    }

    companion object {
        const val SNOOZE_MINUTES = 10
        const val ACTION_DONE = "app.keeply.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "app.keeply.action.REMINDER_SNOOZE"
        private const val ACTION_REPOST = "app.keeply.action.REMINDER_REPOST"
        private const val EXTRA_NOTIFICATION_ID = "notificationId"
        private const val EXTRA_ITEM_ID = "itemId"
        private const val EXTRA_CATEGORY = "category"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_DETAILS = "details"

        private const val EXTRA_PREVIEW = "preview"
        private const val EXTRA_OCCURRENCE = "occurrence"

        fun Intent.putAlert(a: ReminderAlert): Intent = putExtra(EXTRA_NOTIFICATION_ID, a.notificationId).putExtra(EXTRA_ITEM_ID, a.itemId)
            .putExtra(EXTRA_CATEGORY, a.category.name).putExtra(EXTRA_TITLE, a.title).putExtra(EXTRA_BODY, a.body)
            .putExtra(EXTRA_DETAILS, a.details).putExtra(EXTRA_PREVIEW, a.preview)
            .apply { a.occurrenceMinute?.let { putExtra(EXTRA_OCCURRENCE, it) } }

        /** Null when the intent doesn't carry a reminder (e.g. an ordinary app launch). */
        fun alertOrNull(i: Intent): ReminderAlert? = if (i.hasExtra(EXTRA_TITLE) && i.hasExtra(EXTRA_CATEGORY)) alertOf(i) else null

        private fun alertOf(i: Intent) = ReminderAlert(
            notificationId = i.getIntExtra(EXTRA_NOTIFICATION_ID, 0),
            itemId = i.getStringExtra(EXTRA_ITEM_ID),
            category = runCatching { NotificationCategory.valueOf(i.getStringExtra(EXTRA_CATEGORY)!!) }.getOrDefault(NotificationCategory.REMINDERS),
            title = i.getStringExtra(EXTRA_TITLE).orEmpty(),
            body = i.getStringExtra(EXTRA_BODY).orEmpty(),
            details = i.getStringExtra(EXTRA_DETAILS),
            preview = i.getBooleanExtra(EXTRA_PREVIEW, false),
            occurrenceMinute = if (i.hasExtra(EXTRA_OCCURRENCE)) i.getLongExtra(EXTRA_OCCURRENCE, 0) else null,
        )

        fun intent(context: Context, action: String, alert: ReminderAlert): PendingIntent {
            val i = Intent(context, ReminderActionReceiver::class.java).setAction(action)
                .setData(Uri.parse("keeply://action/$action/${alert.notificationId}")) // distinct per notification and button
                .putAlert(alert)
            return PendingIntent.getBroadcast(context, alert.notificationId, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /** Timed reminders come back in 10 minutes; day-level ones tomorrow at 9:00. */
        fun defaultSnoozeAt(category: NotificationCategory): Long =
            if (category == NotificationCategory.REMINDERS) System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
            else LocalDate.now().plusDays(1).atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        /** Clears the alert now and shows it again at [atMillis]. */
        fun snooze(context: Context, alert: ReminderAlert, atMillis: Long) {
            NotificationManagerCompat.from(context).cancel(alert.notificationId)
            val keeply = context.applicationContext as KeeplyApp
            if (!alert.preview) keeply.appScope.launch { keeply.container.vault.resolveNotification(alert.notificationId, "SNOOZED", atMillis) }
            val repost = Intent(context, ReminderActionReceiver::class.java).setAction(ACTION_REPOST)
                .setData(Uri.parse("keeply://repost/${alert.notificationId}")).putAlert(alert)
            val pi = PendingIntent.getBroadcast(context, alert.notificationId, repost, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val alarms = context.getSystemService(AlarmManager::class.java)
            val app = context.applicationContext as KeeplyApp
            if (app.container.reminderAlarms.canScheduleExact()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }

        /**
         * What "done" means depends on the item: a subscription is marked paid (next date moves
         * on), a timed reminder marks that occurrence done; anything else is recorded as done.
         */
        private suspend fun markDone(app: KeeplyApp, id: String, alert: ReminderAlert) {
            val vault = app.container.vault
            when (vault.typeOf(id)) {
                ItemType.SUBSCRIPTION -> vault.markSubscriptionPaid(id)
                ItemType.REMINDER -> alert.occurrenceMinute?.let { vault.markReminderDone(id, localMinuteToDateTime(it)) }
                    ?: vault.logDone(id)
                else -> vault.logDone(id)
            }
        }

        /** Clears the alert and records "Done" (previews are only tests, so they aren't recorded). */
        fun done(context: Context, alert: ReminderAlert, then: () -> Unit = {}) {
            NotificationManagerCompat.from(context).cancel(alert.notificationId)
            val app = context.applicationContext as KeeplyApp
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    if (!alert.preview) {
                        val outcome = if (alert.itemId?.let { app.container.vault.typeOf(it) } == ItemType.SUBSCRIPTION) "PAID" else "DONE"
                        app.container.vault.resolveNotification(alert.notificationId, outcome)
                        alert.itemId?.let { id -> markDone(app, id, alert) }
                    }
                } finally {
                    then()
                }
            }
        }
    }
}
