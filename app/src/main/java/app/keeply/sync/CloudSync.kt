package app.keeply.sync

import android.net.Uri
import android.util.Log
import app.keeply.attachments.AttachmentFiles
import app.keeply.data.db.ActivityEventEntity
import app.keeply.data.repo.ItemBundle
import app.keeply.data.repo.RoomVaultRepository
import app.keeply.data.repo.VaultChange
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Keeps the signed-in user's vault in Cloud Firestore at `users/{uid}/items/{itemId}`.
 *
 * Room stays the app's source for reads, so the UI and FTS search are unchanged and work
 * offline. Local writes are pushed as they happen (Firestore queues them while offline and
 * retries after restarts); a snapshot listener applies writes from the user's other devices.
 * When both sides edited an item, the newer `updatedAt` wins.
 */
class CloudSync(
    private val vault: RoomVaultRepository,
    private val scope: CoroutineScope,
    private val outbox: SyncOutbox,
    private val files: AttachmentFiles,
) {
    // Fetched fresh each time: signing out terminates the instance to clear its disk cache.
    private val firestore get() = FirebaseFirestore.getInstance()
    private val storage get() = FirebaseStorage.getInstance().reference

    /** `users/{uid}/attachments/{id}.jpg`; the storage rules only allow the owner in. */
    private fun attachmentRef(uid: String, id: String) = storage.child("users/$uid/attachments/$id.jpg")
    private val json = Json { ignoreUnknownKeys = true }

    private var uid: String? = null
    private var pushJob: Job? = null
    private var listener: ListenerRegistration? = null
    // Serialises cloud imports with local pushes so an import can't interleave with an export of the same item.
    private val lock = Mutex()

    private fun items(uid: String): CollectionReference = firestore.collection("users").document(uid).collection("items")
    /** Activity history: `users/{uid}/events/{eventId}`; entries are never edited, only added or deleted. */
    private fun events(uid: String): CollectionReference = firestore.collection("users").document(uid).collection("events")
    private var eventsListener: ListenerRegistration? = null

    fun start(uid: String) {
        if (this.uid == uid) return
        stop()
        this.uid = uid
        pushJob = scope.launch {
            // Retry anything the server never confirmed (offline when saved, or rejected before setup).
            lock.withLock { retryPending(uid) }
            vault.changes.collect { push(uid, it) }
        }
        listener = items(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Cloud listener stopped: ${error.code}")
                return@addSnapshotListener
            }
            val changes = snapshot?.documentChanges.orEmpty()
            if (changes.isEmpty()) return@addSnapshotListener
            scope.launch { lock.withLock { changes.forEach { applyRemote(it) } } }
        }
        eventsListener = events(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "History listener stopped: ${error.code}")
                return@addSnapshotListener
            }
            val changes = snapshot?.documentChanges.orEmpty().filter { it.type != DocumentChange.Type.MODIFIED }
            if (changes.isEmpty()) return@addSnapshotListener
            scope.launch {
                changes.forEach {
                    // REMOVED: the entry was deleted from Activity on another device.
                    if (it.type == DocumentChange.Type.ADDED) importEvent(it.document)
                    else runCatching { vault.deleteEventLocal(it.document.id) }
                }
            }
        }
    }

    private suspend fun importEvent(doc: com.google.firebase.firestore.DocumentSnapshot) {
        runCatching {
            vault.importEvent(
                ActivityEventEntity(
                    eventId = doc.id,
                    itemId = doc.getString("itemId") ?: return,
                    type = doc.getString("type") ?: return,
                    title = doc.getString("title") ?: return,
                    kind = doc.getString("kind") ?: return,
                    at = doc.getLong("at") ?: return,
                )
            )
        }.onFailure { Log.w(TAG, "Couldn't apply history entry ${doc.id}", it) }
    }

    /** Uploads history entries the cloud hasn't confirmed; each is marked once Firestore acknowledges it. */
    private suspend fun uploadPendingEvents(uid: String) {
        vault.unuploadedEvents().forEach { e ->
            events(uid).document(e.eventId).set(
                mapOf("itemId" to e.itemId, "type" to e.type, "title" to e.title, "kind" to e.kind, "at" to e.at)
            ).addOnSuccessListener { scope.launch { vault.markEventUploaded(e.eventId) } }
                .addOnFailureListener { Log.w(TAG, "History entry ${e.eventId} not confirmed; will retry", it) }
        }
    }

    fun stop() {
        listener?.remove()
        listener = null
        eventsListener?.remove()
        eventsListener = null
        pushJob?.cancel()
        pushJob = null
        uid = null
    }

    /** Uploads items saved on this device before it had an account, so they move into the new account. */
    suspend fun adoptLocalItems(uid: String) = lock.withLock {
        vault.userItemIds().forEach { id -> vault.exportBundle(id)?.let { upload(uid, it) } }
        uploadPendingEvents(uid)
    }

    /**
     * Waits briefly for queued uploads. Returns false if some changes are still only on this
     * device, so the caller can warn before signing out and clearing the local copy.
     */
    suspend fun flush(uid: String, timeoutMs: Long = 8_000): Boolean {
        val sent = withTimeoutOrNull(timeoutMs) { runCatching { firestore.waitForPendingWrites().await() }; true } ?: false
        // Rejected writes also count as "pending": they'd be lost if the device were cleared now.
        return sent && outbox.pendingUpserts(uid).isEmpty() && outbox.pendingDeletes(uid).isEmpty()
    }

    /** The device is being cleared for this account; stop tracking its unsent changes. */
    fun forget(uid: String) = outbox.forget(uid)

    /** Deletes every item in the user's cloud vault. Used when the account is deleted. */
    suspend fun deleteCloudData(uid: String) {
        for (col in listOf(items(uid), events(uid))) {
            while (true) {
                val page = col.limit(400).get().await()
                if (page.isEmpty) break
                firestore.batch().apply { page.documents.forEach { delete(it.reference) } }.commit().await()
            }
        }
        firestore.collection("users").document(uid).delete().await()
        // Storage may not be enabled for the project; that must not block deleting the account.
        runCatching { deleteStorageFolder(storage.child("users/$uid")) }
            .onFailure { Log.w(TAG, "Couldn't clear Storage for the account", it) }
    }

    private suspend fun deleteStorageFolder(ref: com.google.firebase.storage.StorageReference) {
        val listing = ref.listAll().await()
        listing.items.forEach { runCatching { it.delete().await() } }
        listing.prefixes.forEach { deleteStorageFolder(it) }
    }

    /** After sign-out: drop Firestore's on-disk cache so the next account can't see this one's data. */
    suspend fun clearCache() {
        val db = firestore
        runCatching { db.terminate().await() }
        runCatching { db.clearPersistence().await() }
    }

    private suspend fun retryPending(uid: String) {
        // First run with the outbox: anything already on the device may never have reached the cloud.
        if (!outbox.isInitialised(uid)) {
            vault.userItemIds().forEach { outbox.markUpsert(uid, it) }
            outbox.setInitialised(uid)
        }
        outbox.pendingUpserts(uid).forEach { id ->
            val bundle = vault.exportBundle(id)
            if (bundle != null) upload(uid, bundle) else outbox.clear(uid, id) // deleted or demo since
        }
        outbox.pendingDeletes(uid).forEach { id -> deleteRemote(uid, id) }
        vault.unuploadedAttachments().forEach { uploadAttachment(uid, it.id) }
        uploadPendingEvents(uid)
    }

    /** Uploads an attachment's file; it's marked uploaded only once Storage confirms. */
    private fun uploadAttachment(uid: String, id: String) {
        val file = files.file(id)
        if (!file.exists()) return
        attachmentRef(uid, id).putFile(Uri.fromFile(file), JPEG)
            .addOnSuccessListener { scope.launch { vault.markAttachmentUploaded(id) } }
            .addOnFailureListener { Log.w(TAG, "Attachment $id upload failed; will retry", it) }
    }

    /**
     * Makes sure the attachment's file is on this device, downloading it from Storage if
     * it was added on another device. Returns false if it can't be fetched right now.
     */
    suspend fun ensureLocal(id: String): Boolean {
        if (files.exists(id)) return true
        val uid = uid ?: return false
        return runCatching {
            val target = files.file(id)
            val tmp = java.io.File(target.parentFile, "$id.download")
            attachmentRef(uid, id).getFile(tmp).await()
            tmp.renameTo(target)
        }.onFailure { Log.w(TAG, "Attachment $id download failed", it) }.getOrDefault(false)
    }

    private fun deleteRemote(uid: String, id: String) {
        outbox.markDelete(uid, id)
        items(uid).document(id).delete()
            .addOnSuccessListener { outbox.clear(uid, id) }
            .addOnFailureListener { Log.w(TAG, "Delete of $id not confirmed; will retry", it) }
    }

    private suspend fun push(uid: String, change: VaultChange) = lock.withLock {
        runCatching {
            when (change) {
                is VaultChange.Upserted -> {
                    outbox.markUpsert(uid, change.id)
                    val bundle = vault.exportBundle(change.id)
                    if (bundle != null) {
                        upload(uid, bundle)
                        bundle.attachments.filterNot { it.uploaded }.forEach { uploadAttachment(uid, it.id) }
                    } else {
                        outbox.clear(uid, change.id)
                    }
                }
                VaultChange.HistoryAdded -> Unit // uploaded below with the other pending history
                // Not awaited: Firestore queues the deletes while offline and sends them later.
                is VaultChange.HistoryDeleted -> change.eventIds.chunked(400).forEach { chunk ->
                    firestore.batch().apply { chunk.forEach { delete(events(uid).document(it)) } }.commit()
                        .addOnFailureListener { Log.w(TAG, "History delete not confirmed", it) }
                }
                is VaultChange.AttachmentsDeleted -> change.ids.forEach { id ->
                    // Best effort: an orphaned file costs little and is removed with the account.
                    attachmentRef(uid, id).delete().addOnFailureListener { Log.w(TAG, "Attachment $id delete failed", it) }
                }
                is VaultChange.Deleted -> deleteRemote(uid, change.id)
                VaultChange.Cleared -> {
                    val docs = items(uid).get().await().documents + events(uid).get().await().documents
                    docs.chunked(400).forEach { chunk ->
                        firestore.batch().apply { chunk.forEach { delete(it.reference) } }.commit()
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Upload failed for $change", it) }
        // Every local change writes a history entry; send it along.
        runCatching { uploadPendingEvents(uid) }
    }

    /**
     * Not awaited: Firestore applies the write locally right away and uploads when it can.
     * The item leaves the outbox only once the server confirms it; a rejected write (e.g. the
     * database didn't exist yet) stays queued and is retried on the next start.
     */
    private fun upload(uid: String, bundle: ItemBundle) {
        val id = bundle.item.id
        val updatedAt = bundle.item.updatedAt
        outbox.markUpsert(uid, id)
        items(uid).document(id).set(
            mapOf(
                "type" to bundle.item.type,
                "updatedAt" to updatedAt,
                "data" to json.encodeToString(ItemBundle.serializer(), bundle),
            )
        ).addOnSuccessListener {
            // Only clear if no newer local edit is waiting behind this one.
            scope.launch { if (vault.updatedAt(id) == updatedAt) outbox.clear(uid, id) }
        }.addOnFailureListener { Log.w(TAG, "Upload of $id not confirmed; will retry", it) }
    }

    private suspend fun applyRemote(change: DocumentChange) {
        val doc = change.document
        runCatching {
            when (change.type) {
                DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                    val remoteUpdatedAt = doc.getLong("updatedAt") ?: return
                    val local = vault.updatedAt(doc.id)
                    if (local != null && local >= remoteUpdatedAt) return // ours is the same or newer
                    val data = doc.getString("data") ?: return
                    vault.importBundle(json.decodeFromString(ItemBundle.serializer(), data))
                }
                DocumentChange.Type.REMOVED -> if (!vault.isDemo(doc.id)) vault.deleteLocal(doc.id)
            }
        }.onFailure { Log.w(TAG, "Couldn't apply cloud change to ${doc.id}", it) }
    }

    private companion object {
        const val TAG = "KeeplySync"
        /** The storage rules only accept images, so say so explicitly. */
        val JPEG: StorageMetadata = storageMetadata { contentType = "image/jpeg" }
    }
}
