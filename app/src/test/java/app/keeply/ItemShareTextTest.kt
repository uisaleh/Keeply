package app.keeply

import app.keeply.core.model.BillingCycle
import app.keeply.core.model.Document
import app.keeply.core.model.DocumentKind
import app.keeply.core.model.ItemMeta
import app.keeply.core.model.ItemType
import app.keeply.core.model.Money
import app.keeply.core.model.Subscription
import app.keeply.core.model.SubscriptionStatus
import app.keeply.ui.features.detail.ItemDetail
import app.keeply.ui.features.detail.shareText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

class ItemShareTextTest {

    @Before fun pinLocale() = Locale.setDefault(Locale.US)

    private fun meta(type: ItemType, title: String, notes: String = "", url: String? = null) =
        ItemMeta(title, type, title, "", notes, emptyList(), false, false, false, Instant.EPOCH, Instant.EPOCH, url = url)

    @Test
    fun documentNumberIsNeverShared() {
        val doc = Document(
            meta(ItemType.DOCUMENT, "My passport"), DocumentKind.PASSPORT, number = "A1234567",
            holder = "Sam", issuer = "Passport office", issueDate = null, expiryDate = LocalDate.of(2030, 5, 1), reminderOffsetsDays = emptyList(),
        )
        val text = shareText(ItemDetail.OfDocument(doc))
        assertFalse(text.contains("A1234567"))
        assertTrue(text.startsWith("My passport\n"))
        assertTrue(text.contains("Type: Passport"))
    }

    @Test
    fun subscriptionLeavesOutPaymentMethodButKeepsLinkAndNotes() {
        val sub = Subscription(
            meta(ItemType.SUBSCRIPTION, "Netflix", notes = "Family plan", url = "https://netflix.com/account"),
            serviceName = "Netflix", price = Money(1549, "USD"), cycle = BillingCycle.MONTHLY, customIntervalDays = null,
            nextBillingDate = LocalDate.of(2026, 11, 3), category = "Streaming", paymentMethod = "Visa •4242",
            status = SubscriptionStatus.ACTIVE, reminderOffsetsDays = emptyList(),
        )
        val text = shareText(ItemDetail.OfSubscription(sub))
        assertFalse(text.contains("4242"))
        assertTrue(text.contains("Price: $15.49 · Monthly"))
        assertTrue(text.contains("Link: https://netflix.com/account"))
        assertEquals("Family plan", text.lines().last())
    }
}
