package app.keeply.ui.features.settings

import app.keeply.ui.components.BrandGlow
import app.keeply.ui.components.brandBackdrop
import app.keeply.ui.components.trackY
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.remember
import androidx.compose.material.icons.automirrored.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.NotificationsActive
import app.keeply.core.domain.SubscriptionMath
import app.keeply.core.model.SubscriptionStatus
import app.keeply.ui.features.vault.VaultViewModel
import kotlinx.coroutines.flow.combine
import app.keeply.core.domain.RelativeDays
import app.keeply.core.domain.SpendRange
import app.keeply.core.domain.ReminderSchedule
import app.keeply.core.domain.nextOccurrence
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.compose.material3.LinearProgressIndicator
import app.keeply.core.model.ItemType
import app.keeply.premium.FreePlanLimits
import app.keeply.ui.components.icon
import app.keeply.ui.components.tint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import app.keeply.attachments.ImageSize
import app.keeply.ui.components.PhotoSourceSheet
import app.keeply.ui.components.ProfileAvatar
import app.keeply.ui.components.rememberImagePicker
import app.keeply.ui.theme.Radius
import java.io.File
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.keeply.ui.components.GroupedCard
import app.keeply.ui.components.LocalBottomBarInset
import app.keeply.ui.components.IconTile
import app.keeply.ui.components.KeeplyTag
import app.keeply.ui.components.RowDivider
import app.keeply.ui.components.TagTone
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.platform.LocalContext
import app.keeply.AppContainer
import app.keeply.BuildConfig
import app.keeply.auth.AuthError
import app.keeply.auth.GoogleSignIn
import app.keeply.auth.ReauthRequired
import app.keeply.core.model.Money
import app.keeply.data.settings.AppSettings
import app.keeply.data.settings.NotificationCategory
import app.keeply.data.settings.ThemeMode
import app.keeply.premium.EntitlementTier
import app.keeply.ui.app.keeplyViewModel
import app.keeply.ui.components.ChoiceChips
import app.keeply.ui.components.CurrencyField
import app.keeply.ui.components.ConfirmDialog
import app.keeply.ui.components.KeeplyCard
import app.keeply.ui.components.KeeplyTextField
import app.keeply.ui.components.ScreenTitle
import app.keeply.ui.features.common.NotificationPermissionCard
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PRIVACY_POLICY_URL = "https://sites.google.com/view/keeplyapp/home"

data class VaultStats(
    val items: Int,
    val upcoming: Int,
    /** E.g. "Next: Adobe Creative Cloud, tomorrow"; null when nothing is due soon. */
    val next: String?,
    val subscriptions: Int,
    val monthly: String?,
    val warranties: Int,
    val activeWarranties: Int,
)

/** Adding or changing a password from Profile. [done] is a message to show once it worked. */
data class PasswordUiState(val busy: Boolean = false, val error: String? = null, val done: String? = null)

data class AccountUiState(
    val busy: Boolean = false,
    val error: String? = null,
    val confirmUnsyncedSignOut: Boolean = false,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings?> = container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val tier: StateFlow<EntitlementTier> = container.entitlements.tier
    /** Counts for the tiles under the profile; each opens the matching Vault view. */
    val stats: StateFlow<VaultStats?> = combine(
        container.vault.observeItems(null), container.vault.observeSubscriptions(), container.vault.observeWarranties(),
        combine(container.vault.observeDocuments(), container.vault.observeReminderNotes()) { d, n -> d to n },
    ) { items, subs, warranties, (documents, notes) ->
        val today = LocalDate.now()
        val horizon = today.plusDays(VaultViewModel.UPCOMING_DAYS.toLong())
        fun upcoming(d: LocalDate) = !d.isBefore(today) && !d.isAfter(horizon)
        val active = subs.filter { it.status == SubscriptionStatus.ACTIVE }
        val due = active.filter { upcoming(it.nextBillingDate) }.map { it.serviceName to it.nextBillingDate } +
            warranties.filter { it.meta.markedAt == null && upcoming(it.expiryDate) }.map { "${it.productName} warranty" to it.expiryDate } +
            documents.mapNotNull { d -> d.expiryDate?.takeIf(::upcoming)?.let { d.meta.title to it } } +
            notes.mapNotNull { n ->
                n.nextOccurrence(java.time.LocalDateTime.now())
                    ?.toLocalDate()?.takeIf(::upcoming)?.let { n.meta.title to it }
            }
        val next = due.minByOrNull { it.second }
        VaultStats(
            items = items.size,
            upcoming = due.size,
            next = next?.let { (title, date) -> "Next: $title, ${RelativeDays.countdown(ChronoUnit.DAYS.between(today, date)).lowercase()}" },
            subscriptions = active.size,
            monthly = SubscriptionMath.totals(subs).firstOrNull()?.monthly?.format(),
            warranties = warranties.size,
            activeWarranties = warranties.count { !it.expiryDate.isBefore(today) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The spending chart's date range; "This month" to start. */
    private val spendRange = MutableStateFlow<Triple<SpendRange, LocalDate?, LocalDate?>>(Triple(SpendRange.THIS_MONTH, null, null))
    val selectedRange: StateFlow<Triple<SpendRange, LocalDate?, LocalDate?>> = spendRange.asStateFlow()

    fun setSpendRange(r: SpendRange) { spendRange.value = Triple(r, null, null) }
    fun setCustomRange(from: LocalDate, to: LocalDate) { spendRange.value = Triple(SpendRange.CUSTOM, minOf(from, to), maxOf(from, to)) }

    /** Items added in the chosen range (from the synced history), for the Overview's Vault view. */
    val additions: StateFlow<app.keeply.core.domain.AdditionsSummary?> = combine(container.vault.observeActivity(1_000), spendRange) { events, (range, cf, ct) ->
        val today = LocalDate.now()
        val (from, to) = range.dates(today) ?: ((cf ?: today) to (ct ?: today))
        app.keeply.core.domain.AdditionsOverview.compute(events, from, to)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Spending in the default currency for the chosen range. */
    val spending: StateFlow<app.keeply.core.domain.SpendingSummary?> = combine(
        combine(container.vault.observeReceipts(), container.vault.observePayments()) { r, p -> r to p },
        container.vault.observeSubscriptions(), container.settings.settings, spendRange,
    ) { (receipts, payments), subs, prefs, (range, cf, ct) ->
        val today = LocalDate.now()
        val (from, to) = range.dates(today) ?: ((cf ?: today) to (ct ?: today))
        app.keeply.core.domain.SpendingOverview.compute(receipts, subs, prefs.defaultCurrency, from, to, today, payments)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Items per type, demo items excluded, for the free-plan usage on the Plan card. */
    val counts: StateFlow<Map<ItemType, Int>> = container.vault.observeCounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setTheme(m: ThemeMode) = viewModelScope.launch { container.settings.setThemeMode(m) }
    fun setAppLock(on: Boolean) = viewModelScope.launch {
        container.settings.setAppLock(on)
        // Just proved who you are by turning it on; don't lock again straight away.
        if (on) container.appLock.unlocked()
    }
    fun setLockAfter(s: Int) = viewModelScope.launch { container.settings.setLockAfterSeconds(s) }
    fun setPrivacy(on: Boolean) = viewModelScope.launch { container.settings.setPrivacyScreen(on) }
    fun setFloating(on: Boolean) = viewModelScope.launch { container.settings.setFloatingButton(on) }
    fun setSuggestions(on: Boolean) = viewModelScope.launch { container.settings.setSmartSuggestions(on) }
    fun setCurrency(code: String) = viewModelScope.launch { container.settings.setDefaultCurrency(code) }
    fun setNotif(c: NotificationCategory, on: Boolean) = viewModelScope.launch { container.settings.setNotificationEnabled(c, on) }

    val account = container.session.account

    private val _accountState = MutableStateFlow(AccountUiState())
    val accountState: StateFlow<AccountUiState> = _accountState.asStateFlow()

    /** If uploads are still pending (offline), asks for confirmation instead of signing out. */
    fun signOut(force: Boolean = false) = accountAction {
        if (!container.session.signOut(force)) _accountState.update { it.copy(confirmUnsyncedSignOut = true) }
    }

    fun dismissUnsyncedSignOut() = _accountState.update { it.copy(confirmUnsyncedSignOut = false) }

    private val _passwordState = MutableStateFlow(PasswordUiState())
    val passwordState: StateFlow<PasswordUiState> = _passwordState.asStateFlow()

    /** Lets a Google account sign in with email and password too. */
    fun addPassword(activityContext: android.content.Context, password: String) = passwordAction("Password added. You can now sign in with your email too.") {
        try {
            container.auth.addPassword(password)
        } catch (e: ReauthRequired) {
            // Firebase wants a fresh sign-in before adding a new way in: confirm with Google, then retry.
            val token = GoogleSignIn.requestIdToken(activityContext) ?: throw AuthError("Confirm with Google to add a password.")
            container.auth.ensureRecentLogin(container.auth.googleCredential(token))
            container.auth.addPassword(password)
        }
    }

    fun changePassword(current: String, new: String) = passwordAction("Password changed.") {
        container.auth.changePassword(current, new)
    }

    fun resetPasswordState() = _passwordState.update { PasswordUiState() }

    private fun passwordAction(doneMessage: String, block: suspend () -> Unit) {
        if (_passwordState.value.busy) return
        viewModelScope.launch {
            _passwordState.update { PasswordUiState(busy = true) }
            try {
                block()
                _passwordState.update { PasswordUiState(done = doneMessage) }
            } catch (e: ReauthRequired) {
                _passwordState.update { PasswordUiState(error = "For your security, sign out and back in, then try again.") }
            } catch (e: AuthError) {
                _passwordState.update { PasswordUiState(error = e.message) }
            }
        }
    }


    var photoBusy by mutableStateOf(false)
        private set

    /** Shrinks the picked image, uploads it and sets it on the account; null removes the photo. */
    fun setProfilePhoto(uri: Uri?, cacheDir: File) {
        if (photoBusy) return
        viewModelScope.launch {
            photoBusy = true
            _accountState.update { it.copy(error = null) }
            try {
                val file = uri?.let {
                    File(cacheDir, "profile.jpg").takeIf { f -> container.attachmentFiles.importTo(it, f, ImageSize.ICON) }
                        ?: throw AuthError("That image couldn't be opened. Try a different photo.")
                }
                container.auth.setProfilePhoto(file)
                file?.delete()
            } catch (e: AuthError) {
                _accountState.update { it.copy(error = e.message) }
            } finally {
                photoBusy = false
            }
        }
    }

    private fun accountAction(block: suspend () -> Unit) {
        if (_accountState.value.busy) return
        viewModelScope.launch {
            _accountState.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (e: AuthError) {
                _accountState.update { it.copy(error = e.message) }
            } finally {
                _accountState.update { it.copy(busy = false) }
            }
        }
    }

    /** Wipes the vault (on this device and in the cloud) and preferences. The account stays signed in. */
    fun deleteEverything(onDone: () -> Unit) = viewModelScope.launch {
        container.vault.deleteAllData()
        container.settings.clearAll()
        container.settings.completeOnboarding() // still signed in: go straight back to an empty vault
        onDone()
    }
}

@Composable
fun SettingsScreen(onViewPremium: () -> Unit, onOpenProfile: () -> Unit) {
    val vm = keeplyViewModel { c, _ -> SettingsViewModel(c) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val tier by vm.tier.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val settings = s ?: return
    val context = LocalContext.current
    val c = Keeply.colors
    var confirmWipe by rememberSaveable { mutableStateOf(false) }
    val account by vm.account.collectAsStateWithLifecycle()
    val accountState by vm.accountState.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg).padding(top = Space.md, bottom = Space.xxl + LocalBottomBarInset.current),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        ScreenTitle("Settings")

        account?.let { a ->
            KeeplyCard(onClick = onOpenProfile, onClickLabel = "Open profile") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    ProfileAvatar(a.displayName ?: a.email, a.photoUrl, size = 48.dp)
                    Column(Modifier.weight(1f)) {
                        Text(a.displayName ?: a.email ?: "Signed in", style = Keeply.type.bodyMdMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Profile, saved items and spending", style = Keeply.type.caption, color = c.textMuted)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = c.textPlaceholder)
                }
            }
        }

        SettingsSection("Security") {
            SecuritySettings(settings.appLock, settings.lockAfterSeconds, settings.privacyScreen, vm::setAppLock, vm::setLockAfter, vm::setPrivacy)
        }

        SettingsSection("Plan") {
            GroupedCard {
                SettingsRow(
                    Icons.Outlined.WorkspacePremium, c.tintOrange,
                    title = if (tier == EntitlementTier.FREE) "Keeply Free" else "Keeply Premium",
                    subtitle = "See what Premium adds", onClick = onViewPremium, chevron = true,
                )
                if (tier == EntitlementTier.FREE) {
                    RowDivider(startInset = Space.md)
                    FreePlanUsage(counts)
                }
            }
        }

        SettingsSection("Appearance") {
            KeeplyCard {
                Text("Theme", style = Keeply.type.bodyMdMedium, color = c.text)
                ChoiceChips(ThemeMode.entries, settings.themeMode, label = { it.label }, onSelect = { vm.setTheme(it) })
            }
        }

        SettingsSection("Quick add") {
            QuickAddSettings(settings.floatingButton, vm::setFloating)
            SmartSuggestionsSetting(settings.smartSuggestions, vm::setSuggestions)
        }

        SettingsSection("Preferences") {
            KeeplyCard {
                CurrencyField("Default currency", settings.defaultCurrency, { vm.setCurrency(it) }, hint = "Used for new receipts and subscriptions")
            }
        }

        SettingsSection("Notifications") {
            NotificationPermissionCard()
            GroupedCard {
                NotificationCategory.entries.forEachIndexed { i, cat ->
                    if (i > 0) RowDivider()
                    val on = cat in settings.enabledNotifications
                    val (icon, tint) = when (cat) {
                        NotificationCategory.SUBSCRIPTIONS -> Icons.Outlined.Autorenew to c.tintViolet
                        NotificationCategory.WARRANTIES -> Icons.Outlined.VerifiedUser to c.tintGreen
                        NotificationCategory.DOCUMENTS -> Icons.Outlined.Badge to c.tintTeal
                        NotificationCategory.REMINDERS -> Icons.Outlined.Alarm to c.tintPink
                    }
                    SettingsRow(
                        icon, tint, cat.label, cat.description,
                        modifier = Modifier.toggleable(on, role = Role.Switch, onValueChange = { vm.setNotif(cat, it) }),
                        trailing = {
                            Switch(
                                checked = on, onCheckedChange = null,
                                colors = SwitchDefaults.colors(checkedTrackColor = c.accent, uncheckedBorderColor = c.borderStrong, uncheckedTrackColor = c.fieldFill),
                            )
                        },
                    )
                }
            }
        }

        SettingsSection("Privacy") {
            KeeplyCard {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    IconTile(Icons.Outlined.Lock, c.tintBlue)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                        Text("Your vault is saved to your account", style = Keeply.type.bodyMdMedium, color = c.text)
                        Text(
                            "Everything you add is stored on this phone and synced to your Keeply account, so you can sign in on a new phone " +
                                "and pick up where you left off. Only you can access it. No ads, no analytics.",
                            style = Keeply.type.bodySm, color = c.textMuted,
                        )
                        Text(
                            "Read more",
                            style = Keeply.type.bodySm, color = c.accent,
                            modifier = Modifier
                                .clickable(role = Role.Button) {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                                    } catch (ignored: ActivityNotFoundException) { }
                                }
                                .padding(vertical = Space.xxs),
                        )
                    }
                }
            }
        }

        SettingsSection("Data") {
            GroupedCard {
                SettingsRow(
                    Icons.Outlined.DeleteSweep, c.dangerSoft to c.danger, "Delete all data",
                    subtitle = "Empty your vault but keep your account", titleColor = c.danger, onClick = { confirmWipe = true },
                )
            }
        }

        Text(
            "Keeply ${BuildConfig.VERSION_NAME}", style = Keeply.type.caption, color = c.textPlaceholder,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
    }

    if (confirmWipe) {
        TypeToConfirmDialog(
            title = "Delete everything?",
            body = "This permanently removes every item, setting and history entry from this phone and from your account. Your account stays. This can't be undone.",
            word = "DELETE",
            onConfirm = {
                confirmWipe = false
                NotificationManagerCompat.from(context).cancelAll()
                vm.deleteEverything {
                    // Fresh task so navigation state from before the wipe isn't restored.
                    context.startActivity(
                        android.content.Intent(context, app.keeply.MainActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                }
            },
            onDismiss = { confirmWipe = false },
        )
    }




}


@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(title, style = Keeply.type.bodySmMedium, color = Keeply.colors.textMuted, modifier = Modifier.padding(start = Space.xxs).semantics { heading() })
        content()
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    tint: Pair<Color, Color>,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    titleColor: Color = Keeply.colors.text,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Keeply.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        IconTile(icon, tint, size = 40.dp, iconSize = 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Keeply.type.bodyMdMedium, color = titleColor)
            if (subtitle != null) Text(subtitle, style = Keeply.type.caption, color = c.textMuted)
        }
        trailing?.invoke()
        if (chevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = c.textPlaceholder)
    }
}

/** Unread count pill (99+ at most). */
@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val c = Keeply.colors
    Box(
        modifier.heightIn(min = 20.dp).widthIn(min = 20.dp).clip(Radius.pill).background(c.accent).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else "$count", style = Keeply.type.micro, color = c.onAccent)
    }
}

/** How much of the free plan is used and what's left, overall and for each type. */
@Composable
private fun FreePlanUsage(counts: Map<ItemType, Int>) {
    val c = Keeply.colors
    val used = counts.values.sum()
    val total = FreePlanLimits.TOTAL_ITEMS
    val left = (total - used).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$used of $total items used", style = Keeply.type.bodyMdMedium, color = c.text, modifier = Modifier.weight(1f))
            Text("$left left", style = Keeply.type.bodySmMedium, color = if (left == 0) c.danger else c.success)
        }
        LinearProgressIndicator(
            progress = { (used.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(Radius.pill),
            color = if (left == 0) c.danger else c.accent, trackColor = c.bgSubtle, drawStopIndicator = {}, gapSize = 0.dp,
        )
        ItemType.available.forEach { type ->
            val limit = FreePlanLimits.perType[type] ?: return@forEach
            val n = counts[type] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                IconTile(type.icon, type.tint, size = 28.dp, iconSize = 16.dp)
                Text(type.pluralLabel, style = Keeply.type.bodySm, color = c.text, modifier = Modifier.weight(1f))
                Text(
                    "$n of $limit · ${(limit - n).coerceAtLeast(0)} left",
                    style = Keeply.type.bodySm, color = if (n >= limit) c.danger else c.textMuted,
                )
            }
        }
    }
}

/** One card split into four by dividers: what's in the vault at a glance, each quarter a shortcut into Vault. */
@Composable
private fun StatsGrid(st: VaultStats, onOpenVault: (String?) -> Unit) {
    val c = Keeply.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Radius.lg,
        color = c.surfaceRaised,
        border = if (c.isDark) BorderStroke(1.dp, c.border) else null,
    ) {
        Column {
            Row(Modifier.height(IntrinsicSize.Min)) {
                StatCell(
                    "${st.items}", "Items saved", "Everything in your vault",
                    Icons.Outlined.Inventory2, c.tintOrange, Modifier.weight(1f),
                ) { onOpenVault(VaultViewModel.ALL) }
                VerticalDivider(color = c.border)
                StatCell(
                    "${st.upcoming}", "Upcoming", st.next ?: "Nothing due this month",
                    Icons.Outlined.NotificationsActive, c.dangerSoft to c.danger, Modifier.weight(1f),
                    highlight = st.upcoming > 0,
                ) { onOpenVault(VaultViewModel.UPCOMING) }
            }
            HorizontalDivider(color = c.border)
            Row(Modifier.height(IntrinsicSize.Min)) {
                StatCell(
                    "${st.subscriptions}", "Subscriptions", st.monthly?.let { "$it a month" } ?: "Active",
                    Icons.Outlined.Autorenew, c.tintViolet, Modifier.weight(1f),
                ) { onOpenVault(ItemType.SUBSCRIPTION.name) }
                VerticalDivider(color = c.border)
                StatCell(
                    "${st.warranties}", "Warranties", "${st.activeWarranties} still covered",
                    Icons.Outlined.VerifiedUser, c.tintGreen, Modifier.weight(1f),
                ) { onOpenVault(ItemType.WARRANTY.name) }
            }
        }
    }
}

@Composable
private fun StatCell(
    value: String, label: String, caption: String, icon: ImageVector, tint: Pair<Color, Color>,
    modifier: Modifier, highlight: Boolean = false, onClick: () -> Unit,
) {
    val c = Keeply.colors
    Column(
        modifier.fillMaxHeight().clickable(onClickLabel = "Open $label", role = Role.Button, onClick = onClick).padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = Keeply.type.heading1, color = if (highlight) c.danger else c.text, modifier = Modifier.weight(1f))
            IconTile(icon, tint, size = 32.dp, iconSize = 18.dp)
        }
        Text(label, style = Keeply.type.bodyMdMedium, color = c.text)
        Text(caption, style = Keeply.type.caption, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** For irreversible actions: the confirm button only works once [word] is typed exactly. */
@Composable
private fun TypeToConfirmDialog(title: String, body: String, word: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val matches = typed.trim() == word
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Keeply.colors.surfaceRaised,
        shape = Radius.xxl,
        title = { Text(title, style = Keeply.type.heading3) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(body, style = Keeply.type.bodyMd, color = Keeply.colors.textMuted)
                KeeplyTextField(
                    "Type $word to confirm", typed, { typed = it.uppercase() },
                    placeholder = word, imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = matches) {
                Text("Delete everything", style = Keeply.type.button, color = if (matches) Keeply.colors.danger else Keeply.colors.textPlaceholder)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = Keeply.type.button, color = Keeply.colors.textMuted) } },
    )
}

/** Profile: who you are, what you've saved, what you spend, and signing out. */
@Composable
fun ProfileScreen(onBack: () -> Unit, onOpenVault: (String?) -> Unit) {
    val vm = keeplyViewModel { c, _ -> SettingsViewModel(c) }
    val stats by vm.stats.collectAsStateWithLifecycle()
    val spending by vm.spending.collectAsStateWithLifecycle()
    val range by vm.selectedRange.collectAsStateWithLifecycle()
    val additions by vm.additions.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val accountState by vm.accountState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = Keeply.colors
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var choosePhoto by rememberSaveable { mutableStateOf(false) }
    var passwordSheet by rememberSaveable { mutableStateOf(false) }
    val passwordState by vm.passwordState.collectAsStateWithLifecycle()
    // Close the sheet and say so once the password is added or changed.
    androidx.compose.runtime.LaunchedEffect(passwordState.done) {
        passwordState.done?.let { msg ->
            passwordSheet = false
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            vm.resetPasswordState()
        }
    }
    val photoPicker = rememberImagePicker(multiple = false) { uris -> uris.firstOrNull()?.let { vm.setProfilePhoto(it, context.cacheDir) } }
    // The same brand aurora as Home: fading out under the profile card, its sides running down
    // beside the "Saved" tiles.
    val scroll = rememberScrollState()
    val glow = remember { BrandGlow() }

    Column(
        Modifier.fillMaxSize().background(c.bg).brandBackdrop(glow, { scroll.value }, sideDrop = 0.dp)
            .statusBarsPadding().verticalScroll(scroll)
            .padding(horizontal = Space.lg).padding(top = Space.sm, bottom = Space.xxl).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Surface(shape = Radius.pill, color = c.surfaceRaised, onClick = onBack, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = c.text) }
            }
            Text("Profile", style = Keeply.type.heading3, color = c.text)
        }

        account?.let { a ->
            KeeplyCard(Modifier.trackY(glow, { it.center.y }) { glow.fadeAnchor = it }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    Box(
                        Modifier.clip(Radius.pill).then(
                            if (BuildConfig.PHOTOS_ENABLED) Modifier.clickable(onClickLabel = "Change profile photo", role = Role.Button) { choosePhoto = true }
                            else Modifier
                        ),
                    ) {
                        ProfileAvatar(a.displayName ?: a.email, a.photoUrl, size = 64.dp)
                        // Small camera badge so it's obvious the photo can be changed.
                        if (BuildConfig.PHOTOS_ENABLED) Box(
                            Modifier.align(Alignment.BottomEnd).size(24.dp).clip(Radius.pill).background(c.accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (vm.photoBusy) CircularProgressIndicator(Modifier.size(14.dp), color = c.onAccent, strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(14.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(a.displayName ?: a.email ?: "Signed in", style = Keeply.type.heading4, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (a.displayName != null && a.email != null) {
                            Text(a.email, style = Keeply.type.bodySm, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        KeeplyTag(
                            when {
                                a.hasGoogle && a.hasPassword -> "Google + password"
                                a.hasGoogle -> "Google account"
                                else -> "Email account"
                            },
                            TagTone.Neutral,
                        )
                    }
                }
                accountState.error?.let { Text(it, style = Keeply.type.bodySmMedium, color = c.danger) }
            }
        }

        stats?.let { st ->
            Box(Modifier.trackY(glow, { it.center.y }) { glow.sidesAnchor = it }) {
                SettingsSection("Saved") { StatsGrid(st, onOpenVault) }
            }
        }

        val sp = spending; val ad = additions
        if (sp != null && ad != null) {
            SettingsSection("Overview") { OverviewCard(sp, ad, range.first, vm::setSpendRange, vm::setCustomRange) }
        }

        account?.let { a ->
            SettingsSection("Sign-in") {
                GroupedCard {
                    if (a.hasGoogle) {
                        SettingsRow(
                            Icons.Outlined.VerifiedUser, c.tintBlue, "Google",
                            subtitle = "Sign in with ${a.email ?: "your Google account"}",
                            trailing = { KeeplyTag("Connected", TagTone.Success) },
                        )
                        RowDivider()
                    }
                    SettingsRow(
                        Icons.Outlined.Lock, c.tintOrange, if (a.hasPassword) "Change password" else "Add a password",
                        subtitle = if (a.hasPassword) "Used to sign in with ${a.email ?: "your email"}"
                        else "Sign in with ${a.email ?: "your email"} and a password, not just Google",
                        onClick = { vm.resetPasswordState(); passwordSheet = true },
                    )
                }
            }
        }

        GroupedCard {
            SettingsRow(
                Icons.AutoMirrored.Outlined.Logout, c.dangerSoft to c.danger,
                title = if (accountState.busy) "Please wait…" else "Sign out", titleColor = c.danger,
                subtitle = "Your vault stays safe in your account",
                onClick = { if (!accountState.busy) confirmSignOut = true },
            )
        }
    }

    account?.let { a ->
        if (passwordSheet) {
            PasswordSheet(
                change = a.hasPassword, email = a.email, busy = passwordState.busy, serverError = passwordState.error,
                onSubmit = { current, new -> if (a.hasPassword) vm.changePassword(current, new) else vm.addPassword(context, new) },
                onDismiss = { passwordSheet = false },
            )
        }
    }

    if (choosePhoto) {
        PhotoSourceSheet(
            "Profile photo", photoPicker,
            onDismiss = { choosePhoto = false },
            onRemove = if (account?.photoUrl != null) ({ vm.setProfilePhoto(null, context.cacheDir) }) else null,
        )
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            body = "Your vault stays safe in your account. It will be removed from this phone until you sign in again.",
            confirmLabel = "Sign out",
            destructive = false,
            onConfirm = { confirmSignOut = false; NotificationManagerCompat.from(context).cancelAll(); vm.signOut() },
            onDismiss = { confirmSignOut = false },
        )
    }

    if (accountState.confirmUnsyncedSignOut) {
        ConfirmDialog(
            title = "Some changes haven't uploaded",
            body = "This phone seems to be offline, so your latest changes are only on this device. If you sign out now, they'll be lost. Connect to the internet and try again, or sign out anyway.",
            confirmLabel = "Sign out anyway",
            onConfirm = { vm.dismissUnsyncedSignOut(); vm.signOut(force = true) },
            onDismiss = vm::dismissUnsyncedSignOut,
        )
    }
}

/**
 * Floating + over other apps (needs "Display over other apps") and a home-screen shortcut.
 * The switch only shows as on when the permission is actually granted.
 */
@Composable
private fun QuickAddSettings(enabled: Boolean, setEnabled: (Boolean) -> Unit) {
    val context = LocalContext.current
    val c = Keeply.colors
    var allowed by remember { mutableStateOf(app.keeply.quickadd.FloatingAddService.canShow(context)) }
    // Coming back from the system permission screen: finish turning it on if it was granted.
    var waitingForPermission by rememberSaveable { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        allowed = app.keeply.quickadd.FloatingAddService.canShow(context)
        if (waitingForPermission) {
            waitingForPermission = false
            if (allowed) { setEnabled(true); app.keeply.quickadd.FloatingAddService.start(context) }
        }
    }
    val on = enabled && allowed
    GroupedCard {
        SettingsRow(
            Icons.Outlined.AddCircleOutline, c.tintOrange, "Floating add button",
            subtitle = if (on) "A + stays on your screen in every app. Drag it anywhere." else "Keep a + on screen in every app to save things fast",
            modifier = Modifier.toggleable(on, role = Role.Switch, onValueChange = { turnOn ->
                if (!turnOn) {
                    setEnabled(false); app.keeply.quickadd.FloatingAddService.stop(context)
                } else if (app.keeply.quickadd.FloatingAddService.canShow(context)) {
                    setEnabled(true); app.keeply.quickadd.FloatingAddService.start(context)
                } else {
                    waitingForPermission = true
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }),
            trailing = {
                Switch(on, null, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, uncheckedBorderColor = c.borderStrong, uncheckedTrackColor = c.fieldFill))
            },
        )
        if (!allowed) {
            Text(
                "Turning this on opens Android's \"Display over other apps\" setting. Allow it for Keeply, then come back.",
                style = Keeply.type.caption, color = c.textMuted,
                modifier = Modifier.padding(start = 72.dp, end = Space.md, bottom = Space.sm),
            )
        }
        RowDivider()
        SettingsRow(
            Icons.AutoMirrored.Outlined.AddToHomeScreen, c.tintBlue, "Add to home screen",
            subtitle = "Put a \"+ Add\" icon on your home screen",
            chevron = true,
            onClick = {
                val sm = context.getSystemService(android.content.pm.ShortcutManager::class.java)
                if (sm != null && sm.isRequestPinShortcutSupported) {
                    val shortcut = android.content.pm.ShortcutInfo.Builder(context, "quick_add")
                        .setShortLabel("Add")
                        .setLongLabel("Add to Keeply")
                        .setIcon(android.graphics.drawable.Icon.createWithResource(context, app.keeply.R.drawable.ic_shortcut_add))
                        .setIntent(app.keeply.quickadd.FloatingAddService.addIntent(context))
                        .build()
                    sm.requestPinShortcut(shortcut, null)
                } else {
                    android.widget.Toast.makeText(context, "Your home screen doesn't support pinned shortcuts.", android.widget.Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}

/**
 * Smart suggestions need notification access. The guide explains exactly what is read and kept
 * before sending people to Android's permission pages (Play's prominent-disclosure requirement).
 */
@Composable
private fun SmartSuggestionsSetting(enabled: Boolean, setEnabled: (Boolean) -> Unit) {
    val context = LocalContext.current
    val c = Keeply.colors
    var access by remember { mutableStateOf(app.keeply.quickadd.SuggestionListener.hasAccess(context)) }
    var guide by rememberSaveable { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        access = app.keeply.quickadd.SuggestionListener.hasAccess(context)
        // Back from Android's settings with access granted: done.
        if (guide && access) {
            guide = false
            setEnabled(true)
            android.widget.Toast.makeText(context, "Smart suggestions are on", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val on = enabled && access
    GroupedCard {
        SettingsRow(
            Icons.Outlined.AutoAwesome, c.tintViolet, "Smart suggestions",
            subtitle = if (on) "Keeply will offer to save payments, orders and renewals it spots in your notifications"
            else "Get offered to save payments and renewals from your notifications",
            modifier = Modifier.toggleable(on, role = Role.Switch, onValueChange = { turnOn ->
                when {
                    !turnOn -> setEnabled(false)
                    access -> setEnabled(true)
                    else -> guide = true
                }
            }),
            trailing = {
                Switch(on, null, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, uncheckedBorderColor = c.borderStrong, uncheckedTrackColor = c.fieldFill))
            },
        )
    }
    if (guide) SmartSuggestionsGuide(onDismiss = { guide = false })
}

@Composable
private fun SecuritySettings(
    appLock: Boolean, lockAfter: Int, privacy: Boolean,
    setAppLock: (Boolean) -> Unit, setLockAfter: (Int) -> Unit, setPrivacy: (Boolean) -> Unit,
) {
    val c = Keeply.colors
    val activity = LocalContext.current as androidx.fragment.app.FragmentActivity
    var noScreenLock by rememberSaveable { mutableStateOf(false) }
    val switchColors = SwitchDefaults.colors(checkedTrackColor = c.accent, uncheckedBorderColor = c.borderStrong, uncheckedTrackColor = c.fieldFill)
    GroupedCard {
        SettingsRow(
            Icons.Outlined.Fingerprint, c.tintGreen, "App lock",
            subtitle = if (appLock) "Fingerprint, face or screen lock needed to open Keeply" else "Ask for your fingerprint, face or screen lock to open Keeply",
            modifier = Modifier.toggleable(appLock, role = Role.Switch, onValueChange = { on ->
                when {
                    !app.keeply.security.AppLock.canUse(activity) && on -> noScreenLock = true
                    // Turning it on or off both need you to prove it's you.
                    else -> app.keeply.security.AppLock.authenticate(activity, if (on) "Turn on app lock" else "Turn off app lock") { ok -> if (ok) setAppLock(on) }
                }
            }),
            trailing = { Switch(appLock, null, colors = switchColors) },
        )
        if (appLock) {
            Column(Modifier.padding(start = 72.dp, end = Space.md, bottom = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Lock again after leaving Keeply", style = Keeply.type.caption, color = c.textMuted)
                ChoiceChips(listOf(0, 60, 300), lockAfter, label = { if (it == 0) "Immediately" else "${it / 60} min" }, onSelect = setLockAfter)
            }
        }
        if (noScreenLock) {
            Text(
                "Your phone has no screen lock. Set a PIN, pattern or fingerprint in your phone's settings first.",
                style = Keeply.type.caption, color = c.danger, modifier = Modifier.padding(start = 72.dp, end = Space.md, bottom = Space.sm),
            )
        }
        RowDivider()
        SettingsRow(
            Icons.Outlined.VisibilityOff, c.tintSlate, "Privacy screen",
            subtitle = "Block screenshots and hide Keeply in recent apps",
            modifier = Modifier.toggleable(privacy, role = Role.Switch, onValueChange = setPrivacy),
            trailing = { Switch(privacy, null, colors = switchColors) },
        )
    }
}
