package app.keeply.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.keeply.core.model.DocumentKind
import app.keeply.core.model.ItemType
import app.keeply.ui.theme.Keeply

/** Outlined line icons only (no filled or duotone icons, no emoji). */
val ItemType.icon: ImageVector
    get() = when (this) {
        ItemType.RECEIPT -> Icons.AutoMirrored.Outlined.ReceiptLong
        ItemType.WARRANTY -> Icons.Outlined.VerifiedUser
        ItemType.LINK -> Icons.Outlined.Link
        ItemType.SUBSCRIPTION -> Icons.Outlined.Autorenew
        ItemType.DOCUMENT -> Icons.Outlined.Badge
        ItemType.REMINDER -> Icons.Outlined.Alarm
        ItemType.SCREENSHOT -> Icons.Outlined.PhotoLibrary
    }

/** Documents keep the document colour but show what they are, so a passport doesn't look like a licence. */
val DocumentKind.icon: ImageVector
    get() = when (this) {
        DocumentKind.NATIONAL_ID -> Icons.Outlined.Badge
        DocumentKind.PASSPORT -> Icons.Outlined.Flight
        DocumentKind.DRIVING_LICENCE -> Icons.Outlined.DirectionsCar
        DocumentKind.VISA -> Icons.Outlined.Approval
        DocumentKind.INSURANCE -> Icons.Outlined.HealthAndSafety
        DocumentKind.BIRTH_CERTIFICATE -> Icons.Outlined.ChildCare
        DocumentKind.OTHER -> Icons.Outlined.Description
    }

/** The type icon, or for a document the icon of its kind. */
fun iconFor(type: ItemType, documentKind: DocumentKind?): ImageVector =
    documentKind?.takeIf { type == ItemType.DOCUMENT }?.icon ?: type.icon

/** Each type keeps one colour everywhere, so lists can be scanned by colour as well as label. */
val ItemType.tint: Pair<Color, Color>
    @Composable get() = when (this) {
        ItemType.RECEIPT -> Keeply.colors.tintOrange
        ItemType.WARRANTY -> Keeply.colors.tintGreen
        ItemType.SUBSCRIPTION -> Keeply.colors.tintViolet
        ItemType.LINK -> Keeply.colors.tintBlue
        ItemType.DOCUMENT -> Keeply.colors.tintTeal
        ItemType.REMINDER -> Keeply.colors.tintPink
        ItemType.SCREENSHOT -> Keeply.colors.tintSlate
    }
