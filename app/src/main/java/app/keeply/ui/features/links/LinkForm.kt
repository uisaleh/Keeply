package app.keeply.ui.features.links

import java.time.LocalDate
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.produceState
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import androidx.navigation.toRoute
import app.keeply.core.model.Repeat
import app.keeply.AppContainer
import app.keeply.core.domain.UrlParser
import app.keeply.data.repo.LinkDraft
import app.keeply.ui.app.keeplyViewModel
import app.keeply.ui.components.ButtonSize
import app.keeply.ui.components.ButtonVariant
import app.keeply.ui.components.KeeplyButton
import app.keeply.ui.components.KeeplyTextField
import app.keeply.ui.features.common.AlertSection
import app.keeply.ui.features.common.FormScaffold
import app.keeply.ui.features.common.FormViewModel
import app.keeply.ui.navigation.LinkFormRoute
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space
import kotlinx.coroutines.launch

@OptIn(SavedStateHandleSaveableApi::class)
class LinkFormViewModel(private val container: AppContainer, handle: SavedStateHandle) : FormViewModel() {
    private val route = handle.toRoute<LinkFormRoute>()
    val isEditing = route.id != null

    var url by handle.saveable { mutableStateOf("") }
    var title by handle.saveable { mutableStateOf("") }
    var collection by handle.saveable { mutableStateOf("") }
    var tags by handle.saveable { mutableStateOf("") }
    var notes by handle.saveable { mutableStateOf("") }
    var favorite by handle.saveable { mutableStateOf(false) }
    private var loaded by handle.saveable { mutableStateOf(false) }
    var remindOn by handle.saveable { mutableStateOf(false) }
    var remindDay by handle.saveable { mutableStateOf(LocalDate.now().plusDays(1).toEpochDay()) }
    var remindMinute by handle.saveable { mutableStateOf(9 * 60) }
    var remindRepeat by handle.saveable { mutableStateOf(Repeat.NEVER.name) }
    private val remindAt get() = if (remindOn) LocalDate.ofEpochDay(remindDay).atTime(remindMinute / 60, remindMinute % 60) else null

    var showErrors by mutableStateOf(false)
        private set
    var fetchingTitle by mutableStateOf(false)
        private set
    var titleLookupFailed by mutableStateOf(false)
        private set

    val parsed get() = UrlParser.parse(url)
    val urlError get() = if (showErrors && parsed == null) "That doesn't look like a web link" else null

    init {
        if (!loaded) viewModelScope.launch {
            if (route.id != null) {
                container.vault.link(route.id)?.let { l ->
                    url = l.url; title = l.pageTitle; collection = l.collection
                    tags = l.meta.tags.joinToString(", "); notes = l.meta.notes; favorite = l.meta.isFavorite
                    l.meta.remindAt?.let { remindOn = true; remindDay = it.toLocalDate().toEpochDay(); remindMinute = it.hour * 60 + it.minute }
                    remindRepeat = l.meta.remindRepeat.name
                }
            } else if (route.sharedText != null) {
                // Shared text is often "Title https://…"; keep the surrounding words as notes.
                val found = UrlParser.extractFirst(route.sharedText)
                if (found != null) {
                    url = found
                    val rest = route.sharedText.replace(found, "").trim()
                    if (rest.isNotEmpty()) notes = rest
                } else {
                    url = route.sharedText.trim()
                }
                loaded = true
                fetchTitle()
            }
            loaded = true
            markBaseline()
        }
    }

    override fun snapshot() = listOf(url, title, collection, tags, notes, favorite, remindOn, remindDay, remindMinute, remindRepeat).joinToString("\u0000")

    /** One polite request for the page title. Only runs on share or when the user taps. */
    fun fetchTitle() {
        val p = parsed ?: return
        if (fetchingTitle) return
        fetchingTitle = true
        titleLookupFailed = false
        viewModelScope.launch {
            val t = container.linkMetadata.fetchTitle(p.url)
            if (t != null && title.isBlank()) title = t
            titleLookupFailed = t == null
            fetchingTitle = false
        }
    }

    fun save() {
        showErrors = true
        val p = parsed ?: return
        commit(isEditing) {
            container.vault.saveLink(
                LinkDraft(route.id, p.url, p.domain, title, collection, tags.split(','), notes, favorite, remindAt, Repeat.valueOf(remindRepeat))
            )
            finished = true
        }
    }

    fun delete() = runSave { route.id?.let { container.vault.delete(it) }; finished = true }
}

@Composable
fun LinkFormScreen(onDone: () -> Unit) {
    val vm = keeplyViewModel { c, h -> LinkFormViewModel(c, h) }
    val context = LocalContext.current
    LaunchedEffect(vm.finished) { if (vm.finished) onDone() }

    FormScaffold(
        eyebrow = "Saved link",
        title = if (vm.isEditing) vm.title.ifBlank { vm.parsed?.domain ?: "Link" } else "Save a link",
        isEditing = vm.isEditing, saving = vm.saving, errorMessage = vm.errorMessage, hasUnsavedChanges = vm.hasUnsavedChanges,
        confirmSave = vm.confirmSave, onConfirmSave = vm::confirmPendingSave, onDismissSave = vm::dismissPendingSave,
        onBack = onDone, onSave = vm::save, onDelete = vm::delete, deleteWhat = "link",
    ) {
        // The page's own preview image (or a YouTube thumbnail), straight from the web; nothing stored.
        vm.parsed?.url?.let { pageUrl ->
            val fetcher = (LocalContext.current.applicationContext as app.keeply.KeeplyApp).container.linkMetadata
            val image by produceState<String?>(null, pageUrl) { value = fetcher.fetchImage(pageUrl) }
            image?.let { img ->
                coil.compose.AsyncImage(
                    model = img, contentDescription = "Preview image",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.91f).clip(app.keeply.ui.theme.Radius.md),
                )
            }
        }
        KeeplyTextField(
            "Link", vm.url, { vm.url = it }, placeholder = "https://", error = vm.urlError,
            hint = vm.parsed?.domain, keyboardType = KeyboardType.Uri,
        )
        KeeplyTextField("Title", vm.title, { vm.title = it }, placeholder = "Give it a name you'll recognise",
            hint = when {
                vm.fetchingTitle -> "Looking up the page title…"
                vm.titleLookupFailed -> "Couldn't read a title from that page. Type one instead."
                else -> null
            })
        if (vm.parsed != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                if (vm.title.isBlank() && !vm.fetchingTitle) {
                    TextButton(onClick = vm::fetchTitle) { Text("Get page title", style = Keeply.type.bodySmMedium, color = Keeply.colors.link) }
                }
                if (vm.isEditing) {
                    KeeplyButton("Open link", {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.parsed!!.url)))
                        } catch (ignored: ActivityNotFoundException) { }
                    }, variant = ButtonVariant.Outline, size = ButtonSize.Sm, arrow = true)
                }
            }
        }
        KeeplyTextField("Collection", vm.collection, { vm.collection = it }, placeholder = "Inspiration, recipes…")
        KeeplyTextField("Tags", vm.tags, { vm.tags = it }, placeholder = "ux, shopify", hint = "Separate tags with commas")
        // The whole row toggles, not just the small switch.
        Row(
            Modifier.fillMaxWidth().toggleable(vm.favorite, role = Role.Switch, onValueChange = { vm.favorite = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Favourite", style = Keeply.type.bodyMdMedium, color = Keeply.colors.text, modifier = Modifier.weight(1f))
            Switch(
                checked = vm.favorite, onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = Keeply.colors.accent),
            )
        }
        AlertSection(
            "Remind me", "E.g. read or watch it later",
            vm.remindOn, { vm.remindOn = it }, vm.remindDay, vm.remindMinute, { vm.remindDay = it }, { vm.remindMinute = it },
            Repeat.valueOf(vm.remindRepeat), { vm.remindRepeat = it.name },
        )
        KeeplyTextField("Notes", vm.notes, { vm.notes = it }, singleLine = false, minLines = 2, imeAction = ImeAction.Default)
    }
}
