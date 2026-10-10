package app.keeply.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Fields every vault item shares. */
data class ItemMeta(
    val id: String,
    val type: ItemType,
    val title: String,
    val subtitle: String,
    val notes: String,
    val tags: List<String>,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val isDemo: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val logoId: String? = null,
    /** Optional one-off alert (receipts and links). */
    val remindAt: LocalDateTime? = null,
    /** Optional web page attached to the item. */
    val url: String? = null,
    val remindRepeat: Repeat = Repeat.NEVER,
    /** When it was last marked paid/done/renewed/claimed/refunded/read; null if never. */
    val markedAt: Instant? = null,
    /** True when the last mark can be undone. */
    val canUndoMark: Boolean = false,
    /** For timed reminders: the occurrence (local time) last marked done. */
    val doneThrough: LocalDateTime? = null,
)

data class Receipt(
    val meta: ItemMeta,
    val merchant: String,
    val total: Money,
    val purchaseDate: LocalDate,
    val category: String,
    val paymentMethod: String,
    val lineItems: List<String>,
)

data class Warranty(
    val meta: ItemMeta,
    val productName: String,
    val brand: String,
    val model: String,
    val purchaseDate: LocalDate,
    /** Null when the user entered an explicit expiry date instead of a duration. */
    val durationMonths: Int?,
    val expiryDate: LocalDate,
    val retailer: String,
    val serialNumber: String,
    val sourceReceiptId: String?,
    val reminderOffsetsDays: List<Int>,
)

data class Subscription(
    val meta: ItemMeta,
    val serviceName: String,
    val price: Money,
    val cycle: BillingCycle,
    val customIntervalDays: Int?,
    val nextBillingDate: LocalDate,
    val category: String,
    val paymentMethod: String,
    val status: SubscriptionStatus,
    val reminderOffsetsDays: List<Int>,
)

/**
 * What a document is decides its defaults: an ID card usually never expires and needs no
 * reminder, while a passport should be renewed months ahead.
 */
enum class DocumentKind(val label: String, val expiresByDefault: Boolean, val defaultReminders: List<Int>) {
    NATIONAL_ID("National ID", false, emptyList()),
    PASSPORT("Passport", true, listOf(180, 90, 30)),
    DRIVING_LICENCE("Driving licence", true, listOf(30, 7)),
    VISA("Visa", true, listOf(30, 7)),
    INSURANCE("Insurance card", true, listOf(30)),
    BIRTH_CERTIFICATE("Birth certificate", false, emptyList()),
    OTHER("Other", false, emptyList()),
}

data class Document(
    val meta: ItemMeta,
    val kind: DocumentKind,
    /** Sensitive: never shown in lists or notifications; masked until revealed in the preview. */
    val number: String,
    val holder: String,
    val issuer: String,
    val issueDate: LocalDate?,
    /** Null for documents that don't expire (e.g. most national ID cards). */
    val expiryDate: LocalDate?,
    /** Empty when the user doesn't want to be reminded. */
    val reminderOffsetsDays: List<Int>,
)

/** What a timed reminder is for; each picks sensible defaults for repeat and alerts. */
enum class ReminderCategory(
    val label: String,
    val placeholder: String,
    val defaultRepeat: Repeat,
    /** Minutes before the event, e.g. 1440 = one day. */
    val defaultAlerts: List<Int>,
    /** Local time of day the form starts with, in minutes after midnight. */
    val defaultMinuteOfDay: Int,
) {
    EXAM("Exam", "Physics final", Repeat.NEVER, listOf(1440, 60), 9 * 60),
    BIRTHDAY("Birthday", "Mum's birthday", Repeat.YEARLY, listOf(1440, 0), 9 * 60),
    INTERVIEW("Interview", "Interview at Grameenphone", Repeat.NEVER, listOf(1440, 60), 10 * 60),
    MEETING("Meeting", "Team meeting", Repeat.NEVER, listOf(15), 10 * 60),
    APPOINTMENT("Appointment", "Dentist", Repeat.NEVER, listOf(1440, 60), 11 * 60),
    OTHER("Other", "What should we remind you about?", Repeat.NEVER, listOf(0), 9 * 60),
}

enum class Repeat(val label: String) { NEVER("Never"), DAILY("Every day"), WEEKLY("Every week"), MONTHLY("Every month"), YEARLY("Every year") }

/** A dated, timed reminder ("note"): an exam, a birthday, an interview… */
data class ReminderNote(
    val meta: ItemMeta,
    val category: ReminderCategory,
    val date: LocalDate,
    /** Minutes after local midnight. */
    val minuteOfDay: Int,
    val repeat: Repeat,
    /** Minutes before each occurrence to alert; 0 = at the time. */
    val alertsMinutesBefore: List<Int>,
)

data class SavedLink(
    val meta: ItemMeta,
    val url: String,
    val domain: String,
    val pageTitle: String,
    val collection: String,
)

/** Lightweight row for lists, search results and the dashboard. */
data class VaultItemSummary(
    val id: String,
    val type: ItemType,
    val title: String,
    val subtitle: String,
    val isFavorite: Boolean,
    val isDemo: Boolean,
    val updatedAt: Instant,
    /** Attachment to show instead of the type icon, e.g. a subscription's logo. */
    val logoId: String? = null,
    /** Website of the item's link, for showing that site's icon. */
    val siteDomain: String? = null,
    /** For documents, what kind it is, so lists can show its own icon. */
    val documentKind: DocumentKind? = null,
)

enum class ActivityKind(val label: String) { ADDED("Added"), EDITED("Edited"), CANCELLED("Cancelled"), DELETED("Deleted"), REMINDED("Reminded"), DONE("Done"), PAID("Paid"), UNDONE("Undone") }

/** One entry in the Activity history. The item may no longer exist (e.g. after DELETED). */
data class ActivityEvent(
    val id: Long,
    val itemId: String,
    val type: ItemType,
    val title: String,
    val kind: ActivityKind,
    val at: Instant,
)

/** A subscription payment the user recorded. */
data class Payment(val id: String, val itemId: String, val amount: Money, val paidOn: LocalDate, val forDate: LocalDate)
