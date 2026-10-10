package app.keeply.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.keeply.R

/**
 * Headings: Plus Jakarta Sans 600–800, a geometric sans with tight tracking.
 * UI/body: Inter 400/500/600. Both bundled under OFL (see /licenses).
 */
val DisplaySans = FontFamily(
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

@Immutable
data class KeeplyType(
    val hero: TextStyle,
    val displayLg: TextStyle,
    val heading1: TextStyle,
    val stat: TextStyle,
    val heading3: TextStyle,
    val heading4: TextStyle,
    val heading5: TextStyle,
    val subtitle: TextStyle,
    val bodyMd: TextStyle,
    val bodyMdMedium: TextStyle,
    val bodySm: TextStyle,
    val bodySmMedium: TextStyle,
    val button: TextStyle,
    val caption: TextStyle,
    val captionBold: TextStyle,
    val micro: TextStyle,
    val microUpper: TextStyle,
)

val KeeplyTypeScale = KeeplyType(
    hero = TextStyle(fontFamily = DisplaySans, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.03).em),
    displayLg = TextStyle(fontFamily = DisplaySans, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.025).em),
    heading1 = TextStyle(fontFamily = DisplaySans, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.02).em),
    stat = TextStyle(fontFamily = DisplaySans, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.03).em),
    heading3 = TextStyle(fontFamily = DisplaySans, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.015).em),
    heading4 = TextStyle(fontFamily = DisplaySans, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-0.01).em),
    heading5 = TextStyle(fontFamily = DisplaySans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.005).em),
    subtitle = TextStyle(fontFamily = InterFamily, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMd = TextStyle(fontFamily = InterFamily, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMdMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    bodySm = TextStyle(fontFamily = InterFamily, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    button = TextStyle(fontFamily = DisplaySans, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    caption = TextStyle(fontFamily = InterFamily, fontSize = 13.sp, lineHeight = 18.sp),
    captionBold = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    micro = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    microUpper = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.06.em),
)

/** Material components (dialogs, text fields, snackbars) pick these up. */
val KeeplyMaterialTypography = Typography(
    displayLarge = KeeplyTypeScale.hero,
    displayMedium = KeeplyTypeScale.displayLg,
    displaySmall = KeeplyTypeScale.heading1,
    headlineLarge = KeeplyTypeScale.heading1,
    headlineMedium = KeeplyTypeScale.heading3,
    headlineSmall = KeeplyTypeScale.heading4,
    titleLarge = KeeplyTypeScale.heading4,
    titleMedium = KeeplyTypeScale.heading5,
    titleSmall = KeeplyTypeScale.bodySmMedium,
    bodyLarge = KeeplyTypeScale.bodyMd,
    bodyMedium = KeeplyTypeScale.bodySm,
    bodySmall = KeeplyTypeScale.caption,
    labelLarge = KeeplyTypeScale.button,
    labelMedium = KeeplyTypeScale.micro,
    labelSmall = KeeplyTypeScale.microUpper,
)
