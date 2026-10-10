package app.keeply.ui.features.detail

import app.keeply.core.domain.SubscriptionMath
import app.keeply.core.domain.nextOccurrence
import app.keeply.core.model.SubscriptionStatus
import app.keeply.ui.components.display
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The item as plain text, for Copy and Share on the preview: its title, the facts that matter
 * for its type, its web link and notes. A document's ID number and a subscription's payment
 * method are left out: they're private, and the person can still copy them from Edit.
 */
fun shareText(d: ItemDetail): String {
    val facts: List<Pair<String, String>> = when (d) {
        is ItemDetail.OfReceipt -> with(d.r) {
            listOf(
                "Total" to total.format(), "Merchant" to merchant, "Purchased" to purchaseDate.display(),
                "Category" to category, "Items" to lineItems.joinToString(", "),
            )
        }
        is ItemDetail.OfWarranty -> with(d.w) {
            listOf(
                "Brand" to brand, "Model" to model, "Purchased" to purchaseDate.display(),
                "Cover ends" to expiryDate.display(), "Retailer" to retailer, "Serial number" to serialNumber,
            )
        }
        is ItemDetail.OfSubscription -> with(d.s) {
            val yearly = runCatching { SubscriptionMath.annualCost(price, cycle, customIntervalDays).format() }.getOrNull()
            listOf(
                "Price" to "${price.format()} · ${cycle.label}" + (yearly?.let { " (about $it a year)" } ?: ""),
                "Next payment" to if (status == SubscriptionStatus.ACTIVE) nextBillingDate.display() else "Cancelled",
                "Category" to category,
            )
        }
        is ItemDetail.OfLink -> listOf("Link" to d.l.url, "Collection" to d.l.collection)
        is ItemDetail.OfDocument -> with(d.d) {
            listOf(
                "Type" to kind.label, "Issued by" to issuer, "Issued" to (issueDate?.display() ?: ""),
                "Expires" to (expiryDate?.display() ?: "Doesn't expire"),
            )
        }
        is ItemDetail.OfReminder -> with(d.n) {
            val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            val day = nextOccurrence(LocalDateTime.now())?.toLocalDate() ?: date
            listOf("When" to "${day.display()}, $time", "Type" to category.label)
        }
    }
    val title = (d as? ItemDetail.OfLink)?.l?.pageTitle?.takeIf { it.isNotBlank() } ?: d.meta.title
    return buildList {
        add(title)
        facts.filter { it.second.isNotBlank() }.forEach { (label, value) -> add("$label: $value") }
        // Any item can carry a web page; a saved link already listed it.
        if (d !is ItemDetail.OfLink) d.meta.url?.let { add("Link: $it") }
        d.meta.notes.takeIf { it.isNotBlank() }?.let { add(""); add(it) }
    }.joinToString("\n")
}
