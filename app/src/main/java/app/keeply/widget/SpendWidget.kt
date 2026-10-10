package app.keeply.widget

import android.widget.RemoteViews
import androidx.glance.Image
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.layout.Box
import androidx.glance.layout.size
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.keeply.KeeplyApp
import app.keeply.MainActivity
import app.keeply.R
import app.keeply.core.domain.SpendSummary
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** What the widget shows. */
private sealed interface SpendWidgetState {
    data class Spend(val summary: SpendSummary) : SpendWidgetState
    /** No active subscriptions (or signed out, which clears the vault). */
    data object Empty : SpendWidgetState
    /** App lock is on: spending stays behind it, off the home screen. */
    data object Locked : SpendWidgetState
}

/**
 * The Home spend card on the home screen: what subscriptions cost a month, the yearly total,
 * the next renewal and what's due this week. Same numbers as the app ([SpendSummary]); tapping
 * it opens Keeply. Refreshed when subscriptions or settings change and by the daily worker.
 */
class SpendWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, SQUARE, WIDE, CARD))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as KeeplyApp).container
        val settings = container.settings.settings.first()
        val state = when {
            settings.appLock -> SpendWidgetState.Locked
            else -> SpendSummary.of(container.vault.observeSubscriptions().first(), settings.defaultCurrency, LocalDate.now())
                ?.let { SpendWidgetState.Spend(it) } ?: SpendWidgetState.Empty
        }
        provideContent { Content(state) }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 48.dp)
        private val SQUARE = DpSize(110.dp, 110.dp)
        private val WIDE = DpSize(250.dp, 110.dp)
        /** Room for the whole Home spend card: header, amount, and the renewal panel. */
        private val CARD = DpSize(250.dp, 180.dp)

        /** Redraws every Keeply widget on the home screen; a no-op when none are placed. */
        suspend fun refresh(context: Context) = SpendWidget().updateAll(context)
    }

    @Composable
    private fun Content(state: SpendWidgetState) {
        val size = LocalSize.current
        // The card is only as tall as what's on it, centred in its cell: home-screen cells are
        // often taller than the content, and a full-height card leaves empty orange bands.
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_spend_bg))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                when (state) {
                    SpendWidgetState.Locked -> Message("Keeply is locked", "Open Keeply to see your spending")
                    SpendWidgetState.Empty -> Message("No subscriptions yet", "Add one in Keeply to see what you spend")
                    is SpendWidgetState.Spend -> when {
                        size.height < SQUARE.height -> Amount(state.summary, 22.sp)
                        size.width < WIDE.width -> Square(state.summary)
                        size.height < CARD.height -> Wide(state.summary)
                        else -> Card(state.summary)
                    }
                }
            }
        }
    }

    @Composable
    private fun Square(s: SpendSummary) {
        Column(GlanceModifier.fillMaxWidth()) {
            Label(subsLabel(s))
            Amount(s, 24.sp)
            Spacer(GlanceModifier.height(8.dp))
            s.nextRenewals.firstOrNull()?.let { r ->
                Label("Next renewal")
                Line(r.name, 13.sp, bold = true)
                Line("${r.due} · ${r.price}", 11.sp, alpha = 0.85f)
            } ?: Line("About ${s.yearly} a year", 11.sp, alpha = 0.9f)
        }
    }

    /** The Home spend card, as in the app. */
    @Composable
    private fun Card(s: SpendSummary) {
        Column(GlanceModifier.fillMaxWidth()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    GlanceModifier.size(28.dp).background(ImageProvider(R.drawable.widget_icon_chip_bg)),
                    contentAlignment = Alignment.Center,
                ) { Image(ImageProvider(R.drawable.ic_widget_autorenew), contentDescription = null) }
                Spacer(GlanceModifier.width(8.dp))
                Text(subsLabel(s), style = TextStyle(color = white(0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                Image(ImageProvider(R.drawable.ic_widget_chevron), contentDescription = null)
            }
            Spacer(GlanceModifier.height(8.dp))
            Amount(s, 30.sp)
            Line("About ${s.yearly} a year", 13.sp, alpha = 0.9f)
            if (s.nextRenewals.isNotEmpty()) {
                Spacer(GlanceModifier.height(12.dp))
                Row(
                    GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_glass_bg)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RenewalFlipper(s, GlanceModifier.defaultWeight())
                    if (s.dueThisWeekCount > 0) {
                        Spacer(GlanceModifier.width(12.dp))
                        Box(GlanceModifier.width(1.dp).height(40.dp).background(ImageProvider(R.drawable.widget_divider))) {}
                        Spacer(GlanceModifier.width(12.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Label("Due this week")
                            Line(s.dueThisWeek.orEmpty(), 14.sp, bold = true)
                            Line("${s.dueThisWeekCount} subscription${if (s.dueThisWeekCount == 1) "" else "s"}", 12.sp, alpha = 0.85f)
                        }
                    }
                }
            }
        }
    }

    /** 4x2: the amount on the left, the sliding renewal and what's due this week on the right. */
    @Composable
    private fun Wide(s: SpendSummary) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Label(subsLabel(s))
                Amount(s, 28.sp)
                Line("About ${s.yearly} a year", 12.sp, alpha = 0.9f)
            }
            if (s.nextRenewals.isNotEmpty()) {
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.width(132.dp).background(ImageProvider(R.drawable.widget_glass_bg)).padding(10.dp)) {
                    RenewalFlipper(s, GlanceModifier.fillMaxWidth())
                    if (s.dueThisWeekCount > 0) {
                        Spacer(GlanceModifier.height(6.dp))
                        Label("${s.dueThisWeekCount} due this week")
                        Line(s.dueThisWeek.orEmpty(), 13.sp, bold = true)
                    }
                }
            }
        }
    }

    /**
     * The next renewals, sliding up one after another like the app's card. Widgets can't run
     * app animations, so this is a plain Android ViewFlipper, which the launcher flips itself.
     */
    @Composable
    private fun RenewalFlipper(s: SpendSummary, modifier: GlanceModifier) {
        val pkg = LocalContext.current.packageName
        val flipper = RemoteViews(pkg, R.layout.widget_renewal_flipper)
        s.nextRenewals.forEachIndexed { i, r ->
            val item = RemoteViews(pkg, R.layout.widget_renewal_item)
            item.setTextViewText(R.id.widget_renewal_label, if (i == 0) "Next renewal" else "Then")
            item.setTextViewText(R.id.widget_renewal_name, r.name)
            item.setTextViewText(R.id.widget_renewal_detail, "${r.due} · ${r.price}")
            if (s.nextRenewals.size > 1) s.nextRenewals.indices.forEach { d ->
                item.addView(R.id.widget_renewal_dots, RemoteViews(pkg, if (d == i) R.layout.widget_dot_active else R.layout.widget_dot))
            }
            flipper.addView(R.id.widget_renewal_flipper, item)
        }
        AndroidRemoteViews(flipper, modifier)
    }

    @Composable
    private fun Amount(s: SpendSummary, size: TextUnit) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(s.monthly, style = TextStyle(color = white(1f), fontSize = size, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(
                if (s.monthlyIsAverage) " / mo avg" else " / month",
                style = TextStyle(color = white(0.8f), fontSize = 12.sp), maxLines = 1,
                modifier = GlanceModifier.padding(bottom = 3.dp),
            )
        }
    }

    @Composable
    private fun Message(title: String, body: String) {
        Column(GlanceModifier.fillMaxWidth()) {
            Line(title, 15.sp, bold = true)
            Line(body, 12.sp, alpha = 0.85f)
        }
    }

    @Composable
    private fun Label(text: String) = Text(text, style = TextStyle(color = white(0.75f), fontSize = 11.sp), maxLines = 1)

    @Composable
    private fun Line(text: String, size: TextUnit, bold: Boolean = false, alpha: Float = 1f) =
        Text(
            text, maxLines = 1,
            style = TextStyle(color = white(alpha), fontSize = size, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal),
        )

    private fun subsLabel(s: SpendSummary) = "${s.activeSubs} active subscription${if (s.activeSubs == 1) "" else "s"}"

    private fun white(alpha: Float) = ColorProvider(Color.White.copy(alpha = alpha))
}

class SpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendWidget()
}
