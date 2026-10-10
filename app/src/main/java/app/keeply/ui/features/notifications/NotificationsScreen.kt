package app.keeply.ui.features.notifications

import app.keeply.ui.features.common.ItemIcon
import app.keeply.core.model.VaultItemSummary
import androidx.compose.runtime.LaunchedEffect
import app.keeply.notifications.ReminderAlertSaver
import app.keeply.notifications.stillWaiting
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.keeply.AppContainer
import app.keeply.core.model.ItemType
import app.keeply.data.db.NotificationLogEntity
import app.keeply.data.settings.NotificationCategory
import app.keeply.notifications.ReminderActionReceiver
import app.keeply.notifications.ReminderAlert
import app.keeply.ui.app.keeplyViewModel
import app.keeply.ui.components.ButtonSize
import app.keeply.ui.components.ButtonVariant
import app.keeply.ui.components.ChoiceChips
import app.keeply.ui.components.ConfirmDialog
import app.keeply.ui.components.EmptyState
import app.keeply.ui.components.GroupedCard
import app.keeply.ui.components.IconTile
import app.keeply.ui.components.KeeplyButton
import app.keeply.ui.components.KeeplyTag
import app.keeply.ui.components.RowDivider
import app.keeply.ui.components.SectionHeader
import app.keeply.ui.components.TagTone
import app.keeply.ui.components.display
import app.keeply.ui.components.icon
import app.keeply.ui.components.tint
import app.keeply.ui.features.reminders.ReminderSheet
import app.keeply.ui.features.reminders.markLabel
import app.keeply.ui.features.reminders.snoozeOptions
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Radius
import app.keeply.ui.theme.Space
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One shown reminder, with its item's type (null when the item was deleted). */
data class NotificationEntry(val log: NotificationLogEntity, val type: ItemType?, val item: VaultItemSummary? = null) {
    val unread get() = log.readAt == null
    val snoozed get() = log.outcome == "SNOOZED"
    /** Still waiting for the user: never acted on, or snoozed. Settled ones (paid/done) aren't. */
    val pending get() = (unread || snoozed) && (log.itemId == null || type != null)

    fun alert() = ReminderAlert(
        log.notificationId, log.itemId,
        runCatching { NotificationCategory.valueOf(log.category) }.getOrDefault(NotificationCategory.REMINDERS),
        log.title, log.body, log.details, preview = false, occurrenceMinute = log.occurrenceMinute,
    )
}

data class NotificationDay(val label: String, val entries: List<NotificationEntry>)

data class NotificationsState(val loading: Boolean = true, val days: List<NotificationDay> = emptyList(), val unread: Int = 0, val total: Int = 0)

class NotificationsViewModel(container: AppContainer) : ViewModel() {
    private val vault = container.vault
    val unreadOnly = kotlinx.coroutines.flow.MutableStateFlow(false)

    val state: StateFlow<NotificationsState> = combine(
        vault.observeNotifications(), vault.observeItems(null), unreadOnly,
    ) { logs, items, onlyUnread ->
        val byId = items.associateBy { it.id }
        val all = logs.map { log -> log.itemId?.let(byId::get).let { item -> NotificationEntry(log, item?.type, item) } }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val days = all.filter { !onlyUnread || it.unread }
            .groupBy { Instant.ofEpochMilli(it.log.postedAt).atZone(zone).toLocalDate() }
            .map { (date, list) ->
                NotificationDay(
                    when (date) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> date.display() }, list,
                )
            }
        NotificationsState(false, days, all.count { it.unread }, all.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsState())

    fun markAllRead() = viewModelScope.launch { vault.markAllNotificationsRead() }
    fun open(id: Int) = viewModelScope.launch { vault.openNotification(id) }
    fun clearAll() = viewModelScope.launch { vault.clearNotifications() }
}

private val TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun timeOf(millis: Long): String {
    val at = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val day = when (at.toLocalDate()) {
        LocalDate.now() -> ""
        LocalDate.now().plusDays(1) -> "tomorrow "
        else -> at.toLocalDate().display() + ", "
    }
    return day + at.format(TIME)
}

/**
 * Every reminder Keeply has shown on this phone, newest first. Unread ones act like the
 * notification itself: snooze them or mark them paid/done right here.
 */
@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpenItem: (String) -> Unit) {
    val vm = keeplyViewModel { c, _ -> NotificationsViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()
    val onlyUnread by vm.unreadOnly.collectAsStateWithLifecycle()
    val c = Keeply.colors
    var sheet by rememberSaveable(stateSaver = ReminderAlertSaver) { mutableStateOf<ReminderAlert?>(null) }
    // The sheet someone left for "View details"; it comes back when they return here.
    var reopen by rememberSaveable(stateSaver = ReminderAlertSaver) { mutableStateOf<ReminderAlert?>(null) }
    val context = LocalContext.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val a = reopen ?: return@LaunchedEffect
        reopen = null
        // Marked paid or done on the details page: nothing left to ask, so it stays closed.
        if (a.stillWaiting(context)) sheet = a
    }

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding(),
        contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = Space.sm, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Surface(shape = Radius.pill, color = c.surfaceRaised, onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = c.text) }
                }
                Column(Modifier.weight(1f)) {
                    Text("Notifications", style = Keeply.type.heading3, color = c.text)
                    if (!s.loading) Text(
                        if (s.unread > 0) "${s.unread} unread" else "All caught up",
                        style = Keeply.type.caption, color = c.textMuted,
                    )
                }
                if (s.unread > 0) TextButton(onClick = { vm.markAllRead() }) {
                    Text("Mark all read", style = Keeply.type.button, color = c.link)
                }
                if (s.total > 0) IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear all notifications", tint = c.danger)
                }
            }
        }
        if (s.total > 0) item {
            ChoiceChips(listOf(false, true), onlyUnread, label = { if (it) "Unread (${s.unread})" else "All" }, onSelect = { vm.unreadOnly.value = it })
        }
        if (!s.loading && s.days.isEmpty()) item {
            EmptyState(
                title = if (onlyUnread) "Nothing unread" else "No notifications yet",
                body = if (onlyUnread) "You've dealt with every reminder." else "Payment, expiry and timed reminders will be listed here after they're shown.",
                actionLabel = null, onAction = null, icon = Icons.Outlined.Notifications, tint = c.tintPink,
            )
        }
        s.days.forEach { day ->
            item(key = "h-${day.label}") { SectionHeader(day.label) }
            item(key = "d-${day.label}") {
                GroupedCard {
                    day.entries.forEachIndexed { i, e ->
                        if (i > 0) RowDivider()
                        NotificationRow(
                            e,
                            onClick = {
                                when {
                                    e.pending -> sheet = e.alert()
                                    e.type != null && e.log.itemId != null -> { vm.open(e.log.notificationId); onOpenItem(e.log.itemId) }
                                    else -> vm.open(e.log.notificationId)
                                }
                            },
                        )
                    }
                }
            }
        }
        item { Box(Modifier.navigationBarsPadding()) }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear all notifications?",
            body = "Every notification will be permanently removed from this list and from your phone's notification shade. Your items and upcoming reminders aren't affected. This can't be undone.",
            confirmLabel = "Clear all",
            onConfirm = {
                confirmClear = false
                sheet = null
                NotificationManagerCompat.from(context).cancelAll()
                vm.clearAll()
            },
            onDismiss = { confirmClear = false },
        )
    }

    sheet?.let { a ->
        ReminderSheet(a, onDismiss = { sheet = null }, onOpenItem = a.itemId?.let { id -> { reopen = a; sheet = null; onOpenItem(id) } })
    }
}

@Composable
private fun NotificationRow(e: NotificationEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val c = Keeply.colors
    val alert = remember(e.log) { e.alert() }
    var snoozeMenu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (e.pending) "Open reminder" else "Open item", onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Box {
                e.item?.let { ItemIcon(it, size = 40.dp, iconSize = 20.dp) }
                    ?: IconTile(e.type?.icon ?: Icons.Outlined.Notifications, e.type?.tint ?: c.tintPink, size = 40.dp, iconSize = 20.dp)
                if (e.unread) Box(
                    Modifier.align(Alignment.TopEnd).size(12.dp).clip(Radius.pill).background(c.surfaceRaised).padding(2.dp)
                        .clip(Radius.pill).background(c.accent),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(
                        e.log.title, style = if (e.unread) Keeply.type.bodyMdMedium else Keeply.type.bodyMd,
                        color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Text(timeOf(e.log.postedAt).substringAfter(", "), style = Keeply.type.caption, color = c.textMuted)
                }
                Text(e.log.body, style = Keeply.type.bodySm, color = if (e.unread) c.text else c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                status(e)?.let { (text, tone) -> KeeplyTag(text, tone, Modifier.padding(top = 2.dp)) }
            }
        }
        if (e.pending) {
            Row(Modifier.padding(start = 40.dp + Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Box {
                    KeeplyButton(
                        "Snooze", { snoozeMenu = true },
                        variant = ButtonVariant.Outline, size = ButtonSize.Sm, leadingIcon = Icons.Outlined.Snooze,
                    )
                    DropdownMenu(snoozeMenu, onDismissRequest = { snoozeMenu = false }, containerColor = c.surfaceRaised) {
                        snoozeOptions(alert.category).forEach { o ->
                            DropdownMenuItem(
                                text = { Text(o.label, style = Keeply.type.bodyMd, color = c.text) },
                                onClick = {
                                    snoozeMenu = false
                                    ReminderActionReceiver.snooze(context, alert, o.at())
                                    Toast.makeText(context, "Snoozed: ${o.label.replaceFirstChar { it.lowercase() }}", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }
                KeeplyButton(
                    markLabel(e.type), { ReminderActionReceiver.done(context, alert) },
                    variant = ButtonVariant.Soft, size = ButtonSize.Sm, leadingIcon = Icons.Outlined.CheckCircle,
                )
            }
        }
    }
}

/** What happened to it, spelled out. Unread ones are marked by the dot and bold title instead. */
@Composable
private fun status(e: NotificationEntry): Pair<String, TagTone>? = when {
    e.log.itemId != null && e.type == null -> "Item deleted" to TagTone.Neutral
    e.snoozed -> "Snoozed until ${e.log.snoozedUntil?.let(::timeOf) ?: "later"}" to TagTone.Gold
    e.log.outcome == "PAID" -> "Paid" to TagTone.Success
    e.log.outcome == "DONE" -> "Done" to TagTone.Success
    e.unread -> null
    else -> "Read" to TagTone.Neutral
}
