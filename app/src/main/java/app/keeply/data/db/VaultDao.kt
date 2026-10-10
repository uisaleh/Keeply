package app.keeply.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    // ---- Items -------------------------------------------------------------
    @Upsert suspend fun upsertItem(item: ItemEntity)

    @Query("SELECT * FROM items WHERE id = :id") suspend fun item(id: String): ItemEntity?

    /** Receipts and links with an alert set, for the alarm scheduler. */
    @Query("SELECT * FROM items WHERE remindAtLocalMinute IS NOT NULL AND isArchived = 0")
    fun observeItemAlerts(): Flow<List<ItemEntity>>
    @Query("SELECT * FROM items WHERE remindAtLocalMinute IS NOT NULL AND isArchived = 0")
    suspend fun itemAlerts(): List<ItemEntity>

    @Query("DELETE FROM items WHERE id = :id") suspend fun deleteItem(id: String)

    @Query("DELETE FROM items WHERE isDemo = 1") suspend fun deleteDemoItems()

    @Query("DELETE FROM items") suspend fun deleteAllItems()

    @Query("SELECT id FROM items WHERE isDemo = 0") suspend fun userItemIds(): List<String>

    @Query("UPDATE items SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, now: Long)

    @Query(
        """SELECT items.*, documents.kind AS documentKind FROM items
           LEFT JOIN documents ON documents.itemId = items.id
           WHERE isArchived = 0 AND (:type IS NULL OR type = :type)
           ORDER BY updatedAt DESC"""
    )
    fun observeItems(type: String?): Flow<List<ItemListRow>>

    @Query(
        """SELECT items.*, documents.kind AS documentKind FROM items
           LEFT JOIN documents ON documents.itemId = items.id
           WHERE isArchived = 0 ORDER BY createdAt DESC LIMIT :limit"""
    )
    fun observeRecent(limit: Int): Flow<List<ItemListRow>>

    /** Demo items never count towards free-plan limits. */
    @Query("SELECT type, COUNT(*) AS count FROM items WHERE isDemo = 0 GROUP BY type")
    fun observeCounts(): Flow<List<TypeCount>>

    @Query("SELECT type, COUNT(*) AS count FROM items WHERE isDemo = 0 GROUP BY type")
    suspend fun counts(): List<TypeCount>

    @Query(
        """SELECT items.*, documents.kind AS documentKind FROM items
           JOIN items_fts ON items.rowid = items_fts.rowid
           LEFT JOIN documents ON documents.itemId = items.id
           WHERE items_fts MATCH :match AND items.isArchived = 0
           ORDER BY items.updatedAt DESC LIMIT 100"""
    )
    fun search(match: String): Flow<List<ItemListRow>>

    // ---- Receipts ----------------------------------------------------------
    @Upsert suspend fun upsertReceipt(receipt: ReceiptEntity)
    @Query("DELETE FROM receipt_lines WHERE receiptId = :receiptId") suspend fun clearReceiptLines(receiptId: String)
    @Insert suspend fun insertReceiptLines(lines: List<ReceiptLineEntity>)
    @Query("SELECT * FROM receipt_lines WHERE receiptId = :receiptId ORDER BY position")
    suspend fun receiptLines(receiptId: String): List<ReceiptLineEntity>

    @Query("SELECT * FROM items JOIN receipts ON receipts.itemId = items.id WHERE items.id = :id")
    suspend fun receipt(id: String): ReceiptRow?

    @Query("SELECT * FROM items JOIN receipts ON receipts.itemId = items.id WHERE isArchived = 0 ORDER BY purchaseEpochDay DESC")
    fun observeReceipts(): Flow<List<ReceiptRow>>

    // ---- Warranties --------------------------------------------------------
    @Upsert suspend fun upsertWarranty(warranty: WarrantyEntity)

    @Query("SELECT * FROM items JOIN warranties ON warranties.itemId = items.id WHERE items.id = :id")
    suspend fun warranty(id: String): WarrantyRow?

    @Query("SELECT * FROM items JOIN warranties ON warranties.itemId = items.id WHERE isArchived = 0 ORDER BY expiryEpochDay ASC")
    fun observeWarranties(): Flow<List<WarrantyRow>>

    // ---- Subscriptions -----------------------------------------------------
    @Upsert suspend fun upsertSubscription(subscription: SubscriptionEntity)

    @Query("SELECT * FROM items JOIN subscriptions ON subscriptions.itemId = items.id WHERE items.id = :id")
    suspend fun subscription(id: String): SubscriptionRow?

    @Query("SELECT * FROM items JOIN subscriptions ON subscriptions.itemId = items.id WHERE isArchived = 0 ORDER BY nextBillingEpochDay ASC")
    fun observeSubscriptions(): Flow<List<SubscriptionRow>>

    @Query("SELECT * FROM items JOIN subscriptions ON subscriptions.itemId = items.id WHERE status = 'ACTIVE' AND nextBillingEpochDay < :todayEpochDay")
    suspend fun overdueActiveSubscriptions(todayEpochDay: Long): List<SubscriptionRow>

    // ---- Documents ---------------------------------------------------------
    @Upsert suspend fun upsertDocument(document: DocumentEntity)

    @Query("SELECT * FROM items JOIN documents ON documents.itemId = items.id WHERE items.id = :id")
    suspend fun document(id: String): DocumentRow?

    /** Soonest expiry first; documents without an expiry date last. */
    @Query("SELECT * FROM items JOIN documents ON documents.itemId = items.id WHERE isArchived = 0 ORDER BY expiryEpochDay IS NULL, expiryEpochDay ASC")
    fun observeDocuments(): Flow<List<DocumentRow>>

    @Query("SELECT * FROM documents WHERE itemId = :id") suspend fun documentEntity(id: String): DocumentEntity?

    // ---- Timed reminders ---------------------------------------------------
    @Upsert suspend fun upsertReminderNote(note: ReminderNoteEntity)

    @Query("SELECT * FROM items JOIN reminder_notes ON reminder_notes.itemId = items.id WHERE items.id = :id")
    suspend fun reminderNote(id: String): ReminderNoteRow?

    @Query("SELECT * FROM items JOIN reminder_notes ON reminder_notes.itemId = items.id WHERE isArchived = 0 ORDER BY dateEpochDay, minuteOfDay")
    fun observeReminderNotes(): Flow<List<ReminderNoteRow>>

    @Query("SELECT * FROM items JOIN reminder_notes ON reminder_notes.itemId = items.id WHERE isArchived = 0")
    suspend fun allReminderNotes(): List<ReminderNoteRow>

    @Query("SELECT * FROM reminder_notes WHERE itemId = :id") suspend fun reminderNoteEntity(id: String): ReminderNoteEntity?

    // ---- Payments (subscriptions marked as paid) ----------------------------
    @Insert suspend fun insertPayment(p: PaymentEntity)
    @Upsert suspend fun upsertPayments(p: List<PaymentEntity>)
    @Query("SELECT * FROM payments WHERE itemId = :itemId ORDER BY paidEpochDay DESC, createdAt DESC") suspend fun payments(itemId: String): List<PaymentEntity>
    @Query("SELECT * FROM payments") fun observePayments(): Flow<List<PaymentEntity>>
    @Query("DELETE FROM payments WHERE id = :id") suspend fun deletePayment(id: String)
    @Query("DELETE FROM payments WHERE itemId = :itemId") suspend fun deletePaymentsFor(itemId: String)

    // ---- Notification history (this phone only) ----------------------------
    @Upsert suspend fun upsertNotification(n: NotificationLogEntity)
    @Query("SELECT * FROM notification_log ORDER BY postedAt DESC LIMIT 200") fun observeNotifications(): Flow<List<NotificationLogEntity>>
    @Query("SELECT COUNT(*) FROM notification_log WHERE readAt IS NULL") fun observeUnreadCount(): Flow<Int>
    @Query("SELECT * FROM notification_log WHERE notificationId = :id") suspend fun notification(id: Int): NotificationLogEntity?
    /** An action (paid, done, snoozed) always wins over a plain "opened". */
    @Query("UPDATE notification_log SET readAt = :at, outcome = :outcome, snoozedUntil = :until WHERE notificationId = :id")
    suspend fun resolveNotification(id: Int, at: Long, outcome: String, until: Long?)
    @Query("UPDATE notification_log SET readAt = COALESCE(readAt, :at), outcome = COALESCE(outcome, 'OPENED') WHERE notificationId = :id")
    suspend fun openNotification(id: Int, at: Long)
    @Query("UPDATE notification_log SET readAt = :at WHERE readAt IS NULL") suspend fun markAllNotificationsRead(at: Long)
    /**
     * The item was marked paid/done in the app: its waiting alerts (unread or snoozed) are settled
     * too. For a timed reminder only alerts up to the occurrence that was done.
     */
    @Query(
        "UPDATE notification_log SET readAt = :at, outcome = :outcome, snoozedUntil = NULL WHERE itemId = :itemId " +
            "AND (readAt IS NULL OR outcome = 'SNOOZED') AND (:occurrence IS NULL OR occurrenceMinute IS NULL OR occurrenceMinute <= :occurrence)"
    )
    suspend fun settleItemNotifications(itemId: String, at: Long, outcome: String, occurrence: Long?)
    @Query("DELETE FROM notification_log WHERE notificationId NOT IN (SELECT notificationId FROM notification_log ORDER BY postedAt DESC LIMIT 200)")
    suspend fun trimNotifications()
    @Query("DELETE FROM notification_log") suspend fun clearNotifications()

    // ---- Links -------------------------------------------------------------
    @Upsert suspend fun upsertLink(link: SavedLinkEntity)
    @Query("SELECT COUNT(*) FROM saved_links WHERE url = :url") suspend fun linkCount(url: String): Int

    @Query("SELECT * FROM items JOIN saved_links ON saved_links.itemId = items.id WHERE items.id = :id")
    suspend fun link(id: String): LinkRow?

    // ---- Tags --------------------------------------------------------------
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTag(tag: TagEntity): Long
    @Query("SELECT id FROM tags WHERE name = :name") suspend fun tagId(name: String): Long?
    @Query("DELETE FROM item_tags WHERE itemId = :itemId") suspend fun clearItemTags(itemId: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertItemTags(refs: List<ItemTagCrossRef>)
    @Query("SELECT tags.name FROM tags JOIN item_tags ON item_tags.tagId = tags.id WHERE item_tags.itemId = :itemId ORDER BY tags.name")
    suspend fun tagsFor(itemId: String): List<String>

    // ---- Raw detail rows (cloud sync) ---------------------------------------
    @Query("SELECT * FROM receipts WHERE itemId = :id") suspend fun receiptEntity(id: String): ReceiptEntity?
    @Query("SELECT * FROM warranties WHERE itemId = :id") suspend fun warrantyEntity(id: String): WarrantyEntity?
    @Query("SELECT * FROM subscriptions WHERE itemId = :id") suspend fun subscriptionEntity(id: String): SubscriptionEntity?
    @Query("SELECT * FROM saved_links WHERE itemId = :id") suspend fun linkEntity(id: String): SavedLinkEntity?

    // ---- Attachments --------------------------------------------------------
    @Query("SELECT * FROM attachments WHERE itemId = :itemId ORDER BY kind, position")
    suspend fun attachments(itemId: String): List<AttachmentEntity>
    @Query("SELECT * FROM attachments WHERE id = :id") suspend fun attachment(id: String): AttachmentEntity?
    @Query("SELECT * FROM attachments WHERE uploaded = 0") suspend fun unuploadedAttachments(): List<AttachmentEntity>
    @Query("SELECT id FROM attachments") suspend fun allAttachmentIds(): List<String>
    @Upsert suspend fun upsertAttachments(attachments: List<AttachmentEntity>)
    @Query("DELETE FROM attachments WHERE id IN (:ids)") suspend fun deleteAttachments(ids: List<String>)
    @Query("UPDATE attachments SET uploaded = 1 WHERE id = :id") suspend fun markAttachmentUploaded(id: String)

    // ---- Activity history -------------------------------------------------
    @Insert suspend fun insertEvent(event: ActivityEventEntity)
    /** Events from the cloud: the unique eventId makes a second copy a no-op. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertEventIfMissing(event: ActivityEventEntity)
    @Query("SELECT * FROM activity_events WHERE uploaded = 0") suspend fun unuploadedEvents(): List<ActivityEventEntity>
    @Query("UPDATE activity_events SET uploaded = 1 WHERE eventId = :eventId") suspend fun markEventUploaded(eventId: String)
    @Query("SELECT * FROM activity_events ORDER BY at DESC LIMIT :limit")
    fun observeEvents(limit: Int): Flow<List<ActivityEventEntity>>
    @Query("DELETE FROM activity_events WHERE itemId = :itemId") suspend fun deleteEventsFor(itemId: String)
    @Query("DELETE FROM activity_events") suspend fun deleteAllEvents()
    /** History in [from, until), e.g. one calendar day; [until] is exclusive. */
    @Query("SELECT eventId FROM activity_events WHERE at >= :from AND at < :until") suspend fun eventIdsBetween(from: Long, until: Long): List<String>
    @Query("DELETE FROM activity_events WHERE at >= :from AND at < :until") suspend fun deleteEventsBetween(from: Long, until: Long)
    @Query("DELETE FROM activity_events WHERE eventId = :eventId") suspend fun deleteEvent(eventId: String)

    // ---- Reminders ---------------------------------------------------------
    @Query("DELETE FROM reminders WHERE itemId = :itemId") suspend fun clearReminders(itemId: String)
    @Insert suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Query(
        """SELECT reminders.id AS reminderId, items.id AS itemId, items.type AS type,
                  items.title AS title, reminders.offsetDays AS offsetDays,
                  reminders.triggerEpochDay AS triggerEpochDay
           FROM reminders JOIN items ON items.id = reminders.itemId
           WHERE reminders.delivered = 0 AND reminders.triggerEpochDay <= :todayEpochDay
             AND items.isArchived = 0"""
    )
    suspend fun dueReminders(todayEpochDay: Long): List<DueReminderRow>

    @Query("UPDATE reminders SET delivered = 1 WHERE id IN (:ids)")
    suspend fun markDelivered(ids: List<Long>)
}
