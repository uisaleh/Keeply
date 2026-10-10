package app.keeply.data.repo

import app.keeply.data.db.AttachmentEntity
import app.keeply.data.db.DocumentEntity
import app.keeply.data.db.ItemEntity
import app.keeply.data.db.PaymentEntity
import app.keeply.data.db.ReminderNoteEntity
import app.keeply.data.db.ReceiptEntity
import app.keeply.data.db.SavedLinkEntity
import app.keeply.data.db.SubscriptionEntity
import app.keeply.data.db.WarrantyEntity
import kotlinx.serialization.Serializable

/**
 * Everything needed to recreate one vault item on another device. Reminders are not
 * included: they are re-planned from the detail row's offsets when the bundle is imported.
 */
@Serializable
data class ItemBundle(
    val item: ItemEntity,
    val receipt: ReceiptEntity? = null,
    val receiptLines: List<String> = emptyList(),
    val warranty: WarrantyEntity? = null,
    val subscription: SubscriptionEntity? = null,
    val link: SavedLinkEntity? = null,
    val document: DocumentEntity? = null,
    val reminderNote: ReminderNoteEntity? = null,
    val tags: List<String> = emptyList(),
    /** Metadata only; the image files travel through Cloud Storage. */
    val attachments: List<AttachmentEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
)

/** A local write the cloud copy needs to hear about. */
sealed interface VaultChange {
    data class Upserted(val id: String) : VaultChange
    data class Deleted(val id: String) : VaultChange
    data object Cleared : VaultChange
    /** Only a new history entry (e.g. "Reminded"); nothing about the item itself changed. */
    data object HistoryAdded : VaultChange
    /** History entries the user removed from Activity; removed from the cloud too. */
    data class HistoryDeleted(val eventIds: List<String>) : VaultChange
    /** Photos or logos removed locally; their files must be deleted from Cloud Storage too. */
    data class AttachmentsDeleted(val ids: List<String>) : VaultChange
}
