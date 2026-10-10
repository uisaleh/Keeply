package app.keeply.notifications

import app.keeply.data.repo.dateTimeToLocalMinute
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.keeply.KeeplyApp
import app.keeply.core.domain.ReminderSchedule
import app.keeply.core.domain.nextAlert
import app.keeply.core.model.ItemType
import app.keeply.core.model.ReminderNote
import app.keeply.core.model.Repeat
import app.keeply.data.db.ItemEntity
import app.keeply.data.repo.localMinuteToDateTime
import app.keeply.data.settings.NotificationCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Alarms for timed reminders. Each (reminder, alert) pair gets one alarm for its next
 * occurrence; [reconcile] makes the scheduled set match the vault exactly, so edits,
 * deletions, sign-out and items synced from another phone are all handled the same way.
 */
class ReminderAlarms(context: Context) {
    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(AlarmManager::class.java)
    private val prefs = appContext.getSharedPreferences("keeply_reminder_alarms", Context.MODE_PRIVATE)

    /** On Android 12+ the user can deny exact alarms; then alerts may arrive a few minutes late. */
    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    @Synchronized
    fun reconcile(notes: List<ReminderNote>, itemAlerts: List<ItemEntity> = emptyList()) {
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val wanted = mutableMapOf<String, Long>() // key "itemId|minutesBefore" -> trigger time
        for (note in notes) {
            val first = note.date.atTime(note.minuteOfDay / 60, note.minuteOfDay % 60)
            for (before in note.alertsMinutesBefore) {
                val occ = note.nextAlert(before, now) ?: continue
                wanted["${note.meta.id}|$before"] = occ.minusMinutes(before.toLong()).atZone(zone).toInstant().toEpochMilli()
            }
        }
        // Receipt and link alerts: one-off, at the chosen local time.
        for (item in itemAlerts) {
            val first = item.remindAtLocalMinute?.let(::localMinuteToDateTime) ?: continue
            val repeat = item.remindRepeat?.let { runCatching { Repeat.valueOf(it) }.getOrNull() } ?: Repeat.NEVER
            val at = ReminderSchedule.nextFor(first, repeat, 0, now) ?: continue
            wanted["$ITEM_PREFIX${item.id}"] = at.atZone(zone).toInstant().toEpochMilli()
        }
        val scheduled = prefs.getStringSet(KEY, emptySet())!!
        (scheduled - wanted.keys).forEach { cancel(it) }
        wanted.forEach { (key, at) -> schedule(key, at) }
        prefs.edit().putStringSet(KEY, wanted.keys).apply()
    }

    private fun intent(key: String) = Intent(appContext, ReminderAlarmReceiver::class.java)
        .setAction(ACTION_FIRE)
        // The data URI makes each alarm's PendingIntent distinct (extras alone don't).
        .setData(Uri.parse("keeply://reminder/${Uri.encode(key)}"))
        .putExtra(EXTRA_KEY, key)

    private fun pending(key: String, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(appContext, 0, intent(key), flags or PendingIntent.FLAG_IMMUTABLE)

    private fun schedule(key: String, at: Long) {
        val pi = pending(key, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(key: String) {
        pending(key, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
    }

    companion object {
        private const val KEY = "scheduled"
        const val ACTION_FIRE = "app.keeply.action.REMINDER_ALARM"
        const val EXTRA_KEY = "key"
        const val ITEM_PREFIX = "item:"

        /** Re-reads the vault and re-arms everything (after a reboot, clock change or an alarm firing). */
        fun rescheduleAll(context: Context, then: () -> Unit = {}) {
            val app = context.applicationContext as KeeplyApp
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    app.container.reminderAlarms.reconcile(app.container.vault.allReminderNotes(), app.container.vault.itemAlerts())
                } finally {
                    then()
                }
            }
        }
    }
}

/** Posts the reminder, then schedules its next occurrence (for repeating ones). */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(ReminderAlarms.EXTRA_KEY) ?: return
        if (key.startsWith(ReminderAlarms.ITEM_PREFIX)) return fireItemAlert(context, key)
        val itemId = key.substringBefore('|')
        val before = key.substringAfter('|').toIntOrNull() ?: 0
        val app = context.applicationContext as KeeplyApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val note = app.container.vault.reminderNote(itemId)
                val enabled = NotificationCategory.REMINDERS in app.container.settings.settings.first().enabledNotifications
                if (note != null && enabled) {
                    val first = note.date.atTime(note.minuteOfDay / 60, note.minuteOfDay % 60)
                    // The alarm fired for the occurrence `before` minutes from now.
                    val occ = ReminderSchedule.nextFor(first, note.repeat, before, LocalDateTime.now().minusMinutes(1)) ?: first
                    val (title, body) = ReminderText.forNote(note.category.label, note.meta.title, before, occ)
                    Notifications.post(
                        context, NotificationCategory.REMINDERS, key.hashCode(), itemId, title, body,
                        details = note.meta.notes, withActions = true, occurrenceMinute = dateTimeToLocalMinute(occ),
                    )
                    app.container.vault.logReminded(itemId)
                }
                app.container.reminderAlarms.reconcile(app.container.vault.allReminderNotes(), app.container.vault.itemAlerts())
            } finally {
                pending.finish()
            }
        }
    }

    /** A receipt's or link's own alert: "Return the headphones" / "Receipt · Today, 6:00 PM". */
    private fun fireItemAlert(context: Context, key: String) {
        val itemId = key.removePrefix(ReminderAlarms.ITEM_PREFIX)
        val app = context.applicationContext as KeeplyApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val item = app.container.vault.item(itemId)
                val first = item?.remindAtLocalMinute?.let(::localMinuteToDateTime)
                val repeat = item?.remindRepeat?.let { runCatching { Repeat.valueOf(it) }.getOrNull() } ?: Repeat.NEVER
                // The occurrence that just fired (a minute ago at most).
                val at = first?.let { ReminderSchedule.nextFor(it, repeat, 0, LocalDateTime.now().minusMinutes(2)) ?: it }
                val enabled = NotificationCategory.REMINDERS in app.container.settings.settings.first().enabledNotifications
                if (item != null && at != null && enabled) {
                    val label = runCatching { ItemType.valueOf(item.type).label }.getOrDefault("Item")
                    val (title, body) = ReminderText.forNote(label, item.title, 0, at)
                    Notifications.post(context, NotificationCategory.REMINDERS, key.hashCode(), itemId, title, body, details = item.notes, withActions = true)
                    app.container.vault.logReminded(itemId)
                }
                app.container.reminderAlarms.reconcile(app.container.vault.allReminderNotes(), app.container.vault.itemAlerts())
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms don't survive a reboot or a clock/time-zone change; re-arm them. */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        ReminderAlarms.rescheduleAll(context) { pending.finish() }
    }
}
