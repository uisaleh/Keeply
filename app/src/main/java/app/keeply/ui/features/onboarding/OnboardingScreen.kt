package app.keeply.ui.features.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.keeply.ui.components.KeeplyButton
import app.keeply.ui.theme.KeeplyColors
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Radius
import app.keeply.ui.theme.Space
import kotlinx.coroutines.launch

private data class Page(
    val title: String,
    val body: String,
    val icon: ImageVector,
    val tint: (KeeplyColors) -> Pair<Color, Color>,
    /** Two little floating cards that hint at what the feature looks like in use. */
    val chips: List<String>,
)

private val pages = listOf(
    Page(
        "Everything important, in one place", "Keep receipts, warranties, subscriptions and links organised and easy to find.",
        Icons.Outlined.Inventory2, { it.tintOrange }, listOf("Receipt saved", "MacBook warranty"),
    ),
    Page(
        "Never miss what matters", "Get reminders before warranties end and subscriptions renew.",
        Icons.Outlined.NotificationsActive, { it.tintViolet }, listOf("Renews tomorrow", "Expires in 30 days"),
    ),
    Page(
        "Find it in seconds", "Search your whole vault by shop, brand, product or tag. Works offline.",
        Icons.Outlined.Search, { it.tintBlue }, listOf("\"Apple\"", "3 results"),
    ),
    Page(
        "Safe in your account", "Your vault is saved to your own account, so it moves with you to a new phone. Only you can see it.",
        Icons.Outlined.CloudDone, { it.tintGreen }, listOf("Synced", "Private to you"),
    ),
)

/** Four short pages, then sign-up or sign-in. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    val c = Keeply.colors

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Space.sm),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!last) TextButton(onClick = onFinish) { Text("Skip", style = Keeply.type.button, color = c.textMuted) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val p = pages[index]
            Column(
                Modifier.fillMaxSize().padding(horizontal = Space.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Illustration(p)
                Spacer(Modifier.height(Space.xxl))
                Text(p.title, style = Keeply.type.displayLg, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Space.sm))
                Text(p.body, style = Keeply.type.subtitle, color = c.textMuted, textAlign = TextAlign.Center)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = Space.lg)
                .semantics { contentDescription = "Page ${pager.currentPage + 1} of ${pages.size}" },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            pages.indices.forEach { i ->
                val selected = i == pager.currentPage
                val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot")
                val color by animateColorAsState(if (selected) c.accent else c.borderStrong, label = "dotColor")
                Box(Modifier.height(8.dp).width(width).clip(Radius.pill).background(color))
            }
        }
        KeeplyButton(
            if (last) "Get started" else "Next",
            { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            Modifier.fillMaxWidth().padding(horizontal = Space.xl).padding(bottom = Space.lg),
            arrow = true,
        )
    }
}

@Composable
private fun Illustration(p: Page) {
    val c = Keeply.colors
    val (bg, fg) = p.tint(c)
    Box(Modifier.size(260.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(224.dp).clip(Radius.pill).background(bg))
        Box(Modifier.size(156.dp).clip(Radius.pill).background(c.surfaceRaised.copy(alpha = 0.5f)))
        Surface(shape = Radius.xl, color = c.surfaceRaised, shadowElevation = 8.dp, modifier = Modifier.size(104.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(p.icon, contentDescription = null, tint = fg, modifier = Modifier.size(48.dp)) }
        }
        FloatingChip(p.chips[0], fg, Modifier.align(Alignment.TopStart).offset(y = 28.dp))
        FloatingChip(p.chips[1], fg, Modifier.align(Alignment.BottomEnd).offset(y = (-28).dp))
    }
}

@Composable
private fun FloatingChip(text: String, dot: Color, modifier: Modifier) {
    val c = Keeply.colors
    Surface(shape = Radius.pill, color = c.surfaceRaised, shadowElevation = 6.dp, modifier = modifier.height(36.dp)) {
        Row(Modifier.padding(horizontal = Space.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            Box(Modifier.size(8.dp).clip(Radius.pill).background(dot))
            Text(text, style = Keeply.type.micro, color = c.text)
        }
    }
}
