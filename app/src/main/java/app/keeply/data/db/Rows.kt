package app.keeply.data.db

import androidx.room.Embedded

/** Join projections. Column names are unique across items and each detail table. */
data class ReceiptRow(@Embedded val item: ItemEntity, @Embedded val receipt: ReceiptEntity)
data class WarrantyRow(@Embedded val item: ItemEntity, @Embedded val warranty: WarrantyEntity)
data class SubscriptionRow(@Embedded val item: ItemEntity, @Embedded val subscription: SubscriptionEntity)
data class DocumentRow(@Embedded val item: ItemEntity, @Embedded val document: DocumentEntity)
data class ReminderNoteRow(@Embedded val item: ItemEntity, @Embedded val note: ReminderNoteEntity)
data class LinkRow(@Embedded val item: ItemEntity, @Embedded val link: SavedLinkEntity)

/** An item for lists, with its document kind when it's a document (null otherwise). */
data class ItemListRow(@Embedded val item: ItemEntity, val documentKind: String?)

data class TypeCount(val type: String, val count: Int)

data class DueReminderRow(
    val reminderId: Long,
    val itemId: String,
    val type: String,
    val title: String,
    val offsetDays: Int,
    val triggerEpochDay: Long,
)
