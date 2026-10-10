package app.keeply.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.keeply.KeeplyApp
import app.keeply.core.model.ItemType
import app.keeply.data.settings.NotificationCategory
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Runs about twice a day, entirely offline. Delivers due reminders, then rolls
 * subscriptions whose billing date has passed onto their next cycle.
 * Day-level precision is intentional: reminders are "N days before", not alarms,
 * so no exact-alarm permission is needed.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as KeeplyApp).container
        val dao = container.database.vaultDao()
        val settings = container.settings.settings.first()
        val today = LocalDate.now()

        val due = dao.dueReminders(today.toEpochDay())
        due.forEach { r ->
            val category = when (ItemType.valueOf(r.type)) {
                ItemType.SUBSCRIPTION -> NotificationCategory.SUBSCRIPTIONS
                ItemType.WARRANTY -> NotificationCategory.WARRANTIES
                ItemType.DOCUMENT -> NotificationCategory.DOCUMENTS
                else -> return@forEach
            }
            if (category !in settings.enabledNotifications) return@forEach
            // If the worker ran late, describe the real remaining time; skip events already past.
            val daysLeft = r.offsetDays - (today.toEpochDay() - r.triggerEpochDay).toInt()
            if (daysLeft < 0) return@forEach
            val (title, body) = ReminderText.forCategory(category, r.title, daysLeft)
            val notes = dao.item(r.itemId)?.notes
            Notifications.post(applicationContext, category, r.reminderId.toInt(), r.itemId, title, body, details = notes, withActions = true)
            container.vault.logReminded(r.itemId)
        }
        if (due.isNotEmpty()) dao.markDelivered(due.map { it.reminderId })

        container.vault.rollSubscriptionsForward()
        // "Tomorrow" becomes "Today", and passed renewals move on.
        app.keeply.widget.SpendWidget.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "keeply-reminders"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
