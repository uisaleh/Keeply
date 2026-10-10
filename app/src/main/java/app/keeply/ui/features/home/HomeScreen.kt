package app.keeply.ui.features.home

import app.keeply.ui.components.BrandGlow
import app.keeply.ui.components.brandBackdrop
import app.keeply.ui.components.trackY
import app.keeply.ui.features.common.ItemIcon
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import app.keeply.ui.components.CardTone
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.keeply.core.model.ItemType
import app.keeply.ui.app.keeplyViewModel
import app.keeply.ui.components.ButtonSize
import app.keeply.ui.components.ButtonVariant
import app.keeply.ui.components.GroupedCard
import app.keeply.ui.components.LocalBottomBarInset
import app.keeply.ui.components.IconTile
import app.keeply.ui.components.ItemRow
import app.keeply.ui.components.KeeplyButton
import app.keeply.ui.components.KeeplyCard
import app.keeply.ui.components.KeeplyTag
import app.keeply.ui.components.LogoTile
import app.keeply.ui.components.ProfileAvatar
import app.keeply.ui.components.RowDivider
import app.keeply.ui.components.SectionHeader
import app.keeply.ui.components.TagTone
import app.keeply.ui.components.icon
import app.keeply.ui.components.tint
import app.keeply.ui.features.common.SummaryRow
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Radius
import app.keeply.ui.theme.Space
import app.keeply.core.domain.NextRenewal
import app.keeply.core.domain.SpendSummary

@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onOpenItem: (String) -> Unit,
    onAdd: (ItemType) -> Unit,
    onOpenVault: (ItemType?) -> Unit,
    onViewPremium: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenUpcoming: () -> Unit,
    onSaveCopied: (String) -> Unit,
) {
    val vm = keeplyViewModel { c, _ -> HomeViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val unread by vm.unreadNotifications.collectAsStateWithLifecycle(0)
    val firstName = account?.displayName?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    val c = Keeply.colors
    if (s.loading) return
    val gutter = Modifier.padding(horizontal = Space.lg)
    val glow = remember { BrandGlow() }
    val listState = rememberLazyListState()

    LazyColumn(
        Modifier.fillMaxSize()
            .brandBackdrop(glow, { listState.takeIf { it.firstVisibleItemIndex == 0 }?.firstVisibleItemScrollOffset })
            .statusBarsPadding(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(Space.lg),
        contentPadding = PaddingValues(top = Space.md, bottom = Space.xxl + LocalBottomBarInset.current),
    ) {
        item { Header(s.greeting, firstName, account?.photoUrl, unread, onOpenSettings, onOpenNotifications, gutter) }
        item { SearchBar(onSearch, gutter.trackY(glow, { it.center.y }) { glow.fadeAnchor = it }) }
        item { CopiedLinkCard(onSaveCopied, gutter) }

        item {
            val spend = s.spend
            if (spend != null) {
                SpendCard(spend, { onOpenVault(ItemType.SUBSCRIPTION) }, gutter.trackY(glow, { it.center.y }) { glow.sidesAnchor = it })
            } else if (s.isEmpty) {
                WelcomeCard(gutter.trackY(glow, { it.center.y }) { glow.sidesAnchor = it })
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SectionHeader("Quick add", gutter)
                QuickAddRow(onAdd)
            }
        }

        if (!s.isEmpty) {
            item {
                Column(gutter, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    val attention = s.upcomingPayments + s.expiringWarranties
                    SectionHeader("Needs attention") {
                        if (attention.isNotEmpty()) {
                            TextButton(onClick = onOpenUpcoming) { Text("See all", style = Keeply.type.bodySmMedium, color = c.link) }
                        }
                    }
                    if (attention.isEmpty()) {
                        AllClearCard()
                    } else {
                        GroupedCard {
                            attention.take(4).forEachIndexed { i, a ->
                                if (i > 0) RowDivider()
                                ItemRow(
                                    a.type.icon, a.title, a.detail, onClick = { onOpenItem(a.id) }, tint = a.type.tint,
                                    leading = { ItemIcon(a.type, a.logoId, a.siteDomain, documentKind = a.documentKind) },
                                    trailing = { KeeplyTag(a.tag, if (a.type == ItemType.WARRANTY) TagTone.Gold else TagTone.Cream) },
                                )
                            }
                        }
                    }
                }
            }
        }

        s.itemLimit?.let { limit -> item { FreePlanCard(s.usedItems, limit, onViewPremium, gutter) } }

        if (s.recent.isNotEmpty()) {
            item {
                Column(gutter, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    SectionHeader("Recently added") {
                        TextButton(onClick = { onOpenVault(null) }) { Text("See all", style = Keeply.type.bodySmMedium, color = c.link) }
                    }
                    GroupedCard {
                        s.recent.forEachIndexed { i, item ->
                            if (i > 0) RowDivider()
                            SummaryRow(item, onOpenItem)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    greeting: String, firstName: String?, photoUrl: String?, unread: Int,
    onOpenSettings: () -> Unit, onOpenNotifications: () -> Unit, modifier: Modifier,
) {
    val c = Keeply.colors
    // Warm tones that sit on the aurora: deep ember on the light wash, peach on the dark one.
    val greetingColor = if (c.isDark) Color(0xFFFFC7A8) else Color(0xFF8A2E0E)
    val nameBrush = if (c.isDark) {
        Brush.horizontalGradient(listOf(Color.White, Color(0xFFFFD9C2)))
    } else {
        Brush.horizontalGradient(listOf(c.text, Color(0xFF5A1A06)))
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = Keeply.type.bodyMdMedium, color = greetingColor)
            Text(
                firstName ?: "Your vault", style = Keeply.type.displayLg.copy(brush = nameBrush),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
            )
        }
        Box {
            Surface(
                shape = Radius.pill, color = c.surfaceRaised, onClick = onOpenNotifications,
                modifier = Modifier.size(44.dp).semantics {
                    contentDescription = if (unread > 0) "Notifications, $unread unread" else "Notifications"
                },
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Notifications, contentDescription = null, tint = c.text) }
            }
            if (unread > 0) Box(
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).heightIn(min = 20.dp).widthIn(min = 20.dp)
                    .clip(Radius.pill).background(c.bg).padding(2.dp).clip(Radius.pill).background(c.accent).padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (unread > 9) "9+" else "$unread", style = Keeply.type.micro, color = c.onAccent) }
        }
        Box(Modifier.clip(Radius.pill).clickable(onClickLabel = "Open profile", role = Role.Button, onClick = onOpenSettings)) {
            ProfileAvatar(firstName, photoUrl, size = 44.dp)
        }
    }
}

@Composable
private fun SearchBar(onSearch: () -> Unit, modifier: Modifier) {
    val c = Keeply.colors
    Surface(
        modifier = modifier.fillMaxWidth().height(52.dp).semantics { contentDescription = "Search your vault" },
        shape = Radius.pill, color = c.surfaceRaised, onClick = onSearch,
    ) {
        Row(Modifier.padding(horizontal = Space.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = c.textMuted)
            Text("Search receipts, warranties, links…", style = Keeply.type.bodyMd, color = c.textPlaceholder)
        }
    }
}

/**
 * What subscriptions cost: the monthly figure (an average when some plans aren't monthly), the
 * yearly total, what's due in the next 7 days, and the next few renewals. Only one currency is
 * added up; others are mentioned underneath.
 */
@Composable
private fun SpendCard(st: SpendSummary, onClick: () -> Unit, modifier: Modifier) {
    val soft = Color.White.copy(alpha = 0.8f)
    val glass = Color.White.copy(alpha = 0.16f)
    val dueCount = st.dueThisWeekCount
    Box(
        modifier.fillMaxWidth().clip(Radius.xl)
            .background(Brush.linearGradient(listOf(Keeply.colors.accent, Keeply.colors.accentDeep, Color(0xFFB8340A))))
            .drawBehind {
                // Soft light catching the top-right corner, and a fainter one bottom-left.
                val r = size.width * 0.6f
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), Offset(size.width, 0f), r), r, Offset(size.width, 0f))
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Transparent), Offset(0f, size.height), r), r, Offset(0f, size.height))
            }
            .clickable(onClickLabel = "View subscriptions", role = Role.Button, onClick = onClick)
            .padding(Space.lg),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Box(Modifier.size(28.dp).clip(Radius.pill).background(glass), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Autorenew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Text(
                    "${st.activeSubs} active subscription${if (st.activeSubs == 1) "" else "s"}",
                    style = Keeply.type.bodySmMedium, color = soft, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = soft, modifier = Modifier.size(20.dp))
            }
            Row(Modifier.padding(top = Space.sm), verticalAlignment = Alignment.Bottom) {
                Text(st.monthly, style = Keeply.type.hero, color = Color.White)
                Text(
                    if (st.monthlyIsAverage) " / month avg." else " / month",
                    style = Keeply.type.bodyMdMedium, color = soft, modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text("About ${st.yearly} a year", style = Keeply.type.bodyMd, color = Color.White.copy(alpha = 0.9f))
            st.otherCurrencyNote?.let { Text(it, style = Keeply.type.caption, color = Color.White.copy(alpha = 0.75f)) }

            val renewals = st.nextRenewals
            if (renewals.isNotEmpty()) {
                Row(
                    Modifier.padding(top = Space.md).fillMaxWidth().clip(Radius.lg).background(glass)
                        .padding(horizontal = Space.md, vertical = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    RenewalTicker(renewals, Modifier.weight(1f))
                    if (dueCount > 0) {
                        Box(Modifier.width(1.dp).height(44.dp).align(Alignment.CenterVertically).background(Color.White.copy(alpha = 0.25f)))
                        SpendStat(
                            "Due this week", st.dueThisWeek.orEmpty(),
                            "$dueCount subscription${if (dueCount == 1) "" else "s"}", Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * "Next renewal", then every few seconds the one after it, sliding up, with a dot per renewal.
 * A single renewal just sits still.
 */
@Composable
private fun RenewalTicker(renewals: List<NextRenewal>, modifier: Modifier) {
    var index by remember(renewals) { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (renewals.size > 1) {
        // Only ticks while Home is actually on screen.
        LaunchedEffect(renewals, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(3_500)
                    index = (index + 1) % renewals.size
                }
            }
        }
    }
    // Screen readers get the whole list at once instead of a moving target.
    val spoken = renewals.joinToString("; ") { "${it.name}, ${it.due}, ${it.price}" }
    Column(modifier.clearAndSetSemantics { contentDescription = "Upcoming renewals: $spoken" }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                if (index == 0) "Next renewal" else "Then", style = Keeply.type.micro,
                color = Color.White.copy(alpha = 0.75f), maxLines = 1, modifier = Modifier.weight(1f, fill = false),
            )
            if (renewals.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    renewals.indices.forEach { i ->
                        val active = i == index
                        Box(
                            Modifier.height(4.dp).width(if (active) 10.dp else 4.dp).clip(Radius.pill)
                                .background(Color.White.copy(alpha = if (active) 0.95f else 0.4f)),
                        )
                    }
                }
            }
        }
        AnimatedContent(
            renewals[index.coerceIn(renewals.indices)],
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) using SizeTransform(clip = true)
            },
            label = "renewal",
        ) { r ->
            Column {
                Text(r.name, style = Keeply.type.bodyMdMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${r.due} · ${r.price}", style = Keeply.type.caption, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SpendStat(label: String, value: String, detail: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = Keeply.type.micro, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = Keeply.type.bodyMdMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail.isNotEmpty()) {
            Text(detail, style = Keeply.type.caption, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WelcomeCard(modifier: Modifier) {
    val c = Keeply.colors
    Box(
        modifier.fillMaxWidth().clip(Radius.xl)
            .background(Brush.linearGradient(listOf(c.accent, c.accentDeep, Color(0xFFB8340A))))
            .padding(Space.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("Welcome to Keeply", style = Keeply.type.heading3, color = Color.White)
            Text(
                "Keep receipts, warranties, subscriptions and links in one place. Start by adding something below.",
                style = Keeply.type.bodyMd, color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun QuickAddRow(onAdd: (ItemType) -> Unit) {
    val c = Keeply.colors
    // Fixed-width tiles in a sideways-scrolling row, so every label fits however many types there are.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        ItemType.available.forEach { type ->
            Surface(
                modifier = Modifier.width(96.dp).semantics { contentDescription = "Add ${type.label.lowercase()}" },
                shape = Radius.lg, color = c.surfaceRaised, onClick = { onAdd(type) },
            ) {
                Column(
                    Modifier.padding(vertical = Space.md, horizontal = Space.xxs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    IconTile(type.icon, type.tint, size = 48.dp, iconSize = 24.dp)
                    Text(type.label, style = Keeply.type.micro, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun AllClearCard() {
    val c = Keeply.colors
    KeeplyCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            IconTile(Icons.Outlined.CheckCircle, c.tintGreen)
            Column(Modifier.weight(1f)) {
                Text("You're all caught up", style = Keeply.type.bodyMdMedium, color = c.text)
                Text("No renewals or expiring warranties this week.", style = Keeply.type.caption, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun FreePlanCard(used: Int, limit: Int, onViewPremium: () -> Unit, modifier: Modifier) {
    val c = Keeply.colors
    KeeplyCard(modifier, spacing = Space.sm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            IconTile(Icons.Outlined.WorkspacePremium, c.tintOrange)
            Column(Modifier.weight(1f)) {
                Text("Free plan", style = Keeply.type.bodyMdMedium, color = c.text)
                Text("$used of $limit items used", style = Keeply.type.caption, color = c.textMuted)
            }
            KeeplyButton("Upgrade", onViewPremium, variant = ButtonVariant.Soft, size = ButtonSize.Sm)
        }
        LinearProgressIndicator(
            progress = { (used.toFloat() / limit).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(Radius.pill),
            color = c.accent, trackColor = c.bgSubtle, drawStopIndicator = {}, gapSize = 0.dp,
        )
    }
}

/**
 * "Save the link you just copied?" Checked when Home comes into view (Android only lets the
 * app on screen read the clipboard). Shown once per link and never for one already saved.
 */
@Composable
private fun CopiedLinkCard(onSave: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val container = (context.applicationContext as app.keeply.KeeplyApp).container
    val prefs = remember { context.getSharedPreferences("keeply_clipboard", android.content.Context.MODE_PRIVATE) }
    var offer by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
        val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
        val url = text?.let { app.keeply.core.domain.UrlParser.extractFirst(it) }
        if (url == null || prefs.getString("last_offered", null) == url) return@LifecycleEventEffect
        scope.launch {
            if (!container.vault.hasLink(url)) offer = url
        }
    }
    val url = offer ?: return
    fun forget() { prefs.edit().putString("last_offered", url).apply(); offer = null }
    val c = Keeply.colors
    KeeplyCard(modifier, spacing = Space.sm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            IconTile(Icons.Outlined.ContentPaste, c.tintOrange)
            Column(Modifier.weight(1f)) {
                Text("Save the link you copied?", style = Keeply.type.bodyMdMedium, color = c.text)
                Text(url, style = Keeply.type.caption, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            KeeplyButton("Not now", { forget() }, Modifier.weight(1f), variant = ButtonVariant.Outline, size = ButtonSize.Md)
            KeeplyButton("Save", { forget(); onSave(url) }, Modifier.weight(1f), size = ButtonSize.Md)
        }
    }
}
