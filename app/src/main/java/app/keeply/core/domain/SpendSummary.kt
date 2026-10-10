package app.keeply.core.domain

import app.keeply.core.model.BillingCycle
import app.keeply.core.model.Money
import app.keeply.core.model.Subscription
import app.keeply.core.model.SubscriptionStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A renewal on the spend card: "Claude", "Tomorrow", "$76.00". */
data class NextRenewal(val name: String, val due: String, val price: String)

/**
 * What subscriptions cost, as shown on the Home spend card and the home-screen widget: the
 * monthly figure (an average when some plans aren't monthly), the yearly total, the next few
 * renewals and what's due this week. Only one currency is added up; others are mentioned.
 */
data class SpendSummary(
    val monthly: String,
    val yearly: String,
    /** True when some aren't monthly, so the monthly figure is an average. */
    val monthlyIsAverage: Boolean,
    /** Every active subscription, whatever its currency. */
    val activeSubs: Int,
    val otherCurrencyNote: String?,
    /** The next few renewals in the card's currency within [RENEWAL_DAYS] days, soonest first. */
    val nextRenewals: List<NextRenewal>,
    /** Total of the renewals due in the next 7 days (today included), in the card's currency. */
    val dueThisWeek: String?,
    val dueThisWeekCount: Int,
) {
    companion object {
        /** How many renewals the card cycles through. */
        const val MAX_RENEWALS = 5
        /** How far ahead the card looks for renewals. */
        const val RENEWAL_DAYS = 30L

        /**
         * When each active subscription next bills. Stored billing dates only roll forward when the
         * daily worker runs, so one that has just passed would otherwise drop out until then.
         */
        fun dueDates(subs: List<Subscription>, today: LocalDate): Map<Subscription, LocalDate> =
            subs.filter { it.status == SubscriptionStatus.ACTIVE }.associateWith { s ->
                runCatching { SubscriptionMath.nextBillingOnOrAfter(s.nextBillingDate, s.cycle, s.customIntervalDays, today) }
                    .getOrDefault(s.nextBillingDate)
            }

        /** "This week" is the next 7 days counting today, for the card and the attention list alike. */
        fun isThisWeek(date: LocalDate, today: LocalDate) = !date.isBefore(today) && !date.isAfter(today.plusDays(6))

        /** Null when there's no active subscription to sum up. */
        fun of(subs: List<Subscription>, defaultCurrency: String, today: LocalDate): SpendSummary? {
            val totals = SubscriptionMath.totals(subs)
            // Show the user's own currency first; others are mentioned, never added together.
            val primary = totals.firstOrNull { it.currencyCode == defaultCurrency } ?: totals.firstOrNull() ?: return null
            val others = totals.filter { it != primary }
            val dueOn = dueDates(subs, today)
            // Renewal figures stay in the card's currency so they line up with its total.
            val inPrimary = dueOn.keys.filter { it.price.currencyCode == primary.currencyCode }
            val horizon = today.plusDays(RENEWAL_DAYS)
            val soonest = inPrimary
                .filter { dueOn.getValue(it).let { d -> !d.isBefore(today) && !d.isAfter(horizon) } }
                .sortedBy { dueOn.getValue(it) }.take(MAX_RENEWALS)
            val weekDue = inPrimary.filter { isThisWeek(dueOn.getValue(it), today) }
            val dueFmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
            return SpendSummary(
                monthly = primary.monthly.format(),
                yearly = primary.yearly.format(),
                monthlyIsAverage = inPrimary.any { it.cycle != BillingCycle.MONTHLY },
                activeSubs = dueOn.size,
                otherCurrencyNote = if (others.isNotEmpty()) "Plus " + others.joinToString { "${it.monthly.format()} / month in ${it.currencyCode}" } else null,
                nextRenewals = soonest.map { s ->
                    val date = dueOn.getValue(s)
                    val days = ChronoUnit.DAYS.between(today, date)
                    NextRenewal(s.serviceName, if (days <= 1) RelativeDays.countdown(days) else date.format(dueFmt), s.price.format())
                },
                dueThisWeek = weekDue.takeIf { it.isNotEmpty() }?.let { list -> Money(list.sumOf { it.price.amountMinor }, primary.currencyCode).format() },
                dueThisWeekCount = weekDue.size,
            )
        }
    }
}
