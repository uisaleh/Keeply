package app.keeply.ui.features.reminders

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import androidx.navigation.toRoute
import app.keeply.AppContainer
import app.keeply.share.ShareSuggest
import app.keeply.KeeplyApp
import app.keeply.core.domain.ReminderSchedule
import app.keeply.core.model.ReminderCategory
import app.keeply.core.model.Repeat
import app.keeply.data.repo.ReminderNoteDraft
import app.keeply.data.settings.NotificationCategory
import app.keeply.notifications.ReminderText
import app.keeply.ui.app.keeplyViewModel
import app.keeply.ui.components.ButtonSize
import app.keeply.ui.components.ButtonVariant
import app.keeply.ui.components.CardTone
import app.keeply.ui.components.ChoiceChips
import app.keeply.ui.components.DateField
import app.keeply.ui.components.IconTile
import app.keeply.ui.components.KeeplyButton
import app.keeply.ui.components.KeeplyCard
import app.keeply.ui.components.KeeplyTextField
import app.keeply.ui.components.MultiChoiceChips
import app.keeply.ui.components.TimeField
import app.keeply.ui.features.common.PageLinkField
import app.keeply.ui.features.common.pageUrlError
import app.keeply.ui.features.common.pageUrlToSave
import app.keeply.ui.features.common.FieldGroupLabel
import app.keeply.ui.features.common.FormScaffold
import app.keeply.ui.features.common.FormViewModel
import app.keeply.ui.features.common.NotificationPermissionCard
import app.keeply.ui.features.common.ReminderPreviewButton
import app.keeply.ui.navigation.ReminderFormRoute
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Alert choices in minutes before the event. */
val ALERT_PRESETS = listOf(0, 5, 15, 30, 60, 1440, 10080)

fun alertLabel(minutes: Int): String = when {
    minutes == 0 -> "At the time"
    minutes < 60 -> "$minutes min before"
    minutes == 60 -> "1 hour before"
    minutes < 1440 -> "${minutes / 60} hours before"
    minutes == 1440 -> "1 day before"
    minutes == 10080 -> "1 week before"
    else -> "${minutes / 1440} days before"
}

@OptIn(SavedStateHandleSaveableApi::class)
class ReminderFormViewModel(private val container: AppContainer, handle: SavedStateHandle) : FormViewModel() {
    private val route = handle.toRoute<ReminderFormRoute>()
    val isEditing = route.id != null
    val itemId: String? get() = route.id

    var categoryName by handle.saveable { mutableStateOf(ReminderCategory.EXAM.name) }
        private set
    var title by handle.saveable { mutableStateOf("") }
    var dateEpochDay by handle.saveable { mutableStateOf(LocalDate.now().plusDays(1).toEpochDay()) }
    var minuteOfDay by handle.saveable { mutableStateOf(ReminderCategory.EXAM.defaultMinuteOfDay) }
    var repeatName by handle.saveable { mutableStateOf(ReminderCategory.EXAM.defaultRepeat.name) }
    var alerts by handle.saveable { mutableStateOf(ReminderCategory.EXAM.defaultAlerts.joinToString(",")) }
    var note by handle.saveable { mutableStateOf("") }
    var pageUrl by handle.saveable { mutableStateOf("") }
    private var loaded by handle.saveable { mutableStateOf(false) }

    var showErrors by mutableStateOf(false)
        private set

    val category get() = ReminderCategory.valueOf(categoryName)
    val repeat get() = Repeat.valueOf(repeatName)
    val selectedAlerts: Set<Int> get() = alerts.split(',').mapNotNull { it.toIntOrNull() }.toSet()
    val titleError get() = if (showErrors && title.isBlank()) "Say what it's for, e.g. ${category.placeholder}" else null
    val alertsError get() = if (showErrors && selectedAlerts.isEmpty()) "Pick at least one alert" else null
    val start: LocalDateTime get() = LocalDate.ofEpochDay(dateEpochDay).atTime(minuteOfDay / 60, minuteOfDay % 60)

    /** One-off reminders in the past are allowed (for the record) but won't alert; say so. */
    val pastWarning: String?
        get() = if (repeat == Repeat.NEVER && start.isBefore(LocalDateTime.now())) "This time has already passed, so there won't be an alert." else null

    /** A new reminder takes the category's defaults; an existing one keeps what the user chose. */
    fun selectCategory(c: ReminderCategory) {
        categoryName = c.name
        if (!isEditing) {
            repeatName = c.defaultRepeat.name
            alerts = c.defaultAlerts.joinToString(",")
            minuteOfDay = c.defaultMinuteOfDay
        }
    }

    fun toggleAlert(m: Int) {
        val s = selectedAlerts.toMutableSet()
        if (!s.add(m)) s.remove(m)
        alerts = s.joinToString(",")
    }

    init {
        if (!loaded) viewModelScope.launch {
            if (route.id != null) {
                container.vault.reminderNote(route.id)?.let { r ->
                    categoryName = r.category.name; title = r.meta.title
                    dateEpochDay = r.date.toEpochDay(); minuteOfDay = r.minuteOfDay
                    repeatName = r.repeat.name; alerts = r.alertsMinutesBefore.joinToString(","); note = r.meta.notes
                    pageUrl = r.meta.url.orEmpty()
                }
            } else {
                route.sharedText?.let { t ->
                    val sug = ShareSuggest.analyse(t)
                    title = sug.title; note = sug.text; selectCategory(ReminderCategory.OTHER)
                }
            }
            loaded = true
            markBaseline()
        }
    }

    override fun snapshot() = listOf(categoryName, title, dateEpochDay, minuteOfDay, repeatName, alerts, note, pageUrl).joinToString("\u0000")

    fun save() {
        showErrors = true
        if (title.isBlank() || selectedAlerts.isEmpty()) return
        if (pageUrlError(pageUrl) != null) return
        commit(isEditing) {
            container.vault.saveReminderNote(
                ReminderNoteDraft(route.id, categoryName, title, dateEpochDay, minuteOfDay, repeatName, selectedAlerts.toList(), note, pageUrlToSave(pageUrl))
            )
            finished = true
        }
    }

    fun delete() = runSave { route.id?.let { container.vault.delete(it) }; finished = true }
}

@Composable
fun ReminderFormScreen(onDone: () -> Unit) {
    val vm = keeplyViewModel { c, h -> ReminderFormViewModel(c, h) }
    LaunchedEffect(vm.finished) { if (vm.finished) onDone() }
    val c = Keeply.colors

    FormScaffold(
        eyebrow = "Reminder",
        title = if (vm.isEditing) vm.title.ifBlank { "Reminder" } else "Add a reminder",
        isEditing = vm.isEditing, saving = vm.saving, errorMessage = vm.errorMessage, hasUnsavedChanges = vm.hasUnsavedChanges,
        confirmSave = vm.confirmSave, onConfirmSave = vm::confirmPendingSave, onDismissSave = vm::dismissPendingSave,
        onBack = onDone, onSave = vm::save, onDelete = vm::delete, deleteWhat = "reminder",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            FieldGroupLabel("What is it?")
            ChoiceChips(ReminderCategory.entries, vm.category, label = { it.label }, onSelect = vm::selectCategory)
        }
        KeeplyTextField("Title", vm.title, { vm.title = it }, placeholder = vm.category.placeholder, error = vm.titleError)
        PageLinkField(vm.pageUrl, { vm.pageUrl = it }, hint = "Optional. A meeting link, venue or event page.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            DateField("Date", LocalDate.ofEpochDay(vm.dateEpochDay), { vm.dateEpochDay = it.toEpochDay() }, Modifier.weight(1.2f))
            TimeField("Time", vm.minuteOfDay, { vm.minuteOfDay = it }, Modifier.weight(1f))
        }
        vm.pastWarning?.let { Text(it, style = Keeply.type.caption, color = c.danger) }
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            FieldGroupLabel("Repeat")
            ChoiceChips(Repeat.entries, vm.repeat, label = { it.label }, onSelect = { vm.repeatName = it.name })
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            FieldGroupLabel("Alert me")
            MultiChoiceChips(ALERT_PRESETS, vm.selectedAlerts, label = ::alertLabel, onToggle = vm::toggleAlert)
            vm.alertsError?.let { Text(it, style = Keeply.type.caption, color = c.danger) }
        }
        ExactAlarmCard()
        NotificationPermissionCard()
        ReminderPreviewButton(
            NotificationCategory.REMINDERS, vm.itemId,
            caption = "See exactly how the alert will look ${alertLabel(vm.selectedAlerts.minOrNull() ?: 0).lowercase()}.",
            details = vm.note,
        ) {
            val at = ReminderSchedule.nextOccurrence(vm.start, vm.repeat, LocalDateTime.now()) ?: vm.start
            ReminderText.forNote(vm.category.label, vm.title.ifBlank { vm.category.placeholder }, vm.selectedAlerts.minOrNull() ?: 0, at)
        }
        KeeplyTextField(
            "Note", vm.note, { vm.note = it }, singleLine = false, minLines = 3, imeAction = ImeAction.Default,
            placeholder = "Room number, what to bring, gift ideas…",
            hint = "Shown when you expand the alert. Hidden on the lock screen.",
        )
    }
}

/**
 * Android 12+ lets people switch off "Alarms & reminders" for an app; then alerts can be a
 * few minutes late. Explain and offer the switch, only when it's actually off.
 */
@Composable
private fun ExactAlarmCard() {
    val context = LocalContext.current
    val alarms = (context.applicationContext as KeeplyApp).container.reminderAlarms
    var allowed by remember { mutableStateOf(alarms.canScheduleExact()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = alarms.canScheduleExact() }
    if (allowed || Build.VERSION.SDK_INT < 31) return
    val c = Keeply.colors
    KeeplyCard(tone = CardTone.Warm, spacing = Space.sm) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            IconTile(Icons.Outlined.Alarm, c.surfaceRaised to c.link)
            Column(Modifier.weight(1f)) {
                Text("Get alerts on the minute", style = Keeply.type.bodyMdMedium, color = c.text)
                Text(
                    "Allow \"Alarms & reminders\" so an exam or interview alert arrives exactly on time, not a few minutes late.",
                    style = Keeply.type.bodySm, color = c.textMuted,
                )
            }
        }
        KeeplyButton(
            "Allow exact alerts",
            {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            Modifier.fillMaxWidth(), variant = ButtonVariant.Primary, size = ButtonSize.Md,
        )
    }
}
