package app.keeply.ui.features.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import app.keeply.KeeplyApp
import app.keeply.core.model.ItemType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.keeply.data.settings.NotificationCategory
import app.keeply.notifications.ReminderActionReceiver
import app.keeply.notifications.ReminderAlert
import app.keeply.ui.components.ButtonVariant
import app.keeply.ui.components.IconTile
import app.keeply.ui.components.KeeplyButton
import app.keeply.ui.components.KeeplyCard
import app.keeply.ui.components.ChoiceChips
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal data class SnoozeOption(val label: String, val at: () -> Long)

private fun inMinutes(m: Long) = SnoozeOption(if (m < 60) "$m min" else "${m / 60} hour${if (m >= 120) "s" else ""}") {
    System.currentTimeMillis() + m * 60_000L
}

private fun tomorrowAt(h: Int, label: String) = SnoozeOption(label) {
    LocalDate.now().plusDays(1).atTime(LocalTime.of(h, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** Timed reminders need minutes; renewals and expiries need days. */
internal fun snoozeOptions(category: NotificationCategory): List<SnoozeOption> =
    if (category == NotificationCategory.REMINDERS) {
        listOf(inMinutes(5), inMinutes(10), inMinutes(30), inMinutes(60), inMinutes(180), tomorrowAt(9, "Tomorrow 9 AM"))
    } else {
        listOf(inMinutes(60), inMinutes(180), tomorrowAt(9, "Tomorrow 9 AM"), tomorrowAt(18, "Tomorrow 6 PM"), SnoozeOption("In 3 days") {
            LocalDate.now().plusDays(3).atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        })
    }

/** A subscription alert is settled by paying it; everything else by doing it. */
internal fun markLabel(type: ItemType?) = if (type == ItemType.SUBSCRIPTION) "Mark as paid" else "Mark as done"

/** The alert's item type (null while loading, or when the item is gone). */
@Composable
internal fun rememberItemType(itemId: String?): ItemType? {
    val context = LocalContext.current
    val type by produceState<ItemType?>(null, itemId) {
        value = itemId?.let { (context.applicationContext as KeeplyApp).container.vault.typeOf(it) }
    }
    return type
}

/**
 * Opens when a reminder notification is tapped: the full text (including the note), a choice of
 * snooze times, and Mark as done. Works even where the system hides notification buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSheet(alert: ReminderAlert, onDismiss: () -> Unit, onOpenItem: (() -> Unit)?) {
    val context = LocalContext.current
    val c = Keeply.colors
    val timed = alert.category == NotificationCategory.REMINDERS
    val options = snoozeOptions(alert.category)
    val type = rememberItemType(alert.itemId)
    // Seeing it here counts as reading it (Notifications page).
    LaunchedEffect(alert.notificationId) {
        if (!alert.preview) (context.applicationContext as KeeplyApp).container.vault.openNotification(alert.notificationId)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceRaised,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.padding(horizontal = Space.lg).padding(bottom = Space.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                IconTile(Icons.Outlined.Snooze, c.tintPink)
                Column(Modifier.weight(1f)) {
                    Text(alert.title, style = Keeply.type.heading3, color = c.text)
                    Text(alert.body, style = Keeply.type.bodySm, color = c.textMuted)
                }
            }
            if (!alert.details.isNullOrBlank()) {
                KeeplyCard(spacing = Space.xxs) {
                    Text(if (timed) "Note" else "Notes", style = Keeply.type.captionBold, color = c.textMuted)
                    Text(alert.details.trim(), style = Keeply.type.bodyMd, color = c.text)
                }
            }
            Text("Remind me again in", style = Keeply.type.bodySmMedium, color = c.textMuted)
            ChoiceChips<SnoozeOption?>(options, null, label = { it?.label.orEmpty() }, onSelect = { o ->
                if (o != null) {
                    ReminderActionReceiver.snooze(context, alert, o.at())
                    android.widget.Toast.makeText(context, "Snoozed: ${o.label.replaceFirstChar { it.lowercase() }}", android.widget.Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            })
            KeeplyButton(
                markLabel(type), { ReminderActionReceiver.done(context, alert); onDismiss() },
                Modifier.fillMaxWidth(), leadingIcon = Icons.Outlined.CheckCircle,
            )
            if (onOpenItem != null) KeeplyButton("Open details", onOpenItem, Modifier.fillMaxWidth(), variant = ButtonVariant.Outline)
        }
    }
}
