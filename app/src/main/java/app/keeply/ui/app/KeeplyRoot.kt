package app.keeply.ui.app

import androidx.compose.ui.platform.LocalContext
import app.keeply.notifications.stillWaiting
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.outlined.Lock
import app.keeply.ui.features.share.SaveSharedSheet
import app.keeply.share.ShareSuggestion
import app.keeply.share.ShareSuggest
import androidx.compose.runtime.remember
import app.keeply.ui.features.reminders.ReminderSheet
import app.keeply.notifications.ReminderAlert
import app.keeply.ui.components.RowDivider
import app.keeply.ui.components.GroupedCard
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import app.keeply.ui.components.LocalBottomBarInset
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.keeply.ui.components.IconTile
import app.keeply.ui.components.tint
import app.keeply.ui.theme.Radius
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.keeply.core.model.ItemType
import app.keeply.premium.AddDecision
import app.keeply.ui.components.ButtonVariant
import app.keeply.ui.components.ItemRow
import app.keeply.ui.components.KeeplyButton
import app.keeply.ui.components.icon
import app.keeply.ui.features.activity.ActivityScreen
import app.keeply.ui.features.auth.AuthScreen
import app.keeply.ui.features.documents.DocumentFormScreen
import app.keeply.ui.features.reminders.ReminderFormScreen
import app.keeply.ui.features.detail.ItemDetailScreen
import app.keeply.ui.features.home.HomeScreen
import app.keeply.ui.features.links.LinkFormScreen
import app.keeply.ui.features.onboarding.OnboardingScreen
import app.keeply.ui.features.premium.PremiumScreen
import app.keeply.ui.features.receipts.ReceiptFormScreen
import app.keeply.ui.features.search.SearchScreen
import app.keeply.ui.features.settings.ProfileScreen
import app.keeply.ui.features.settings.SettingsScreen
import app.keeply.ui.features.subscriptions.SubscriptionFormScreen
import app.keeply.ui.features.vault.VaultScreen
import app.keeply.ui.features.vault.VaultViewModel
import app.keeply.ui.features.warranties.WarrantyFormScreen
import app.keeply.ui.navigation.ActivityRoute
import app.keeply.ui.navigation.DocumentFormRoute
import app.keeply.ui.navigation.HomeRoute
import app.keeply.ui.navigation.ProfileRoute
import app.keeply.ui.navigation.NotificationsRoute
import app.keeply.ui.features.notifications.NotificationsScreen
import app.keeply.ui.navigation.ReminderFormRoute
import app.keeply.ui.navigation.ItemDetailRoute
import app.keeply.ui.navigation.LinkFormRoute
import app.keeply.ui.navigation.PremiumRoute
import app.keeply.ui.navigation.ReceiptFormRoute
import app.keeply.ui.navigation.SearchRoute
import app.keeply.ui.navigation.SettingsRoute
import app.keeply.ui.navigation.SubscriptionFormRoute
import app.keeply.ui.navigation.VaultRoute
import app.keeply.ui.navigation.WarrantyFormRoute
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.KeeplyTheme
import app.keeply.ui.theme.Space

/** Something the system asked us to open: a shared URL or a reminder notification. */
sealed interface LaunchEvent {
    data class SharedText(val text: String) : LaunchEvent
    data class OpenItem(val id: String) : LaunchEvent
    /** The floating + or the home-screen shortcut: open the Add menu. */
    data object QuickAdd : LaunchEvent
    /** A reminder notification was tapped: show its details with snooze and done. */
    data class Reminder(val alert: ReminderAlert) : LaunchEvent
}

@Composable
fun KeeplyRoot(launchEvent: LaunchEvent?, onLaunchEventConsumed: () -> Unit) {
    val appVm = keeplyViewModel { c, _ -> AppViewModel(c) }
    val state by appVm.state.collectAsStateWithLifecycle()
    val ready = state as? RootState.Ready ?: return // DataStore reads in a few ms; splash covers it.

    val account by appVm.account.collectAsStateWithLifecycle()

    KeeplyTheme(mode = ready.settings.themeMode) {
        val signedIn = account
        when {
            !ready.settings.onboardingComplete -> Box(Modifier.fillMaxSize().background(Keeply.colors.bg).navigationBarsPadding()) {
                OnboardingScreen(onFinish = appVm::completeOnboarding)
            }
            signedIn == null -> Box(Modifier.fillMaxSize().background(Keeply.colors.bg).navigationBarsPadding()) {
                AuthScreen()
            }
            // Keyed by account so a different user never inherits the previous one's screens.
            else -> key(signedIn.uid) {
                val lockedNow by appVm.locked.collectAsStateWithLifecycle()
                val locked = ready.settings.appLock && lockedNow
                Box(Modifier.fillMaxSize()) {
                    SignedInApp(appVm, launchEvent, onLaunchEventConsumed, locked)
                    // Drawn over the vault (so your place is kept) and opaque, so nothing shows through.
                    if (locked) LockScreen(onUnlocked = appVm::unlocked)
                }
            }
        }
    }
}

@Composable
private fun SignedInApp(appVm: AppViewModel, launchEvent: LaunchEvent?, onLaunchEventConsumed: () -> Unit, locked: Boolean) {
    run {
        val nav = rememberNavController()
        var showAdd by rememberSaveable { mutableStateOf(false) }
        var openAlert by remember { mutableStateOf<ReminderAlert?>(null) }
        // A reminder sheet left for "View details", and the screen it was over: it comes back
        // when the person returns to that screen.
        var alertAfterDetails by remember { mutableStateOf<Pair<ReminderAlert, androidx.navigation.NavBackStackEntry>?>(null) }
        var shared by remember { mutableStateOf<ShareSuggestion?>(null) }

        /** Opens the right form for shared or copied text, pre-filled. */
        fun saveShared(type: ItemType, text: String) = appVm.requestAdd(type) {
            when (type) {
                ItemType.LINK -> nav.navigate(LinkFormRoute(sharedText = text))
                ItemType.RECEIPT -> nav.navigate(ReceiptFormRoute(sharedText = text))
                ItemType.SUBSCRIPTION -> nav.navigate(SubscriptionFormRoute(sharedText = text))
                ItemType.REMINDER -> nav.navigate(ReminderFormRoute(sharedText = text))
                ItemType.WARRANTY -> nav.navigate(WarrantyFormRoute())
                ItemType.DOCUMENT -> nav.navigate(DocumentFormRoute())
                ItemType.SCREENSHOT -> Unit
            }
        }
        val limit by appVm.limit.collectAsStateWithLifecycle()

        fun startAdd(type: ItemType) = appVm.requestAdd(type) {
            when (type) {
                ItemType.RECEIPT -> nav.navigate(ReceiptFormRoute())
                ItemType.WARRANTY -> nav.navigate(WarrantyFormRoute())
                ItemType.SUBSCRIPTION -> nav.navigate(SubscriptionFormRoute())
                ItemType.LINK -> nav.navigate(LinkFormRoute())
                ItemType.DOCUMENT -> nav.navigate(DocumentFormRoute())
                ItemType.REMINDER -> nav.navigate(ReminderFormRoute())
                ItemType.SCREENSHOT -> Unit
            }
        }
        // Lists, search and reminder taps open the read-only preview; editing starts from there.
        fun openItem(id: String) = nav.navigate(ItemDetailRoute(id))
        fun editItem(type: ItemType, id: String) {
            when (type) {
                ItemType.RECEIPT -> nav.navigate(ReceiptFormRoute(id))
                ItemType.WARRANTY -> nav.navigate(WarrantyFormRoute(id))
                ItemType.SUBSCRIPTION -> nav.navigate(SubscriptionFormRoute(id))
                ItemType.LINK -> nav.navigate(LinkFormRoute(id))
                ItemType.DOCUMENT -> nav.navigate(DocumentFormRoute(id))
                ItemType.REMINDER -> nav.navigate(ReminderFormRoute(id))
                ItemType.SCREENSHOT -> Unit
            }
        }

        // Only reached when signed in, so shared links and reminder taps wait until then.
        // Shares, reminder taps and quick add wait until the vault is unlocked.
        LaunchedEffect(launchEvent, locked) {
            if (locked) return@LaunchedEffect
            val event = launchEvent ?: return@LaunchedEffect
            when (event) {
                is LaunchEvent.SharedText -> shared = ShareSuggest.analyse(event.text)
                is LaunchEvent.OpenItem -> openItem(event.id)
                is LaunchEvent.Reminder -> openAlert = event.alert
                LaunchEvent.QuickAdd -> showAdd = true
            }
            onLaunchEventConsumed()
        }

        val entry by nav.currentBackStackEntryAsState()
        val context = LocalContext.current
        LaunchedEffect(entry) {
            val (alert, returnTo) = alertAfterDetails ?: return@LaunchedEffect
            when {
                entry?.id == returnTo.id -> {
                    alertAfterDetails = null
                    // Marked paid or done on the details page: nothing left to ask.
                    if (alert.stillWaiting(context)) openAlert = alert
                }
                // That screen is gone (they moved on elsewhere), so don't spring it on them later.
                returnTo.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.DESTROYED -> alertAfterDetails = null
            }
        }
        val dest = entry?.destination
        val topLevel = dest != null &&
            (dest.hasRoute<HomeRoute>() || dest.hasRoute<VaultRoute>() || dest.hasRoute<ActivityRoute>() || dest.hasRoute<SettingsRoute>())

        // The bar floats over the content (no strip behind it), so lists scroll underneath and
        // top-level screens add LocalBottomBarInset as bottom padding to keep their last row reachable.
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(Modifier.fillMaxSize().background(Keeply.colors.bg)) {
            CompositionLocalProvider(LocalBottomBarInset provides if (topLevel) BottomBarHeight + navBarBottom else 0.dp) {
                NavHost(
                    navController = nav,
                    startDestination = HomeRoute,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            onSearch = { nav.navigate(SearchRoute) },
                            onOpenItem = ::openItem,
                            onAdd = ::startAdd,
                            onOpenVault = { filter -> nav.navigateTopLevel(VaultRoute(filter?.name), restore = false) },
                            onViewPremium = { nav.navigate(PremiumRoute) },
                            onOpenSettings = { nav.navigate(ProfileRoute) },
                            onOpenNotifications = { nav.navigate(NotificationsRoute) },
                            onOpenUpcoming = { nav.navigateTopLevel(VaultRoute(VaultViewModel.UPCOMING), restore = false) },
                            onSaveCopied = { text -> shared = ShareSuggest.analyse(text) },
                        )
                    }
                    composable<VaultRoute> { e ->
                        VaultScreen(
                            initialFilter = e.toRoute<VaultRoute>().filter,
                            onOpenItem = ::openItem, onAdd = ::startAdd, onSearch = { nav.navigate(SearchRoute) },
                        )
                    }
                    composable<ActivityRoute> { ActivityScreen(onOpenItem = ::openItem, onAdd = ::startAdd) }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            onViewPremium = { nav.navigate(PremiumRoute) },
                            onOpenProfile = { nav.navigate(ProfileRoute) },
                        )
                    }
                    composable<ProfileRoute> {
                        ProfileScreen(
                            onBack = { nav.popBackStack() },
                            onOpenVault = { filter -> nav.navigateTopLevel(VaultRoute(filter), restore = false) },
                        )
                    }
                    composable<NotificationsRoute> { NotificationsScreen(onBack = { nav.popBackStack() }, onOpenItem = ::openItem) }
                    composable<SearchRoute> { SearchScreen(onBack = { nav.popBackStack() }, onOpenItem = ::openItem) }
                    composable<PremiumRoute> { PremiumScreen(onBack = { nav.popBackStack() }) }
                    composable<ReceiptFormRoute> {
                        ReceiptFormScreen(
                            onDone = { nav.popBackStack() },
                            onAddWarranty = { receiptId ->
                                appVm.requestAdd(ItemType.WARRANTY) {
                                    nav.navigate(WarrantyFormRoute(fromReceiptId = receiptId)) { popUpTo<ReceiptFormRoute> { inclusive = true } }
                                }
                            },
                        )
                    }
                    composable<ItemDetailRoute> {
                        ItemDetailScreen(onBack = { nav.popBackStack() }, onEdit = ::editItem)
                    }
                    composable<WarrantyFormRoute> { WarrantyFormScreen(onDone = { nav.popBackStack() }) }
                    composable<SubscriptionFormRoute> { SubscriptionFormScreen(onDone = { nav.popBackStack() }) }
                    composable<LinkFormRoute> { LinkFormScreen(onDone = { nav.popBackStack() }) }
                    composable<DocumentFormRoute> { DocumentFormScreen(onDone = { nav.popBackStack() }) }
                    composable<ReminderFormRoute> { ReminderFormScreen(onDone = { nav.popBackStack() }) }
                }
            }
            if (topLevel) {
                KeeplyBottomBar(
                    isSelected = { matcher -> dest?.let(matcher) == true },
                    onTab = { nav.navigateTopLevel(it) },
                    onAdd = { showAdd = true },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        // Sheets open in their own windows, above the lock screen, so they wait for unlock too.
        if (locked) return@run
        shared?.let { sug ->
            SaveSharedSheet(sug, onDismiss = { shared = null }, onPick = { type -> shared = null; saveShared(type, sug.text) })
        }
        openAlert?.let { a ->
            ReminderSheet(
                a, onDismiss = { openAlert = null },
                onOpenItem = a.itemId?.let { id ->
                    {
                        nav.currentBackStackEntry?.let { here -> alertAfterDetails = a to here }
                        openAlert = null
                        openItem(id)
                    }
                },
            )
        }
        if (showAdd) AddSheet(onDismiss = { showAdd = false }, onPick = { showAdd = false; startAdd(it) })
        limit?.let { d ->
            LimitSheet(
                d, onDismiss = appVm::dismissLimit,
                onViewPremium = { appVm.dismissLimit(); nav.navigate(PremiumRoute) },
                onAddOther = { appVm.dismissLimit(); startAdd(it) },
            )
        }
    }
}

/** Tab navigation keeps each tab's state; deep links into a filtered tab start fresh. */
private fun NavHostController.navigateTopLevel(route: Any, restore: Boolean = true) {
    // Home is the root of the back stack: going "to" Home means popping back to it. Navigating
    // to it with restoreState would instead bring back whatever tab was saved under Home.
    if (route == HomeRoute) {
        if (!popBackStack(HomeRoute, inclusive = false)) navigate(HomeRoute)
        return
    }
    navigate(route) {
        popUpTo(HomeRoute) { saveState = restore }
        launchSingleTop = true
        restoreState = restore
    }
}

/** Height the floating bar covers above the system navigation bar (bar + raised Add button + margin). */
private val BottomBarHeight = 108.dp

private data class Tab(val route: Any, val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val matches: (NavDestination) -> Boolean)

/**
 * Floating navigation: a rounded bar lifted off the screen edge with a soft shadow, the
 * selected tab in a tinted pill, and the Add button raised above the bar's centre.
 */
@Composable
private fun KeeplyBottomBar(isSelected: ((NavDestination) -> Boolean) -> Boolean, onTab: (Any) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = Keeply.colors
    val left = listOf(
        Tab(HomeRoute, "Home", Icons.Outlined.Home, Icons.Rounded.Home) { it.hasRoute<HomeRoute>() },
        Tab(VaultRoute(), "Vault", Icons.Outlined.Inventory2, Icons.Rounded.Inventory2) { it.hasRoute<VaultRoute>() },
    )
    val right = listOf(
        Tab(ActivityRoute, "Activity", Icons.Outlined.History, Icons.Rounded.History) { it.hasRoute<ActivityRoute>() },
        Tab(SettingsRoute, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings) { it.hasRoute<SettingsRoute>() },
    )
    Box(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.md).padding(bottom = Space.sm),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(68.dp),
            shape = RoundedCornerShape(26.dp),
            color = c.surfaceRaised,
            shadowElevation = 12.dp,
            border = if (c.isDark) BorderStroke(1.dp, c.border) else null,
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                left.forEach { t -> BarTab(t, isSelected(t.matches), onTab, Modifier.weight(1f)) }
                Spacer(Modifier.width(76.dp)) // room for the raised Add button
                right.forEach { t -> BarTab(t, isSelected(t.matches), onTab, Modifier.weight(1f)) }
            }
        }
        // Raised Add button on the bar's top edge, ringed in the page colour so it looks cut into the bar.
        Box(Modifier.align(Alignment.TopCenter).size(68.dp).clip(Radius.pill).background(c.bg).padding(5.dp)) {
            Box(
                Modifier.fillMaxSize().shadow(10.dp, Radius.pill, spotColor = c.accent).clip(Radius.pill)
                    .background(Brush.linearGradient(listOf(c.accent, c.accentDeep)))
                    .clickable(onClickLabel = "Add to your vault", role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = "Add" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(30.dp)) }
        }
    }
}

@Composable
private fun BarTab(tab: Tab, selected: Boolean, onTab: (Any) -> Unit, modifier: Modifier) {
    val c = Keeply.colors
    val pill by animateColorAsState(if (selected) c.selected else Color.Transparent, label = "tabPill")
    val tint by animateColorAsState(if (selected) c.link else c.textPlaceholder, label = "tabTint")
    Column(
        modifier.fillMaxHeight().clip(Radius.lg)
            .selectable(selected = selected, role = Role.Tab, onClick = { onTab(tab.route) }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.clip(Radius.pill).background(pill).padding(horizontal = 14.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
            Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            style = if (selected) Keeply.type.micro.copy(fontWeight = FontWeight.SemiBold) else Keeply.type.micro,
            color = if (selected) c.text else c.textPlaceholder,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(onDismiss: () -> Unit, onPick: (ItemType) -> Unit) {
    val descriptions = mapOf(
        ItemType.RECEIPT to "Merchant, amount, date and items",
        ItemType.WARRANTY to "Know when cover runs out",
        ItemType.SUBSCRIPTION to "See what renews and when",
        ItemType.LINK to "Save a page with notes and tags",
        ItemType.DOCUMENT to "ID card, passport, licence, visa…",
        ItemType.REMINDER to "Exam, birthday, interview… at a set time",
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Keeply.colors.surfaceRaised,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.padding(bottom = Space.lg).navigationBarsPadding()) {
            Text(
                "Add to your vault", style = Keeply.type.heading3, color = Keeply.colors.text,
                modifier = Modifier.padding(horizontal = Space.lg).padding(bottom = Space.xs),
            )
            ItemType.available.forEach { type ->
                ItemRow(type.icon, type.label, descriptions[type].orEmpty(), onClick = { onPick(type) }, tint = type.tint, showChevron = true, modifier = Modifier.padding(horizontal = Space.xs))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LimitSheet(decision: AddDecision, onDismiss: () -> Unit, onViewPremium: () -> Unit, onAddOther: (ItemType) -> Unit) {
    val c = Keeply.colors
    val (headline, body, others) = when (decision) {
        is AddDecision.TypeLimitReached -> Triple(
            "You've used all ${decision.limit} free ${decision.type.pluralLabel.lowercase()}",
            "Upgrade to Premium for unlimited ${decision.type.pluralLabel.lowercase()}, or delete one you no longer need.",
            decision.stillAvailable,
        )
        is AddDecision.TotalLimitReached -> Triple(
            "Your free vault is full",
            "You've saved all ${decision.limit} free items. Upgrade to Premium to keep adding, or delete something you no longer need.",
            emptyMap(),
        )
        AddDecision.Allowed -> return
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceRaised, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(
            Modifier.padding(horizontal = Space.lg).padding(bottom = Space.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            IconTile(Icons.Outlined.WorkspacePremium, c.tintOrange, size = 56.dp, iconSize = 28.dp)
            Text(headline, style = Keeply.type.heading3, color = c.text)
            Text(body, style = Keeply.type.bodyMd, color = c.textMuted)
            KeeplyButton("See Premium", onViewPremium, Modifier.fillMaxWidth(), arrow = true)
            if (others.isNotEmpty()) {
                // Point at what's still free instead of leaving a dead end.
                Text("You can still add for free", style = Keeply.type.bodySmMedium, color = c.textMuted)
                GroupedCard {
                    others.entries.forEachIndexed { i, (type, left) ->
                        if (i > 0) RowDivider()
                        ItemRow(type.icon, type.label, "$left left on the free plan", onClick = { onAddOther(type) }, tint = type.tint, showChevron = true)
                    }
                }
            }
            KeeplyButton("Maybe later", onDismiss, Modifier.fillMaxWidth(), variant = ButtonVariant.Ghost)
        }
    }
}

/** Full-screen lock: the system unlock sheet opens straight away; a button reopens it. */
@Composable
private fun LockScreen(onUnlocked: () -> Unit) {
    val c = Keeply.colors
    val activity = androidx.compose.ui.platform.LocalContext.current as androidx.fragment.app.FragmentActivity
    fun ask() = app.keeply.security.AppLock.authenticate(activity, "Unlock Keeply") { ok -> if (ok) onUnlocked() }
    LaunchedEffect(Unit) { ask() }
    Column(
        Modifier.fillMaxSize().background(c.bg)
            .pointerInput(Unit) { detectTapGestures { } } // swallow touches meant for the vault behind
            .padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconTile(Icons.Outlined.Lock, c.tintOrange, size = 72.dp, iconSize = 36.dp)
        Spacer(Modifier.height(Space.lg))
        Text("Keeply is locked", style = Keeply.type.heading1, color = c.text)
        Spacer(Modifier.height(Space.xs))
        Text("Unlock with your fingerprint, face or screen lock.", style = Keeply.type.bodyMd, color = c.textMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(Space.xl))
        KeeplyButton("Unlock", ::ask, Modifier.fillMaxWidth())
    }
}
