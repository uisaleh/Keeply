package app.keeply.data.repo

import androidx.room.withTransaction
import app.keeply.core.domain.FtsQuery
import app.keeply.core.domain.ReminderPlanner
import app.keeply.core.domain.SubscriptionMath
import app.keeply.core.model.ActivityEvent
import app.keeply.core.model.ActivityKind
import app.keeply.core.model.BillingCycle
import app.keeply.core.model.Document
import app.keeply.core.model.DocumentKind
import app.keeply.core.model.ReminderCategory
import app.keeply.core.model.ReminderNote
import app.keeply.core.model.Repeat
import app.keeply.core.model.ItemType
import app.keeply.core.model.Money
import app.keeply.core.model.Receipt
import app.keeply.core.model.SavedLink
import app.keeply.core.model.Subscription
import app.keeply.core.model.VaultItemSummary
import app.keeply.core.model.Warranty
import app.keeply.attachments.AttachmentFiles
import app.keeply.data.db.ActivityEventEntity
import app.keeply.data.db.AttachmentEntity
import app.keeply.data.db.DocumentEntity
import app.keeply.data.db.ItemEntity
import app.keeply.data.db.ItemListRow
import app.keeply.data.db.PaymentEntity
import app.keeply.core.model.Payment
import java.time.LocalDateTime
import app.keeply.data.db.ReminderNoteEntity
import app.keeply.data.db.ItemTagCrossRef
import app.keeply.data.db.KeeplyDatabase
import app.keeply.data.db.ReceiptEntity
import app.keeply.data.db.ReceiptLineEntity
import app.keeply.data.db.ReminderEntity
import app.keeply.data.db.SavedLinkEntity
import app.keeply.data.db.SubscriptionEntity
import app.keeply.data.db.TagEntity
import app.keeply.data.db.WarrantyEntity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

class RoomVaultRepository(
    private val db: KeeplyDatabase,
    private val files: AttachmentFiles,
    private val clock: Clock = Clock.systemDefaultZone(),
) : VaultRepository {

    private val dao = db.vaultDao()

    private val _changes = MutableSharedFlow<VaultChange>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.SUSPEND)
    /** Local writes, emitted after they commit. Imports from the cloud are not echoed here. */
    val changes: SharedFlow<VaultChange> = _changes
    private fun now() = clock.millis()
    private fun today() = LocalDate.now(clock)

    override fun observeItems(type: ItemType?) = dao.observeItems(type?.name).map { it.map(ItemListRow::toSummary) }
    override fun observeRecent(limit: Int) = dao.observeRecent(limit).map { it.map(ItemListRow::toSummary) }
    override fun observeCounts(): Flow<Map<ItemType, Int>> =
        dao.observeCounts().map { rows -> rows.associate { ItemType.valueOf(it.type) to it.count } }

    override fun search(query: String): Flow<List<VaultItemSummary>> {
        val match = FtsQuery.build(query) ?: return flowOf(emptyList())
        return dao.search(match).map { it.map(ItemListRow::toSummary) }
    }

    override fun observeReceipts() = dao.observeReceipts().map { rows -> rows.map { it.toModel() } }
    override fun observeWarranties() = dao.observeWarranties().map { rows -> rows.map { it.toModel() } }
    override fun observeSubscriptions() = dao.observeSubscriptions().map { rows -> rows.map { it.toModel() } }
    override fun observeDocuments() = dao.observeDocuments().map { rows -> rows.map { it.toModel() } }
    override fun observeReminderNotes() = dao.observeReminderNotes().map { rows -> rows.map { it.toModel() } }
    /** For the alarm scheduler after a reboot or an alarm firing (no Flow collector running yet). */
    suspend fun allReminderNotes(): List<ReminderNote> = dao.allReminderNotes().map { it.toModel() }

    override suspend fun counts() = dao.counts().associate { ItemType.valueOf(it.type) to it.count }
    override suspend fun typeOf(id: String) = dao.item(id)?.let { ItemType.valueOf(it.type) }

    override suspend fun receipt(id: String): Receipt? = dao.receipt(id)?.let { row ->
        row.toModel(dao.receiptLines(id).map { it.description }, dao.tagsFor(id))
    }
    override suspend fun warranty(id: String): Warranty? = dao.warranty(id)?.toModel(dao.tagsFor(id))
    override suspend fun subscription(id: String): Subscription? = dao.subscription(id)?.toModel(dao.tagsFor(id))
    override suspend fun link(id: String): SavedLink? = dao.link(id)?.toModel(dao.tagsFor(id))
    override suspend fun document(id: String): Document? = dao.document(id)?.toModel(dao.tagsFor(id))
    override suspend fun reminderNote(id: String): ReminderNote? = dao.reminderNote(id)?.toModel(dao.tagsFor(id))
    override suspend fun attachments(itemId: String): List<AttachmentEntity> = dao.attachments(itemId)

    // ---- Writes ---------------------------------------------------------------

    private suspend fun upsertItem(
        id: String?, type: ItemType, title: String, subtitle: String, notes: String,
        searchText: String, isFavorite: Boolean? = null,
        /** Only receipts and links set this; other types keep whatever is there. */
        remindAt: java.time.LocalDateTime? = null, setRemindAt: Boolean = false,
        remindRepeat: Repeat = Repeat.NEVER,
        /** Optional page link; every form sends it, so it's always replaced. */
        url: String? = null,
    ): String {
        val existing = id?.let { dao.item(it) }
        val itemId = existing?.id ?: UUID.randomUUID().toString()
        val t = now()
        dao.upsertItem(
            ItemEntity(
                id = itemId, type = type.name, title = title.trim(), subtitle = subtitle.trim(),
                notes = notes.trim(), searchText = searchText,
                isFavorite = isFavorite ?: existing?.isFavorite ?: false,
                isArchived = existing?.isArchived ?: false,
                isDemo = existing?.isDemo ?: false,
                createdAt = existing?.createdAt ?: t, updatedAt = t,
                logoId = existing?.logoId,
                remindAtLocalMinute = if (setRemindAt) remindAt?.let(::dateTimeToLocalMinute) else existing?.remindAtLocalMinute,
                remindRepeat = if (setRemindAt) remindRepeat.name.takeIf { remindAt != null && remindRepeat != Repeat.NEVER } else existing?.remindRepeat,
                pageUrl = url?.trim()?.takeIf { it.isNotEmpty() },
                // The mark itself stays (a claimed warranty is still claimed), but after an edit
                // the old state no longer applies, so it can't be undone any more.
                markedAt = existing?.markedAt,
                markUndo = null,
            )
        )
        return itemId
    }

    private suspend fun logEvent(itemId: String, type: ItemType, title: String, kind: ActivityKind) =
        dao.insertEvent(
            ActivityEventEntity(
                eventId = UUID.randomUUID().toString(), itemId = itemId, type = type.name,
                title = title.trim(), kind = kind.name, at = now(),
            )
        )

    /** Added for a new item, Edited for an existing one (demo items are never logged). */
    private suspend fun logSave(existing: ItemEntity?, id: String, type: ItemType, title: String, kind: ActivityKind? = null) {
        if (existing?.isDemo == true) return
        logEvent(id, type, title, kind ?: if (existing == null) ActivityKind.ADDED else ActivityKind.EDITED)
    }

    /** Makes the item's attachments exactly [wanted]; returns the ids that were removed. */
    private suspend fun applyAttachments(itemId: String, wanted: List<AttachmentDraft>): List<String> {
        val existing = dao.attachments(itemId).associateBy { it.id }
        val removed = existing.keys - wanted.map { it.id }.toSet()
        if (removed.isNotEmpty()) dao.deleteAttachments(removed.toList())
        val t = now()
        dao.upsertAttachments(
            wanted.mapIndexed { i, a ->
                AttachmentEntity(a.id, itemId, a.kind, i, existing[a.id]?.createdAt ?: t, uploaded = existing[a.id]?.uploaded ?: false)
            }
        )
        dao.item(itemId)?.let { item -> dao.upsertItem(item.copy(logoId = wanted.firstOrNull { it.kind == KIND_LOGO }?.id)) }
        return removed.toList()
    }

    /** Emits the change after the transaction commits, and drops removed files from the device. */
    private suspend fun afterSave(id: String, removedAttachments: List<String>): String {
        if (removedAttachments.isNotEmpty()) {
            files.delete(removedAttachments)
            _changes.emit(VaultChange.AttachmentsDeleted(removedAttachments))
        }
        _changes.emit(VaultChange.Upserted(id))
        return id
    }

    private suspend fun replaceReminders(itemId: String, eventDate: LocalDate, offsets: List<Int>) {
        dao.clearReminders(itemId)
        val planned = ReminderPlanner.plan(eventDate, offsets, today())
        if (planned.isNotEmpty()) {
            dao.insertReminders(planned.map { ReminderEntity(itemId = itemId, offsetDays = it.offsetDays, triggerEpochDay = it.date.toEpochDay()) })
        }
    }

    override suspend fun saveReceipt(draft: ReceiptDraft): String {
        var removed = emptyList<String>()
        val id = db.withTransaction {
            val existing = draft.id?.let { dao.item(it) }
            val subtitle = Money(draft.totalMinor, draft.currency).format() + " · " + LocalDate.ofEpochDay(draft.purchaseEpochDay).format(SUBTITLE_DATE)
            val id = upsertItem(
                draft.id, ItemType.RECEIPT,
                title = draft.merchant,
                subtitle = subtitle,
                notes = draft.notes,
                searchText = listOf(draft.merchant, draft.category, draft.paymentMethod, draft.lineItems.joinToString(" ")).joinToString(" "),
                remindAt = draft.remindAt, setRemindAt = true, remindRepeat = draft.remindRepeat, url = draft.url,
            )
            dao.upsertReceipt(ReceiptEntity(id, draft.merchant.trim(), draft.totalMinor, draft.currency, draft.purchaseEpochDay, draft.category.trim(), draft.paymentMethod.trim()))
            dao.clearReceiptLines(id)
            val lines = draft.lineItems.map(String::trim).filter(String::isNotEmpty)
            if (lines.isNotEmpty()) dao.insertReceiptLines(lines.mapIndexed { i, d -> ReceiptLineEntity(receiptId = id, position = i, description = d) })
            removed = applyAttachments(id, draft.attachments)
            logSave(existing, id, ItemType.RECEIPT, draft.merchant)
            id
        }
        return afterSave(id, removed)
    }

    override suspend fun saveWarranty(draft: WarrantyDraft): String {
        var removed = emptyList<String>()
        val id = db.withTransaction {
            val existing = draft.id?.let { dao.item(it) }
            val brandModel = listOf(draft.brand, draft.model).filter(String::isNotBlank).joinToString(" ")
            val id = upsertItem(
                draft.id, ItemType.WARRANTY,
                title = draft.productName,
                subtitle = brandModel,
                notes = draft.notes,
                searchText = listOf(draft.brand, draft.model, draft.retailer, draft.serialNumber).joinToString(" "),
                url = draft.url,
            )
            dao.upsertWarranty(
                WarrantyEntity(id, draft.productName.trim(), draft.brand.trim(), draft.model.trim(), draft.purchaseEpochDay,
                    draft.durationMonths, draft.expiryEpochDay, draft.retailer.trim(), draft.serialNumber.trim(), draft.sourceReceiptId,
                    encodeOffsets(draft.reminderOffsetsDays))
            )
            if (existing?.markedAt != null) dao.clearReminders(id) // claimed: stays quiet
            else replaceReminders(id, LocalDate.ofEpochDay(draft.expiryEpochDay), draft.reminderOffsetsDays)
            removed = applyAttachments(id, draft.attachments)
            logSave(existing, id, ItemType.WARRANTY, draft.productName)
            id
        }
        return afterSave(id, removed)
    }

    override suspend fun saveSubscription(draft: SubscriptionDraft): String {
        var removed = emptyList<String>()
        val id = db.withTransaction {
            val existing = draft.id?.let { dao.item(it) }
            val wasActive = draft.id?.let { dao.subscriptionEntity(it)?.status == "ACTIVE" } ?: false
            val cycle = BillingCycle.valueOf(draft.cycle)
            val subtitle = Money(draft.amountMinor, draft.currency).format() + " · " + cycle.label
            val id = upsertItem(
                draft.id, ItemType.SUBSCRIPTION,
                title = draft.serviceName,
                subtitle = subtitle,
                notes = draft.notes,
                searchText = listOf(draft.category, draft.paymentMethod, cycle.label).joinToString(" "),
                url = draft.url,
            )
            dao.upsertSubscription(
                SubscriptionEntity(id, draft.serviceName.trim(), draft.amountMinor, draft.currency, draft.cycle,
                    draft.customIntervalDays, draft.nextBillingEpochDay, draft.category.trim(), draft.paymentMethod.trim(), draft.status,
                    encodeOffsets(draft.reminderOffsetsDays))
            )
            if (draft.status == "ACTIVE") {
                replaceReminders(id, LocalDate.ofEpochDay(draft.nextBillingEpochDay), draft.reminderOffsetsDays)
            } else {
                dao.clearReminders(id)
            }
            removed = applyAttachments(id, draft.attachments)
            // Switching an active subscription to cancelled is worth its own line in the history.
            logSave(existing, id, ItemType.SUBSCRIPTION, draft.serviceName, if (wasActive && draft.status == "CANCELLED") ActivityKind.CANCELLED else null)
            id
        }
        return afterSave(id, removed)
    }

    override suspend fun saveDocument(draft: DocumentDraft): String {
        var removed = emptyList<String>()
        val id = db.withTransaction {
            val existing = draft.id?.let { dao.item(it) }
            val kind = runCatching { DocumentKind.valueOf(draft.kind) }.getOrDefault(DocumentKind.OTHER)
            val expiry = draft.expiryEpochDay?.let(LocalDate::ofEpochDay)
            // The number is searchable on this device but never shown in a list subtitle.
            val subtitle = listOfNotNull(kind.label, expiry?.let { "expires ${it.format(SUBTITLE_DATE)}" }).joinToString(" · ")
            val id = upsertItem(
                draft.id, ItemType.DOCUMENT,
                title = draft.name.ifBlank { kind.label },
                subtitle = subtitle,
                notes = draft.notes,
                searchText = listOf(kind.label, draft.holder, draft.issuer, draft.number).joinToString(" "),
                url = draft.url,
            )
            val offsets = if (expiry != null) draft.reminderOffsetsDays else emptyList()
            dao.upsertDocument(
                DocumentEntity(id, kind.name, draft.number.trim(), draft.holder.trim(), draft.issuer.trim(), draft.issueEpochDay, draft.expiryEpochDay, encodeOffsets(offsets))
            )
            if (expiry != null && offsets.isNotEmpty()) replaceReminders(id, expiry, offsets) else dao.clearReminders(id)
            removed = applyAttachments(id, draft.attachments)
            logSave(existing, id, ItemType.DOCUMENT, draft.name.ifBlank { kind.label })
            id
        }
        return afterSave(id, removed)
    }

    override suspend fun saveReminderNote(draft: ReminderNoteDraft): String = db.withTransaction {
        val existing = draft.id?.let { dao.item(it) }
        val category = runCatching { ReminderCategory.valueOf(draft.category) }.getOrDefault(ReminderCategory.OTHER)
        val repeat = runCatching { Repeat.valueOf(draft.repeat) }.getOrDefault(Repeat.NEVER)
        val date = LocalDate.ofEpochDay(draft.dateEpochDay)
        val time = LocalTime.of(draft.minuteOfDay / 60, draft.minuteOfDay % 60).format(SUBTITLE_TIME)
        val subtitle = when (repeat) {
            Repeat.NEVER -> "${category.label} · ${date.format(SUBTITLE_DATE)}, $time"
            Repeat.YEARLY -> "${category.label} · every year on ${date.format(DAY_MONTH)}"
            else -> "${category.label} · ${repeat.label.lowercase()} at $time"
        }
        val id = upsertItem(
            draft.id, ItemType.REMINDER,
            title = draft.title,
            subtitle = subtitle,
            notes = draft.note,
            searchText = listOf(category.label, repeat.label).joinToString(" "),
            url = draft.url,
        )
        dao.upsertReminderNote(
            ReminderNoteEntity(id, category.name, draft.dateEpochDay, draft.minuteOfDay, repeat.name, draft.alertsMinutesBefore.distinct().sortedDescending().joinToString(","))
        )
        logSave(existing, id, ItemType.REMINDER, draft.title)
        id
    }.also { _changes.tryEmit(VaultChange.Upserted(it)) }

    override suspend fun saveLink(draft: LinkDraft): String = db.withTransaction {
        val existing = draft.id?.let { dao.item(it) }
        val tags = draft.tags.map { it.trim().lowercase() }.filter(String::isNotEmpty).distinct()
        val id = upsertItem(
            draft.id, ItemType.LINK,
            title = draft.title.ifBlank { draft.domain },
            subtitle = draft.domain,
            notes = draft.notes,
            searchText = listOf(draft.domain, draft.collection, tags.joinToString(" ")).joinToString(" "),
            isFavorite = draft.isFavorite,
            remindAt = draft.remindAt, setRemindAt = true, remindRepeat = draft.remindRepeat, url = draft.url,
        )
        dao.upsertLink(SavedLinkEntity(id, draft.url, draft.domain, draft.title.trim(), draft.collection.trim()))
        dao.clearItemTags(id)
        val tagIds = tags.map { name -> dao.tagId(name) ?: dao.insertTag(TagEntity(name = name)) }
        if (tagIds.isNotEmpty()) dao.insertItemTags(tagIds.map { ItemTagCrossRef(id, it) })
        logSave(existing, id, ItemType.LINK, draft.title.ifBlank { draft.domain })
        id
    }.also { _changes.tryEmit(VaultChange.Upserted(it)) }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        dao.setFavorite(id, favorite, now())
        _changes.emit(VaultChange.Upserted(id))
    }
    override suspend fun delete(id: String) {
        val attachmentIds = dao.attachments(id).map { it.id }
        val item = dao.item(id)
        db.withTransaction {
            dao.deleteItem(id) // attachment rows cascade
            if (item != null && !item.isDemo) logEvent(id, ItemType.valueOf(item.type), item.title, ActivityKind.DELETED)
        }
        files.delete(attachmentIds)
        _changes.emit(VaultChange.Deleted(id))
        if (attachmentIds.isNotEmpty()) _changes.emit(VaultChange.AttachmentsDeleted(attachmentIds))
    }
    /** Removes sample items left by older test builds; they were never synced. */
    override suspend fun deleteDemoData() = dao.deleteDemoItems()
    override suspend fun deleteAllData() {
        val attachmentIds = dao.allAttachmentIds()
        db.withTransaction {
            dao.deleteAllItems()
            dao.deleteAllEvents()
            dao.clearNotifications()
        }
        files.deleteAll()
        _changes.emit(VaultChange.Cleared)
        if (attachmentIds.isNotEmpty()) _changes.emit(VaultChange.AttachmentsDeleted(attachmentIds))
    }

    override suspend fun rollSubscriptionsForward() = db.withTransaction {
        val today = today()
        dao.overdueActiveSubscriptions(today.toEpochDay()).map { row ->
            val s = row.subscription
            val next = SubscriptionMath.nextBillingOnOrAfter(
                LocalDate.ofEpochDay(s.nextBillingEpochDay), BillingCycle.valueOf(s.cycle), s.customIntervalDays, today,
            )
            val offsets = decodeOffsets(s.reminderOffsets)
            dao.upsertSubscription(s.copy(nextBillingEpochDay = next.toEpochDay()))
            replaceReminders(s.itemId, next, offsets)
            s.itemId
        }
    }.forEach { _changes.emit(VaultChange.Upserted(it)) }

    // ---- Cloud sync -------------------------------------------------------------

    suspend fun userItemIds(): List<String> = dao.userItemIds()

    /** Null for demo items and items that no longer exist: neither belongs in the cloud. */
    suspend fun exportBundle(id: String): ItemBundle? {
        val item = dao.item(id)?.takeUnless { it.isDemo } ?: return null
        return ItemBundle(
            item = item,
            receipt = dao.receiptEntity(id),
            receiptLines = dao.receiptLines(id).map { it.description },
            warranty = dao.warrantyEntity(id),
            subscription = dao.subscriptionEntity(id),
            link = dao.linkEntity(id),
            document = dao.documentEntity(id),
            reminderNote = dao.reminderNoteEntity(id),
            tags = dao.tagsFor(id),
            attachments = dao.attachments(id),
            payments = dao.payments(id),
        )
    }

    /** Writes a bundle from the cloud as-is (keeping its id and timestamps). Not echoed to [changes]. */
    suspend fun importBundle(b: ItemBundle) {
        val before = dao.attachments(b.item.id).map { it.id }.toSet()
        importBundleTx(b)
        // Files for photos the other device removed; new ones download when first shown.
        files.delete(before - b.attachments.map { it.id }.toSet())
    }

    private suspend fun importBundleTx(b: ItemBundle) = db.withTransaction {
        val id = b.item.id
        // Replace rather than upsert so detail rows from an older shape of the item don't linger.
        dao.deleteItem(id)
        dao.upsertItem(b.item.copy(isDemo = false))
        b.receipt?.let { r ->
            dao.upsertReceipt(r)
            if (b.receiptLines.isNotEmpty()) {
                dao.insertReceiptLines(b.receiptLines.mapIndexed { i, d -> ReceiptLineEntity(receiptId = id, position = i, description = d) })
            }
        }
        // A warranty can point at a receipt that hasn't arrived yet; the link fills in when it does.
        b.warranty?.let { w ->
            val source = w.sourceReceiptId?.takeIf { dao.receiptEntity(it) != null }
            dao.upsertWarranty(w.copy(sourceReceiptId = source))
            replaceReminders(id, LocalDate.ofEpochDay(w.expiryEpochDay), decodeOffsets(w.reminderOffsets))
        }
        b.subscription?.let { s ->
            dao.upsertSubscription(s)
            if (s.status == "ACTIVE") replaceReminders(id, LocalDate.ofEpochDay(s.nextBillingEpochDay), decodeOffsets(s.reminderOffsets))
        }
        b.link?.let { dao.upsertLink(it) }
        b.reminderNote?.let { dao.upsertReminderNote(it) }
        b.document?.let { d ->
            dao.upsertDocument(d)
            val expiry = d.expiryEpochDay
            val offsets = decodeOffsets(d.reminderOffsets)
            if (expiry != null && offsets.isNotEmpty()) replaceReminders(id, LocalDate.ofEpochDay(expiry), offsets)
        }
        val tagIds = b.tags.map { name -> dao.tagId(name) ?: dao.insertTag(TagEntity(name = name)) }
        if (tagIds.isNotEmpty()) dao.insertItemTags(tagIds.map { ItemTagCrossRef(id, it) })
        // They exist in Cloud Storage already (that's where this bundle came from).
        if (b.attachments.isNotEmpty()) dao.upsertAttachments(b.attachments.map { it.copy(itemId = id, uploaded = true) })
        if (b.payments.isNotEmpty()) dao.upsertPayments(b.payments.map { it.copy(itemId = id) })
        // A claimed warranty from another device must stay quiet here too.
        if (b.warranty != null && b.item.markedAt != null) dao.clearReminders(id)
    }

    suspend fun updatedAt(id: String): Long? = dao.item(id)?.updatedAt
    suspend fun isDemo(id: String): Boolean = dao.item(id)?.isDemo == true

    /** Applies a deletion that happened on another device. Not echoed to [changes]. */
    suspend fun deleteLocal(id: String) = dao.deleteItem(id)

    /** Signing out: the next account on this device must start from an empty vault. */
    suspend fun clearLocal() {
        db.withTransaction {
            dao.deleteAllItems()
            dao.deleteAllEvents()
            dao.clearNotifications()
        }
        files.deleteAll()
    }

    fun observeActivity(limit: Int = 200): Flow<List<ActivityEvent>> = dao.observeEvents(limit).map { rows ->
        rows.mapNotNull { e ->
            val type = runCatching { ItemType.valueOf(e.type) }.getOrNull() ?: return@mapNotNull null
            val kind = runCatching { ActivityKind.valueOf(e.kind) }.getOrNull() ?: return@mapNotNull null
            ActivityEvent(e.id, e.itemId, type, e.title, kind, java.time.Instant.ofEpochMilli(e.at))
        }
    }

    /**
     * Permanently removes history recorded in [from, until) (epoch millis, [until] exclusive),
     * on this device and in the cloud. The items themselves are untouched.
     */
    suspend fun deleteActivity(from: Long = Long.MIN_VALUE, until: Long = Long.MAX_VALUE) {
        val ids = db.withTransaction {
            dao.eventIdsBetween(from, until).also { dao.deleteEventsBetween(from, until) }
        }
        if (ids.isNotEmpty()) _changes.emit(VaultChange.HistoryDeleted(ids))
    }

    /** A history entry was removed on another device. */
    suspend fun deleteEventLocal(eventId: String) = dao.deleteEvent(eventId)

    /** A reminder notification was shown; recorded so Activity shows what you were reminded about. */
    suspend fun logReminded(itemId: String) {
        val item = dao.item(itemId)?.takeUnless { it.isDemo } ?: return
        logEvent(itemId, ItemType.valueOf(item.type), item.title, ActivityKind.REMINDED)
        _changes.emit(VaultChange.HistoryAdded)
    }

    /** "Mark as done" on a reminder notification. */
    suspend fun logDone(itemId: String) {
        val item = dao.item(itemId)?.takeUnless { it.isDemo } ?: return
        logEvent(itemId, ItemType.valueOf(item.type), item.title, ActivityKind.DONE)
        _changes.emit(VaultChange.HistoryAdded)
    }

    suspend fun hasLink(url: String): Boolean = dao.linkCount(url) > 0 || dao.linkCount(url.trimEnd('/')) > 0

    fun observeItemAlerts(): Flow<List<ItemEntity>> = dao.observeItemAlerts()
    suspend fun item(id: String): ItemEntity? = dao.item(id)
    suspend fun itemAlerts(): List<ItemEntity> = dao.itemAlerts()

    // ---- Notification history (this phone only) ------------------------------------

    fun observeNotifications() = dao.observeNotifications()
    fun observeUnreadNotifications(): Flow<Int> = dao.observeUnreadCount()

    /** A reminder was shown (or came back after a snooze): it's unread again. */
    suspend fun logNotification(n: app.keeply.data.db.NotificationLogEntity) {
        dao.upsertNotification(n)
        dao.trimNotifications()
    }

    suspend fun resolveNotification(id: Int, outcome: String, snoozedUntil: Long? = null) = dao.resolveNotification(id, now(), outcome, snoozedUntil)
    suspend fun openNotification(id: Int) = dao.openNotification(id, now())
    suspend fun markAllNotificationsRead() = dao.markAllNotificationsRead(now())
    /** Permanently empties the notification list on this phone. Scheduled and snoozed reminders still fire. */
    suspend fun clearNotifications() = dao.clearNotifications()
    suspend fun notification(id: Int) = dao.notification(id)

    // ---- Mark as paid / done / renewed / claimed / refunded / read, and undo ----------

    fun observePayments(): Flow<List<Payment>> = dao.observePayments().map { rows -> rows.map { it.toModel() } }
    suspend fun payments(itemId: String): List<Payment> = dao.payments(itemId).map { it.toModel() }

    /**
     * Subscription: records a payment today for the next billing date and moves that date on by
     * one cycle (reminders follow). Counts in Spent on the day it was paid.
     */
    suspend fun markSubscriptionPaid(id: String) = commitMark(id, ActivityKind.PAID) { item ->
        val sub = dao.subscriptionEntity(id) ?: return@commitMark null
        val due = LocalDate.ofEpochDay(sub.nextBillingEpochDay)
        // Exactly one cycle after the date just paid (Oct 5 monthly -> Nov 5).
        val next = SubscriptionMath.nextBillingOnOrAfter(due, BillingCycle.valueOf(sub.cycle), sub.customIntervalDays, due.plusDays(1))
        dao.insertPayment(PaymentEntity(UUID.randomUUID().toString(), id, sub.amountMinor, sub.currency, today().toEpochDay(), due.toEpochDay(), now()))
        dao.upsertSubscription(sub.copy(nextBillingEpochDay = next.toEpochDay(), status = "ACTIVE"))
        if (decodeOffsets(sub.reminderOffsets).isNotEmpty()) replaceReminders(id, next, decodeOffsets(sub.reminderOffsets))
        item.copy(markedAt = now(), markUndo = "pay")
    }

    /** Timed reminder: this occurrence is done; a repeating one moves on to the next. */
    suspend fun markReminderDone(id: String, occurrence: LocalDateTime) = commitMark(id, ActivityKind.DONE) { item ->
        item.copy(markedAt = dateTimeToLocalMinute(occurrence), markUndo = item.markedAt?.toString() ?: "")
    }

    /** Document renewed: new expiry date, reminders planned for it. */
    suspend fun markDocumentRenewed(id: String, newExpiry: LocalDate) = commitMark(id, ActivityKind.DONE) { item ->
        val doc = dao.documentEntity(id) ?: return@commitMark null
        dao.upsertDocument(doc.copy(expiryEpochDay = newExpiry.toEpochDay()))
        val offsets = decodeOffsets(doc.reminderOffsets)
        if (offsets.isNotEmpty()) replaceReminders(id, newExpiry, offsets) else dao.clearReminders(id)
        item.copy(markedAt = now(), markUndo = doc.expiryEpochDay?.toString() ?: "")
    }

    /** Warranty claimed, receipt refunded or link read: a flag; claimed warranties and read links stop alerting. */
    suspend fun markFlag(id: String) = commitMark(id, ActivityKind.DONE) { item ->
        when (ItemType.valueOf(item.type)) {
            ItemType.WARRANTY -> dao.clearReminders(id)
            else -> Unit
        }
        // A read link's "remind me" is cancelled; keep it so undo can bring it back.
        val undo = if (item.type == ItemType.LINK.name) item.remindAtLocalMinute?.let { "$it|${item.remindRepeat.orEmpty()}" } ?: "" else ""
        val cleared = if (item.type == ItemType.LINK.name) item.copy(remindAtLocalMinute = null, remindRepeat = null) else item
        cleared.copy(markedAt = now(), markUndo = undo)
    }

    /** Puts back whatever the last mark changed. */
    suspend fun undoMark(id: String) {
        val title = db.withTransaction {
            val item = dao.item(id) ?: return@withTransaction null
            val undo = item.markUndo ?: return@withTransaction null
            val restored = when (ItemType.valueOf(item.type)) {
                ItemType.SUBSCRIPTION -> {
                    val last = dao.payments(id).firstOrNull() ?: return@withTransaction null
                    val sub = dao.subscriptionEntity(id) ?: return@withTransaction null
                    dao.deletePayment(last.id)
                    dao.upsertSubscription(sub.copy(nextBillingEpochDay = last.forEpochDay))
                    val offsets = decodeOffsets(sub.reminderOffsets)
                    if (offsets.isNotEmpty()) replaceReminders(id, LocalDate.ofEpochDay(last.forEpochDay), offsets)
                    // Earlier payments can still be undone one by one.
                    val earlier = dao.payments(id).firstOrNull()
                    item.copy(markedAt = earlier?.createdAt, markUndo = earlier?.let { "pay" })
                }
                ItemType.REMINDER -> item.copy(markedAt = undo.toLongOrNull(), markUndo = null)
                ItemType.DOCUMENT -> {
                    val doc = dao.documentEntity(id)
                    val old = undo.toLongOrNull()
                    if (doc != null) {
                        dao.upsertDocument(doc.copy(expiryEpochDay = old))
                        val offsets = decodeOffsets(doc.reminderOffsets)
                        if (old != null && offsets.isNotEmpty()) replaceReminders(id, LocalDate.ofEpochDay(old), offsets) else dao.clearReminders(id)
                    }
                    item.copy(markedAt = null, markUndo = null)
                }
                ItemType.WARRANTY -> {
                    dao.warrantyEntity(id)?.let { w -> replaceReminders(id, LocalDate.ofEpochDay(w.expiryEpochDay), decodeOffsets(w.reminderOffsets)) }
                    item.copy(markedAt = null, markUndo = null)
                }
                ItemType.LINK -> {
                    val minute = undo.substringBefore('|').toLongOrNull()
                    val repeat = undo.substringAfter('|', "").ifEmpty { null }
                    item.copy(markedAt = null, markUndo = null, remindAtLocalMinute = minute ?: item.remindAtLocalMinute, remindRepeat = repeat ?: item.remindRepeat)
                }
                else -> item.copy(markedAt = null, markUndo = null)
            }
            dao.upsertItem(restored.copy(updatedAt = now()))
            logEvent(id, ItemType.valueOf(item.type), item.title, ActivityKind.UNDONE)
            item.title
        }
        if (title != null) _changes.emit(VaultChange.Upserted(id))
    }

    /** Runs a mark in one transaction, saves the item, records it in Activity and syncs. */
    private suspend fun commitMark(id: String, kind: ActivityKind, change: suspend (ItemEntity) -> ItemEntity?) {
        val done = db.withTransaction {
            val item = dao.item(id) ?: return@withTransaction false
            val updated = change(item) ?: return@withTransaction false
            dao.upsertItem(updated.copy(updatedAt = now()))
            if (!item.isDemo) logEvent(id, ItemType.valueOf(item.type), item.title, kind)
            val occurrence = if (item.type == ItemType.REMINDER.name) updated.markedAt else null
            dao.settleItemNotifications(id, now(), if (kind == ActivityKind.PAID) "PAID" else "DONE", occurrence)
            true
        }
        if (done) _changes.emit(VaultChange.Upserted(id))
    }

    suspend fun unuploadedEvents(): List<ActivityEventEntity> = dao.unuploadedEvents()
    suspend fun markEventUploaded(eventId: String) = dao.markEventUploaded(eventId)

    /** A history entry from the user's other device; already in the cloud, so marked uploaded. */
    suspend fun importEvent(e: ActivityEventEntity) = dao.insertEventIfMissing(e.copy(id = 0, uploaded = true))

    suspend fun unuploadedAttachments(): List<AttachmentEntity> = dao.unuploadedAttachments()
    suspend fun attachment(id: String): AttachmentEntity? = dao.attachment(id)
    suspend fun markAttachmentUploaded(id: String) = dao.markAttachmentUploaded(id)


    companion object {
        const val KIND_PHOTO = "PHOTO"
        const val KIND_LOGO = "LOGO"
        private val SUBTITLE_DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        private val SUBTITLE_TIME: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
    }
}
