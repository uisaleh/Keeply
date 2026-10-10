package app.keeply.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.keeply.ui.theme.Keeply

/**
 * Where the brand wash behind the top of a screen sits, in window pixels. Updated as the screen
 * scrolls, so the wash moves with the content. NaN while that element isn't on screen.
 */
class BrandGlow {
    /** Centre of what the middle of the wash fades out under (Home: the search bar). */
    var fadeAnchor by mutableFloatStateOf(Float.NaN)
    /** Centre of what the two sides run down beside (Home: the spend card). */
    var sidesAnchor by mutableFloatStateOf(Float.NaN)
    var top by mutableFloatStateOf(0f)
    /** Sides anchor minus fade anchor, last time both were on screen. */
    internal var gap = Float.NaN
    /** Where the fade anchor sits with the screen scrolled to the top. */
    internal var rest = Float.NaN

    /**
     * The fade anchor, worked out from the sides anchor once it has scrolled away (a lazy list
     * drops it while the wash under it can still be on screen).
     */
    internal fun fade(): Float {
        if (!fadeAnchor.isNaN() && !sidesAnchor.isNaN()) gap = sidesAnchor - fadeAnchor
        return if (fadeAnchor.isNaN() && !gap.isNaN()) sidesAnchor - gap else fadeAnchor
    }
}

/** Reports [pick] of this element's window bounds to [onY], and NaN once it leaves composition. */
@Composable
fun Modifier.trackY(glow: BrandGlow, pick: (Rect) -> Float, onY: (Float) -> Unit): Modifier {
    DisposableEffect(glow) { onDispose { onY(Float.NaN) } }
    return onGloballyPositioned { onY(pick(it.boundsInWindow())) }
}

/**
 * The brand wash: one aurora of brand orange, amber, rose and a touch of violet across the top.
 * Down the middle it fades out just under [BrandGlow.fadeAnchor] (Home: the search bar); down the
 * two sides it carries on and fades out beside [BrandGlow.sidesAnchor] (Home: the spend card), or
 * a little under the middle when there's nothing there, so that element sits in a soft U of colour.
 * Drawn rather than composed, so scrolling only redraws it.
 *
 * [scrolledBy] is how far the screen has scrolled while the fade anchor's position at the top is
 * still known (e.g. a lazy list's offset while its first item shows), else null.
 */
@Composable
fun Modifier.brandBackdrop(
    glow: BrandGlow, scrolledBy: () -> Int?, midDrop: Dp = 76.dp, sideDrop: Dp = 80.dp,
): Modifier {
    val c = Keeply.colors
    val density = LocalDensity.current
    val tail = with(density) { 40.dp.toPx() }
    // How far past the two anchors' centres the middle and the sides reach.
    val midDropPx = with(density) { midDrop.toPx() }
    val sideDropPx = with(density) { sideDrop.toPx() }
    // Dark mode uses deeper, less neon tones and a lighter base wash, so the aurora glows out of
    // the near-black instead of turning it muddy orange.
    val dark = c.isDark
    val strength = if (dark) 0.6f else 0.8f
    val base = if (dark) 0.22f else 0.4f
    val amber = if (dark) Color(0xFFFF8A2A) else Color(0xFFFFA24A)
    val rose = if (dark) Color(0xFFD81E5B) else Color(0xFFF0306A)
    val violet = if (dark) Color(0xFF7B4DE0) else Color(0xFF9B5CFF)
    return onGloballyPositioned { glow.top = it.boundsInWindow().top }.drawBehind {
        val fade = glow.fade()
        if (fade.isNaN()) return@drawBehind
        // Lay the wash out as if unscrolled, then slide it by however far the screen has moved,
        // so it scrolls with the content instead of squashing.
        scrolledBy()?.let { glow.rest = fade + it }
        if (glow.rest.isNaN()) return@drawBehind
        val shift = fade - glow.rest
        val mid = glow.rest + midDropPx - glow.top
        val sides = (if (glow.sidesAnchor.isNaN()) glow.rest + midDropPx + tail else glow.sidesAnchor - shift + sideDropPx) - glow.top
        val end = maxOf(mid, sides)
        if (end + shift <= 0f) return@drawBehind
        translate(top = shift) {
            val w = size.width
            fun glowAt(color: Color, alpha: Float, x: Float, y: Float, r: Float) = drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.45f), Color.Transparent), Offset(x, y), r),
                radius = r, center = Offset(x, y),
            )
            // A long, thin, tilted band of light: a stretched glow.
            fun ribbon(color: Color, alpha: Float, x: Float, y: Float, length: Float, tilt: Float) =
                withTransform({ rotate(tilt, Offset(x, y)); scale(1f, 0.28f, Offset(x, y)) }) { glowAt(color, alpha, x, y, length) }
            // A fade from solid at the top of an edge down to nothing at [to], used to shape the mask.
            fun sideFade(x: Float, to: Float) {
                val top = mid * 0.4f
                val r = w * 0.13f
                withTransform({ scale(1f, (to - top) / r, Offset(x, top)) }) {
                    drawCircle(
                        Brush.radialGradient(0f to Color.Black, 0.6f to Color.Black.copy(alpha = 0.9f), 1f to Color.Transparent, center = Offset(x, top), radius = r),
                        radius = r, center = Offset(x, top),
                    )
                }
            }

            val area = Rect(0f, 0f, w, end)
            drawIntoCanvas { it.saveLayer(area, Paint()) }
            // The aurora itself.
            drawRect(c.accent.copy(alpha = strength * base), size = area.size)
            glowAt(c.accentDeep, strength * 0.85f, w * 0.5f, -mid * 0.2f, w * 0.75f)
            ribbon(amber, strength, w * 0.2f, mid * 0.18f, w * 0.7f, -14f)
            ribbon(rose, strength * 0.9f, w * 0.85f, mid * 0.32f, w * 0.65f, -22f)
            ribbon(c.accent, strength * 0.9f, w * 0.45f, mid * 0.55f, w * 0.75f, -9f)
            ribbon(violet, strength * 0.35f, w * 0.95f, mid * 0.8f, w * 0.5f, -18f)
            // The sides carry the top's own oranges down, so they read as the same aurora.
            for (x in listOf(0f, w)) {
                val tilt = if (x == 0f) -70f else 70f
                ribbon(c.accentDeep, strength * 0.65f, x, mid * 0.75f, w * 0.24f, tilt)
                ribbon(c.accent, strength * 0.55f, x, (mid + sides) / 2f, w * 0.2f, tilt)
                glowAt(amber, strength * 0.35f, x, mid * 0.6f, w * 0.17f)
            }
            // Its shape: the middle fades out under one anchor, the sides beside the other.
            drawIntoCanvas { it.saveLayer(area, Paint().apply { blendMode = BlendMode.DstIn }) }
            drawRect(
                Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black.copy(alpha = 0.85f), 0.85f to Color.Black.copy(alpha = 0.35f), 1f to Color.Transparent, endY = mid),
                size = Size(w, mid),
            )
            sideFade(0f, sides)
            sideFade(w, sides)
            drawIntoCanvas { it.restore() }
            drawIntoCanvas { it.restore() }
        }
    }
}
