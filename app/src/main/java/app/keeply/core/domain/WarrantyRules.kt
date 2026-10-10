package app.keeply.core.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class WarrantyStatus(val label: String) {
    ACTIVE("Active"),
    EXPIRING_SOON("Expiring soon"),
    EXPIRED("Expired"),
}

object WarrantyRules {
    const val DEFAULT_EXPIRING_SOON_DAYS = 60L

    fun expiryFrom(purchaseDate: LocalDate, durationMonths: Int): LocalDate {
        require(durationMonths > 0) { "Duration must be positive" }
        return purchaseDate.plusMonths(durationMonths.toLong())
    }

    fun status(
        expiryDate: LocalDate,
        today: LocalDate,
        expiringSoonDays: Long = DEFAULT_EXPIRING_SOON_DAYS,
    ): WarrantyStatus {
        val daysLeft = ChronoUnit.DAYS.between(today, expiryDate)
        return when {
            daysLeft < 0 -> WarrantyStatus.EXPIRED
            daysLeft <= expiringSoonDays -> WarrantyStatus.EXPIRING_SOON
            else -> WarrantyStatus.ACTIVE
        }
    }

    fun daysUntil(date: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)
}

/** Human phrasing shared by the dashboard, lists and notifications. */
object RelativeDays {
    /** Short countdown for badges: "Today", "Tomorrow", "In 5 days". */
    fun countdown(days: Long): String = when {
        days <= 0L -> "Today"
        days == 1L -> "Tomorrow"
        else -> "In $days days"
    }

    fun describeUntil(date: LocalDate, today: LocalDate, verb: String): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days < -1 -> "${verb.replaceFirstChar { it.uppercase() }}d ${-days} days ago"
            days == -1L -> "${verb.replaceFirstChar { it.uppercase() }}d yesterday"
            days == 0L -> "${verb.replaceFirstChar { it.uppercase() }}s today"
            days == 1L -> "${verb.replaceFirstChar { it.uppercase() }}s tomorrow"
            days < 60 -> "${verb.replaceFirstChar { it.uppercase() }}s in $days days"
            else -> {
                val months = ChronoUnit.MONTHS.between(today, date)
                // "in 5 years" reads better than "in 60 months" for passports and the like.
                if (months >= 24) "${verb.replaceFirstChar { it.uppercase() }}s in ${months / 12} years"
                else "${verb.replaceFirstChar { it.uppercase() }}s in $months months"
            }
        }
    }
}
