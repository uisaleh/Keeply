package app.keeply.data.repo

import app.keeply.core.model.BillingCycle
import app.keeply.core.model.Document
import app.keeply.core.model.DocumentKind
import app.keeply.core.model.ItemMeta
import app.keeply.core.model.ReminderCategory
import app.keeply.core.model.ReminderNote
import app.keeply.core.model.Repeat
import app.keeply.core.model.ItemType
import app.keeply.core.model.Money
import app.keeply.core.model.Receipt
import app.keeply.core.model.SavedLink
import app.keeply.core.model.Subscription
import app.keeply.core.model.SubscriptionStatus
import app.keeply.core.model.VaultItemSummary
import app.keeply.core.model.Warranty
import app.keeply.data.db.ItemEntity
import app.keeply.data.db.ItemListRow
import app.keeply.data.db.DocumentRow
import app.keeply.data.db.LinkRow
import app.keeply.data.db.ReminderNoteRow
import app.keeply.data.db.ReceiptRow
import app.keeply.data.db.SubscriptionRow
import app.keeply.data.db.WarrantyRow
import java.time.Instant
import java.time.LocalDate

internal fun ItemEntity.toMeta(tags: List<String> = emptyList()) = ItemMeta(
    id = id, type = ItemType.valueOf(type), title = title, subtitle = subtitle, notes = notes,
    tags = tags, isFavorite = isFavorite, isArchived = isArchived, isDemo = isDemo,
    createdAt = Instant.ofEpochMilli(createdAt), updatedAt = Instant.ofEpochMilli(updatedAt), logoId = logoId,
    remindAt = remindAtLocalMinute?.let(::localMinuteToDateTime),
    url = pageUrl,
    remindRepeat = remindRepeat?.let { runCatching { Repeat.valueOf(it) }.getOrNull() } ?: Repeat.NEVER,
    // Timed reminders keep the occurrence's local minute here; other types an epoch-millis time.
    markedAt = markedAt?.takeIf { type != ItemType.REMINDER.name }?.let(Instant::ofEpochMilli),
    canUndoMark = markUndo != null,
    doneThrough = markedAt?.takeIf { type == ItemType.REMINDER.name }?.let(::localMinuteToDateTime),
)

/** Local date-time <-> minutes since 1970-01-01T00:00 (local, no zone), as stored in the database. */
fun localMinuteToDateTime(m: Long): java.time.LocalDateTime = java.time.LocalDateTime.of(1970, 1, 1, 0, 0).plusMinutes(m)
fun dateTimeToLocalMinute(t: java.time.LocalDateTime): Long = java.time.temporal.ChronoUnit.MINUTES.between(java.time.LocalDateTime.of(1970, 1, 1, 0, 0), t)

internal fun ItemEntity.toSummary() = VaultItemSummary(
    id = id, type = ItemType.valueOf(type), title = title, subtitle = subtitle,
    isFavorite = isFavorite, isDemo = isDemo, updatedAt = Instant.ofEpochMilli(updatedAt), logoId = logoId,
    siteDomain = pageUrl?.let { app.keeply.core.domain.UrlParser.parse(it)?.domain },
)

internal fun ItemListRow.toSummary() = item.toSummary().copy(
    documentKind = documentKind?.let { runCatching { DocumentKind.valueOf(it) }.getOrDefault(DocumentKind.OTHER) },
)

internal fun ReceiptRow.toModel(lines: List<String> = emptyList(), tags: List<String> = emptyList()) = Receipt(
    meta = item.toMeta(tags), merchant = receipt.merchant,
    total = Money(receipt.totalMinor, receipt.currency),
    purchaseDate = LocalDate.ofEpochDay(receipt.purchaseEpochDay),
    category = receipt.category, paymentMethod = receipt.paymentMethod, lineItems = lines,
)

internal fun WarrantyRow.toModel(tags: List<String> = emptyList()) = Warranty(
    meta = item.toMeta(tags), productName = warranty.productName, brand = warranty.brand, model = warranty.model,
    purchaseDate = LocalDate.ofEpochDay(warranty.purchaseEpochDay), durationMonths = warranty.durationMonths,
    expiryDate = LocalDate.ofEpochDay(warranty.expiryEpochDay), retailer = warranty.retailer,
    serialNumber = warranty.serialNumber, sourceReceiptId = warranty.sourceReceiptId,
    reminderOffsetsDays = decodeOffsets(warranty.reminderOffsets),
)

internal fun SubscriptionRow.toModel(tags: List<String> = emptyList()) = Subscription(
    meta = item.toMeta(tags), serviceName = subscription.serviceName,
    price = Money(subscription.amountMinor, subscription.currency),
    cycle = BillingCycle.valueOf(subscription.cycle), customIntervalDays = subscription.customIntervalDays,
    nextBillingDate = LocalDate.ofEpochDay(subscription.nextBillingEpochDay), category = subscription.category,
    paymentMethod = subscription.paymentMethod, status = SubscriptionStatus.valueOf(subscription.status),
    reminderOffsetsDays = decodeOffsets(subscription.reminderOffsets),
)

internal fun DocumentRow.toModel(tags: List<String> = emptyList()) = Document(
    meta = item.toMeta(tags),
    kind = runCatching { DocumentKind.valueOf(document.kind) }.getOrDefault(DocumentKind.OTHER),
    number = document.number, holder = document.holder, issuer = document.issuer,
    issueDate = document.issueEpochDay?.let(LocalDate::ofEpochDay),
    expiryDate = document.expiryEpochDay?.let(LocalDate::ofEpochDay),
    reminderOffsetsDays = decodeOffsets(document.reminderOffsets),
)

internal fun ReminderNoteRow.toModel(tags: List<String> = emptyList()) = ReminderNote(
    meta = item.toMeta(tags),
    category = runCatching { ReminderCategory.valueOf(note.category) }.getOrDefault(ReminderCategory.OTHER),
    date = LocalDate.ofEpochDay(note.dateEpochDay),
    minuteOfDay = note.minuteOfDay,
    repeat = runCatching { Repeat.valueOf(note.repeat) }.getOrDefault(Repeat.NEVER),
    alertsMinutesBefore = note.alerts.split(',').mapNotNull { it.trim().toIntOrNull() },
)

internal fun LinkRow.toModel(tags: List<String> = emptyList()) = SavedLink(
    meta = item.toMeta(tags), url = link.url, domain = link.domain, pageTitle = link.pageTitle, collection = link.collection,
)

internal fun encodeOffsets(offsets: List<Int>): String = offsets.distinct().sortedDescending().joinToString(",")
internal fun decodeOffsets(raw: String): List<Int> = raw.split(',').mapNotNull { it.trim().toIntOrNull() }

internal fun app.keeply.data.db.PaymentEntity.toModel() = app.keeply.core.model.Payment(
    id, itemId, Money(amountMinor, currency), LocalDate.ofEpochDay(paidEpochDay), LocalDate.ofEpochDay(forEpochDay),
)
