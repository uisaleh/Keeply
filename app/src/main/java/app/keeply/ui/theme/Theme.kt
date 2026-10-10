package app.keeply.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.keeply.data.settings.ThemeMode

/** 4dp base spacing scale from the design system. */
@Immutable
object Space {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 40.dp
    val sectionSm: Dp = 48.dp
}

/** Radii: tags/chips are pills, inputs and buttons 14, cards 20, sheets 28. */
@Immutable
object Radius {
    val xs = RoundedCornerShape(6.dp)
    val sm = RoundedCornerShape(10.dp)
    val md = RoundedCornerShape(14.dp)
    val lg = RoundedCornerShape(20.dp)
    val xl = RoundedCornerShape(24.dp)
    val xxl = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/** Motion durations (ms) and the rule that everything settles, nothing bounces. */
object Motion {
    const val FAST = 150
    const val BASE = 200
    const val SLOW = 400
    const val REVEAL = 700
}

val LocalKeeplyColors = staticCompositionLocalOf { LightKeeplyColors }
val LocalKeeplyType = staticCompositionLocalOf { KeeplyTypeScale }

object Keeply {
    val colors: KeeplyColors @Composable get() = LocalKeeplyColors.current
    val type: KeeplyType @Composable get() = LocalKeeplyType.current
}

@Composable
fun KeeplyTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = if (dark) DarkKeeplyColors else LightKeeplyColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.btnDark, onSecondary = c.onBtnDark,
            background = c.bg, onBackground = c.text, surface = c.surfaceRaised, onSurface = c.text,
            surfaceVariant = c.surfaceWarm, onSurfaceVariant = c.textMuted, surfaceContainer = c.surfaceRaised,
            surfaceContainerHigh = c.surfaceRaised, surfaceContainerHighest = c.fieldFill, surfaceContainerLow = c.bgSubtle,
            outline = c.borderStrong, outlineVariant = c.border, error = c.danger, onError = c.bg,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.btnDark, onSecondary = c.onBtnDark,
            background = c.bg, onBackground = c.text, surface = c.surfaceRaised, onSurface = c.text,
            surfaceVariant = c.surfaceWarm, onSurfaceVariant = c.textMuted, surfaceContainer = c.surfaceRaised,
            surfaceContainerHigh = c.surfaceRaised, surfaceContainerHighest = c.fieldFill, surfaceContainerLow = c.bgSubtle,
            outline = c.borderStrong, outlineVariant = c.border, error = c.danger, onError = SunsetTokens.White,
        )
    }
    CompositionLocalProvider(LocalKeeplyColors provides c, LocalKeeplyType provides KeeplyTypeScale) {
        MaterialTheme(
            colorScheme = scheme,
            typography = KeeplyMaterialTypography,
            shapes = Shapes(extraSmall = Radius.xs, small = Radius.sm, medium = Radius.md, large = Radius.lg, extraLarge = Radius.xxl),
            content = content,
        )
    }
}
