package app.keeply.ui.features.warranties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import androidx.navigation.toRoute
import app.keeply.AppContainer
import app.keeply.core.domain.ReminderPlanner
import app.keeply.core.domain.WarrantyRules
import app.keeply.core.domain.WarrantyStatus
import app.keeply.data.repo.WarrantyDraft
import app.keeply.ui.app.keeplyViewModel
import app.keeply.ui.components.ChoiceChips
import app.keeply.ui.components.DateField
import app.keeply.ui.components.KeeplyTag
import app.keeply.ui.components.KeeplyTextField
import app.keeply.ui.components.TagTone
import app.keeply.ui.components.display
import app.keeply.ui.features.common.PageLinkField
import app.keeply.ui.features.common.pageUrlError
import app.keeply.ui.features.common.pageUrlToSave
import app.keeply.ui.features.common.FieldGroupLabel
import app.keeply.ui.features.common.AttachmentsEditor
import app.keeply.ui.features.common.FormPhotos
import app.keeply.ui.features.common.FormScaffold
import app.keeply.ui.features.common.FormViewModel
import app.keeply.ui.features.common.ReminderPicker
import app.keeply.ui.features.common.parseCustomOffset
import app.keeply.ui.navigation.WarrantyFormRoute
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(SavedStateHandleSaveableApi::class)
class WarrantyFormViewModel(private val container: AppContainer, handle: SavedStateHandle) : FormViewModel() {
    private val route = handle.toRoute<WarrantyFormRoute>()
    val isEditing = route.id != null

    var productName by handle.saveable { mutableStateOf("") }
    var brand by handle.saveable { mutableStateOf("") }
    var model by handle.saveable { mutableStateOf("") }
    var purchaseEpochDay by handle.saveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var useDuration by handle.saveable { mutableStateOf(true) }
    var durationMonths by handle.saveable { mutableStateOf("12") }
    var expiryEpochDay by handle.saveable { mutableStateOf(LocalDate.now().plusYears(1).toEpochDay()) }
    var retailer by handle.saveable { mutableStateOf("") }
    var serialNumber by handle.saveable { mutableStateOf("") }
    var notes by handle.saveable { mutableStateOf("") }
    var offsets by handle.saveable { mutableStateOf(ReminderPlanner.WARRANTY_DEFAULT.joinToString(",")) }
    var customOffset by handle.saveable { mutableStateOf("") }
    private var sourceReceiptId by handle.saveable { mutableStateOf(route.fromReceiptId) }
    var pageUrl by handle.saveable { mutableStateOf("") }
    private var loaded by handle.saveable { mutableStateOf(false) }
    val attachments = AttachmentsEditor(container.attachmentFiles, viewModelScope, handle)

    var showErrors by mutableStateOf(false)
        private set

    val selectedOffsets: Set<Int> get() = offsets.split(',').mapNotNull { it.toIntOrNull() }.toSet()
    fun toggleOffset(d: Int) {
        val s = selectedOffsets.toMutableSet()
        if (!s.add(d)) s.remove(d)
        offsets = s.joinToString(",")
    }

    val nameError get() = if (showErrors && productName.isBlank()) "Add the product name" else null
    val durationError get() = if (showErrors && useDuration && (durationMonths.toIntOrNull() ?: 0) !in 1..600) "Enter months, e.g. 12" else null
    val customError get() = if (customOffset.isNotEmpty() && parseCustomOffset(customOffset) == null) "Enter 0–${ReminderPlanner.MAX_CUSTOM_OFFSET_DAYS}" else null

    /** Expiry derived live so the user sees exactly what will be saved. */
    val computedExpiry: LocalDate?
        get() = if (useDuration) {
            durationMonths.toIntOrNull()?.takeIf { it in 1..600 }?.let { WarrantyRules.expiryFrom(LocalDate.ofEpochDay(purchaseEpochDay), it) }
        } else LocalDate.ofEpochDay(expiryEpochDay)

    init {
        if (!loaded) viewModelScope.launch {
            if (route.id != null) {
                container.vault.warranty(route.id)?.let { w ->
                    productName = w.productName; brand = w.brand; model = w.model
                    purchaseEpochDay = w.purchaseDate.toEpochDay()
                    useDuration = w.durationMonths != null
                    durationMonths = w.durationMonths?.toString() ?: ""
                    expiryEpochDay = w.expiryDate.toEpochDay()
                    retailer = w.retailer; serialNumber = w.serialNumber; notes = w.meta.notes
                    sourceReceiptId = w.sourceReceiptId
                    pageUrl = w.meta.url.orEmpty()
                    val presets = ReminderPlanner.PRESET_OFFSETS.toSet()
                    offsets = w.reminderOffsetsDays.filter { it in presets }.joinToString(",")
                    customOffset = w.reminderOffsetsDays.firstOrNull { it !in presets }?.toString() ?: ""
                }
            } else if (route.fromReceiptId != null) {
                // Prefill from the receipt the user just saved; every field stays editable.
                container.vault.receipt(route.fromReceiptId)?.let { r ->
                    productName = r.lineItems.firstOrNull() ?: ""
                    retailer = r.merchant
                    purchaseEpochDay = r.purchaseDate.toEpochDay()
                }
            }
            if (route.id != null) attachments.load(container.vault.attachments(route.id)) else attachments.markLoaded()
            loaded = true
            markBaseline()
        }
    }

    override fun snapshot() = listOf(productName, brand, model, purchaseEpochDay, useDuration, durationMonths, expiryEpochDay, retailer, serialNumber, notes, offsets, customOffset, attachments.drafts(), pageUrl).joinToString("\u0000")

    override fun onCleared() = attachments.discardUnsaved()

    fun save() {
        showErrors = true
        val expiry = computedExpiry
        if (productName.isBlank() || expiry == null || customError != null) return
        if (pageUrlError(pageUrl) != null) return
        val all = selectedOffsets + listOfNotNull(parseCustomOffset(customOffset))
        commit(isEditing) {
            container.vault.saveWarranty(
                WarrantyDraft(
                    route.id, productName, brand, model, purchaseEpochDay,
                    if (useDuration) durationMonths.toInt() else null, expiry.toEpochDay(),
                    retailer, serialNumber, sourceReceiptId, all.toList(), notes, attachments.drafts(), pageUrlToSave(pageUrl),
                )
            )
            attachments.markSaved()
            finished = true
        }
    }

    fun delete() = runSave { route.id?.let { container.vault.delete(it) }; finished = true }
}

@Composable
fun WarrantyFormScreen(onDone: () -> Unit) {
    val vm = keeplyViewModel { c, h -> WarrantyFormViewModel(c, h) }
    LaunchedEffect(vm.finished) { if (vm.finished) onDone() }

    FormScaffold(
        eyebrow = "Warranty",
        title = if (vm.isEditing) vm.productName.ifBlank { "Warranty" } else "Add a warranty",
        isEditing = vm.isEditing, saving = vm.saving, errorMessage = vm.errorMessage, hasUnsavedChanges = vm.hasUnsavedChanges,
        confirmSave = vm.confirmSave, onConfirmSave = vm::confirmPendingSave, onDismissSave = vm::dismissPendingSave,
        onBack = onDone, onSave = vm::save, onDelete = vm::delete, deleteWhat = "warranty",
    ) {
        KeeplyTextField("Product", vm.productName, { vm.productName = it }, placeholder = "MacBook Pro", error = vm.nameError)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            KeeplyTextField("Brand", vm.brand, { vm.brand = it }, Modifier.weight(1f))
            KeeplyTextField("Model", vm.model, { vm.model = it }, Modifier.weight(1f))
        }
        DateField("Purchase date", LocalDate.ofEpochDay(vm.purchaseEpochDay), { vm.purchaseEpochDay = it.toEpochDay() })

        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            FieldGroupLabel("Cover")
            ChoiceChips(listOf(true, false), vm.useDuration, label = { if (it) "Length of cover" else "End date" }, onSelect = { vm.useDuration = it })
        }
        if (vm.useDuration) {
            KeeplyTextField("Months of cover", vm.durationMonths, { vm.durationMonths = it.filter(Char::isDigit).take(3) }, keyboardType = KeyboardType.Number, error = vm.durationError)
        } else {
            DateField("Warranty ends", LocalDate.ofEpochDay(vm.expiryEpochDay), { vm.expiryEpochDay = it.toEpochDay() })
        }
        vm.computedExpiry?.let { expiry ->
            val status = WarrantyRules.status(expiry, LocalDate.now())
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                KeeplyTag(status.label, when (status) { WarrantyStatus.ACTIVE -> TagTone.Success; WarrantyStatus.EXPIRING_SOON -> TagTone.Gold; WarrantyStatus.EXPIRED -> TagTone.Danger })
                Text("Ends ${expiry.display()}", style = Keeply.type.bodySm, color = Keeply.colors.textMuted)
            }
        }
        ReminderPicker(ReminderPlanner.PRESET_OFFSETS, vm.selectedOffsets, vm::toggleOffset, vm.customOffset, { vm.customOffset = it }, vm.customError)
        PageLinkField(vm.pageUrl, { vm.pageUrl = it }, hint = "Optional. The product or brand page. Adds the site's logo.")
        KeeplyTextField("Retailer", vm.retailer, { vm.retailer = it })
        KeeplyTextField("Serial number", vm.serialNumber, { vm.serialNumber = it })
        FormPhotos(vm.attachments, hint = "Warranty card, serial-number sticker or the product itself. Up to ${AttachmentsEditor.MAX_PHOTOS} photos.")
        KeeplyTextField("Notes", vm.notes, { vm.notes = it }, singleLine = false, minLines = 2, imeAction = ImeAction.Default)
    }
}
