package app.keeply.ui.components

import java.time.LocalTime
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Radius
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Lets a form jump to its first invalid field when Save is tapped. The form bumps [attempt];
 * the first field (top to bottom) showing an error claims that attempt, scrolls itself into
 * view and takes focus, so a missing value is never hidden off-screen.
 */
class FormErrorScroller {
    var attempt by mutableIntStateOf(0)
        private set
    private var claimed = 0

    fun onSaveAttempt() { attempt++ }

    internal fun claim(): Boolean {
        if (attempt == 0 || claimed == attempt) return false
        claimed = attempt
        return true
    }
}

val LocalFormErrorScroller = staticCompositionLocalOf<FormErrorScroller?> { null }

/** Scrolls this field into view and focuses it when it's the first error after a Save attempt. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.revealOnError(error: String?, focus: FocusRequester?): Modifier {
    val scroller = LocalFormErrorScroller.current ?: return this
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(scroller.attempt) {
        if (error != null && scroller.claim()) {
            requester.bringIntoView()
            runCatching { focus?.requestFocus() }
        }
    }
    return bringIntoViewRequester(requester)
}

/** Label 13sp/500 above, field 6dp radius with strong border, hint or error below. */
@Composable
fun KeeplyTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    hint: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    readOnly: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    fieldModifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val c = Keeply.colors
    val focus = remember { FocusRequester() }
    Column(modifier.fillMaxWidth().revealOnError(error, if (readOnly) null else focus), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = Keeply.type.captionBold, color = c.textMuted)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier.focusRequester(focus).fillMaxWidth().semantics {
                contentDescription = label
                val errorText = error
                if (errorText != null) this.error(errorText)
            },
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, style = Keeply.type.bodyMd, color = c.textPlaceholder) },
            textStyle = Keeply.type.bodyMd.copy(color = c.text),
            singleLine = singleLine,
            minLines = minLines,
            readOnly = readOnly,
            isError = error != null,
            shape = Radius.md,
            trailingIcon = trailing,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            // Filled look: soft fill at rest, an accent outline only while focused or in error.
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.accent, unfocusedBorderColor = Color.Transparent,
                errorBorderColor = c.danger, cursorColor = c.accent,
                focusedContainerColor = c.surfaceRaised, unfocusedContainerColor = c.fieldFill,
                errorContainerColor = c.dangerSoft.copy(alpha = 0.5f), disabledContainerColor = c.fieldFill,
                disabledBorderColor = Color.Transparent,
            ),
        )
        val sub = error ?: hint
        if (sub != null) Text(sub, style = Keeply.type.caption, color = if (error != null) c.danger else c.textMuted)
    }
}

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
fun LocalDate.display(): String = format(DATE_FMT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate?, onDate: (LocalDate) -> Unit, modifier: Modifier = Modifier, hint: String? = null, error: String? = null) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        KeeplyTextField(
            label = label, value = date?.display() ?: "", onValueChange = {}, readOnly = true,
            placeholder = "Choose a date", hint = hint, error = error,
            trailing = { Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = Keeply.colors.textMuted) },
        )
        // Read-only fields swallow taps; this overlay opens the picker and announces the action.
        Box(Modifier.matchParentSize().clickable(onClickLabel = "Choose $label", role = Role.Button) { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** Read-only field that opens the system-style clock picker; [minuteOfDay] is minutes after midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, minuteOfDay: Int, onTime: (Int) -> Unit, modifier: Modifier = Modifier, hint: String? = null) {
    var open by remember { mutableStateOf(false) }
    val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    Box(modifier) {
        KeeplyTextField(
            label = label, value = time.format(TIME_FMT), onValueChange = {}, readOnly = true, hint = hint,
            trailing = { Icon(Icons.Outlined.Schedule, contentDescription = null, tint = Keeply.colors.textMuted) },
        )
        Box(Modifier.matchParentSize().clickable(onClickLabel = "Choose $label", role = Role.Button) { open = true })
    }
    if (open) {
        val context = LocalContext.current
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = android.text.format.DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = Keeply.colors.surfaceRaised,
            title = { Text(label, style = Keeply.type.heading4) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = { onTime(state.hour * 60 + state.minute); open = false }) { Text("Done", color = Keeply.colors.link) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel", color = Keeply.colors.textMuted) } },
        )
    }
}

/** Horizontal single-select pill chips. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, edgePadding: androidx.compose.ui.unit.Dp = 0.dp,
    /** Scroll the selected chip on screen whenever the selection changes, e.g. from a swipe. */
    keepSelectedVisible: Boolean = false,
) {
    val c = Keeply.colors
    // Padding inside the scroll so chips slide to the screen edge instead of being clipped at the gutter.
    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = edgePadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val requester = remember { BringIntoViewRequester() }
            if (keepSelectedVisible && option == selected) LaunchedEffect(Unit) { requester.bringIntoView() }
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), style = Keeply.type.bodySmMedium) },
                shape = Radius.pill,
                modifier = Modifier.height(38.dp).bringIntoViewRequester(requester),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = c.btnDark, selectedLabelColor = c.onBtnDark,
                    containerColor = c.surfaceRaised, labelColor = c.text,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = option == selected,
                    borderColor = c.border, selectedBorderColor = c.btnDark,
                ),
            )
        }
    }
}

/** Multi-select variant for reminder offsets etc. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MultiChoiceChips(options: List<T>, selected: Set<T>, label: (T) -> String, onToggle: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Keeply.colors
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isOn = option in selected
            FilterChip(
                selected = isOn,
                onClick = { onToggle(option) },
                label = { Text(label(option), style = Keeply.type.bodySmMedium) },
                shape = Radius.pill,
                modifier = Modifier.height(38.dp),
                leadingIcon = if (isOn) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = c.selected, selectedLabelColor = c.link, selectedLeadingIconColor = c.link,
                    containerColor = c.fieldFill, labelColor = c.text,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = isOn, borderColor = Color.Transparent, selectedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}
