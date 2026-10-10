package app.keeply.ui.features.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import app.keeply.core.domain.ReminderPlanner
import app.keeply.ui.components.KeeplyTextField
import app.keeply.ui.components.MultiChoiceChips
import app.keeply.ui.theme.Space

/** Preset "days before" chips plus an optional custom offset. */
@Composable
fun ReminderPicker(
    presets: List<Int>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    custom: String,
    onCustom: (String) -> Unit,
    customError: String?,
    notificationsNote: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        FieldGroupLabel("Remind me")
        MultiChoiceChips(presets, selected, label = ::reminderLabel, onToggle = onToggle)
        KeeplyTextField(
            "Custom reminder (days before)", custom, { onCustom(it.filter(Char::isDigit).take(3)) },
            placeholder = "Optional", keyboardType = KeyboardType.Number,
            error = customError, hint = notificationsNote ?: "Up to ${ReminderPlanner.MAX_CUSTOM_OFFSET_DAYS} days",
        )
    }
}

fun parseCustomOffset(raw: String): Int? = raw.toIntOrNull()?.takeIf { it in 0..ReminderPlanner.MAX_CUSTOM_OFFSET_DAYS }

/** "On the day", "1 day before", "7 days before", "3 months before" (whole 30-day months from 60 up). */
fun reminderLabel(days: Int): String = when {
    days == 0 -> "On the day"
    days == 1 -> "1 day before"
    days >= 60 && days % 30 == 0 -> "${days / 30} months before"
    else -> "$days days before"
}
