package app.keeply.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import app.keeply.KeeplyApp
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Radius
import app.keeply.ui.theme.Space
import coil.compose.AsyncImage
import java.io.File
import java.util.UUID

/**
 * Shows an attachment by id. If it was added on another device, the file is downloaded from
 * Cloud Storage the first time it's shown; until then (or offline) a placeholder is drawn.
 */
@Composable
fun AttachmentImage(id: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, contentDescription: String? = null) {
    val container = (LocalContext.current.applicationContext as KeeplyApp).container
    val file by produceState<File?>(initialValue = container.attachmentFiles.file(id).takeIf { it.exists() }, id) {
        if (value == null && container.cloudSync.ensureLocal(id)) value = container.attachmentFiles.file(id)
    }
    val c = Keeply.colors
    Box(modifier.background(c.fieldFill), contentAlignment = Alignment.Center) {
        val f = file
        if (f != null) {
            AsyncImage(model = f, contentDescription = contentDescription, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = c.textPlaceholder)
        }
    }
}

/** Launchers for "take a photo" and "choose from gallery". Uses the system photo picker: no storage permission needed. */
class ImagePicker internal constructor(val takePhoto: () -> Unit, val chooseFromGallery: () -> Unit)

@Composable
fun rememberImagePicker(multiple: Boolean, maxItems: Int = 6, onPicked: (List<Uri>) -> Unit): ImagePicker {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onPicked)
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { callback(listOf(it)) }
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { callback(listOf(it)) } }
    val many = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems.coerceAtLeast(2))) { uris ->
        if (uris.isNotEmpty()) callback(uris)
    }
    return remember(multiple) {
        ImagePicker(
            takePhoto = { newCameraUri(context).also { cameraUri = it; camera.launch(it) } },
            chooseFromGallery = {
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (multiple) many.launch(request) else single.launch(request)
            },
        )
    }
}

/** A temporary file the camera app writes into, shared through our FileProvider. */
private fun newCameraUri(context: Context): Uri {
    val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** "Take photo / Choose from gallery / Remove" bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSourceSheet(title: String, picker: ImagePicker, onDismiss: () -> Unit, onRemove: (() -> Unit)? = null, removeLabel: String = "Remove photo") {
    val c = Keeply.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceRaised, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.padding(bottom = Space.lg).navigationBarsPadding()) {
            Text(title, style = Keeply.type.heading3, color = c.text, modifier = Modifier.padding(horizontal = Space.lg).padding(bottom = Space.xs))
            ItemRow(Icons.Outlined.PhotoCamera, "Take a photo", "Use the camera", onClick = { onDismiss(); picker.takePhoto() }, tint = c.tintBlue, modifier = Modifier.padding(horizontal = Space.xs))
            ItemRow(Icons.Outlined.PhotoLibrary, "Choose from gallery", "Pick from your photos", onClick = { onDismiss(); picker.chooseFromGallery() }, tint = c.tintViolet, modifier = Modifier.padding(horizontal = Space.xs))
            if (onRemove != null) {
                ItemRow(Icons.Outlined.Delete, removeLabel, "", onClick = { onDismiss(); onRemove() }, tint = c.dangerSoft to c.danger, modifier = Modifier.padding(horizontal = Space.xs))
            }
        }
    }
}

/** Horizontal strip of photo thumbnails with an "Add photo" tile at the end. */
@Composable
fun PhotosField(
    label: String,
    photoIds: List<String>,
    canAdd: Boolean,
    importing: Boolean,
    error: String?,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    hint: String? = null,
) {
    val c = Keeply.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = Keeply.type.captionBold, color = c.textMuted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            photoIds.forEachIndexed { i, id ->
                AttachmentImage(
                    id,
                    Modifier.size(88.dp).clip(Radius.md).clickable(onClickLabel = "View photo ${i + 1}", role = Role.Button) { onOpen(id) },
                    contentDescription = "Photo ${i + 1}",
                )
            }
            if (importing) {
                Box(Modifier.size(88.dp).clip(Radius.md).background(c.fieldFill), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 2.dp)
                }
            } else if (canAdd) {
                Column(
                    Modifier.size(88.dp).clip(Radius.md).background(c.selected).clickable(onClickLabel = "Add photo", role = Role.Button, onClick = onAdd),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null, tint = c.link)
                    Text("Add photo", style = Keeply.type.micro, color = c.link)
                }
            }
        }
        val sub = error ?: hint
        if (sub != null) Text(sub, style = Keeply.type.caption, color = if (error != null) c.danger else c.textMuted)
    }
}

/** Full-screen photo with close and delete. */
@Composable
fun PhotoViewer(id: String, onDismiss: () -> Unit, onDelete: (() -> Unit)?) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AttachmentImage(id, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, contentDescription = "Photo")
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(Space.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ViewerButton(Icons.Rounded.Close, "Close", onDismiss)
                if (onDelete != null) ViewerButton(Icons.Outlined.Delete, "Delete photo", onDelete)
            }
        }
    }
}

@Composable
private fun ViewerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.clip(Radius.pill).background(Color.Black.copy(alpha = 0.5f))) {
        Icon(icon, contentDescription = label, tint = Color.White)
    }
}

/** Square logo slot: shows the uploaded logo, or a prompt to add one. */
@Composable
fun LogoField(logoId: String?, importing: Boolean, onClick: () -> Unit, label: String = "Logo", hint: String = "Optional. Shown instead of the icon in your lists.") {
    val c = Keeply.colors
    Row(
        Modifier.fillMaxWidth().clip(Radius.md).clickable(onClickLabel = if (logoId == null) "Add logo" else "Change logo", role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(Modifier.size(64.dp).clip(Radius.md).background(c.selected), contentAlignment = Alignment.Center) {
            when {
                importing -> CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
                logoId != null -> AttachmentImage(logoId, Modifier.fillMaxSize(), contentDescription = "Logo")
                else -> Icon(Icons.Outlined.AddAPhoto, contentDescription = null, tint = c.link)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = Keeply.type.bodyMdMedium, color = c.text)
            Text(if (logoId == null) hint else "Tap to change or remove", style = Keeply.type.caption, color = c.textMuted)
        }
    }
}

/** Logo inside the same rounded tile as [IconTile], so list rows line up. */
@Composable
fun LogoTile(logoId: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 44.dp, shape: Shape = Radius.md) {
    AttachmentImage(logoId, modifier.size(size).clip(shape))
}

/** Profile photo when there is one, otherwise the initial (or a person icon). */
@Composable
fun ProfileAvatar(name: String?, photoUrl: String?, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 40.dp) {
    if (photoUrl == null) {
        Avatar(name, modifier, size)
        return
    }
    var failed by remember(photoUrl) { mutableStateOf(false) }
    if (failed) {
        Avatar(name, modifier, size)
    } else {
        AsyncImage(
            model = photoUrl, contentDescription = "Profile photo", contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier = modifier.size(size).clip(Radius.pill).background(Keeply.colors.selected),
        )
    }
}


/**
 * A website's own icon for a saved link, fetched by domain from Google's public favicon service
 * (only the domain is sent, never the full link). Falls back to the plain link tile.
 */
@Composable
fun SiteIconTile(domain: String, fallback: @Composable () -> Unit, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val clean = domain.trim().removePrefix("www.").takeIf { it.contains('.') && !it.contains('/') }
    var failed by remember(clean) { mutableStateOf(clean == null) }
    if (failed) return fallback()
    Box(
        Modifier.size(size).clip(Radius.md).background(Keeply.colors.fieldFill),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = "https://www.google.com/s2/favicons?domain=${android.net.Uri.encode(clean)}&sz=128",
            contentDescription = null,
            onError = { failed = true },
            onSuccess = { s ->
                // The service returns a 16px generic globe when it knows nothing about a site.
                if (s.result.drawable.intrinsicWidth in 1..20) failed = true
            },
            modifier = Modifier.size(size * 0.62f).clip(Radius.sm),
        )
    }
}
