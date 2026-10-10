package app.keeply.ui.features.subscriptions

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
import app.keeply.share.ShareSuggest
import app.keeply.core.domain.ReminderPlanner
import app.keeply.core.domain.SubscriptionMath
import app.keeply.core.model.BillingCycle
import app.keeply.core.model.Money
import app.keeply.core.model.SubscriptionStatus
import app.keeply.data.repo.SubscriptionDraft
import app.keeply.ui.app.keeplyViewModel
import app.keeply.ui.components.ChoiceChips
import app.keeply.ui.components.CurrencyField
import app.keeply.ui.components.DateField
import app.keeply.ui.components.KeeplyTextField
import app.keeply.ui.features.common.PageLinkField
import app.keeply.ui.features.common.pageUrlError
import app.keeply.ui.features.common.pageUrlToSave
import app.keeply.ui.features.common.FieldGroupLabel
import app.keeply.ui.features.common.AttachmentsEditor
import app.keeply.data.settings.NotificationCategory
import app.keeply.ui.features.common.FormLogo
import app.keeply.ui.features.common.ReminderPreviewButton
import app.keeply.ui.features.common.FormPhotos
import app.keeply.ui.features.common.FormScaffold
import app.keeply.ui.features.common.FormViewModel
import app.keeply.ui.features.common.ReminderPicker
import app.keeply.ui.features.common.parseCustomOffset
import app.keeply.ui.navigation.SubscriptionFormRoute
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

private val SUB_PRESETS = listOf(7, 3, 1, 0)

@OptIn(SavedStateHandleSaveableApi::class)
class SubscriptionFormViewModel(private val container: AppContainer, handle: SavedStateHandle) : FormViewModel() {
    private val route = handle.toRoute<SubscriptionFormRoute>()
    val isEditing = route.id != null
    val itemId: String? get() = route.id

    var serviceName by handle.saveable { mutableStateOf("") }
    var amount by handle.saveable { mutableStateOf("") }
    var currency by handle.saveable { mutableStateOf("USD") }
    var cycle by handle.saveable { mutableStateOf(BillingCycle.MONTHLY.name) }
    var customDays by handle.saveable { mutableStateOf("") }
    var nextBillingEpochDay by handle.saveable { mutableStateOf(LocalDate.now().plusMonths(1).toEpochDay()) }
    var category by handle.saveable { mutableStateOf("") }
    var paymentMethod by handle.saveable { mutableStateOf("") }
    var status by handle.saveable { mutableStateOf(SubscriptionStatus.ACTIVE.name) }
    var notes by handle.saveable { mutableStateOf("") }
    var offsets by handle.saveable { mutableStateOf(ReminderPlanner.SUBSCRIPTION_DEFAULT.joinToString(",")) }
    var customOffset by handle.saveable { mutableStateOf("") }
    var pageUrl by handle.saveable { mutableStateOf("") }
    private var loaded by handle.saveable { mutableStateOf(false) }
    val attachments = AttachmentsEditor(container.attachmentFiles, viewModelScope, handle)

    var showErrors by mutableStateOf(false)
        private set

    val billingCycle get() = BillingCycle.valueOf(cycle)
    val selectedOffsets: Set<Int> get() = offsets.split(',').mapNotNull { it.toIntOrNull() }.toSet()
    fun toggleOffset(d: Int) {
        val s = selectedOffsets.toMutableSet()
        if (!s.add(d)) s.remove(d)
        offsets = s.joinToString(",")
    }

    private val code get() = currency.trim().uppercase()
    private val customInterval get() = customDays.toIntOrNull()?.takeIf { it in 1..3650 }

    val nameError get() = if (showErrors && serviceName.isBlank()) "Add the service name" else null
    val amountError get() = if (showErrors && (!Money.isValidCurrency(code) || Money.parse(amount, code) == null)) "Enter an amount like 22.99" else null
    val customDaysError get() = if (showErrors && billingCycle == BillingCycle.CUSTOM && customInterval == null) "Enter how many days between payments" else null
    val customOffsetError get() = if (customOffset.isNotEmpty() && parseCustomOffset(customOffset) == null) "Enter 0–${ReminderPlanner.MAX_CUSTOM_OFFSET_DAYS}" else null

    /** Live yearly projection so the cost is obvious before saving. */
    val yearlyPreview: String?
        get() {
            if (!Money.isValidCurrency(code)) return null
            val price = Money.parse(amount, code) ?: return null
            if (billingCycle == BillingCycle.CUSTOM && customInterval == null) return null
            return SubscriptionMath.annualCost(price, billingCycle, customInterval).format()
        }

    init {
        if (!loaded) viewModelScope.launch {
            if (route.id != null) {
                container.vault.subscription(route.id)?.let { s ->
                    serviceName = s.serviceName
                    amount = s.price.toDecimal().toPlainString()
                    currency = s.price.currencyCode
                    cycle = s.cycle.name
                    customDays = s.customIntervalDays?.toString() ?: ""
                    nextBillingEpochDay = s.nextBillingDate.toEpochDay()
                    category = s.category; paymentMethod = s.paymentMethod
                    status = s.status.name; notes = s.meta.notes
                    pageUrl = s.meta.url.orEmpty()
                    offsets = s.reminderOffsetsDays.filter { it in SUB_PRESETS }.joinToString(",")
                    customOffset = s.reminderOffsetsDays.firstOrNull { it !in SUB_PRESETS }?.toString() ?: ""
                }
            } else {
                currency = container.settings.settings.first().defaultCurrency
                route.sharedText?.let { t ->
                    val sug = ShareSuggest.analyse(t)
                    serviceName = sug.title; sug.amount?.let { amount = it }; sug.currency?.let { currency = it }; notes = sug.text
                }
            }
            if (route.id != null) attachments.load(container.vault.attachments(route.id)) else attachments.markLoaded()
            loaded = true
            markBaseline()
        }
    }

    override fun snapshot() = listOf(serviceName, amount, currency, cycle, customDays, nextBillingEpochDay, category, paymentMethod, status, notes, offsets, customOffset, attachments.drafts(), pageUrl).joinToString("\u0000")

    override fun onCleared() = attachments.discardUnsaved()

    fun save() {
        showErrors = true
        if (serviceName.isBlank() || !Money.isValidCurrency(code) || customOffsetError != null) return
        if (pageUrlError(pageUrl) != null) return
        val price = Money.parse(amount, code) ?: return
        if (billingCycle == BillingCycle.CUSTOM && customInterval == null) return
        val all = selectedOffsets + listOfNotNull(parseCustomOffset(customOffset))
        // A past "next payment" date is rolled onto the next real cycle so reminders work.
        val next = SubscriptionMath.nextBillingOnOrAfter(
            LocalDate.ofEpochDay(nextBillingEpochDay), billingCycle,
            if (billingCycle == BillingCycle.CUSTOM) customInterval else null, LocalDate.now(),
        )
        commit(isEditing) {
            container.vault.saveSubscription(
                SubscriptionDraft(
                    route.id, serviceName, price.amountMinor, code, cycle,
                    if (billingCycle == BillingCycle.CUSTOM) customInterval else null,
                    next.toEpochDay(), category, paymentMethod, status, all.toList(), notes, attachments.drafts(), pageUrlToSave(pageUrl),
                )
            )
            attachments.markSaved()
            finished = true
        }
    }

    fun delete() = runSave { route.id?.let { container.vault.delete(it) }; finished = true }
}

@Composable
fun SubscriptionFormScreen(onDone: () -> Unit) {
    val vm = keeplyViewModel { c, h -> SubscriptionFormViewModel(c, h) }
    LaunchedEffect(vm.finished) { if (vm.finished) onDone() }

    FormScaffold(
        eyebrow = "Subscription",
        title = if (vm.isEditing) vm.serviceName.ifBlank { "Subscription" } else "Add a subscription",
        isEditing = vm.isEditing, saving = vm.saving, errorMessage = vm.errorMessage, hasUnsavedChanges = vm.hasUnsavedChanges,
        confirmSave = vm.confirmSave, onConfirmSave = vm::confirmPendingSave, onDismissSave = vm::dismissPendingSave,
        onBack = onDone, onSave = vm::save, onDelete = vm::delete, deleteWhat = "subscription",
    ) {
        KeeplyTextField("Service", vm.serviceName, { vm.serviceName = it }, placeholder = "Adobe Creative Cloud", error = vm.nameError)
        FormLogo(vm.attachments, hint = "Optional. Add the service's logo, e.g. the Claude logo for Claude.")
        PageLinkField(vm.pageUrl, { vm.pageUrl = it }, hint = "Optional. The service's site, e.g. netflix.com. Adds its logo.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            KeeplyTextField("Amount", vm.amount, { vm.amount = it }, Modifier.weight(2f), placeholder = "22.99", error = vm.amountError, keyboardType = KeyboardType.Decimal)
            CurrencyField("Currency", vm.currency, { vm.currency = it }, Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            FieldGroupLabel("Billing cycle")
            ChoiceChips(BillingCycle.entries, vm.billingCycle, label = { it.label }, onSelect = { vm.cycle = it.name })
        }
        if (vm.billingCycle == BillingCycle.CUSTOM) {
            KeeplyTextField("Every how many days?", vm.customDays, { vm.customDays = it.filter(Char::isDigit).take(4) }, keyboardType = KeyboardType.Number, error = vm.customDaysError)
        }
        vm.yearlyPreview?.let { Text("About $it per year", style = Keeply.type.bodySmMedium, color = Keeply.colors.link) }
        DateField("Next payment", LocalDate.ofEpochDay(vm.nextBillingEpochDay), { vm.nextBillingEpochDay = it.toEpochDay() })
        if (vm.isEditing) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                FieldGroupLabel("Status")
                ChoiceChips(SubscriptionStatus.entries, SubscriptionStatus.valueOf(vm.status), label = { if (it == SubscriptionStatus.ACTIVE) "Active" else "Cancelled" }, onSelect = { vm.status = it.name })
            }
        }
        ReminderPicker(SUB_PRESETS, vm.selectedOffsets, vm::toggleOffset, vm.customOffset, { vm.customOffset = it }, vm.customOffsetError)
        ReminderPreviewButton(
            NotificationCategory.SUBSCRIPTIONS,
            itemTitle = vm.serviceName.ifBlank { "Your subscription" },
            // The closest reminder the user picked is the one they'll notice most.
            daysBefore = (vm.selectedOffsets + listOfNotNull(parseCustomOffset(vm.customOffset))).minOrNull() ?: 1,
            itemId = vm.itemId,
        )
        KeeplyTextField("Category", vm.category, { vm.category = it }, placeholder = "Software, streaming…")
        KeeplyTextField("Payment method", vm.paymentMethod, { vm.paymentMethod = it }, placeholder = "Visa ending 4417")
        FormPhotos(vm.attachments, hint = "Optional: invoices, plan details or a screenshot of the price.")
        KeeplyTextField("Notes", vm.notes, { vm.notes = it }, singleLine = false, minLines = 2, imeAction = ImeAction.Default)
    }
}
