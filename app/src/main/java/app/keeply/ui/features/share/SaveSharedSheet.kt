package app.keeply.ui.features.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.keeply.core.model.ItemType
import app.keeply.share.ShareSuggestion
import app.keeply.ui.components.ItemRow
import app.keeply.ui.components.KeeplyCard
import app.keeply.ui.components.KeeplyTag
import app.keeply.ui.components.TagTone
import app.keeply.ui.components.icon
import app.keeply.ui.components.tint
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space

/** "Save to Keeply": what was shared, Keeply's best guess first, and every other way to keep it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSharedSheet(suggestion: ShareSuggestion, onDismiss: () -> Unit, onPick: (ItemType) -> Unit) {
    val c = Keeply.colors
    val hints = mapOf(
        ItemType.LINK to "Keep the page with notes and tags",
        ItemType.RECEIPT to "Merchant and amount filled in for you",
        ItemType.SUBSCRIPTION to "Track the payment and get reminded",
        ItemType.REMINDER to "Get an alert at a date and time",
        ItemType.DOCUMENT to "Keep the details with an expiry reminder",
        ItemType.WARRANTY to "Know when cover runs out",
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceRaised,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.padding(bottom = Space.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text("Save to Keeply", style = Keeply.type.heading3, color = c.text, modifier = Modifier.padding(horizontal = Space.lg))
            KeeplyCard(Modifier.padding(horizontal = Space.lg), spacing = 2.dp) {
                Text(suggestion.text, style = Keeply.type.bodySm, color = c.textMuted, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            suggestion.types.forEachIndexed { i, type ->
                ItemRow(
                    type.icon, "Save as ${type.label.lowercase()}", hints[type].orEmpty(),
                    onClick = { onPick(type) }, tint = type.tint, showChevron = true,
                    modifier = Modifier.padding(horizontal = Space.xs),
                    trailing = if (i == 0) ({ KeeplyTag("Suggested", TagTone.Cream) }) else null,
                )
            }
        }
    }
}
