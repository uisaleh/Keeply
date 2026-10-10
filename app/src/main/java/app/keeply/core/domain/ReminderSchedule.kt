package app.keeply.core.domain

import app.keeply.core.model.Repeat
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * When a timed reminder happens and when its alerts should fire. All in local date-time, so
 * a 9:00 birthday stays at 9:00 wherever the phone is.
 */
object ReminderSchedule {
    private const val MAX_STEPS = 5_000

    /** The [n]th occurrence, always counted from the first one so a 31st doesn't drift to the 28th. */
    fun occurrence(first: LocalDateTime, repeat: Repeat, n: Long): LocalDateTime = when (repeat) {
        Repeat.NEVER -> first
        Repeat.DAILY -> first.plusDays(n)
        Repeat.WEEKLY -> first.plusWeeks(n)
        Repeat.MONTHLY -> first.plusMonths(n)
        Repeat.YEARLY -> first.plusYears(n)
    }

    /**
     * The first occurrence whose alert, [minutesBefore] earlier, is still after [now];
     * null when a one-off reminder has already passed.
     */
    fun nextFor(first: LocalDateTime, repeat: Repeat, minutesBefore: Int, now: LocalDateTime): LocalDateTime? {
        if (repeat == Repeat.NEVER) return first.takeIf { it.minusMinutes(minutesBefore.toLong()).isAfter(now) }
        val unit = when (repeat) {
            Repeat.DAILY -> ChronoUnit.DAYS
            Repeat.WEEKLY -> ChronoUnit.WEEKS
            Repeat.MONTHLY -> ChronoUnit.MONTHS
            else -> ChronoUnit.YEARS
        }
        // Jump close to now instead of stepping through years of daily repeats.
        var n = max(0L, unit.between(first, now) - 1)
        repeat(MAX_STEPS) {
            val occ = occurrence(first, repeat, n)
            if (occ.minusMinutes(minutesBefore.toLong()).isAfter(now)) return occ
            n++
        }
        return null
    }

    /** The next time the event itself happens (now or later), for lists and "Upcoming". */
    fun nextOccurrence(first: LocalDateTime, repeat: Repeat, now: LocalDateTime): LocalDateTime? =
        nextFor(first, repeat, 0, now.minusSeconds(1))
}

/** When this reminder next happens, skipping any occurrence already marked done. */
fun app.keeply.core.model.ReminderNote.nextOccurrence(now: LocalDateTime): LocalDateTime? =
    nextAlert(0, now)

/** The next occurrence whose alert ([minutesBefore] earlier) is still ahead, after anything marked done. */
fun app.keeply.core.model.ReminderNote.nextAlert(minutesBefore: Int, now: LocalDateTime): LocalDateTime? {
    val first = date.atTime(minuteOfDay / 60, minuteOfDay % 60)
    val done = meta.doneThrough
    // occ - before > after  <=>  occ > done, when after = done - before.
    val after = if (done != null) maxOf(now, done.minusMinutes(minutesBefore.toLong())) else now
    if (repeat == app.keeply.core.model.Repeat.NEVER && done != null && !first.isAfter(done)) return null
    return ReminderSchedule.nextFor(first, repeat, minutesBefore, after)
}
