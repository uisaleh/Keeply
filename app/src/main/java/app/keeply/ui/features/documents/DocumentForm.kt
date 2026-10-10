package app.keeply.ui.features.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import androidx.navigation.toRoute
import app.keeply.AppContainer
import app.keeply.core.domain.RelativeDays
import app.keeply.core.model.DocumentKind
import app.keeply.data.repo.DocumentDraft
import app.keeply.data.settings.NotificationCategory
import app.keeply.ui.app.keeplyViewModel
import app.keeply.ui.components.ChoiceChips
import app.keeply.ui.components.DateField
import app.keeply.ui.components.KeeplyTag
import app.keeply.ui.components.KeeplyTextField
import app.keeply.ui.components.TagTone
import app.keeply.ui.features.common.PageLinkField
import app.keeply.ui.features.common.pageUrlError
import app.keeply.ui.features.common.pageUrlToSave
import app.keeply.ui.features.common.AttachmentsEditor
import app.keeply.ui.features.common.FieldGroupLabel
import app.keeply.ui.features.common.FormPhotos
import app.keeply.ui.features.common.FormScaffold
import app.keeply.ui.features.common.FormViewModel
import app.keeply.ui.features.common.ReminderPicker
import app.keeply.ui.features.common.ReminderPreviewButton
import app.keeply.ui.features.common.parseCustomOffset
import app.keeply.ui.navigation.DocumentFormRoute
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Reminder choices for documents: passports need months of notice, licences a few weeks. */
val DOCUMENT_PRESETS = listOf(180, 90, 30, 7)

@OptIn(SavedStateHandleSaveableApi::class)
class DocumentFormViewModel(private val container: AppContainer, handle: SavedStateHandle) : FormViewModel() {
    private val route = handle.toRoute<DocumentFormRoute>()
    val isEditing = route.id != null
    val itemId: String? get() = route.id

    var kindName by handle.saveable { mutableStateOf(DocumentKind.PASSPORT.name) }
        private set
    var name by handle.saveable { mutableStateOf("") }
    var number by handle.saveable { mutableStateOf("") }
    var holder by handle.saveable { mutableStateOf("") }
    var issuer by handle.saveable { mutableStateOf("") }
    var issueEpochDay by handle.saveable { mutableStateOf<Long?>(null) }
    var hasExpiry by handle.saveable { mutableStateOf(DocumentKind.PASSPORT.expiresByDefault) }
    var expiryEpochDay by handle.saveable { mutableStateOf(LocalDate.now().plusYears(5).toEpochDay()) }
    var remind by handle.saveable { mutableStateOf(DocumentKind.PASSPORT.defaultReminders.isNotEmpty()) }
    var offsets by handle.saveable { mutableStateOf(DocumentKind.PASSPORT.defaultReminders.joinToString(",")) }
    var customOffset by handle.saveable { mutableStateOf("") }
    var notes by handle.saveable { mutableStateOf("") }
    var pageUrl by handle.saveable { mutableStateOf("") }
    private var loaded by handle.saveable { mutableStateOf(false) }
    val attachments = AttachmentsEditor(container.attachmentFiles, viewModelScope, handle)

    var showErrors by mutableStateOf(false)
        private set

    val kind: DocumentKind get() = DocumentKind.valueOf(kindName)
    val selectedOffsets: Set<Int> get() = offsets.split(',').mapNotNull { it.toIntOrNull() }.toSet()
    val customOffsetError get() = if (customOffset.isNotEmpty() && parseCustomOffset(customOffset) == null) "Enter 0–365" else null
    val reminderError get() = if (showErrors && hasExpiry && remind && selectedOffsets.isEmpty() && parseCustomOffset(customOffset) == null) "Pick at least one reminder, or turn reminders off" else null

    /**
     * Picking a type applies its defaults (a passport expires and reminds months ahead, a
     * national ID doesn't) unless the user is editing an existing document.
     */
    fun selectKind(k: DocumentKind) {
        val previousDefaultName = kind.label
        kindName = k.name
        if (name.isBlank() || name == previousDefaultName) name = ""
        if (!isEditing) {
            hasExpiry = k.expiresByDefault
            remind = k.defaultReminders.isNotEmpty()
            offsets = k.defaultReminders.joinToString(",")
        }
    }

    fun toggleOffset(d: Int) {
        val s = selectedOffsets.toMutableSet()
        if (!s.add(d)) s.remove(d)
        offsets = s.joinToString(",")
    }

    init {
        if (!loaded) viewModelScope.launch {
            if (route.id != null) {
                container.vault.document(route.id)?.let { d ->
                    kindName = d.kind.name
                    name = d.meta.title.takeUnless { it == d.kind.label } ?: ""
                    number = d.number; holder = d.holder; issuer = d.issuer
                    issueEpochDay = d.issueDate?.toEpochDay()
                    hasExpiry = d.expiryDate != null
                    d.expiryDate?.let { expiryEpochDay = it.toEpochDay() }
                    remind = d.reminderOffsetsDays.isNotEmpty()
                    offsets = d.reminderOffsetsDays.filter { it in DOCUMENT_PRESETS }.joinToString(",")
                    customOffset = d.reminderOffsetsDays.firstOrNull { it !in DOCUMENT_PRESETS }?.toString() ?: ""
                    notes = d.meta.notes
                    pageUrl = d.meta.url.orEmpty()
                }
            }
            if (route.id != null) attachments.load(container.vault.attachments(route.id)) else attachments.markLoaded()
            loaded = true
            markBaseline()
        }
    }

    override fun onCleared() = attachments.discardUnsaved()

    override fun snapshot() = listOf(
        kindName, name, number, holder, issuer, issueEpochDay, hasExpiry, expiryEpochDay, remind, offsets, customOffset, notes, attachments.drafts(), pageUrl,
    ).joinToString("\u0000")

    fun save() {
        showErrors = true
        if (customOffsetError != null || reminderError != null) return
        if (pageUrlError(pageUrl) != null) return
        val reminders = if (hasExpiry && remind) (selectedOffsets + listOfNotNull(parseCustomOffset(customOffset))).toList() else emptyList()
        commit(isEditing) {
            container.vault.saveDocument(
                DocumentDraft(
                    route.id, kindName, name.ifBlank { kind.label }, number, holder, issuer, issueEpochDay,
                    if (hasExpiry) expiryEpochDay else null, reminders, notes, attachments.drafts(), pageUrlToSave(pageUrl),
                )
            )
            attachments.markSaved()
            finished = true
        }
    }

    fun delete() = runSave { route.id?.let { container.vault.delete(it) }; finished = true }
}

@Composable
fun DocumentFormScreen(onDone: () -> Unit) {
    val vm = keeplyViewModel { c, h -> DocumentFormViewModel(c, h) }
    LaunchedEffect(vm.finished) { if (vm.finished) onDone() }
    val c = Keeply.colors

    FormScaffold(
        eyebrow = "Document",
        title = if (vm.isEditing) vm.name.ifBlank { vm.kind.label } else "Add a document",
        isEditing = vm.isEditing, saving = vm.saving, errorMessage = vm.errorMessage, hasUnsavedChanges = vm.hasUnsavedChanges,
        confirmSave = vm.confirmSave, onConfirmSave = vm::confirmPendingSave, onDismissSave = vm::dismissPendingSave,
        onBack = onDone, onSave = vm::save, onDelete = vm::delete, deleteWhat = "document",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            FieldGroupLabel("Type")
            ChoiceChips(DocumentKind.entries, vm.kind, label = { it.label }, onSelect = vm::selectKind)
        }
        KeeplyTextField(
            "Name", vm.name, { vm.name = it }, placeholder = vm.kind.label,
            hint = "Optional. E.g. \"My passport\" or \"Dad's licence\".",
        )
        KeeplyTextField(
            "Document number", vm.number, { vm.number = it.take(40) }, keyboardType = KeyboardType.Ascii,
            hint = "Optional. Kept private: hidden in lists and never shown in notifications.",
        )
        PageLinkField(vm.pageUrl, { vm.pageUrl = it }, hint = "Optional. Where to renew it, e.g. the passport office site.")
        KeeplyTextField("Holder's name", vm.holder, { vm.holder = it }, placeholder = "As printed on the document")
        KeeplyTextField("Issued by", vm.issuer, { vm.issuer = it }, placeholder = "Country or authority")
        DateField("Issue date", vm.issueEpochDay?.let(LocalDate::ofEpochDay), { vm.issueEpochDay = it.toEpochDay() }, hint = "Optional")
        if (vm.issueEpochDay != null) {
            TextButton(onClick = { vm.issueEpochDay = null }) {
                Text("Remove issue date", style = Keeply.type.bodySmMedium, color = c.link)
            }
        }

        SwitchRow("Has an expiry date", if (vm.hasExpiry) "Turn off for documents that never expire, like most ID cards" else "This document doesn't expire", vm.hasExpiry) { vm.hasExpiry = it }
        if (vm.hasExpiry) {
            DateField("Expires on", LocalDate.ofEpochDay(vm.expiryEpochDay), { vm.expiryEpochDay = it.toEpochDay() })
            ExpiryStatus(LocalDate.ofEpochDay(vm.expiryEpochDay))
            SwitchRow("Remind me before it expires", if (vm.remind) "You'll get a notification on the days you pick" else "No reminders for this document", vm.remind) { vm.remind = it }
            if (vm.remind) {
                ReminderPicker(DOCUMENT_PRESETS, vm.selectedOffsets, vm::toggleOffset, vm.customOffset, { vm.customOffset = it }, vm.customOffsetError ?: vm.reminderError)
                ReminderPreviewButton(
                    NotificationCategory.DOCUMENTS,
                    itemTitle = vm.name.ifBlank { "Your ${vm.kind.label.lowercase()}" },
                    daysBefore = (vm.selectedOffsets + listOfNotNull(parseCustomOffset(vm.customOffset))).minOrNull() ?: 30,
                    itemId = vm.itemId,
                )
            }
        }
        FormPhotos(vm.attachments, hint = "Photo of the front and back. Up to ${AttachmentsEditor.MAX_PHOTOS} photos.")
        KeeplyTextField("Notes", vm.notes, { vm.notes = it }, singleLine = false, minLines = 2, imeAction = ImeAction.Default)
    }
}

/** A labelled switch where the whole row is the touch target. */
@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Keeply.colors
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Keeply.type.bodyMdMedium, color = c.text)
            Text(subtitle, style = Keeply.type.caption, color = c.textMuted)
        }
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, uncheckedTrackColor = c.fieldFill, uncheckedBorderColor = c.borderStrong))
    }
}

@Composable
private fun ExpiryStatus(expiry: LocalDate) {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), expiry)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
        when {
            days < 0 -> KeeplyTag("Expired", TagTone.Danger)
            days <= 30 -> KeeplyTag(RelativeDays.countdown(days), TagTone.Danger)
            else -> KeeplyTag("Valid", TagTone.Success)
        }
        Text(RelativeDays.describeUntil(expiry, LocalDate.now(), "expire"), style = Keeply.type.bodySm, color = Keeply.colors.textMuted)
    }
}
