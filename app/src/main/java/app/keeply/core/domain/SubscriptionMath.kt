package app.keeply.core.domain

import app.keeply.core.model.BillingCycle
import app.keeply.core.model.Money
import app.keeply.core.model.Subscription
import app.keeply.core.model.SubscriptionStatus
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/** Pure subscription arithmetic. No Android dependencies, fully unit-tested. */
object SubscriptionMath {

    private val MC = MathContext.DECIMAL64
    private val DAYS_PER_YEAR = BigDecimal("365.25")

    /** How many times this cycle bills in one year. */
    fun chargesPerYear(cycle: BillingCycle, customIntervalDays: Int?): BigDecimal = when (cycle) {
        BillingCycle.WEEKLY -> BigDecimal(52)
        BillingCycle.MONTHLY -> BigDecimal(12)
        BillingCycle.QUARTERLY -> BigDecimal(4)
        BillingCycle.YEARLY -> BigDecimal.ONE
        BillingCycle.CUSTOM -> {
            val days = requireNotNull(customIntervalDays) { "Custom cycle needs an interval" }
            require(days > 0) { "Interval must be positive" }
            DAYS_PER_YEAR.divide(BigDecimal(days), MC)
        }
    }

    fun annualCost(price: Money, cycle: BillingCycle, customIntervalDays: Int?): Money =
        Money.fromDecimal(
            price.toDecimal().multiply(chargesPerYear(cycle, customIntervalDays), MC),
            price.currencyCode,
        )

    data class CurrencyTotals(val currencyCode: String, val monthly: Money, val yearly: Money, val count: Int)

    /**
     * Totals for active subscriptions, grouped by currency (never converted).
     * Yearly is the sum of exact annual costs; monthly is yearly / 12, so a set of
     * monthly subscriptions totalling 50.97 projects to exactly 611.64.
     */
    fun totals(subscriptions: List<Subscription>): List<CurrencyTotals> =
        subscriptions
            .filter { it.status == SubscriptionStatus.ACTIVE }
            .groupBy { it.price.currencyCode }
            .map { (code, subs) ->
                val yearly = subs.fold(BigDecimal.ZERO) { acc, s ->
                    acc + s.price.toDecimal().multiply(chargesPerYear(s.cycle, s.customIntervalDays), MC)
                }
                val monthly = yearly.divide(BigDecimal(12), MC)
                CurrencyTotals(
                    currencyCode = code,
                    monthly = Money.fromDecimal(monthly.setScale(Money.fractionDigits(code), RoundingMode.HALF_UP), code),
                    yearly = Money.fromDecimal(yearly, code),
                    count = subs.size,
                )
            }
            .sortedByDescending { it.count }

    /**
     * Rolls a billing date forward until it is on or after [today]. Month-based cycles
     * step from the original anchor so a 31st anchor lands on month-end, not drifting.
     */
    fun nextBillingOnOrAfter(
        anchor: LocalDate,
        cycle: BillingCycle,
        customIntervalDays: Int?,
        today: LocalDate,
    ): LocalDate {
        if (!anchor.isBefore(today)) return anchor
        var n = 1L
        while (true) {
            val candidate = when (cycle) {
                BillingCycle.WEEKLY -> anchor.plusWeeks(n)
                BillingCycle.MONTHLY -> anchor.plusMonths(n)
                BillingCycle.QUARTERLY -> anchor.plusMonths(3 * n)
                BillingCycle.YEARLY -> anchor.plusYears(n)
                BillingCycle.CUSTOM -> anchor.plusDays(requireNotNull(customIntervalDays) * n)
            }
            if (!candidate.isBefore(today)) return candidate
            n++
        }
    }
}
