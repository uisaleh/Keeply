package app.keeply.ui.features.vault

import androidx.compose.material.icons.rounded.Star
import app.keeply.ui.features.common.ItemIcon
import app.keeply.ui.features.common.siteOf
import androidx.compose.material.icons.outlined.CalendarMonth
import app.keeply.core.domain.RelativeDays
import app.keeply.core.model.SubscriptionStatus
import app.keeply.ui.components.ItemRow
import app.keeply.ui.components.KeeplyTag
import app.keeply.ui.components.LogoTile
import app.keeply.ui.components.TagTone
import app.keeply.ui.components.display
import app.keeply.ui.features.common.NotificationPermissionCard
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.FormatStyle
import app.keeply.core.domain.ReminderSchedule
import app.keeply.core.domain.nextOccurrence
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.keeply.AppContainer
import app.keeply.core.model.ItemType
import app.keeply.core.model.VaultItemSummary
import app.keeply.ui.app.keeplyViewModel
import app.keeply.ui.components.ChoiceChips
import app.keeply.ui.components.EmptyState
import app.keeply.ui.components.GroupedCard
import app.keeply.ui.components.LocalBottomBarInset
import app.keeply.ui.components.RowDivider
import app.keeply.ui.components.ScreenTitle
import app.keeply.ui.components.icon
import app.keeply.ui.components.tint
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Radius
import app.keeply.ui.features.common.SummaryRow
import app.keeply.ui.theme.Space
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A dated event for an item: a subscription renewal or a warranty ending. */
data class DueInfo(val date: LocalDate, val days: Long, val detail: String)

/** One row of the Upcoming filter. */
data class UpcomingEntry(val summary: VaultItemSummary, val due: DueInfo)

class VaultViewModel(container: AppContainer, private val handle: SavedStateHandle, initial: String?) : ViewModel() {
    /** Opens on Upcoming: what's due is what people come here to check. */
    val filter: StateFlow<String> = handle.getStateFlow(KEY, initial ?: UPCOMING)

    /** Every renewal and warranty end from today on, soonest first. */
    private val dated: StateFlow<List<UpcomingEntry>> = combine(
        container.vault.observeSubscriptions(), container.vault.observeWarranties(), container.vault.observeDocuments(),
        combine(container.vault.observeReminderNotes(), container.vault.observeItemAlerts()) { n, i -> n to i },
    ) { subs, warranties, documents, (notes, itemAlerts) ->
        val today = LocalDate.now()
        fun days(d: LocalDate) = ChronoUnit.DAYS.between(today, d)
        buildList {
            subs.filter { it.status == SubscriptionStatus.ACTIVE && !it.nextBillingDate.isBefore(today) }.forEach { s ->
                add(UpcomingEntry(s.meta.toSummary(), DueInfo(s.nextBillingDate, days(s.nextBillingDate), "Renews ${s.nextBillingDate.short(today)} · ${s.price.format()}")))
            }
            warranties.filter { it.meta.markedAt == null && !it.expiryDate.isBefore(today) }.forEach { w -> // claimed ones are dealt with
                add(UpcomingEntry(w.meta.toSummary(), DueInfo(w.expiryDate, days(w.expiryDate), "Warranty ends ${w.expiryDate.short(today)}")))
            }
            notes.forEach { n ->
                val next = n.nextOccurrence(LocalDateTime.now()) ?: return@forEach
                val time = next.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                add(UpcomingEntry(n.meta.toSummary(), DueInfo(next.toLocalDate(), days(next.toLocalDate()), "${n.category.label} · ${next.toLocalDate().short(today)}, $time")))
            }
            itemAlerts.forEach { e ->
                val first = e.remindAtLocalMinute?.let { app.keeply.data.repo.localMinuteToDateTime(it) } ?: return@forEach
                val repeat = e.remindRepeat?.let { runCatching { app.keeply.core.model.Repeat.valueOf(it) }.getOrNull() } ?: app.keeply.core.model.Repeat.NEVER
                val at = ReminderSchedule.nextFor(first, repeat, 0, LocalDateTime.now()) ?: return@forEach
                val type = runCatching { ItemType.valueOf(e.type) }.getOrNull() ?: return@forEach
                val summary = VaultItemSummary(e.id, type, e.title, e.subtitle, e.isFavorite, e.isDemo, java.time.Instant.ofEpochMilli(e.updatedAt), e.logoId, siteOf(e.pageUrl))
                val time = at.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                add(UpcomingEntry(summary, DueInfo(at.toLocalDate(), days(at.toLocalDate()), "Reminder · ${at.toLocalDate().short(today)}, $time")))
            }
            documents.forEach { d ->
                val expiry = d.expiryDate ?: return@forEach
                if (expiry.isBefore(today)) return@forEach
                add(UpcomingEntry(d.meta.toSummary().copy(documentKind = d.kind), DueInfo(expiry, days(expiry), "${d.kind.label} expires ${expiry.short(today)}")))
            }
        }.sortedBy { it.due.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The Upcoming filter: the next [UPCOMING_DAYS] days. */
    val upcoming: StateFlow<List<UpcomingEntry>> = dated
        .map { list -> list.filter { it.due.days <= UPCOMING_DAYS } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Items due in the next [UPCOMING_DAYS] days, flagged red in every list. */
    val dueSoon: StateFlow<Map<String, DueInfo>> = upcoming
        .map { list -> list.associate { it.summary.id to it.due } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Every item with anything coming up moved to the top, soonest first. Each page of the
     * swipeable list picks its own type out of this, so neighbouring pages are ready mid-swipe.
     */
    val items: StateFlow<List<VaultItemSummary>?> = container.vault.observeItems(null)
        .combine(dueSoon) { list, due ->
            val (soon, rest) = list.partition { it.id in due }
            soon.sortedBy { due.getValue(it.id).date } + rest
        }
        .map<List<VaultItemSummary>, List<VaultItemSummary>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setFilter(key: String) { handle[KEY] = key }

    companion object {
        private const val KEY = "selectedFilter"
        const val ALL = "ALL"
        const val UPCOMING = "UPCOMING"
        const val FAVORITES = "FAVORITES"
        const val UPCOMING_DAYS = 30
    }
}

/** "Oct 4" this year, "Jan 12, 2027" otherwise: short enough to leave room for the price. */
private fun LocalDate.short(today: LocalDate): String =
    if (year == today.year) format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())) else display()

private fun app.keeply.core.model.ItemMeta.toSummary() = VaultItemSummary(
    id = id, type = type, title = title, subtitle = subtitle, isFavorite = isFavorite,
    isDemo = isDemo, updatedAt = updatedAt, logoId = logoId, siteDomain = siteOf(url),
)

@Composable
fun VaultScreen(initialFilter: String?, onOpenItem: (String) -> Unit, onAdd: (ItemType) -> Unit, onSearch: () -> Unit) {
    val vm = keeplyViewModel { c, h -> VaultViewModel(c, h, initialFilter) }
    val filter by vm.filter.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val upcoming by vm.upcoming.collectAsStateWithLifecycle()
    val dueSoon by vm.dueSoon.collectAsStateWithLifecycle()
    val options = listOf(VaultViewModel.UPCOMING, VaultViewModel.ALL, VaultViewModel.FAVORITES) + ItemType.available.map { it.name }
    val favoriteCount = items?.count { it.isFavorite } ?: 0
    val c = Keeply.colors

    // One page per chip: swipe sideways to move between them, or tap a chip to jump.
    val pager = rememberPagerState(initialPage = options.indexOf(filter).coerceAtLeast(0)) { options.size }
    val scope = rememberCoroutineScope()
    // Follow the filter when something else changes it (a chip, "Show all items", a deep link)...
    LaunchedEffect(filter) {
        val page = options.indexOf(filter)
        if (page >= 0 && page != pager.currentPage) pager.animateScrollToPage(page)
    }
    // ...and remember where a swipe lands.
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { vm.setFilter(options[it]) } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.lg, top = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScreenTitle("Vault", modifier = Modifier.weight(1f))
            Surface(shape = Radius.pill, color = c.surfaceRaised, onClick = onSearch, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Search, contentDescription = "Search vault", tint = c.text) }
            }
        }
        ChoiceChips(
            options, options[pager.currentPage],
            label = { key ->
                when (key) {
                    VaultViewModel.ALL -> "All"
                    VaultViewModel.FAVORITES -> if (favoriteCount == 0) "Favourites" else "Favourites · $favoriteCount"
                    VaultViewModel.UPCOMING -> if (upcoming.isEmpty()) "Upcoming" else "Upcoming · ${upcoming.size}"
                    else -> ItemType.valueOf(key).pluralLabel
                }
            },
            onSelect = { key -> scope.launch { pager.animateScrollToPage(options.indexOf(key)) } },
            modifier = Modifier.padding(vertical = Space.md),
            edgePadding = Space.lg,
            keepSelectedVisible = true,
        )
        HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1, key = { options[it] }) { page ->
            val key = options[page]
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Space.md),
                contentPadding = PaddingValues(bottom = Space.xxl + LocalBottomBarInset.current),
            ) {
                if (key == VaultViewModel.UPCOMING) {
                    upcomingSection(upcoming, onOpenItem, onShowAll = { vm.setFilter(VaultViewModel.ALL) })
                } else if (key == VaultViewModel.FAVORITES) {
                    favoritesSection(items?.filter { it.isFavorite }, dueSoon, onOpenItem, onShowAll = { vm.setFilter(VaultViewModel.ALL) })
                } else {
                    val type = key.takeIf { it != VaultViewModel.ALL }?.let { ItemType.valueOf(it) }
                    itemsSection(items?.let { all -> if (type == null) all else all.filter { it.type == type } }, type, dueSoon, onOpenItem, onAdd)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsSection(
    list: List<VaultItemSummary>?, type: ItemType?, dueSoon: Map<String, DueInfo>,
    onOpenItem: (String) -> Unit, onAdd: (ItemType) -> Unit,
) {
    when {
        list == null -> Unit
        list.isEmpty() -> item {
            val e = emptyCopy(type)
            EmptyState(
                e.title, e.body, e.cta, { onAdd(e.type) }, Modifier.padding(horizontal = Space.lg),
                icon = e.type.icon, tint = e.type.tint,
            )
        }
        else -> {
            item {
                Text(
                    "${list.size} ${if (list.size == 1) "item" else "items"}",
                    style = Keeply.type.bodySmMedium, color = Keeply.colors.textMuted, modifier = Modifier.padding(horizontal = Space.lg),
                )
            }
            item {
                GroupedCard(Modifier.padding(horizontal = Space.lg)) {
                    list.forEachIndexed { i, item ->
                        if (i > 0) RowDivider()
                        SummaryRow(item, onOpenItem, badge = dueSoon[item.id]?.let { d -> { DueBadge(d.days) } })
                    }
                }
            }
        }
    }
}

/** Starred items of every type, with anything coming up first like the other lists. */
private fun androidx.compose.foundation.lazy.LazyListScope.favoritesSection(
    list: List<VaultItemSummary>?, dueSoon: Map<String, DueInfo>, onOpenItem: (String) -> Unit, onShowAll: () -> Unit,
) {
    when {
        list == null -> Unit
        list.isEmpty() -> item {
            EmptyState(
                "No favourites yet",
                "Open any item and tap the star at the top to keep it here, one tap away.",
                "Show all items", onShowAll, Modifier.padding(horizontal = Space.lg),
                icon = Icons.Rounded.Star, tint = Keeply.colors.tintOrange,
            )
        }
        else -> itemsSection(list, null, dueSoon, onOpenItem, onAdd = {})
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.upcomingSection(
    upcoming: List<UpcomingEntry>, onOpenItem: (String) -> Unit, onShowAll: () -> Unit,
) {
    if (upcoming.isEmpty()) {
        item {
            EmptyState(
                "Nothing coming up",
                "Subscription renewals and warranty end dates in the next ${VaultViewModel.UPCOMING_DAYS} days show up here.",
                "Show all items", onShowAll, Modifier.padding(horizontal = Space.lg),
                icon = Icons.Outlined.CalendarMonth, tint = Keeply.colors.tintViolet,
            )
        }
        return
    }
    item { NotificationPermissionCard(Modifier.padding(horizontal = Space.lg)) }
    item {
        // Say what's actually next instead of describing the filter.
        val next = upcoming.first()
        Text(
            "${upcoming.size} coming up · next: ${next.summary.title}, ${RelativeDays.countdown(next.due.days).lowercase()}",
            style = Keeply.type.bodySmMedium, color = Keeply.colors.textMuted, modifier = Modifier.padding(horizontal = Space.lg),
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
    item {
        GroupedCard(Modifier.padding(horizontal = Space.lg)) {
            upcoming.forEachIndexed { i, e ->
                if (i > 0) RowDivider()
                ItemRow(
                    e.summary.type.icon, e.summary.title, e.due.detail, onClick = { onOpenItem(e.summary.id) },
                    tint = e.summary.type.tint, showChevron = true,
                    leading = { ItemIcon(e.summary) },
                    trailing = { DueBadge(e.due.days) },
                )
            }
        }
    }
}

/** Red countdown badge; spelled out ("In 3 days") so it never relies on colour alone. */
@Composable
private fun DueBadge(days: Long) = KeeplyTag(RelativeDays.countdown(days), TagTone.Danger)

private data class EmptyCopy(val title: String, val body: String, val cta: String, val type: ItemType)

private fun emptyCopy(filter: ItemType?) = when (filter) {
    ItemType.RECEIPT -> EmptyCopy("No receipts yet.", "Add your first receipt and Keeply will keep the proof of purchase safe and searchable.", "Add receipt", ItemType.RECEIPT)
    ItemType.WARRANTY -> EmptyCopy("No warranties yet.", "Track cover for the things you own and get a reminder before it runs out.", "Add warranty", ItemType.WARRANTY)
    ItemType.SUBSCRIPTION -> EmptyCopy("No subscriptions tracked.", "Add recurring payments so you know what is coming up.", "Add subscription", ItemType.SUBSCRIPTION)
    ItemType.REMINDER -> EmptyCopy("No reminders yet.", "Add an exam, birthday or interview with a date and time, and get an alert right when you need it.", "Add reminder", ItemType.REMINDER)
    ItemType.DOCUMENT -> EmptyCopy("No documents yet.", "Keep your ID card, passport and licence details in one place, with a reminder before they expire.", "Add document", ItemType.DOCUMENT)
    ItemType.LINK -> EmptyCopy("No saved links.", "Share any page to Keeply from your browser, or paste a link here.", "Save a link", ItemType.LINK)
    else -> EmptyCopy("Nothing here yet.", "Your receipts, warranties, subscriptions and links will live here.", "Add a receipt", ItemType.RECEIPT)
}
