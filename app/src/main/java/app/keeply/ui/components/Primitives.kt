package app.keeply.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Radius
import app.keeply.ui.theme.Space

/** Space a top-level screen must leave at its bottom so the floating nav bar doesn't cover its last row. */
val LocalBottomBarInset = staticCompositionLocalOf { 0.dp }

enum class TagTone { Brand, Gold, Cream, Dark, Neutral, Success, Danger }

/** Small pill label. Status is always spelled out, never colour alone. */
@Composable
fun KeeplyTag(text: String, tone: TagTone = TagTone.Neutral, modifier: Modifier = Modifier) {
    val c = Keeply.colors
    val (bg, fg) = when (tone) {
        TagTone.Brand -> c.accent to c.onAccent
        TagTone.Gold -> c.warningSoft to c.onWarning
        TagTone.Cream -> c.selected to c.link
        TagTone.Dark -> c.btnDark to c.onBtnDark
        TagTone.Neutral -> c.bgSubtle to c.textMuted
        TagTone.Success -> c.successSoft to c.success
        TagTone.Danger -> c.dangerSoft to c.danger
    }
    Text(
        text = text,
        style = Keeply.type.micro,
        color = fg,
        maxLines = 1,
        modifier = modifier.clip(Radius.pill).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Small section kicker above a card's main line. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Keeply.colors.textMuted) {
    Text(text.uppercase(), style = Keeply.type.microUpper, color = color, modifier = modifier)
}

enum class CardTone { Default, Warm, Dark }

/**
 * Default: white card on the off-white page, 20dp radius, no outline (a hairline in dark
 * mode, where colour alone doesn't separate). Warm: soft orange tint. Dark: inverted.
 */
@Composable
fun KeeplyCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Default,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(Space.lg),
    spacing: Dp = Space.xs,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Keeply.colors
    val (bg, border) = when (tone) {
        CardTone.Default -> c.surfaceRaised to (if (c.isDark) BorderStroke(1.dp, c.border) else null)
        CardTone.Warm -> c.surfaceWarm to null
        CardTone.Dark -> c.btnDark to null
    }
    Surface(modifier = modifier.fillMaxWidth(), shape = Radius.lg, color = bg, border = border) {
        Column(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** A white rounded container for a list of rows, with inset dividers between them. */
@Composable
fun GroupedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Keeply.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Radius.lg,
        color = c.surfaceRaised,
        border = if (c.isDark) BorderStroke(1.dp, c.border) else null,
    ) {
        Column(Modifier.padding(vertical = Space.xxs), content = content)
    }
}

/** Divider between rows inside a [GroupedCard], inset to line up with the text. */
@Composable
fun RowDivider(startInset: Dp = 72.dp) =
    HorizontalDivider(Modifier.padding(start = startInset, end = Space.md), thickness = 1.dp, color = Keeply.colors.border)

/** Big bold page title with an optional muted subtitle. Marked as a heading for TalkBack. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Text(title, style = Keeply.type.displayLg, color = Keeply.colors.text, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, style = Keeply.type.bodyMd, color = Keeply.colors.textMuted)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Keeply.type.heading4, color = Keeply.colors.text, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

/** Bold numeral, optional unit, muted label. */
@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier, unit: String? = null, color: Color = Keeply.colors.text, labelColor: Color = Keeply.colors.textMuted) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = Keeply.type.stat, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = Keeply.type.heading5, color = labelColor, modifier = Modifier.padding(bottom = 5.dp))
            }
        }
        Text(label, style = Keeply.type.bodySm, color = labelColor)
    }
}

/** Rounded-square icon on a tinted background. `tint` is (background, foreground). */
@Composable
fun IconTile(icon: ImageVector, tint: Pair<Color, Color>, modifier: Modifier = Modifier, size: Dp = 44.dp, iconSize: Dp = 22.dp) {
    Box(Modifier.then(modifier).size(size).clip(Radius.md).background(tint.first), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint.second, modifier = Modifier.size(iconSize))
    }
}

/** A tappable list row: tinted icon tile, title, subtitle, trailing slot. 64dp min height. */
@Composable
fun ItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Pair<Color, Color>? = null,
    showChevron: Boolean = false,
    /** Replaces the icon tile, e.g. with a logo. */
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Keeply.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        if (leading != null) leading() else IconTile(icon, tint ?: c.tintSlate)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Keeply.type.bodyMdMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = Keeply.type.caption, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
        if (showChevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = c.textPlaceholder)
    }
}

/** Circle with the person's initial. */
@Composable
fun Avatar(name: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val c = Keeply.colors
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
    Box(
        modifier.size(size).clip(Radius.pill).background(c.selected),
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            Text(initial, style = if (size >= 56.dp) Keeply.type.heading3 else Keeply.type.heading5, color = c.link)
        } else {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = c.link, modifier = Modifier.size(size / 2))
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = Keeply.colors.border)

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))
