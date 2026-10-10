package app.keeply

import app.keeply.core.domain.SpendSummary
import app.keeply.core.model.BillingCycle
import app.keeply.core.model.ItemMeta
import app.keeply.core.model.ItemType
import app.keeply.core.model.Money
import app.keeply.core.model.Subscription
import app.keeply.core.model.SubscriptionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

class SpendSummaryTest {

    private val today = LocalDate.of(2026, 10, 4)

    private fun sub(
        name: String, amount: Long, next: LocalDate, cycle: BillingCycle = BillingCycle.MONTHLY,
        currency: String = "USD", status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    ) = Subscription(
        meta = ItemMeta(name, ItemType.SUBSCRIPTION, name, "", "", emptyList(), false, false, false, Instant.EPOCH, Instant.EPOCH),
        serviceName = name, price = Money(amount, currency), cycle = cycle, customIntervalDays = null,
        nextBillingDate = next, category = "", paymentMethod = "", status = status, reminderOffsetsDays = emptyList(),
    )

    // Dates and amounts are formatted for the phone's locale; pin it so the expectations hold.
    @Before fun pinLocale() = Locale.setDefault(Locale.US)

    private fun summary(vararg subs: Subscription, currency: String = "USD") = SpendSummary.of(subs.toList(), currency, today)

    @Test
    fun noActiveSubscriptionsMeansNoCard() {
        assertNull(summary())
        assertNull(summary(sub("Old", 500, today, status = SubscriptionStatus.CANCELLED)))
    }

    @Test
    fun renewalsAreSoonestFirstAndOnlyWithinThirtyDays() {
        val s = summary(
            sub("Later", 999, today.plusDays(30)),
            sub("Soon", 7600, today.plusDays(1)),
            sub("TooFar", 6700, today.plusDays(31)),
        )!!
        assertEquals(listOf("Soon", "Later"), s.nextRenewals.map { it.name })
        assertEquals("Tomorrow", s.nextRenewals.first().due)
    }

    @Test
    fun aDatePassedBeforeTheDailyRollStillCountsAsItsNextRenewal() {
        // Billed yesterday but not rolled forward yet: next is a month on, not dropped.
        val s = summary(sub("Netflix", 999, today.minusDays(1)))!!
        assertEquals(listOf("Netflix"), s.nextRenewals.map { it.name })
        assertEquals("Nov 3", s.nextRenewals.single().due)
    }

    @Test
    fun dueThisWeekIsTheNextSevenDaysCountingToday() {
        val s = summary(
            sub("Today", 100, today),
            sub("Day6", 200, today.plusDays(6)),
            sub("Day7", 400, today.plusDays(7)),
        )!!
        assertEquals(2, s.dueThisWeekCount)
        assertEquals("$3.00", s.dueThisWeek)
    }

    @Test
    fun otherCurrenciesAreMentionedNotAddedOrListed() {
        val s = summary(
            sub("Usd", 1000, today.plusDays(2)),
            sub("Eur", 1200, today.plusDays(1), currency = "EUR"),
        )!!
        assertEquals("$10.00", s.monthly)
        assertEquals(2, s.activeSubs)
        assertEquals(listOf("Usd"), s.nextRenewals.map { it.name })
        assertTrue(s.otherCurrencyNote!!.contains("EUR"))
    }

    @Test
    fun monthlyIsAnAverageOnlyWhenSomePlansArent() {
        assertFalse(summary(sub("A", 1000, today.plusDays(3)))!!.monthlyIsAverage)
        assertTrue(summary(sub("A", 1000, today.plusDays(3)), sub("B", 12000, today.plusDays(90), BillingCycle.YEARLY))!!.monthlyIsAverage)
    }
}
