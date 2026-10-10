package app.keeply.core.domain

import java.time.LocalDate

/**
 * Converts "remind me N days before" offsets into concrete dates.
 * Past dates are dropped (we never fire reminders for moments that already passed),
 * duplicates are collapsed, and results are sorted.
 */
object ReminderPlanner {
    val PRESET_OFFSETS = listOf(90, 60, 30, 7)
    val SUBSCRIPTION_DEFAULT = listOf(1)
    val WARRANTY_DEFAULT = listOf(30, 7)
    const val MAX_CUSTOM_OFFSET_DAYS = 365

    data class Planned(val offsetDays: Int, val date: LocalDate)

    fun plan(eventDate: LocalDate, offsetsDays: Collection<Int>, today: LocalDate): List<Planned> =
        offsetsDays
            .filter { it in 0..MAX_CUSTOM_OFFSET_DAYS }
            .distinct()
            .map { Planned(it, eventDate.minusDays(it.toLong())) }
            .filter { !it.date.isBefore(today) }
            .sortedBy { it.date }
}
