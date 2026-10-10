package app.keeply.ui.features.home

import app.keeply.ui.features.common.siteOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.keeply.AppContainer
import app.keeply.core.domain.RelativeDays
import app.keeply.core.domain.SpendSummary
import app.keeply.core.domain.WarrantyRules
import app.keeply.core.domain.WarrantyStatus
import app.keeply.core.model.ItemType
import app.keeply.core.model.SubscriptionStatus
import app.keeply.core.model.VaultItemSummary
import app.keeply.premium.FreePlanLimits
import app.keeply.premium.isPremium
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import app.keeply.core.domain.ReminderSchedule
import app.keeply.core.domain.nextOccurrence
import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class AttentionItem(
    val id: String, val type: ItemType, val title: String, val detail: String, val tag: String,
    val logoId: String? = null, val siteDomain: String? = null,
    val documentKind: app.keeply.core.model.DocumentKind? = null,
)

data class HomeState(
    val loading: Boolean = true,
    val greeting: String = "",
    val isEmpty: Boolean = true,
    val expiringWarranties: List<AttentionItem> = emptyList(),
    val upcomingPayments: List<AttentionItem> = emptyList(),
    /** The spend card; null when there's no active subscription. */
    val spend: SpendSummary? = null,
    val recentSummary: String? = null,
    val recent: List<VaultItemSummary> = emptyList(),
    val usedItems: Int = 0,
    val itemLimit: Int? = FreePlanLimits.TOTAL_ITEMS,
)

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val vault = container.vault
    /** Collected separately so a new name or photo shows immediately. */
    val account = container.session.account
    /** Unread reminders, shown as a badge on the profile picture. */
    val unreadNotifications = vault.observeUnreadNotifications()

    // Warranties and documents travel together so the combine below stays within five flows.
    private val expiring = combine(vault.observeWarranties(), vault.observeDocuments(), vault.observeReminderNotes()) { w, d, n -> Triple(w, d, n) }

    val state: StateFlow<HomeState> = combine(
        expiring,
        vault.observeSubscriptions(),
        vault.observeRecent(20),
        vault.observeCounts(),
        combine(container.entitlements.tier, container.settings.settings) { t, s -> t to s },
    ) { (warranties, documents, notes), subs, recent, counts, (tier, prefs) ->
        val today = LocalDate.now()

        val expiring = warranties
            .filter { it.meta.markedAt == null && WarrantyRules.status(it.expiryDate, today) == WarrantyStatus.EXPIRING_SOON }
            .map { AttentionItem(it.meta.id, ItemType.WARRANTY, it.productName, RelativeDays.describeUntil(it.expiryDate, today, "expire"), "Expiring", it.meta.logoId, siteOf(it.meta.url)) } +
            // Documents with reminders on that expire within a month (or already have).
            // Timed reminders happening within the next 3 days.
            notes.mapNotNull { n ->
                val next = n.nextOccurrence(LocalDateTime.now())
                    ?.takeIf { !it.toLocalDate().isAfter(today.plusDays(3)) } ?: return@mapNotNull null
                val time = next.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                val day = when (next.toLocalDate()) { today -> "Today"; today.plusDays(1) -> "Tomorrow"; else -> next.toLocalDate().dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) }
                AttentionItem(n.meta.id, ItemType.REMINDER, n.meta.title, "$day at $time", n.category.label, n.meta.logoId, siteOf(n.meta.url))
            } +
            documents.filter { d ->
                val e = d.expiryDate
                e != null && d.reminderOffsetsDays.isNotEmpty() && !e.isAfter(today.plusDays(30)) && !e.isBefore(today.minusDays(30))
            }.map { d ->
                AttentionItem(d.meta.id, ItemType.DOCUMENT, d.meta.title, RelativeDays.describeUntil(d.expiryDate!!, today, "expire"), if (d.expiryDate.isBefore(today)) "Expired" else "Renew", d.meta.logoId, siteOf(d.meta.url), d.kind)
            }

        val dueOn = SpendSummary.dueDates(subs, today)
        val upcoming = dueOn.filterValues { SpendSummary.isThisWeek(it, today) }.entries.sortedBy { it.value }
            .map { (s, date) -> AttentionItem(s.meta.id, ItemType.SUBSCRIPTION, s.serviceName, "${RelativeDays.describeUntil(date, today, "renew")} · ${s.price.format()}", "Renewal", s.meta.logoId, siteOf(s.meta.url)) }

        val weekAgo = Instant.now().minus(7, ChronoUnit.DAYS)
        val recentCounts = recent.filter { it.updatedAt.isAfter(weekAgo) }.groupingBy { it.type }.eachCount()
        val recentSummary = recentCounts.entries
            .sortedByDescending { it.value }
            .joinToString(" and ") { (type, n) -> "$n ${if (n == 1) type.label.lowercase() else type.pluralLabel.lowercase()}" }
            .ifEmpty { null }

        HomeState(
            loading = false,
            greeting = greetingFor(LocalTime.now()),
            isEmpty = recent.isEmpty(),
            expiringWarranties = expiring,
            upcomingPayments = upcoming,
            spend = SpendSummary.of(subs, prefs.defaultCurrency, today),
            recentSummary = recentSummary,
            recent = recent.take(5),
            usedItems = counts.values.sum(),
            itemLimit = if (tier.isPremium) null else FreePlanLimits.TOTAL_ITEMS,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private fun greetingFor(t: LocalTime) = when (t.hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}

