package app.keeply.notifications

import app.keeply.data.settings.NotificationCategory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Reminder wording, shared by the real reminders and the in-app preview so they always match. */
object ReminderText {
    fun forCategory(category: NotificationCategory, itemTitle: String, daysLeft: Int): Pair<String, String> = when (category) {
        NotificationCategory.SUBSCRIPTIONS ->
            "$itemTitle renews ${whenText(daysLeft)}" to "Open Keeply to review this payment."
        NotificationCategory.WARRANTIES ->
            "$itemTitle warranty expires ${whenText(daysLeft)}" to "Check whether you need a repair or claim before then."
        NotificationCategory.DOCUMENTS ->
            "$itemTitle expires ${whenText(daysLeft)}" to "Renew it in time so you're not caught without it."
        NotificationCategory.REMINDERS -> "$itemTitle ${whenText(daysLeft)}" to "Open Keeply for the details."
    }

    /**
     * A timed reminder: "Physics final" / "In 1 hour · 9:00 AM". The note itself is left out
     * because it may be private and notifications can show on the lock screen.
     */
    fun forNote(categoryLabel: String, itemTitle: String, minutesBefore: Int, at: LocalDateTime): Pair<String, String> {
        val time = at.toLocalTime().format(TIME)
        val day = when (at.toLocalDate()) {
            LocalDate.now() -> "Today"
            LocalDate.now().plusDays(1) -> "Tomorrow"
            else -> at.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
        val lead = when {
            minutesBefore <= 0 -> "Now"
            minutesBefore < 60 -> "In $minutesBefore min"
            minutesBefore < 1440 -> "In ${minutesBefore / 60} hour${if (minutesBefore >= 120) "s" else ""}"
            minutesBefore < 10080 -> "In ${minutesBefore / 1440} day${if (minutesBefore >= 2880) "s" else ""}"
            else -> "In ${minutesBefore / 10080} week${if (minutesBefore >= 20160) "s" else ""}"
        }
        return itemTitle to "$categoryLabel · $lead · $day, $time"
    }

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    private fun whenText(days: Int) = when (days) {
        0 -> "today"
        1 -> "tomorrow"
        else -> "in $days days"
    }
}
