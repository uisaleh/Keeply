package app.keeply.ui.features.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.keeply.ui.theme.Radius
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import app.keeply.KeeplyApp
import app.keeply.premium.FreePlanLimits
import app.keeply.premium.PremiumProduct
import app.keeply.premium.PriceCatalog
import app.keeply.ui.components.CardTone
import app.keeply.ui.components.KeeplyCard
import app.keeply.ui.components.KeeplyTag
import app.keeply.ui.components.TagTone
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space

/**
 * Only what Premium actually unlocks in this build. Add a line here when a feature ships,
 * never before: everything else in the app is already free.
 */
private val premiumIncludes = listOf(
    "No limits: save as many receipts, warranties, links, subscriptions, documents and reminders as you like",
    "Everything you've already saved stays exactly where it is",
)

/** Included on every plan, so free users know they're not missing out on the basics. */
private val freeIncludes = listOf(
    "${FreePlanLimits.PER_TYPE} of each type, ${FreePlanLimits.TOTAL_ITEMS} items in total",
    "Reminders before renewals, warranty ends, document expiry and your own timed reminders",
    "Sync to your account and the Activity history",
    "Search across your whole vault",
)

/**
 * Plan comparison. Purchase buttons appear only when Google Play Billing (with server-side
 * verification) is wired in — this build has no way to charge anyone, so it says so plainly.
 */
@Composable
fun PremiumScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as KeeplyApp).container
    val c = Keeply.colors
    Column(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg).padding(top = Space.sm, bottom = Space.xxl).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Surface(shape = Radius.pill, color = c.surfaceRaised, onClick = onBack, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = c.text) }
        }
        Column(
            Modifier.fillMaxWidth().clip(Radius.xl)
                .background(Brush.linearGradient(listOf(c.accent, c.accentDeep, Color(0xFFB8340A))))
                .padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            Text("Keeply Premium", style = Keeply.type.displayLg, color = Color.White, modifier = Modifier.semantics { heading() })
            Text(
                "Keeply Free covers ${FreePlanLimits.TOTAL_ITEMS} items with reminders, sync and search. Premium removes the limits.",
                style = Keeply.type.bodyMd, color = Color.White.copy(alpha = 0.9f),
            )
        }
        KeeplyCard(spacing = Space.sm) {
            Text("Premium adds", style = Keeply.type.heading4, color = c.text)
            premiumIncludes.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(Radius.pill).background(c.successSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = c.success, modifier = Modifier.size(16.dp))
                    }
                    Text(line, style = Keeply.type.bodyMd, color = c.text)
                }
            }
        }
        KeeplyCard(spacing = Space.sm) {
            Text("Included free", style = Keeply.type.heading4, color = c.text)
            freeIncludes.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(Radius.pill).background(c.bgSubtle), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(16.dp))
                    }
                    Text(line, style = Keeply.type.bodyMd, color = c.textMuted)
                }
            }
        }
        PriceCatalog.all.forEach { PlanCard(it) }
        if (!container.entitlements.purchasesAvailable) {
            KeeplyCard(tone = CardTone.Warm) {
                Text("Premium isn't on sale yet", style = Keeply.type.bodyMdMedium, color = c.text)
                Text(
                    "Purchases open in an upcoming update through Google Play. Until then everything in Keeply Free works fully, and nothing you save will be lost when you upgrade.",
                    style = Keeply.type.bodySm, color = c.textMuted,
                )
            }
        }
    }
}

@Composable
private fun PlanCard(p: PremiumProduct) {
    val c = Keeply.colors
    KeeplyCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (p.isRecurring) "Annual" else "Lifetime", style = Keeply.type.heading5, color = c.text, modifier = Modifier.weight(1f))
            if (!p.isRecurring) KeeplyTag("One-time", TagTone.Gold)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(p.listPriceUsd, style = Keeply.type.stat, color = c.text)
            Text(p.periodLabel, style = Keeply.type.bodySm, color = c.textMuted, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(
            if (p.isRecurring) "Cancel any time in Google Play. Local price shown at checkout."
            else "Pay once, no limits for good. Local price shown at checkout.",
            style = Keeply.type.caption, color = c.textMuted,
        )
    }
}
