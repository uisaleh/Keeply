package app.keeply.ui.features.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.keeply.core.model.VaultItemSummary
import app.keeply.ui.components.ItemRow
import app.keeply.ui.components.IconTile
import app.keeply.ui.components.LogoTile
import app.keeply.ui.components.SiteIconTile
import app.keeply.core.model.ItemType
import app.keeply.ui.components.KeeplyTag
import app.keeply.ui.components.TagTone
import app.keeply.ui.components.icon
import app.keeply.ui.components.tint
import app.keeply.ui.theme.Keeply

/** Standard row for any vault item in lists, search and the dashboard. */
@Composable
fun SummaryRow(item: VaultItemSummary, onOpen: (String) -> Unit, modifier: Modifier = Modifier, badge: (@Composable () -> Unit)? = null) {
    ItemRow(
        icon = item.type.icon,
        title = item.title,
        // The coloured icon shows the type; TalkBack gets it spoken instead.
        subtitle = item.subtitle,
        onClick = { onOpen(item.id) },
        modifier = modifier.semantics { stateDescription = item.type.label },
        tint = item.type.tint,
        showChevron = true,
        leading = { ItemIcon(item) },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                badge?.invoke()
                if (item.isDemo) KeeplyTag("Demo", TagTone.Neutral)
                if (item.isFavorite) Icon(Icons.Rounded.Star, contentDescription = "Favourite", tint = Keeply.colors.warning, modifier = Modifier.size(18.dp))
            }
        },
    )
}
