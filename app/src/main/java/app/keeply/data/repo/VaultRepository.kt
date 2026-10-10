package app.keeply.data.repo

import app.keeply.core.model.ItemType
import app.keeply.core.model.Receipt
import app.keeply.core.model.SavedLink
import app.keeply.core.model.Subscription
import app.keeply.core.model.VaultItemSummary
import app.keeply.core.model.Warranty
import kotlinx.coroutines.flow.Flow

/** "PHOTO" or "LOGO". The image file must already exist in [app.keeply.attachments.AttachmentFiles]. */
data class AttachmentDraft(val id: String, val kind: String)

/**
 * Inputs for creating or editing. `id == null` creates a new item.
 * `attachments` is the complete final set: anything not listed is removed.
 */
data class ReceiptDraft(
    val id: String?, val merchant: String, val totalMinor: Long, val currency: String,
    val purchaseEpochDay: Long, val category: String, val paymentMethod: String,
    val lineItems: List<String>, val notes: String,
    val attachments: List<AttachmentDraft> = emptyList(),
    /** Optional alert, e.g. before the return window closes. */
    val remindAt: java.time.LocalDateTime? = null,
    val remindRepeat: app.keeply.core.model.Repeat = app.keeply.core.model.Repeat.NEVER,
    val url: String? = null,
)

data class WarrantyDraft(
    val id: String?, val productName: String, val brand: String, val model: String,
    val purchaseEpochDay: Long, val durationMonths: Int?, val expiryEpochDay: Long,
    val retailer: String, val serialNumber: String, val sourceReceiptId: String?,
    val reminderOffsetsDays: List<Int>, val notes: String,
    val attachments: List<AttachmentDraft> = emptyList(),
    /** Optional web page, e.g. the product or service site. */
    val url: String? = null,
)

data class SubscriptionDraft(
    val id: String?, val serviceName: String, val amountMinor: Long, val currency: String,
    val cycle: String, val customIntervalDays: Int?, val nextBillingEpochDay: Long,
    val category: String, val paymentMethod: String, val status: String,
    val reminderOffsetsDays: List<Int>, val notes: String,
    val attachments: List<AttachmentDraft> = emptyList(),
    /** Optional web page, e.g. the product or service site. */
    val url: String? = null,
)

data class DocumentDraft(
    val id: String?, val kind: String, val name: String, val number: String, val holder: String,
    val issuer: String, val issueEpochDay: Long?, val expiryEpochDay: Long?,
    /** Empty = no reminders (also when there's no expiry date). */
    val reminderOffsetsDays: List<Int>, val notes: String,
    val attachments: List<AttachmentDraft> = emptyList(),
    /** Optional web page, e.g. the product or service site. */
    val url: String? = null,
)

data class ReminderNoteDraft(
    val id: String?, val category: String, val title: String, val dateEpochDay: Long, val minuteOfDay: Int,
    val repeat: String, val alertsMinutesBefore: List<Int>, val note: String,
    val url: String? = null,
)

data class LinkDraft(
    val id: String?, val url: String, val domain: String, val title: String,
    val collection: String, val tags: List<String>, val notes: String, val isFavorite: Boolean,
    /** Optional alert, e.g. "read this on Saturday". */
    val remindAt: java.time.LocalDateTime? = null,
    val remindRepeat: app.keeply.core.model.Repeat = app.keeply.core.model.Repeat.NEVER,
)

interface VaultRepository {
    fun observeItems(type: ItemType?): Flow<List<VaultItemSummary>>
    fun observeRecent(limit: Int): Flow<List<VaultItemSummary>>
    fun observeCounts(): Flow<Map<ItemType, Int>>
    fun search(query: String): Flow<List<VaultItemSummary>>
    fun observeReceipts(): Flow<List<Receipt>>
    fun observeWarranties(): Flow<List<Warranty>>
    fun observeSubscriptions(): Flow<List<Subscription>>
    fun observeDocuments(): Flow<List<app.keeply.core.model.Document>>
    fun observeReminderNotes(): Flow<List<app.keeply.core.model.ReminderNote>>

    suspend fun counts(): Map<ItemType, Int>
    suspend fun typeOf(id: String): ItemType?
    suspend fun receipt(id: String): Receipt?
    suspend fun warranty(id: String): Warranty?
    suspend fun subscription(id: String): Subscription?
    suspend fun link(id: String): SavedLink?
    suspend fun document(id: String): app.keeply.core.model.Document?
    suspend fun reminderNote(id: String): app.keeply.core.model.ReminderNote?
    suspend fun attachments(itemId: String): List<app.keeply.data.db.AttachmentEntity>

    /** Each save returns the item id. All writes are transactional. */
    suspend fun saveReceipt(draft: ReceiptDraft): String
    suspend fun saveWarranty(draft: WarrantyDraft): String
    suspend fun saveSubscription(draft: SubscriptionDraft): String
    suspend fun saveLink(draft: LinkDraft): String
    suspend fun saveDocument(draft: DocumentDraft): String
    suspend fun saveReminderNote(draft: ReminderNoteDraft): String

    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun delete(id: String)
    suspend fun deleteDemoData()
    suspend fun deleteAllData()

    /** Rolls overdue billing dates forward and re-plans their reminders. Run daily. */
    suspend fun rollSubscriptionsForward()
}
