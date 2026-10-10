package app.keeply

import app.keeply.core.domain.ReminderSchedule
import app.keeply.core.model.Repeat
import app.keeply.core.domain.FtsQuery
import app.keeply.core.domain.RelativeDays
import app.keeply.core.domain.ReminderPlanner
import app.keeply.core.domain.UrlParser
import app.keeply.core.domain.WarrantyRules
import app.keeply.core.domain.WarrantyStatus
import app.keeply.core.model.ItemType
import app.keeply.core.model.Money
import app.keeply.premium.AddDecision
import app.keeply.premium.EntitlementPolicy
import app.keeply.premium.EntitlementTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DomainRulesTest {
    private val today = LocalDate.of(2026, 10, 3)

    @Test
    fun warrantyStatusBoundaries() {
        assertEquals(WarrantyStatus.EXPIRED, WarrantyRules.status(today.minusDays(1), today))
        assertEquals(WarrantyStatus.EXPIRING_SOON, WarrantyRules.status(today, today))
        assertEquals(WarrantyStatus.EXPIRING_SOON, WarrantyRules.status(today.plusDays(60), today))
        assertEquals(WarrantyStatus.ACTIVE, WarrantyRules.status(today.plusDays(61), today))
        assertEquals(LocalDate.of(2027, 3, 21), WarrantyRules.expiryFrom(LocalDate.of(2026, 3, 21), 12))
    }

    @Test
    fun reminderPlannerDropsPastAndDuplicates() {
        val planned = ReminderPlanner.plan(today.plusDays(45), listOf(90, 60, 30, 30, 7, 400), today)
        assertEquals(listOf(30, 7), planned.map { it.offsetDays })
        assertEquals(today.plusDays(15), planned.first().date)
    }

    @Test
    fun ftsQueryIsSanitised() {
        assertEquals("apple* mac*", FtsQuery.build("Apple  \"mac\""))
        assertEquals("best* buy*", FtsQuery.build("best OR buy*"))
        assertNull(FtsQuery.build("  ** \" "))
    }

    @Test
    fun urlParsing() {
        assertEquals("shopify.com", UrlParser.parse("www.shopify.com/blog")?.domain)
        assertEquals("https://x.io/a", UrlParser.parse("https://x.io/a")?.url)
        assertNull(UrlParser.parse("javascript:alert(1)"))
        assertNull(UrlParser.parse("ftp://files.example.com"))
        assertNull(UrlParser.parse("not a url"))
        assertNull(UrlParser.parse("localhost"))
        assertEquals("https://dribbble.com/shots/1", UrlParser.extractFirst("Check this out: https://dribbble.com/shots/1."))
    }

    @Test
    fun moneyParsing() {
        assertEquals(Money(129900, "USD"), Money.parse("1,299.00", "USD"))
        assertEquals(Money(1500, "JPY"), Money.parse("1500", "JPY"))
        assertNull(Money.parse("12.345", "USD"))
        assertNull(Money.parse("-4", "USD"))
        assertNull(Money.parse("abc", "USD"))
    }

    @Test
    fun freePlanLimits() {
        val counts = mapOf(ItemType.RECEIPT to 5)
        val d = EntitlementPolicy.canAdd(EntitlementTier.FREE, ItemType.RECEIPT, counts)
        assertTrue(d is AddDecision.TypeLimitReached && d.limit == 5 && d.stillAvailable[ItemType.LINK] == 5 && ItemType.RECEIPT !in d.stillAvailable)
        assertEquals(AddDecision.Allowed, EntitlementPolicy.canAdd(EntitlementTier.FREE, ItemType.LINK, counts))
        val full = ItemType.available.associateWith { 5 }
        assertEquals(30, full.values.sum())
        assertTrue(EntitlementPolicy.canAdd(EntitlementTier.FREE, ItemType.WARRANTY, full) is AddDecision.TypeLimitReached)
        assertEquals(emptyMap<ItemType, Int>(), EntitlementPolicy.remaining(full))
        assertEquals(AddDecision.Allowed, EntitlementPolicy.canAdd(EntitlementTier.PREMIUM_LIFETIME, ItemType.RECEIPT, full))
    }

    @Test
    fun relativeDays() {
        assertEquals("Renews tomorrow", RelativeDays.describeUntil(today.plusDays(1), today, "renew"))
        assertEquals("Expires in 30 days", RelativeDays.describeUntil(today.plusDays(30), today, "expire"))
        assertEquals("Expires in 5 years", RelativeDays.describeUntil(today.plusYears(5), today, "expire"))
        assertEquals("Tomorrow", RelativeDays.countdown(1))
        assertTrue(RelativeDays.describeUntil(today.minusDays(3), today, "expire").startsWith("Expired 3"))
    }
}

class ReminderScheduleTest {
    private val now = java.time.LocalDateTime.of(2026, 10, 3, 18, 0)

    @org.junit.Test
    fun oneOffAlertsOnlyInTheFuture() {
        val exam = java.time.LocalDateTime.of(2026, 10, 4, 9, 0)
        org.junit.Assert.assertEquals(exam, ReminderSchedule.nextFor(exam, Repeat.NEVER, 60, now))
        // A day-before alert for tomorrow 9:00 was due today at 9:00: already gone.
        org.junit.Assert.assertNull(ReminderSchedule.nextFor(exam, Repeat.NEVER, 1440, now))
    }

    @org.junit.Test
    fun yearlyBirthdayRollsToNextYear() {
        val birthday = java.time.LocalDateTime.of(1990, 3, 12, 9, 0)
        org.junit.Assert.assertEquals(java.time.LocalDateTime.of(2027, 3, 12, 9, 0), ReminderSchedule.nextOccurrence(birthday, Repeat.YEARLY, now))
    }

    @org.junit.Test
    fun monthlyKeepsTheThirtyFirstWhereItCan() {
        val rent = java.time.LocalDateTime.of(2026, 1, 31, 10, 0)
        org.junit.Assert.assertEquals(java.time.LocalDateTime.of(2026, 10, 31, 10, 0), ReminderSchedule.nextOccurrence(rent, Repeat.MONTHLY, now))
    }
}

class LinkImageTest {
    @org.junit.Test
    fun findsOgImageAndResolvesRelative() {
        val html = """<head><meta property="og:image" content="/img/cover.png"></head>"""
        org.junit.Assert.assertEquals("https://example.com/img/cover.png", app.keeply.links.LinkMetadataFetcher.extractImage(html, "https://example.com/post/1"))
        val rev = """<meta content="https://cdn.x.com/a.jpg" name="twitter:image">"""
        org.junit.Assert.assertEquals("https://cdn.x.com/a.jpg", app.keeply.links.LinkMetadataFetcher.extractImage(rev, "https://x.com"))
        org.junit.Assert.assertNull(app.keeply.links.LinkMetadataFetcher.extractImage("<title>No image</title>", "https://x.com"))
        // Plain http images are refused.
        org.junit.Assert.assertNull(app.keeply.links.LinkMetadataFetcher.extractImage("""<meta property="og:image" content="http://x.com/a.jpg">""", "http://x.com"))
    }
}

class YouTubeThumbnailTest {
    @org.junit.Test
    fun recognisesCommonYouTubeLinks() {
        val t = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        val f = app.keeply.links.LinkMetadataFetcher::youTubeThumbnail
        org.junit.Assert.assertEquals(t, f("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"))
        org.junit.Assert.assertEquals(t, f("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        org.junit.Assert.assertEquals(t, f("https://m.youtube.com/shorts/dQw4w9WgXcQ"))
        org.junit.Assert.assertNull(f("https://www.youtube.com/@somechannel"))
        org.junit.Assert.assertNull(f("https://example.com/watch?v=dQw4w9WgXcQ"))
    }
}

class ShareSuggestTest {
    private val s = app.keeply.share.ShareSuggest::analyse

    @org.junit.Test
    fun guessesTheRightKind() {
        assertEquals(ItemType.LINK, s("https://youtu.be/dQw4w9WgXcQ").types.first())
        val receipt = s("Daraz order confirmation\nTotal paid: ৳1,250.00")
        assertEquals(ItemType.RECEIPT, receipt.types.first()); assertEquals("1250.00", receipt.amount)
        val daraz = s("Daraz order confirmation. Total paid: Tk 1,250.00")
        assertEquals("Daraz", daraz.title); assertEquals("BDT", daraz.currency); assertEquals("1250.00", daraz.amount)
        assertEquals(ItemType.SUBSCRIPTION, s("Your Netflix subscription renews on 12 Nov. Billing: $9.99 monthly").types.first())
        assertEquals(ItemType.REMINDER, s("Interview scheduled for Monday at 10am").types.first())
    }
}

class SuggestFromNotificationTest {
    private val s = app.keeply.share.ShareSuggest::analyse

    @org.junit.Test
    fun onlyClearPaymentsAreSuggested() {
        org.junit.Assert.assertTrue(s("Daraz: Your order is confirmed. Total paid Tk 1,250.00").worthSuggesting)
        org.junit.Assert.assertTrue(s("Your Netflix subscription renews on Nov 12 for $9.99").worthSuggesting)
        // No amount, a chat message, and an OTP must not trigger a suggestion.
        org.junit.Assert.assertFalse(s("Your order has shipped").worthSuggesting)
        org.junit.Assert.assertFalse(s("hey are we still meeting tomorrow?").worthSuggesting)
        org.junit.Assert.assertFalse(s("Your OTP for payment of Tk 500 is 482913. Do not share.").worthSuggesting)
    }
}
