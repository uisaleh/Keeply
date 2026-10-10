package app.keeply.ui.features.activity

import app.keeply.ui.features.common.ItemIcon
import app.keeply.core.model.VaultItemSummary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.keeply.AppContainer
import app.keeply.core.model.ActivityEvent
import app.keeply.core.model.ActivityKind
import app.keeply.core.model.ItemType
import app.keeply.ui.app.keeplyViewModel
import app.keeply.ui.components.ConfirmDialog
import app.keeply.ui.components.EmptyState
import app.keeply.ui.components.GroupedCard
import app.keeply.ui.components.IconTile
import app.keeply.ui.components.ItemRow
import app.keeply.ui.components.SiteIconTile
import app.keeply.ui.components.KeeplyTag
import app.keeply.ui.components.LocalBottomBarInset
import app.keeply.ui.components.RowDivider
import app.keeply.ui.components.ScreenTitle
import app.keeply.ui.components.SectionHeader
import app.keeply.ui.components.TagTone
import app.keeply.ui.components.display
import app.keeply.ui.components.icon
import app.keeply.ui.components.tint
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A day's worth of history, e.g. "Today" and everything added, edited, cancelled or deleted that day. */
data class ActivityDay(val date: LocalDate, val label: String, val events: List<ActivityEvent>)

data class ActivityState(
    val loading: Boolean = true,
    val days: List<ActivityDay> = emptyList(),
    /** Items that still exist; history rows for anything else can't be opened. */
    val existingIds: Set<String> = emptySet(),
    /** Each item that still exists, so history rows show the same logo or site icon as the lists. */
    val items: Map<String, VaultItemSummary> = emptyMap(),
)

/** The history of what was added, edited, cancelled and deleted, newest first, grouped by day. */
class ActivityViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<ActivityState> = combine(
        container.vault.observeActivity(), container.vault.observeItems(null),
    ) { events, items ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val days = events
            .groupBy { it.at.atZone(zone).toLocalDate() }
            .map { (date, dayEvents) ->
                val label = when (date) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else -> date.display()
                }
                ActivityDay(date, label, dayEvents)
            }
        ActivityState(false, days, items.map { it.id }.toSet(), items.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityState())

    /** Permanently removes that day's history (local midnight to midnight), here and in the cloud. */
    fun deleteDay(date: LocalDate) = viewModelScope.launch {
        val zone = ZoneId.systemDefault()
        container.vault.deleteActivity(
            date.atStartOfDay(zone).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }

    /** Permanently removes all history, here and in the cloud. */
    fun deleteAll() = viewModelScope.launch { container.vault.deleteActivity() }
}

@Composable
fun ActivityScreen(onOpenItem: (String) -> Unit, onAdd: (ItemType) -> Unit) {
    val vm = keeplyViewModel { c, _ -> ActivityViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()
    if (s.loading) return
    val gutter = Modifier.padding(horizontal = Space.lg)
    // Epoch day of the date awaiting confirmation; ALL_DAYS for the whole history.
    var confirmDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
        contentPadding = PaddingValues(top = Space.md, bottom = Space.xxl + LocalBottomBarInset.current),
    ) {
        item {
            Row(gutter.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                ScreenTitle("Activity", "Everything you've added, edited, cancelled or deleted, and what you were reminded about.", Modifier.weight(1f))
                if (s.days.isNotEmpty()) {
                    IconButton(onClick = { confirmDelete = ALL_DAYS }) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Delete all history", tint = Keeply.colors.danger)
                    }
                }
            }
        }
        if (s.days.isEmpty()) {
            item {
                EmptyState(
                    "No activity yet", "When you add, edit, cancel or delete something, it's recorded here with the date and time.",
                    "Add a receipt", { onAdd(ItemType.RECEIPT) }, gutter,
                    icon = Icons.Outlined.History, tint = Keeply.colors.tintBlue,
                )
            }
            return@LazyColumn
        }
        items(s.days, key = { it.date.toEpochDay() }) { day ->
            Column(gutter, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SectionHeader(day.label) {
                    IconButton(onClick = { confirmDelete = day.date.toEpochDay() }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete history for ${day.label}", tint = Keeply.colors.textMuted)
                    }
                }
                GroupedCard {
                    day.events.forEachIndexed { i, e ->
                        if (i > 0) RowDivider()
                        val exists = e.itemId in s.existingIds
                        ItemRow(
                            e.type.icon, e.title,
                            "${e.type.label} · ${e.at.atZone(ZoneId.systemDefault()).toLocalTime().format(TIME)}" +
                                if (!exists && e.kind != ActivityKind.DELETED) " · since deleted" else "",
                            onClick = { if (exists) onOpenItem(e.itemId) },
                            tint = e.type.tint,
                            showChevron = exists,
                            leading = s.items[e.itemId]?.let { item -> { ItemIcon(item) } },
                            trailing = { KeeplyTag(e.kind.label, e.kind.tone) },
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { target ->
        val dismiss = { confirmDelete = null }
        if (target == ALL_DAYS) {
            ConfirmDialog(
                title = "Delete all history?",
                body = "Every Activity entry will be permanently removed from this phone and from your account. Your items aren't affected. This can't be undone.",
                confirmLabel = "Delete all",
                onConfirm = { dismiss(); vm.deleteAll() },
                onDismiss = dismiss,
            )
        } else {
            val date = LocalDate.ofEpochDay(target)
            val label = s.days.firstOrNull { it.date == date }?.label?.let { if (it == "Today" || it == "Yesterday") it.lowercase() else it } ?: date.display()
            ConfirmDialog(
                title = "Delete this day's history?",
                body = "All Activity entries from $label will be permanently removed from this phone and from your account. Your items aren't affected. This can't be undone.",
                confirmLabel = "Delete",
                onConfirm = { dismiss(); vm.deleteDay(date) },
                onDismiss = dismiss,
            )
        }
    }
}

private const val ALL_DAYS = Long.MIN_VALUE

private val TIME: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** Added green, edited neutral, cancelled amber, deleted red; always spelled out too. */
private val ActivityKind.tone: TagTone
    get() = when (this) {
        ActivityKind.ADDED -> TagTone.Success
        ActivityKind.EDITED -> TagTone.Neutral
        ActivityKind.CANCELLED -> TagTone.Gold
        ActivityKind.DELETED -> TagTone.Danger
        ActivityKind.REMINDED -> TagTone.Brand
        ActivityKind.DONE -> TagTone.Success
        ActivityKind.PAID -> TagTone.Success
        ActivityKind.UNDONE -> TagTone.Neutral
    }
