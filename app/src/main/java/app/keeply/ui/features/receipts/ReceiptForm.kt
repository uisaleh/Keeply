package app.keeply.ui.features.receipts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.keeply.core.model.Repeat
import app.keeply.AppContainer
import app.keeply.share.ShareSuggest
import app.keeply.core.model.Money
import app.keeply.data.repo.ReceiptDraft
import app.keeply.ui.app.keeplyViewModel
import app.keeply.ui.components.CurrencyField
import app.keeply.ui.components.DateField
import app.keeply.ui.components.KeeplyTextField
import app.keeply.ui.features.common.PageLinkField
import app.keeply.ui.features.common.pageUrlError
import app.keeply.ui.features.common.pageUrlToSave
import app.keeply.ui.features.common.AlertSection
import app.keeply.ui.features.common.AttachmentsEditor
import app.keeply.ui.features.common.FormPhotos
import app.keeply.ui.features.common.FormScaffold
import app.keeply.ui.features.common.FormViewModel
import app.keeply.ui.navigation.ReceiptFormRoute
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(SavedStateHandleSaveableApi::class)
class ReceiptFormViewModel(private val container: AppContainer, handle: SavedStateHandle) : FormViewModel() {
    private val route = handle.toRoute<ReceiptFormRoute>()
    val isEditing = route.id != null

    var merchant by handle.saveable { mutableStateOf("") }
    var amount by handle.saveable { mutableStateOf("") }
    var currency by handle.saveable { mutableStateOf("USD") }
    var purchaseEpochDay by handle.saveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var category by handle.saveable { mutableStateOf("") }
    var paymentMethod by handle.saveable { mutableStateOf("") }
    var lineItems by handle.saveable { mutableStateOf("") }
    var notes by handle.saveable { mutableStateOf("") }
    var pageUrl by handle.saveable { mutableStateOf("") }
    private var loaded by handle.saveable { mutableStateOf(false) }
    var remindOn by handle.saveable { mutableStateOf(false) }
    var remindDay by handle.saveable { mutableStateOf(LocalDate.now().plusDays(1).toEpochDay()) }
    var remindMinute by handle.saveable { mutableStateOf(9 * 60) }
    var remindRepeat by handle.saveable { mutableStateOf(Repeat.NEVER.name) }
    private val remindAt get() = if (remindOn) LocalDate.ofEpochDay(remindDay).atTime(remindMinute / 60, remindMinute % 60) else null
    val attachments = AttachmentsEditor(container.attachmentFiles, viewModelScope, handle)

    var showErrors by mutableStateOf(false)
        private set
    /** Set after a new receipt is saved, to ask about a warranty. */
    var askWarrantyFor by mutableStateOf<String?>(null)
        private set

    val merchantError get() = if (showErrors && merchant.isBlank()) "Add the shop or merchant name" else null
    val amountError get() = if (showErrors && Money.parse(amount, currency) == null) "Enter an amount like 899.00" else null

    init {
        if (!loaded) viewModelScope.launch {
            if (route.id != null) {
                container.vault.receipt(route.id)?.let { r ->
                    merchant = r.merchant
                    amount = r.total.toDecimal().toPlainString()
                    currency = r.total.currencyCode
                    purchaseEpochDay = r.purchaseDate.toEpochDay()
                    category = r.category
                    paymentMethod = r.paymentMethod
                    lineItems = r.lineItems.joinToString("\n")
                    notes = r.meta.notes
                    pageUrl = r.meta.url.orEmpty()
                    r.meta.remindAt?.let { remindOn = true; remindDay = it.toLocalDate().toEpochDay(); remindMinute = it.hour * 60 + it.minute }
                    remindRepeat = r.meta.remindRepeat.name
                }
            } else {
                currency = container.settings.settings.first().defaultCurrency
                // Shared from another app: start from what we could read, keep the full text as notes.
                route.sharedText?.let { t ->
                    val sug = ShareSuggest.analyse(t)
                    merchant = sug.title; sug.amount?.let { amount = it }; sug.currency?.let { currency = it }; notes = sug.text
                }
            }
            if (route.id != null) attachments.load(container.vault.attachments(route.id)) else attachments.markLoaded()
            loaded = true
            markBaseline()
        }
    }

    override fun snapshot() = listOf(merchant, amount, currency, purchaseEpochDay, category, paymentMethod, lineItems, notes, attachments.drafts(), remindOn, remindDay, remindMinute, remindRepeat, pageUrl).joinToString("\u0000")

    override fun onCleared() = attachments.discardUnsaved()

    fun save() {
        showErrors = true
        val code = currency.trim().uppercase()
        if (merchant.isBlank() || !Money.isValidCurrency(code)) return
        if (pageUrlError(pageUrl) != null) return
        val money = Money.parse(amount, code) ?: return
        commit(isEditing) {
            val id = container.vault.saveReceipt(
                ReceiptDraft(route.id, merchant, money.amountMinor, code, purchaseEpochDay, category, paymentMethod, lineItems.lines(), notes, attachments.drafts(), remindAt = remindAt, remindRepeat = Repeat.valueOf(remindRepeat), url = pageUrlToSave(pageUrl))
            )
            attachments.markSaved()
            if (isEditing) finished = true else askWarrantyFor = id
        }
    }

    fun delete() = runSave { route.id?.let { container.vault.delete(it) }; finished = true }
    fun skipWarranty() { askWarrantyFor = null; finished = true }
}

@Composable
fun ReceiptFormScreen(onDone: () -> Unit, onAddWarranty: (String) -> Unit) {
    val vm = keeplyViewModel { c, h -> ReceiptFormViewModel(c, h) }
    LaunchedEffect(vm.finished) { if (vm.finished) onDone() }

    FormScaffold(
        eyebrow = "Receipt",
        title = if (vm.isEditing) vm.merchant.ifBlank { "Receipt" } else "Add a receipt",
        isEditing = vm.isEditing, saving = vm.saving, errorMessage = vm.errorMessage, hasUnsavedChanges = vm.hasUnsavedChanges,
        confirmSave = vm.confirmSave, onConfirmSave = vm::confirmPendingSave, onDismissSave = vm::dismissPendingSave,
        onBack = onDone, onSave = vm::save, onDelete = vm::delete, deleteWhat = "receipt",
    ) {
        KeeplyTextField("Merchant", vm.merchant, { vm.merchant = it }, placeholder = "Best Buy", error = vm.merchantError)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            KeeplyTextField("Total", vm.amount, { vm.amount = it }, Modifier.weight(2f), placeholder = "899.00", error = vm.amountError, keyboardType = KeyboardType.Decimal)
            CurrencyField("Currency", vm.currency, { vm.currency = it }, Modifier.weight(1f))
        }
        DateField("Purchase date", LocalDate.ofEpochDay(vm.purchaseEpochDay), { vm.purchaseEpochDay = it.toEpochDay() })
        FormPhotos(vm.attachments, hint = "Snap the receipt so you have proof of purchase. Up to ${AttachmentsEditor.MAX_PHOTOS} photos.")
        KeeplyTextField("Items", vm.lineItems, { vm.lineItems = it }, placeholder = "One item per line", singleLine = false, minLines = 2, imeAction = ImeAction.Default)
        PageLinkField(vm.pageUrl, { vm.pageUrl = it }, hint = "Optional. The shop or order page, e.g. daraz.com.bd. Adds the site's logo.")
        KeeplyTextField("Category", vm.category, { vm.category = it }, placeholder = "Electronics")
        KeeplyTextField("Payment method", vm.paymentMethod, { vm.paymentMethod = it }, placeholder = "Card, cash…")
        AlertSection(
            "Remind me", "E.g. before the return window closes or to claim a refund",
            vm.remindOn, { vm.remindOn = it }, vm.remindDay, vm.remindMinute, { vm.remindDay = it }, { vm.remindMinute = it },
            Repeat.valueOf(vm.remindRepeat), { vm.remindRepeat = it.name },
        )
        KeeplyTextField("Notes", vm.notes, { vm.notes = it }, singleLine = false, minLines = 2, imeAction = ImeAction.Default)
    }

    vm.askWarrantyFor?.let { receiptId ->
        AlertDialog(
            onDismissRequest = vm::skipWarranty,
            title = { Text("Does this product have a warranty?", style = Keeply.type.heading4) },
            text = { Text("Keeply can remind you before it expires. We'll fill in the purchase details for you.", style = Keeply.type.bodyMd, color = Keeply.colors.textMuted) },
            confirmButton = { TextButton(onClick = { onAddWarranty(receiptId) }) { Text("Yes, add warranty", color = Keeply.colors.link, style = Keeply.type.button) } },
            dismissButton = { TextButton(onClick = vm::skipWarranty) { Text("Not now", color = Keeply.colors.text, style = Keeply.type.button) } },
            containerColor = Keeply.colors.surfaceRaised,
        )
    }
}
