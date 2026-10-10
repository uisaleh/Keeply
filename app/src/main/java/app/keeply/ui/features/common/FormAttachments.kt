package app.keeply.ui.features.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.keeply.BuildConfig
import app.keeply.ui.components.LogoField
import app.keeply.ui.components.PhotoSourceSheet
import app.keeply.ui.components.PhotoViewer
import app.keeply.ui.components.PhotosField
import app.keeply.ui.components.rememberImagePicker

/** Photos section for a form: thumbnails, camera/gallery sheet and a full-screen viewer. */
@Composable
fun FormPhotos(editor: AttachmentsEditor, hint: String, label: String = "Photos") {
    if (!BuildConfig.PHOTOS_ENABLED) return
    var chooseSource by rememberSaveable { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberImagePicker(multiple = true, maxItems = AttachmentsEditor.MAX_PHOTOS - editor.photos.size) { editor.addPhotos(it) }

    PhotosField(
        label = label,
        photoIds = editor.photos,
        canAdd = editor.canAddPhotos,
        importing = editor.importing,
        error = editor.importError,
        hint = hint,
        onAdd = { chooseSource = true },
        onOpen = { viewing = it },
    )
    if (chooseSource) PhotoSourceSheet("Add a photo", picker, onDismiss = { chooseSource = false })
    viewing?.let { id ->
        PhotoViewer(id, onDismiss = { viewing = null }, onDelete = { editor.removePhoto(id); viewing = null })
    }
}

/** Logo picker for a form (e.g. upload the Claude logo for a Claude subscription). */
@Composable
fun FormLogo(editor: AttachmentsEditor, hint: String) {
    if (!BuildConfig.PHOTOS_ENABLED) return
    var chooseSource by rememberSaveable { mutableStateOf(false) }
    val picker = rememberImagePicker(multiple = false) { uris -> uris.firstOrNull()?.let(editor::setLogo) }

    LogoField(editor.logo, editor.importing, onClick = { chooseSource = true }, hint = hint)
    if (chooseSource) {
        PhotoSourceSheet(
            if (editor.logo == null) "Add a logo" else "Change logo", picker,
            onDismiss = { chooseSource = false },
            onRemove = if (editor.logo != null) editor::removeLogo else null,
            removeLabel = "Remove logo",
        )
    }
}
