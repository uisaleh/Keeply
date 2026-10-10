package app.keeply.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One row per vault item regardless of type. Type-specific columns live in detail
 * tables keyed by the same id (1:1, cascade delete). `searchText` is a denormalised
 * bag of merchant/brand/tag/domain words maintained by the repository so FTS can
 * match across every type with one query.
 */
@Entity(
    tableName = "items",
    indices = [Index("type"), Index("updatedAt"), Index("isArchived")],
)
@Serializable
data class ItemEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val subtitle: String,
    val notes: String,
    val searchText: String,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val isDemo: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** Attachment shown instead of the type icon in lists (e.g. a subscription's brand logo). */
    val logoId: String? = null,
    /**
     * Optional one-off alert for types without their own dates (receipts, links): local
     * date-time as epoch minutes since 1970-01-01T00:00 local, so it stays "9:00" if you travel.
     */
    val remindAtLocalMinute: Long? = null,
    /** Optional web page for any item (the subscription's site, a product page…), for its icon and preview. */
    val pageUrl: String? = null,
    /** How [remindAtLocalMinute] repeats: a [app.keeply.core.model.Repeat] name; null means once. */
    val remindRepeat: String? = null,
    /**
     * When the item was last marked (paid, done, renewed, claimed, refunded or read); null if not.
     * For timed reminders it's the local minute of the occurrence that was marked done.
     */
    val markedAt: Long? = null,
    /** What to put back if the last mark is undone (small JSON, type-specific). */
    val markUndo: String? = null,
)

@Fts4(contentEntity = ItemEntity::class)
@Entity(tableName = "items_fts")
data class ItemFts(
    val title: String,
    val subtitle: String,
    val notes: String,
    val searchText: String,
)

@Entity(
    tableName = "receipts",
    foreignKeys = [ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("merchant"), Index("purchaseEpochDay")],
)
@Serializable
data class ReceiptEntity(
    @PrimaryKey val itemId: String,
    val merchant: String,
    val totalMinor: Long,
    val currency: String,
    val purchaseEpochDay: Long,
    val category: String,
    val paymentMethod: String,
)

@Entity(
    tableName = "receipt_lines",
    foreignKeys = [ForeignKey(ReceiptEntity::class, ["itemId"], ["receiptId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("receiptId")],
)
data class ReceiptLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: String,
    val position: Int,
    val description: String,
)

@Entity(
    tableName = "warranties",
    foreignKeys = [
        ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ReceiptEntity::class, ["itemId"], ["sourceReceiptId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("expiryEpochDay"), Index("sourceReceiptId")],
)
@Serializable
data class WarrantyEntity(
    @PrimaryKey val itemId: String,
    val productName: String,
    val brand: String,
    val model: String,
    val purchaseEpochDay: Long,
    val durationMonths: Int?,
    val expiryEpochDay: Long,
    val retailer: String,
    val serialNumber: String,
    val sourceReceiptId: String?,
    /** The user's chosen "days before" offsets, e.g. "30,7". Source of truth for re-planning. */
    val reminderOffsets: String,
)

@Entity(
    tableName = "subscriptions",
    foreignKeys = [ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("nextBillingEpochDay"), Index("status")],
)
@Serializable
data class SubscriptionEntity(
    @PrimaryKey val itemId: String,
    val serviceName: String,
    val amountMinor: Long,
    val currency: String,
    val cycle: String,
    val customIntervalDays: Int?,
    val nextBillingEpochDay: Long,
    val category: String,
    val paymentMethod: String,
    val status: String,
    val reminderOffsets: String,
)

@Entity(
    tableName = "reminder_notes",
    foreignKeys = [ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dateEpochDay")],
)
@Serializable
data class ReminderNoteEntity(
    @PrimaryKey val itemId: String,
    /** A [app.keeply.core.model.ReminderCategory] name. */
    val category: String,
    /** First (or only) occurrence, in local time: date plus minutes after midnight. */
    val dateEpochDay: Long,
    val minuteOfDay: Int,
    /** A [app.keeply.core.model.Repeat] name. */
    val repeat: String,
    /** Minutes before each occurrence to alert, e.g. "1440,60,0". */
    val alerts: String,
)

@Entity(
    tableName = "documents",
    foreignKeys = [ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("expiryEpochDay")],
)
@Serializable
data class DocumentEntity(
    @PrimaryKey val itemId: String,
    /** A [app.keeply.core.model.DocumentKind] name. */
    val kind: String,
    val number: String,
    val holder: String,
    val issuer: String,
    val issueEpochDay: Long?,
    val expiryEpochDay: Long?,
    /** "Days before" offsets, e.g. "180,30"; empty means no reminders. */
    val reminderOffsets: String,
)

@Entity(
    tableName = "saved_links",
    foreignKeys = [ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("domain"), Index("collection")],
)
@Serializable
data class SavedLinkEntity(
    @PrimaryKey val itemId: String,
    val url: String,
    val domain: String,
    val pageTitle: String,
    val collection: String,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "item_tags",
    primaryKeys = ["itemId", "tagId"],
    foreignKeys = [
        ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class ItemTagCrossRef(val itemId: String, val tagId: Long)

/**
 * A reminder the user asked for. `offsetDays` is the user's choice ("30 days before");
 * `triggerEpochDay` is derived from the item's event date and recomputed on every save.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId"), Index("triggerEpochDay")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val offsetDays: Int,
    val triggerEpochDay: Long,
    val delivered: Boolean = false,
)

/**
 * A photo or logo attached to an item. The file itself lives at `files/attachments/{id}.jpg`
 * on the device and at `users/{uid}/attachments/{id}.jpg` in Cloud Storage, so no paths are stored.
 */
@Serializable
@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    /** "PHOTO" or "LOGO". */
    val kind: String,
    val position: Int,
    val createdAt: Long,
    /** False until Cloud Storage confirms the upload; retried on the next start. */
    val uploaded: Boolean = false,
)

/**
 * One line of the Activity history. Kept separately from the item (no foreign key) so the
 * record of a deletion survives the item itself.
 */
@Entity(tableName = "activity_events", indices = [Index("at"), Index(value = ["eventId"], unique = true)])
data class ActivityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Same on every device; also the document id at `users/{uid}/events/{eventId}`. */
    val eventId: String,
    val itemId: String,
    val type: String,
    val title: String,
    /** "ADDED", "EDITED", "CANCELLED" or "DELETED". */
    val kind: String,
    val at: Long,
    /** False until the cloud copy is confirmed; retried on the next change or start. */
    val uploaded: Boolean = false,
)

/** A subscription payment the user marked as paid. Counts in Spent on [paidEpochDay]. */
@Serializable
@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId"), Index("paidEpochDay")],
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val amountMinor: Long,
    val currency: String,
    val paidEpochDay: Long,
    /** The billing date this payment covers, so that renewal isn't counted (or shown as due) again. */
    val forEpochDay: Long,
    val createdAt: Long,
)

/**
 * A reminder notification Keeply showed on this phone, for the Notifications page. Keyed by the
 * system notification id, so a snoozed alert that comes back updates its row instead of adding one.
 * Device-only: not synced.
 */
@Entity(tableName = "notification_log", indices = [Index("postedAt")])
data class NotificationLogEntity(
    @PrimaryKey val notificationId: Int,
    val itemId: String?,
    /** A [app.keeply.data.settings.NotificationCategory] name. */
    val category: String,
    val title: String,
    val body: String,
    val details: String?,
    val occurrenceMinute: Long?,
    val postedAt: Long,
    val readAt: Long? = null,
    /** "PAID", "DONE", "SNOOZED" or "OPENED" once acted on. */
    val outcome: String? = null,
    val snoozedUntil: Long? = null,
)
