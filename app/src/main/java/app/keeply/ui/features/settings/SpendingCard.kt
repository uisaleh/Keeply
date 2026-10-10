package app.keeply.ui.features.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.keeply.core.domain.Granularity
import app.keeply.core.domain.SpendRange
import app.keeply.core.domain.SpendingSummary
import app.keeply.core.model.Money
import app.keeply.ui.components.KeeplyCard
import app.keeply.ui.components.icon
import app.keeply.ui.components.tint
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Radius
import app.keeply.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Series colours, validated (lightness band, CVD separation, contrast) against each theme's card surface. */
private val RECEIPTS_LIGHT = Color(0xFFE5400A)
private val SUBS_LIGHT = Color(0xFF6D3FE0)
private val RECEIPTS_DARK = Color(0xFFE95A20)
private val SUBS_DARK = Color(0xFF8B6CEB)

private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val FULL_FMT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/**
 * Spending for a chosen date range: total, receipts vs subscriptions, and a smooth line per
 * source. Tap the chart to read a single day or month.
 */
@Composable
fun SpendingCard(
    summary: SpendingSummary, range: SpendRange, onRange: (SpendRange) -> Unit, onCustom: (LocalDate, LocalDate) -> Unit,
    modeSwitch: @Composable () -> Unit = {},
) {
    val c = Keeply.colors
    val receiptsColor = if (c.isDark) RECEIPTS_DARK else RECEIPTS_LIGHT
    val subsColor = if (c.isDark) SUBS_DARK else SUBS_LIGHT
    val buckets = summary.buckets
    // -1 = nothing selected: the headline shows the whole range.
    var selected by remember(summary) { mutableIntStateOf(-1) }
    fun money(minor: Long) = Money(minor, summary.currency).format()
    fun bucketLabel(i: Int): String {
        val d = buckets[i].start
        return if (summary.granularity == Granularity.DAY) d.format(DAY_FMT)
        else d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + if (d.year != LocalDate.now().year) " ${d.year % 100}" else ""
    }
    // Day by day, single payments would be spikes; a running total reads as a smooth climb.
    val cumulative = summary.granularity == Granularity.DAY
    fun series(f: (app.keeply.core.domain.SpendBucket) -> Long): List<Long> {
        val raw = buckets.map(f)
        return if (cumulative) raw.runningReduce { acc, v -> acc + v } else raw
    }
    // Days or months that haven't happened yet have no "spent" value: the lines stop at today.
    val lastIdx = buckets.indexOfLast { !it.start.isAfter(summary.asOf) }
    val receiptsSeries = series { it.receiptsMinor }.take(lastIdx + 1)
    val subsSeries = series { it.subscriptionsMinor }.take(lastIdx + 1)
    val sel = buckets.getOrNull(selected)
    val rangeText = if (range == SpendRange.CUSTOM) "${summary.from.format(FULL_FMT)} – ${summary.to.format(FULL_FMT)}" else range.label

    KeeplyCard(spacing = Space.sm) {
        modeSwitch()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    sel == null -> "Spent"
                    cumulative -> "Spent by ${bucketLabel(selected)}"
                    else -> "Spent in ${bucketLabel(selected)}"
                },
                style = Keeply.type.bodySmMedium, color = c.textMuted, modifier = Modifier.weight(1f),
            )
            RangePicker(range, onRange, onCustom)
        }
        Text(money(if (sel != null) receiptsSeries[selected] + subsSeries[selected] else summary.totalMinor), style = Keeply.type.heading1, color = c.text)
        if (sel == null && range == SpendRange.CUSTOM) Text(rangeText, style = Keeply.type.caption, color = c.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            LegendItem(receiptsColor, "Receipts", money(if (sel != null) receiptsSeries[selected] else summary.receiptsMinor))
            LegendItem(subsColor, "Subscriptions", money(if (sel != null) subsSeries[selected] else summary.subscriptionsMinor))
        }
        if (sel == null && summary.dueLaterMinor > 0) {
            Text(
                "Still due in this period: ${money(summary.dueLaterMinor)} · ${summary.dueLaterCount} renewal${if (summary.dueLaterCount == 1) "" else "s"}",
                style = Keeply.type.bodySmMedium, color = c.textMuted,
            )
        }

        if (summary.totalMinor == 0L || lastIdx < 0) {
            Text(
                if (summary.dueLaterMinor > 0) "Nothing spent yet in ${rangeText.lowercase()}." else "No spending in ${summary.currency} for ${rangeText.lowercase()}.",
                style = Keeply.type.bodySm, color = c.textMuted, modifier = Modifier.padding(vertical = Space.sm),
            )
        } else {
            val grid = c.border
            val crosshair = c.borderStrong
            val surface = c.surfaceRaised
            val max = maxOf(receiptsSeries.max(), subsSeries.max()).coerceAtLeast(1)
            val selectable = lastIdx
            // Dense day ranges skip the dots so the line reads, not the markers.
            val showDots = buckets.size <= 14
            Canvas(
                Modifier.fillMaxWidth().height(150.dp)
                    .semantics {
                        contentDescription = "Spending from ${summary.from.format(FULL_FMT)} to ${summary.to.format(FULL_FMT)}: " +
                            "receipts ${money(summary.receiptsMinor)}, subscriptions ${money(summary.subscriptionsMinor)}"
                    }
                    .pointerInput(buckets) {
                        detectTapGestures { p ->
                            val padPx = 6.dp.toPx()
                            val i = if (buckets.size > 1) {
                                Math.round((p.x - padPx) / ((size.width - 2 * padPx) / (buckets.size - 1))).coerceIn(0, selectable)
                            } else 0
                            selected = if (selected == i) -1 else i
                        }
                    },
            ) {
                val pad = 6.dp.toPx()
                val top = 8.dp.toPx()
                val bottom = size.height - 6.dp.toPx()
                val plotW = size.width - 2 * pad
                fun x(i: Int) = if (buckets.size > 1) pad + plotW * i / (buckets.size - 1) else size.width / 2
                fun y(v: Long) = bottom - (bottom - top) * v.toFloat() / max
                drawLine(grid, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
                drawLine(grid, Offset(0f, (top + bottom) / 2), Offset(size.width, (top + bottom) / 2), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                if (selected >= 0) drawLine(crosshair, Offset(x(selected), top), Offset(x(selected), bottom), 1.dp.toPx())

                listOf(receiptsSeries to receiptsColor, subsSeries to subsColor).forEach { (values, color) ->
                    val pts = values.mapIndexed { i, v -> Offset(x(i), y(v)) }
                    if (pts.size > 1) drawPath(smoothPath(pts, bottom), color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    pts.forEachIndexed { i, p ->
                        if (showDots || i == selected || pts.size == 1) {
                            val r = if (i == selected) 5.dp.toPx() else 4.dp.toPx()
                            drawCircle(surface, r + 2.dp.toPx(), p)
                            drawCircle(color, r, p)
                        }
                    }
                }
            }
            // A few evenly spaced labels so they never collide.
            val labelIdx = when {
                buckets.size <= 7 -> buckets.indices.toList()
                summary.granularity == Granularity.MONTH && buckets.size <= 12 -> buckets.indices.filter { it % 2 == 0 || it == buckets.lastIndex }.distinct()
                else -> listOf(0, buckets.lastIndex / 2, buckets.lastIndex).distinct()
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (labelIdx.size == 1) Arrangement.Center else Arrangement.SpaceBetween) {
                labelIdx.forEach { i ->
                    Text(bucketLabel(i), style = Keeply.type.micro, color = if (i == selected) c.text else c.textMuted, textAlign = TextAlign.Center)
                }
            }
        }
        val notes = buildList {
            if (lastIdx in 0 until buckets.lastIndex) add(if (cumulative) "Days after today fill in as they happen." else "Months after this one fill in as they happen.")
            if (summary.otherCurrencyItems > 0) add("${summary.otherCurrencyItems} item${if (summary.otherCurrencyItems == 1) "" else "s"} in other currencies not included.")
            if (cumulative && buckets.size > 1) add("Lines show the running total.")
            if (selected < 0 && summary.totalMinor > 0 && buckets.size > 1) add("Tap the chart to see a single ${if (cumulative) "day" else "month"}.")
        }
        if (notes.isNotEmpty()) Text(notes.joinToString(" "), style = Keeply.type.caption, color = c.textPlaceholder)
    }
}

/**
 * Monotone cubic curve through the points (Fritsch–Carlson): rounded, but it never overshoots
 * a peak or dips below zero between two equal values the way a plain spline would.
 */
internal fun smoothPath(p: List<Offset>, floor: Float): Path {
    val n = p.size
    val d = FloatArray(n - 1) { (p[it + 1].y - p[it].y) / (p[it + 1].x - p[it].x) }
    val m = FloatArray(n) { i ->
        when (i) {
            0 -> d[0]
            n - 1 -> d[n - 2]
            else -> if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
        }
    }
    for (i in 0 until n - 1) {
        if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
        val a = m[i] / d[i]; val b = m[i + 1] / d[i]
        val s = a * a + b * b
        if (s > 9f) { val t = 3f / kotlin.math.sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
    }
    return Path().apply {
        moveTo(p[0].x, p[0].y)
        for (i in 0 until n - 1) {
            val h = (p[i + 1].x - p[i].x) / 3f
            cubicTo(
                p[i].x + h, (p[i].y + m[i] * h).coerceAtMost(floor),
                p[i + 1].x - h, (p[i + 1].y - m[i + 1] * h).coerceAtMost(floor),
                p[i + 1].x, p[i + 1].y,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RangePicker(range: SpendRange, onRange: (SpendRange) -> Unit, onCustom: (LocalDate, LocalDate) -> Unit) {
    val c = Keeply.colors
    var open by remember { mutableStateOf(false) }
    var pickCustom by remember { mutableStateOf(false) }
    Box {
        Surface(shape = Radius.pill, color = c.fieldFill, onClick = { open = true }) {
            Row(Modifier.padding(start = Space.sm, end = Space.xs, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (range == SpendRange.CUSTOM) "Custom" else range.label, style = Keeply.type.bodySmMedium, color = c.text)
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Choose date range", tint = c.textMuted, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surfaceRaised) {
            SpendRange.entries.forEach { r ->
                DropdownMenuItem(
                    text = { Text(if (r == SpendRange.CUSTOM) "Custom range…" else r.label, style = Keeply.type.bodyMd, color = c.text) },
                    trailingIcon = if (r == range) ({ Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = c.accent) }) else null,
                    onClick = { open = false; if (r == SpendRange.CUSTOM) pickCustom = true else onRange(r) },
                )
            }
        }
    }
    if (pickCustom) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pickCustom = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = state.selectedStartDateMillis
                        val e = state.selectedEndDateMillis ?: s
                        if (s != null && e != null) onCustom(utcDate(s), utcDate(e))
                        pickCustom = false
                    },
                    enabled = state.selectedStartDateMillis != null,
                ) { Text("Done", color = c.link) }
            },
            dismissButton = { TextButton(onClick = { pickCustom = false }) { Text("Cancel", color = c.textMuted) } },
        ) { DateRangePicker(state, modifier = Modifier.height(500.dp)) }
    }
}

private fun utcDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun LegendItem(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Column {
            Text(label, style = Keeply.type.caption, color = Keeply.colors.textMuted)
            Text(value, style = Keeply.type.bodySmMedium, color = Keeply.colors.text)
        }
    }
}

/**
 * Overview with a Spent / Vault switch: what you spent, or what you added to the vault, over
 * the same date range.
 */
@Composable
fun OverviewCard(
    spending: SpendingSummary, additions: app.keeply.core.domain.AdditionsSummary,
    range: SpendRange, onRange: (SpendRange) -> Unit, onCustom: (LocalDate, LocalDate) -> Unit,
) {
    var mode by remember { mutableIntStateOf(0) }
    val switch: @Composable () -> Unit = {
        app.keeply.ui.components.ChoiceChips(listOf("Spent", "Vault"), if (mode == 0) "Spent" else "Vault", label = { it }, onSelect = { mode = if (it == "Spent") 0 else 1 })
    }
    if (mode == 0) SpendingCard(spending, range, onRange, onCustom, switch)
    else VaultAddedCard(additions, range, onRange, onCustom, switch)
}

/**
 * Fixed colour per type for this chart (validated as an ordered palette: CVD and normal-vision
 * separation pass in both themes). Colour follows the type, never its rank, so filtering the
 * date range never repaints a line.
 */
@Composable
private fun seriesColor(type: app.keeply.core.model.ItemType): Color {
    val dark = Keeply.colors.isDark
    return when (type) {
        app.keeply.core.model.ItemType.LINK -> if (dark) Color(0xFF3987E5) else Color(0xFF2A78D6)
        app.keeply.core.model.ItemType.RECEIPT -> if (dark) Color(0xFFD95926) else Color(0xFFEB6834)
        app.keeply.core.model.ItemType.DOCUMENT -> if (dark) Color(0xFF199E70) else Color(0xFF1BAF7A)
        app.keeply.core.model.ItemType.SUBSCRIPTION -> if (dark) Color(0xFF9085E9) else Color(0xFF4A3AA7)
        app.keeply.core.model.ItemType.REMINDER -> if (dark) Color(0xFFD55181) else Color(0xFFE87BA4)
        app.keeply.core.model.ItemType.WARRANTY -> Color(0xFF008300)
        app.keeply.core.model.ItemType.SCREENSHOT -> Keeply.colors.textMuted
    }
}

/** Items added over the range: one line per type, plus a labelled breakdown (identity is never colour alone). */
@Composable
private fun VaultAddedCard(
    summary: app.keeply.core.domain.AdditionsSummary, range: SpendRange,
    onRange: (SpendRange) -> Unit, onCustom: (LocalDate, LocalDate) -> Unit, modeSwitch: @Composable () -> Unit,
) {
    val c = Keeply.colors
    val buckets = summary.buckets
    val cumulative = summary.granularity == Granularity.DAY
    val lastIdx = buckets.indexOfLast { !it.start.isAfter(summary.asOf) }
    // Per type, running total for day ranges; stops at today.
    val lines = summary.byType.map { (type, _) ->
        val raw = summary.series[type].orEmpty().map { it.toLong() }
        type to (if (cumulative) raw.runningReduce { a, b -> a + b } else raw).take(lastIdx + 1)
    }
    val colors = lines.associate { (t, _) -> t to seriesColor(t) }
    var selected by remember(summary) { mutableIntStateOf(-1) }
    fun label(i: Int): String {
        val d = buckets[i].start
        return if (summary.granularity == Granularity.DAY) d.format(DAY_FMT)
        else d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + if (d.year != LocalDate.now().year) " ${d.year % 100}" else ""
    }
    fun items(n: Long) = "$n item${if (n == 1L) "" else "s"}"
    fun valueAt(type: app.keeply.core.model.ItemType): Long = lines.first { it.first == type }.second.let { if (selected in it.indices) it[selected] else 0 }
    val headline = if (selected >= 0) lines.sumOf { (_, v) -> v.getOrElse(selected) { 0 } } else summary.total.toLong()

    KeeplyCard(spacing = Space.sm) {
        modeSwitch()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    selected < 0 -> "Added to your vault"
                    cumulative -> "Added by ${label(selected)}"
                    else -> "Added in ${label(selected)}"
                },
                style = Keeply.type.bodySmMedium, color = c.textMuted, modifier = Modifier.weight(1f),
            )
            RangePicker(range, onRange, onCustom)
        }
        Text(items(headline), style = Keeply.type.heading1, color = c.text)

        if (summary.total == 0 || lastIdx < 0) {
            Text("Nothing added in this period.", style = Keeply.type.bodySm, color = c.textMuted, modifier = Modifier.padding(vertical = Space.sm))
        } else {
            val grid = c.border
            val crosshair = c.borderStrong
            val surface = c.surfaceRaised
            val max = lines.maxOf { (_, v) -> v.maxOrNull() ?: 0 }.coerceAtLeast(1)
            Canvas(
                Modifier.fillMaxWidth().height(150.dp)
                    .semantics {
                        contentDescription = "${items(summary.total.toLong())} added: " + summary.byType.joinToString { (t, n) -> "$n ${t.pluralLabel.lowercase()}" }
                    }
                    .pointerInput(buckets, lastIdx) {
                        detectTapGestures { p ->
                            val padPx = 6.dp.toPx()
                            val i = if (buckets.size > 1) Math.round((p.x - padPx) / ((size.width - 2 * padPx) / (buckets.size - 1))).coerceIn(0, lastIdx) else 0
                            selected = if (selected == i) -1 else i
                        }
                    },
            ) {
                val pad = 6.dp.toPx(); val top = 8.dp.toPx(); val bottom = size.height - 6.dp.toPx()
                fun x(i: Int) = if (buckets.size > 1) pad + (size.width - 2 * pad) * i / (buckets.size - 1) else size.width / 2
                fun y(v: Long) = bottom - (bottom - top) * v.toFloat() / max
                drawLine(grid, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
                drawLine(grid, Offset(0f, (top + bottom) / 2), Offset(size.width, (top + bottom) / 2), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                if (selected >= 0) drawLine(crosshair, Offset(x(selected), top), Offset(x(selected), bottom), 1.dp.toPx())
                lines.forEach { (type, values) ->
                    val color = colors.getValue(type)
                    val pts = values.mapIndexed { i, v -> Offset(x(i), y(v)) }
                    if (pts.size > 1) drawPath(smoothPath(pts, bottom), color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    // End dot on each line (and on the selected day) with a surface ring so crossings stay readable.
                    pts.forEachIndexed { i, p ->
                        if (i == pts.lastIndex || i == selected || (buckets.size <= 7)) {
                            drawCircle(surface, 6.dp.toPx(), p)
                            drawCircle(color, 4.dp.toPx(), p)
                        }
                    }
                }
            }
            val labelIdx = when {
                buckets.size <= 7 -> buckets.indices.toList()
                summary.granularity == Granularity.MONTH && buckets.size <= 12 -> buckets.indices.filter { it % 2 == 0 || it == buckets.lastIndex }.distinct()
                else -> listOf(0, buckets.lastIndex / 2, buckets.lastIndex).distinct()
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (labelIdx.size == 1) Arrangement.Center else Arrangement.SpaceBetween) {
                labelIdx.forEach { i -> Text(label(i), style = Keeply.type.micro, color = if (i == selected) c.text else c.textMuted) }
            }
            // Legend and breakdown in one: line colour, the type's icon and name, and its count.
            Column(Modifier.padding(top = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                summary.byType.forEach { (type, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Box(Modifier.width(16.dp).height(3.dp).background(colors.getValue(type), Radius.pill))
                        app.keeply.ui.components.IconTile(type.icon, type.tint, size = 28.dp, iconSize = 16.dp)
                        Text(type.pluralLabel, style = Keeply.type.bodySm, color = c.text, modifier = Modifier.weight(1f))
                        Text(
                            if (selected >= 0) "${valueAt(type)}" else "$n",
                            style = Keeply.type.bodySmMedium, color = c.text, textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
        val notes = buildList {
            if (cumulative && summary.total > 0 && buckets.size > 1) add("Lines show the running total.")
            if (summary.total > 0) add("Deleted items still count.")
            if (selected < 0 && summary.total > 0 && buckets.size > 1) add("Tap the chart to see a single ${if (cumulative) "day" else "month"}.")
        }
        if (notes.isNotEmpty()) Text(notes.joinToString(" "), style = Keeply.type.caption, color = c.textPlaceholder)
    }
}
