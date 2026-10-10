package app.keeply.core.domain

import app.keeply.core.model.BillingCycle
import app.keeply.core.model.Receipt
import app.keeply.core.model.Subscription
import app.keeply.core.model.SubscriptionStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Spending in one bucket (a day or a month), in minor units (e.g. cents), split by source. */
data class SpendBucket(val start: LocalDate, val receiptsMinor: Long, val subscriptionsMinor: Long) {
    val totalMinor get() = receiptsMinor + subscriptionsMinor
}

enum class Granularity { DAY, MONTH }

data class SpendingSummary(
    val currency: String,
    val from: LocalDate,
    val to: LocalDate,
    val granularity: Granularity,
    val buckets: List<SpendBucket>,
    /** Items in other currencies aren't converted or added; how many were left out. */
    val otherCurrencyItems: Int,
    /** Last day that has happened: buckets after it have no data yet (null in the chart). */
    val asOf: LocalDate,
    /** Subscription charges inside the range but after today: due, not yet spent. */
    val dueLaterMinor: Long = 0,
    val dueLaterCount: Int = 0,
) {
    val receiptsMinor get() = buckets.sumOf { it.receiptsMinor }
    val subscriptionsMinor get() = buckets.sumOf { it.subscriptionsMinor }
    val totalMinor get() = receiptsMinor + subscriptionsMinor
}

/** The date ranges offered in the filter. */
enum class SpendRange(val label: String) {
    TODAY("Today"), THIS_WEEK("This week"), LAST_WEEK("Last week"), THIS_MONTH("This month"),
    LAST_MONTH("Last month"), LAST_3_MONTHS("Last 3 months"), LAST_12_MONTHS("Last 12 months"), CUSTOM("Custom range");

    /** Inclusive dates; weeks start on Monday. CUSTOM is chosen by the user, so it has none. */
    fun dates(today: LocalDate): Pair<LocalDate, LocalDate>? = when (this) {
        TODAY -> today to today
        THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
        LAST_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1).let { it to it.plusDays(6) }
        THIS_MONTH -> YearMonth.from(today).let { it.atDay(1) to it.atEndOfMonth() }
        LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
        LAST_3_MONTHS -> YearMonth.from(today).minusMonths(2).atDay(1) to YearMonth.from(today).atEndOfMonth()
        LAST_12_MONTHS -> YearMonth.from(today).minusMonths(11).atDay(1) to YearMonth.from(today).atEndOfMonth()
        CUSTOM -> null
    }
}

object SpendingOverview {
    /** Up to two months reads best day by day; longer ranges by month. */
    fun granularityFor(from: LocalDate, to: LocalDate) =
        if (ChronoUnit.DAYS.between(from, to) <= 62) Granularity.DAY else Granularity.MONTH

    /**
     * Spending between [from] and [to] (inclusive). Receipts count on their purchase date.
     * Subscriptions count payments the user marked (on the day paid), plus renewals after the
     * day the subscription was added that weren't marked (auto-renewal assumed). Only what has happened by [today] counts as spent;
     * charges later in the range are reported as due. Cancelled subscriptions are left out
     * (their past dates aren't known).
     */
    fun compute(
        receipts: List<Receipt>, subs: List<Subscription>, currency: String, from: LocalDate, to: LocalDate, today: LocalDate,
        payments: List<app.keeply.core.model.Payment> = emptyList(),
    ): SpendingSummary {
        val g = granularityFor(from, to)
        fun key(d: LocalDate) = if (g == Granularity.DAY) d else d.withDayOfMonth(1)
        val keys = generateSequence(key(from)) { if (g == Granularity.DAY) it.plusDays(1) else it.plusMonths(1) }
            .takeWhile { !it.isAfter(to) }.toList()
        val receiptSums = mutableMapOf<LocalDate, Long>()
        val subSums = mutableMapOf<LocalDate, Long>()
        var other = 0
        var dueLater = 0L
        var dueLaterCount = 0

        for (r in receipts) {
            if (r.meta.markedAt != null) continue // refunded
            if (r.total.currencyCode != currency) { other++; continue }
            if (r.purchaseDate.isBefore(from) || r.purchaseDate.isAfter(to)) continue
            receiptSums.merge(key(r.purchaseDate), r.total.amountMinor, Long::plus)
        }
        // Payments the user recorded count on the day they were paid…
        val covered = payments.map { it.itemId to it.forDate }.toSet()
        val activeIds = subs.map { it.meta.id }.toSet()
        for (p in payments) {
            if (p.itemId !in activeIds) continue
            if (p.amount.currencyCode != currency) { other++; continue }
            if (p.paidOn.isBefore(from) || p.paidOn.isAfter(to)) continue
            subSums.merge(key(p.paidOn), p.amount.amountMinor, Long::plus)
        }
        // …and renewals nobody recorded are assumed paid on their date (auto-renewal), never twice.
        for (s in subs) {
            if (s.status != SubscriptionStatus.ACTIVE) continue
            if (s.price.currencyCode != currency) { other++; continue }
            // Only renewals after the day it was added are assumed; before that, nobody told us.
            val since = maxOf(from, s.meta.createdAt.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1))
            val move = { d: LocalDate, dir: Long -> step(d, s.cycle, s.customIntervalDays, dir) }
            // Upcoming: the next billing date and the cycles after it. Days between today and the
            // next billing date aren't charges, so nothing is invented there.
            var charge: LocalDate? = s.nextBillingDate
            var guard = 0
            while (charge != null && !charge.isAfter(to) && guard++ < 5_000) {
                if (!charge.isBefore(from) && (s.meta.id to charge) !in covered) {
                    if (charge.isAfter(today)) { dueLater += s.price.amountMinor; dueLaterCount++ }
                    else subSums.merge(key(charge), s.price.amountMinor, Long::plus)
                }
                charge = move(charge, +1)
            }
            // Past: renewals before the next billing date, since the subscription was added.
            charge = move(s.nextBillingDate, -1)
            guard = 0
            while (charge != null && !charge.isBefore(since) && guard++ < 5_000) {
                if (!charge.isAfter(to) && !charge.isAfter(today) && (s.meta.id to charge) !in covered) {
                    subSums.merge(key(charge), s.price.amountMinor, Long::plus)
                }
                charge = move(charge, -1)
            }
        }
        return SpendingSummary(
            currency, from, to, g,
            keys.map { SpendBucket(it, receiptSums[it] ?: 0, subSums[it] ?: 0) },
            other, asOf = minOf(today, to), dueLaterMinor = dueLater, dueLaterCount = dueLaterCount,
        )
    }

    private fun step(d: LocalDate, cycle: BillingCycle, customDays: Int?, dir: Long): LocalDate? = when (cycle) {
        BillingCycle.WEEKLY -> d.plusWeeks(dir)
        BillingCycle.MONTHLY -> d.plusMonths(dir)
        BillingCycle.QUARTERLY -> d.plusMonths(3 * dir)
        BillingCycle.YEARLY -> d.plusYears(dir)
        BillingCycle.CUSTOM -> customDays?.takeIf { it > 0 }?.let { d.plusDays(it * dir) }
    }
}
