package app.keeply.share

import app.keeply.core.domain.UrlParser
import app.keeply.core.model.ItemType
import java.math.BigDecimal

/** What Keeply guesses from text someone shared or copied, to pre-pick the right kind of item. */
data class ShareSuggestion(
    val text: String,
    /** Best guess first; the rest are offered too. */
    val types: List<ItemType>,
    val url: String?,
    /** First meaningful line, used as a title/merchant. */
    val title: String,
    /** A money amount like "1,299.00" if one appears, for receipts and subscriptions. */
    val amount: String?,
    /** ISO code for the amount's symbol (Tk → BDT), or null to keep the default currency. */
    val currency: String? = null,
    /** How strongly the text points at the first suggested type (0 = no signal). */
    val score: Int = 0,
) {
    /**
     * Strong enough to suggest unprompted (from a notification): a payment-like message with an
     * amount, or a clear warranty/document mention. Codes and OTPs never qualify.
     */
    val worthSuggesting: Boolean
        get() {
            if (ShareSuggest.isSensitive(text)) return false
            val top = types.firstOrNull() ?: return false
            return when (top) {
                ItemType.RECEIPT, ItemType.SUBSCRIPTION -> amount != null && score >= 2
                ItemType.WARRANTY, ItemType.DOCUMENT -> score >= 3
                else -> false
            }
        }
}

object ShareSuggest {
    private val RECEIPT = Regex("""\b(receipt|invoice|order( no| #|ed| confirmation)?|payment (received|successful)|total|paid|purchase)\b""", RegexOption.IGNORE_CASE)
    private val SUBSCRIPTION = Regex("""\b(subscription|subscribed|renew(s|al|ed)?|billing|monthly|yearly|annual plan|trial)\b""", RegexOption.IGNORE_CASE)
    private val WARRANTY = Regex("""\b(warranty|guarantee)\b""", RegexOption.IGNORE_CASE)
    private val DOCUMENT = Regex("""\b(passport|licen[cs]e|visa|national id|nid)\b""", RegexOption.IGNORE_CASE)
    private val REMINDER = Regex("""\b(exam|interview|appointment|meeting|birthday|deadline|tomorrow at|scheduled for|on \d{1,2}(st|nd|rd|th)?)\b""", RegexOption.IGNORE_CASE)
    private val AMOUNT = Regex("""([$€£৳₹]|\b(?:usd|eur|gbp|bdt|tk|inr|rs)\.?)\s?(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val CURRENCIES = mapOf(
        "$" to "USD", "usd" to "USD", "€" to "EUR", "eur" to "EUR", "£" to "GBP", "gbp" to "GBP",
        "৳" to "BDT", "tk" to "BDT", "bdt" to "BDT", "₹" to "INR", "inr" to "INR", "rs" to "INR",
    )
    /** Words that describe the message rather than name the shop or service. */
    private val NOISE = Regex("""\b(your|order|orders|confirmation|confirmed|receipt|invoice|payment|successful|thank you for|subscription|renews?|renewal)\b.*$""", RegexOption.IGNORE_CASE)

    private val SENSITIVE = Regex("""\b(otp|one[- ]time (pass(word|code)|pin)|verification code|security code|do not share|pin is|passcode|login code)\b""", RegexOption.IGNORE_CASE)

    /** Codes and passwords: never suggested, never kept. */
    fun isSensitive(text: String) = SENSITIVE.containsMatchIn(text)

    fun analyse(raw: String): ShareSuggestion {
        val text = raw.trim().take(4_000)
        val url = UrlParser.extractFirst(text)
        val words = if (url != null) text.replace(url, " ") else text
        // Score each type by how strongly the text points at it; a lone link is a link.
        val scores = mapOf(
            ItemType.RECEIPT to RECEIPT.findAll(words).count() * 2,
            ItemType.SUBSCRIPTION to SUBSCRIPTION.findAll(words).count() * 2,
            ItemType.WARRANTY to WARRANTY.findAll(words).count() * 3,
            ItemType.DOCUMENT to DOCUMENT.findAll(words).count() * 3,
            ItemType.REMINDER to REMINDER.findAll(words).count() * 2,
            ItemType.LINK to if (url != null) (if (words.isBlank()) 10 else 3) else 0,
        )
        val ranked = scores.entries.sortedByDescending { it.value }.filter { it.value > 0 }.map { it.key }
        val topScore = scores.values.maxOrNull() ?: 0
        val types = (ranked + listOf(ItemType.REMINDER, ItemType.RECEIPT, ItemType.SUBSCRIPTION, ItemType.LINK, ItemType.DOCUMENT, ItemType.WARRANTY))
            .distinct().filter { it != ItemType.LINK || url != null }
        // First sentence, with "order confirmation…" style words trimmed off: "Daraz order confirmation." -> "Daraz".
        val firstSentence = words.lines().map { it.trim() }.firstOrNull { it.length >= 2 }?.split(Regex("[.:|•–—]"))?.first()?.trim().orEmpty()
        val title = firstSentence.replace(NOISE, "").trim().ifEmpty { firstSentence }.take(60)
        val money = AMOUNT.find(words)
        val amount = money?.groupValues?.get(2)?.replace(",", "")?.takeIf { runCatching { BigDecimal(it) }.isSuccess }
        val currency = money?.groupValues?.get(1)?.lowercase()?.trimEnd('.')?.let { CURRENCIES[it] }
        return ShareSuggestion(text, types, url, title, amount, currency, topScore)
    }
}
