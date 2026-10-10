package app.keeply.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Sunset Editorial palette (project/tokens.json). Raw tokens — prefer [KeeplyColors] roles in UI. */
object SunsetTokens {
    val BrandPrimary = Color(0xFFFA520F)
    val BrandPrimaryDeep = Color(0xFFCC3A05)
    val Sunshine300 = Color(0xFFFFD06A)
    val Sunshine500 = Color(0xFFFFB83E)
    val Sunshine700 = Color(0xFFFFA110)
    val Sunshine800 = Color(0xFFFF8105)
    val YellowSaturated = Color(0xFFFFD900)
    val Block5 = Color(0xFFFFE295)
    val Cream = Color(0xFFFFF8E0)
    val CreamLight = Color(0xFFFFFAEB)
    val CreamDeeper = Color(0xFFFFF0C2)
    val BeigeDeep = Color(0xFFE6D5A8)
    val Ink = Color(0xFF1F1F1F)
    val InkTint = Color(0xFF3D3D3D)
    val Charcoal = Color(0xFF2C2C2C)
    val Slate = Color(0xFF4A4A4A)
    val Steel = Color(0xFF6A6A6A)
    val Stone = Color(0xFF8A8A8A)
    val Muted = Color(0xFFA8A8A8)
    val Hairline = Color(0xFFE5E5E5)
    val HairlineSoft = Color(0xFFEDEDED)
    val HairlineStrong = Color(0xFFC7C7C7)
    val White = Color(0xFFFFFFFF)
    val SurfaceCode = Color(0xFF1C1C1E)
}

/** Semantic roles with light/dark values. */
@Immutable
data class KeeplyColors(
    val bg: Color,
    val bgSubtle: Color,
    val surfaceWarm: Color,
    val surfaceWarmBorder: Color,
    /** Cards and sheets: they sit on [bg] and lift by colour, not outlines. */
    val surfaceRaised: Color,
    /** Text-field and search-bar fill. */
    val fieldFill: Color,
    val text: Color,
    val textMuted: Color,
    val textPlaceholder: Color,
    val border: Color,
    val borderStrong: Color,
    val accent: Color,
    val accentDeep: Color,
    val onAccent: Color,
    val link: Color,
    val btnDark: Color,
    val onBtnDark: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val onWarning: Color,
    val danger: Color,
    val dangerSoft: Color,
    val selected: Color,
    /** Per-type icon tiles: (background, foreground). */
    val tintOrange: Pair<Color, Color>,
    val tintGreen: Pair<Color, Color>,
    val tintViolet: Pair<Color, Color>,
    val tintBlue: Pair<Color, Color>,
    val tintTeal: Pair<Color, Color>,
    val tintPink: Pair<Color, Color>,
    val tintSlate: Pair<Color, Color>,
    val isDark: Boolean,
)

val LightKeeplyColors = KeeplyColors(
    bg = Color(0xFFF5F5F2),
    bgSubtle = Color(0xFFEEEDE9),
    surfaceWarm = Color(0xFFFFF1E9),
    surfaceWarmBorder = Color(0xFFFFD8C2),
    surfaceRaised = SunsetTokens.White,
    fieldFill = Color(0xFFF2F1EE),
    text = Color(0xFF16161A),
    textMuted = Color(0xFF6B6B73),
    textPlaceholder = Color(0xFF9C9CA3),
    border = Color(0xFFEAE9E5),
    borderStrong = Color(0xFFD5D4CF),
    accent = Color(0xFFFF5A1F),
    accentDeep = Color(0xFFE5400A),
    onAccent = SunsetTokens.White,
    link = Color(0xFFE04A12),
    btnDark = Color(0xFF16161A),
    onBtnDark = SunsetTokens.White,
    success = Color(0xFF15803D),
    successSoft = Color(0xFFE3F5E8),
    warning = Color(0xFFF59E0B),
    warningSoft = Color(0xFFFFF4DB),
    onWarning = Color(0xFF7A4B00),
    danger = Color(0xFFDC2626),
    dangerSoft = Color(0xFFFDE8E8),
    selected = Color(0xFFFFE6D9),
    tintOrange = Color(0xFFFFEADF) to Color(0xFFE5400A),
    tintGreen = Color(0xFFE2F5E7) to Color(0xFF15803D),
    tintViolet = Color(0xFFEFE9FF) to Color(0xFF6D3FE0),
    tintBlue = Color(0xFFE3EEFF) to Color(0xFF1D5FD6),
    tintTeal = Color(0xFFDDF4F1) to Color(0xFF0F766E),
    tintPink = Color(0xFFFCE7F3) to Color(0xFFBE185D),
    tintSlate = Color(0xFFEDEDF0) to Color(0xFF4B4B55),
    isDark = false,
)

val DarkKeeplyColors = KeeplyColors(
    bg = Color(0xFF0E0E10),
    bgSubtle = Color(0xFF17171A),
    surfaceWarm = Color(0xFF2A1A12),
    surfaceWarmBorder = Color(0xFF4A2A1A),
    surfaceRaised = Color(0xFF1B1B1F),
    fieldFill = Color(0xFF26262B),
    text = Color(0xFFF4F4F5),
    textMuted = Color(0xFFA1A1AA),
    textPlaceholder = Color(0xFF71717A),
    border = Color(0xFF2A2A2F),
    borderStrong = Color(0xFF3F3F46),
    accent = Color(0xFFFF6A33),
    accentDeep = Color(0xFFFF5A1F),
    onAccent = SunsetTokens.White,
    link = Color(0xFFFF8A5C),
    btnDark = Color(0xFFF4F4F5),
    onBtnDark = Color(0xFF16161A),
    success = Color(0xFF4ADE80),
    successSoft = Color(0xFF12301C),
    warning = Color(0xFFFBBF24),
    warningSoft = Color(0xFF33270A),
    onWarning = Color(0xFFFBBF24),
    danger = Color(0xFFF87171),
    dangerSoft = Color(0xFF3A1515),
    selected = Color(0xFF3A2216),
    tintOrange = Color(0xFF3A2216) to Color(0xFFFF8A5C),
    tintGreen = Color(0xFF12301C) to Color(0xFF4ADE80),
    tintViolet = Color(0xFF261D45) to Color(0xFFB39BFF),
    tintBlue = Color(0xFF14264A) to Color(0xFF7FB0FF),
    tintTeal = Color(0xFF103230) to Color(0xFF5EEAD4),
    tintPink = Color(0xFF3A1A2C) to Color(0xFFF9A8D4),
    tintSlate = Color(0xFF26262B) to Color(0xFFC4C4CC),
    isDark = true,
)
