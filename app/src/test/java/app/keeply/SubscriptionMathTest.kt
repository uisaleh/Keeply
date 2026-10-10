package app.keeply

import app.keeply.core.domain.SubscriptionMath
import app.keeply.core.model.BillingCycle
import app.keeply.core.model.ItemMeta
import app.keeply.core.model.ItemType
import app.keeply.core.model.Money
import app.keeply.core.model.Subscription
import app.keeply.core.model.SubscriptionStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SubscriptionMathTest {

    private fun sub(amount: Long, cycle: BillingCycle, currency: String = "USD", status: SubscriptionStatus = SubscriptionStatus.ACTIVE, customDays: Int? = null) =
        Subscription(
            meta = ItemMeta("id$amount$cycle", ItemType.SUBSCRIPTION, "S", "", "", emptyList(), false, false, false, Instant.EPOCH, Instant.EPOCH),
            serviceName = "S", price = Money(amount, currency), cycle = cycle, customIntervalDays = customDays,
            nextBillingDate = LocalDate.of(2026, 1, 1), category = "", paymentMethod = "", status = status, reminderOffsetsDays = emptyList(),
        )

    @Test
    fun monthlyTotalsProjectExactlyTimesTwelve() {
        // 22.99 + 15.99 + 11.99 = 50.97 -> 611.64
        val totals = SubscriptionMath.totals(listOf(sub(2299, BillingCycle.MONTHLY), sub(1599, BillingCycle.MONTHLY), sub(1199, BillingCycle.MONTHLY)))
        assertEquals(1, totals.size)
        assertEquals(Money(5097, "USD"), totals[0].monthly)
        assertEquals(Money(61164, "USD"), totals[0].yearly)
    }

    @Test
    fun cancelledAndOtherCurrenciesAreSeparated() {
        val totals = SubscriptionMath.totals(listOf(
            sub(1000, BillingCycle.MONTHLY), sub(500, BillingCycle.MONTHLY, status = SubscriptionStatus.CANCELLED),
            sub(12000, BillingCycle.YEARLY, currency = "EUR"),
        ))
        assertEquals(2, totals.size)
        assertEquals(Money(12000, "USD"), totals.first { it.currencyCode == "USD" }.yearly)
        assertEquals(Money(1000, "EUR"), totals.first { it.currencyCode == "EUR" }.monthly)
    }

    @Test
    fun weeklyAndQuarterlyAnnualise() {
        assertEquals(Money(5200, "USD"), SubscriptionMath.annualCost(Money(100, "USD"), BillingCycle.WEEKLY, null))
        assertEquals(Money(4000, "USD"), SubscriptionMath.annualCost(Money(1000, "USD"), BillingCycle.QUARTERLY, null))
    }

    @Test
    fun monthlyRollForwardKeepsMonthEnd() {
        val next = SubscriptionMath.nextBillingOnOrAfter(LocalDate.of(2026, 1, 31), BillingCycle.MONTHLY, null, LocalDate.of(2026, 3, 5))
        assertEquals(LocalDate.of(2026, 3, 31), next)
    }

    @Test
    fun futureAnchorIsUnchanged() {
        val d = LocalDate.of(2026, 12, 1)
        assertEquals(d, SubscriptionMath.nextBillingOnOrAfter(d, BillingCycle.YEARLY, null, LocalDate.of(2026, 10, 3)))
    }

    @Test
    fun customIntervalRollsForward() {
        val next = SubscriptionMath.nextBillingOnOrAfter(LocalDate.of(2026, 1, 1), BillingCycle.CUSTOM, 10, LocalDate.of(2026, 1, 25))
        assertEquals(LocalDate.of(2026, 1, 31), next)
    }
}

class SpendingOverviewTest {
    private fun meta(type: ItemType, created: java.time.LocalDate) = ItemMeta("x", type, "t", "", "", emptyList(), false, false, false,
        created.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(), Instant.EPOCH)

    @org.junit.Test
    fun bucketsByMonthAndByDay() {
        val today = java.time.LocalDate.of(2026, 10, 3)
        val receipt = app.keeply.core.model.Receipt(meta(ItemType.RECEIPT, today), "Shop", app.keeply.core.model.Money(1_000, "USD"), java.time.LocalDate.of(2026, 9, 10), "", "", emptyList())
        val sub = Subscription(meta(ItemType.SUBSCRIPTION, java.time.LocalDate.of(2026, 8, 1)), "Claude", app.keeply.core.model.Money(2_000, "USD"),
            BillingCycle.MONTHLY, null, java.time.LocalDate.of(2026, 10, 5), "", "", SubscriptionStatus.ACTIVE, emptyList())
        val (from, to) = app.keeply.core.domain.SpendRange.LAST_12_MONTHS.dates(today)!!
        val year = app.keeply.core.domain.SpendingOverview.compute(listOf(receipt), listOf(sub), "USD", from, to, today)
        assertEquals(12, year.buckets.size)
        val byMonth = year.buckets.associateBy { it.start.monthValue }
        assertEquals(1_000, byMonth[9]!!.receiptsMinor)
        // Aug 5 and Sep 5 are spent; Oct 5 is still to come, so it's "due", not spent.
        assertEquals(2_000, byMonth[8]!!.subscriptionsMinor); assertEquals(0, byMonth[10]!!.subscriptionsMinor); assertEquals(0, byMonth[7]!!.subscriptionsMinor)
        assertEquals(2_000, year.dueLaterMinor); assertEquals(1, year.dueLaterCount)
        // Last month, day by day: the receipt on Sep 10 and the charge on Sep 5.
        val (f2, t2) = app.keeply.core.domain.SpendRange.LAST_MONTH.dates(today)!!
        val sep = app.keeply.core.domain.SpendingOverview.compute(listOf(receipt), listOf(sub), "USD", f2, t2, today)
        assertEquals(30, sep.buckets.size); assertEquals(3_000, sep.totalMinor)
        assertEquals(2_000, sep.buckets.first { it.start.dayOfMonth == 5 }.subscriptionsMinor)
    }
}

class AdditionsOverviewTest {
    @org.junit.Test
    fun countsAddedEventsByDayAndType() {
        val z = java.time.ZoneOffset.UTC
        fun ev(type: ItemType, kind: app.keeply.core.model.ActivityKind, day: Int) = app.keeply.core.model.ActivityEvent(
            day.toLong(), "i$day", type, "t", kind, java.time.LocalDate.of(2026, 10, day).atStartOfDay(z).toInstant())
        val events = listOf(
            ev(ItemType.LINK, app.keeply.core.model.ActivityKind.ADDED, 1), ev(ItemType.LINK, app.keeply.core.model.ActivityKind.ADDED, 3),
            ev(ItemType.RECEIPT, app.keeply.core.model.ActivityKind.ADDED, 3), ev(ItemType.LINK, app.keeply.core.model.ActivityKind.EDITED, 3),
        )
        val s = app.keeply.core.domain.AdditionsOverview.compute(events, java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 10, 31), z)
        assertEquals(3, s.total)
        assertEquals(2, s.buckets.first { it.start.dayOfMonth == 3 }.count)
        assertEquals(ItemType.LINK to 2, s.byType.first())
    }
}

class MarkPaidSpendingTest {
    private fun meta(type: ItemType, created: java.time.LocalDate) = ItemMeta("sub", type, "t", "", "", emptyList(), false, false, false,
        created.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(), Instant.EPOCH)

    @org.junit.Test
    fun paidEarlyCountsOnPaymentDayAndIsNotDueTwice() {
        val today = java.time.LocalDate.of(2026, 10, 3)
        // Renewal was due Oct 5; marked paid on Oct 3, so next billing moved to Nov 5.
        val sub = Subscription(meta(ItemType.SUBSCRIPTION, java.time.LocalDate.of(2026, 10, 1)), "Claude", Money(7_600, "USD"),
            BillingCycle.MONTHLY, null, java.time.LocalDate.of(2026, 11, 5), "", "", SubscriptionStatus.ACTIVE, emptyList())
        val pay = app.keeply.core.model.Payment("p", "sub", Money(7_600, "USD"), today, java.time.LocalDate.of(2026, 10, 5))
        val (from, to) = app.keeply.core.domain.SpendRange.THIS_MONTH.dates(today)!!
        val s = app.keeply.core.domain.SpendingOverview.compute(emptyList(), listOf(sub), "USD", from, to, today, listOf(pay))
        assertEquals(7_600, s.subscriptionsMinor)   // spent today
        assertEquals(0, s.dueLaterMinor)           // Oct 5 is covered, not "still due"
        assertEquals(7_600, s.buckets.first { it.start.dayOfMonth == 3 }.subscriptionsMinor)
    }
}

class NextCycleTest {
    @org.junit.Test
    fun oneCycleAfterThePaidDate() {
        val due = java.time.LocalDate.of(2026, 10, 5)
        assertEquals(java.time.LocalDate.of(2026, 11, 5), SubscriptionMath.nextBillingOnOrAfter(due, BillingCycle.MONTHLY, null, due.plusDays(1)))
        assertEquals(java.time.LocalDate.of(2027, 10, 5), SubscriptionMath.nextBillingOnOrAfter(due, BillingCycle.YEARLY, null, due.plusDays(1)))
        assertEquals(java.time.LocalDate.of(2026, 10, 12), SubscriptionMath.nextBillingOnOrAfter(due, BillingCycle.WEEKLY, null, due.plusDays(1)))
    }
}

class AddedTodayTest {
    @org.junit.Test
    fun subscriptionAddedTodayIsNotAssumedPaidToday() {
        val today = java.time.LocalDate.of(2026, 10, 3)
        val meta = ItemMeta("n", ItemType.SUBSCRIPTION, "Netflix", "", "", emptyList(), false, false, false,
            today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(), Instant.EPOCH)
        val sub = Subscription(meta, "Netflix", Money(999, "USD"), BillingCycle.MONTHLY, null, java.time.LocalDate.of(2026, 11, 3), "", "", SubscriptionStatus.ACTIVE, emptyList())
        val (from, to) = app.keeply.core.domain.SpendRange.THIS_MONTH.dates(today)!!
        assertEquals(0, app.keeply.core.domain.SpendingOverview.compute(emptyList(), listOf(sub), "USD", from, to, today).subscriptionsMinor)
    }
}

class NoInventedChargesTest {
    @org.junit.Test
    fun nothingDueBeforeTheNextBillingDate() {
        val today = java.time.LocalDate.of(2026, 10, 3)
        val meta = ItemMeta("c", ItemType.SUBSCRIPTION, "x", "", "", emptyList(), false, false, false,
            today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(), Instant.EPOCH)
        // Monthly, but the user says the next payment is Dec 24: Oct 24 and Nov 24 aren't charges.
        val sub = Subscription(meta, "x", Money(6_700, "USD"), BillingCycle.MONTHLY, null, java.time.LocalDate.of(2026, 12, 24), "", "", SubscriptionStatus.ACTIVE, emptyList())
        val (from, to) = app.keeply.core.domain.SpendRange.THIS_MONTH.dates(today)!!
        val s = app.keeply.core.domain.SpendingOverview.compute(emptyList(), listOf(sub), "USD", from, to, today)
        assertEquals(0, s.dueLaterMinor); assertEquals(0, s.subscriptionsMinor)
    }
}
