package app.keeply.ui.features.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.keeply.core.model.Repeat
import app.keeply.ui.components.ChoiceChips
import app.keeply.ui.components.DateField
import app.keeply.ui.components.TimeField
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space
import java.time.LocalDate
import java.time.LocalDateTime

/** Optional one-off alert for items without dates of their own (receipts, links). */
@Composable
fun AlertSection(
    title: String, hint: String, on: Boolean, onToggle: (Boolean) -> Unit,
    dateEpochDay: Long, minuteOfDay: Int, onDate: (Long) -> Unit, onTime: (Int) -> Unit,
    repeat: Repeat, onRepeat: (Repeat) -> Unit,
) {
    val c = Keeply.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Row(
            Modifier.fillMaxWidth().toggleable(on, role = Role.Switch, onValueChange = onToggle),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Keeply.type.bodyMdMedium, color = c.text)
                Text(hint, style = Keeply.type.caption, color = c.textMuted)
            }
            Switch(on, null, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, uncheckedTrackColor = c.fieldFill, uncheckedBorderColor = c.borderStrong))
        }
        if (on) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                DateField("Date", LocalDate.ofEpochDay(dateEpochDay), { onDate(it.toEpochDay()) }, Modifier.weight(1.2f))
                TimeField("Time", minuteOfDay, onTime, Modifier.weight(1f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                FieldGroupLabel("Repeat")
                ChoiceChips(Repeat.entries, repeat, label = { if (it == Repeat.NEVER) "Once" else it.label }, onSelect = onRepeat)
            }
            val at = LocalDate.ofEpochDay(dateEpochDay).atTime(minuteOfDay / 60, minuteOfDay % 60)
            if (repeat == Repeat.NEVER && !at.isAfter(LocalDateTime.now())) Text("This time has already passed, so there won't be an alert.", style = Keeply.type.caption, color = c.danger)
        }
    }
}
