package app.keeply.ui.features.common

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import app.keeply.attachments.AttachmentFiles
import app.keeply.attachments.ImageSize
import app.keeply.data.db.AttachmentEntity
import app.keeply.data.repo.AttachmentDraft
import app.keeply.data.repo.RoomVaultRepository.Companion.KIND_LOGO
import app.keeply.data.repo.RoomVaultRepository.Companion.KIND_PHOTO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A form's photos and logo while it's being edited. Picked images are copied into app
 * storage straight away; nothing is attached to the item until Save. Files added during
 * this edit that never get saved are deleted again by [discardUnsaved].
 */
class AttachmentsEditor(
    private val files: AttachmentFiles,
    private val scope: CoroutineScope,
    private val handle: SavedStateHandle,
) {
    var photos by mutableStateOf(handle.get<ArrayList<String>>(KEY_PHOTOS)?.toList() ?: emptyList())
        private set
    var logo by mutableStateOf(handle.get<String>(KEY_LOGO))
        private set
    var importing by mutableStateOf(false)
        private set
    /** Set when a picked file couldn't be read as an image. */
    var importError by mutableStateOf<String?>(null)
        private set

    private val added: MutableSet<String> = handle.get<ArrayList<String>>(KEY_ADDED)?.toMutableSet() ?: mutableSetOf()
    private var loaded: Boolean = handle.get<Boolean>(KEY_LOADED) ?: false

    val canAddPhotos get() = photos.size < MAX_PHOTOS

    /** Fills in the item's saved attachments, once (not again after process recreation). */
    fun load(existing: List<AttachmentEntity>) {
        if (loaded) return
        photos = existing.filter { it.kind == KIND_PHOTO }.sortedBy { it.position }.map { it.id }
        logo = existing.firstOrNull { it.kind == KIND_LOGO }?.id
        loaded = true
        persist()
    }

    fun markLoaded() {
        loaded = true
        persist()
    }

    fun addPhotos(uris: List<Uri>) = import(uris.take(MAX_PHOTOS - photos.size), ImageSize.PHOTO) { ids -> photos = photos + ids }

    fun setLogo(uri: Uri) = import(listOf(uri), ImageSize.ICON) { ids ->
        logo?.let(::dropIfUnsaved)
        logo = ids.firstOrNull()
    }

    fun removePhoto(id: String) {
        photos = photos - id
        dropIfUnsaved(id)
        persist()
    }

    fun removeLogo() {
        logo?.let(::dropIfUnsaved)
        logo = null
        persist()
    }

    fun drafts(): List<AttachmentDraft> =
        photos.map { AttachmentDraft(it, KIND_PHOTO) } + listOfNotNull(logo?.let { AttachmentDraft(it, KIND_LOGO) })

    /** After a successful save the new files belong to the item. */
    fun markSaved() {
        added.clear()
        persist()
    }

    /** Leaving without saving: remove files that were only ever part of this edit. */
    fun discardUnsaved() {
        files.delete(added)
        added.clear()
    }

    private fun import(uris: List<Uri>, maxSide: Int, onDone: (List<String>) -> Unit) {
        if (uris.isEmpty()) return
        scope.launch {
            importing = true
            importError = null
            val ids = uris.mapNotNull { files.import(it, maxSide) }
            if (ids.size < uris.size) importError = "Some images couldn't be opened. Try a different photo."
            added += ids
            onDone(ids)
            importing = false
            persist()
        }
    }

    private fun dropIfUnsaved(id: String) {
        if (added.remove(id)) files.delete(listOf(id))
    }

    private fun persist() {
        handle[KEY_PHOTOS] = ArrayList(photos)
        handle[KEY_LOGO] = logo
        handle[KEY_ADDED] = ArrayList(added)
        handle[KEY_LOADED] = loaded
    }

    companion object {
        const val MAX_PHOTOS = 6
        private const val KEY_PHOTOS = "att_photos"
        private const val KEY_LOGO = "att_logo"
        private const val KEY_ADDED = "att_added"
        private const val KEY_LOADED = "att_loaded"
    }
}
