package app.keeply.ui.features.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.keeply.core.domain.UrlParser
import app.keeply.core.model.DocumentKind
import app.keeply.core.model.ItemType
import app.keeply.core.model.VaultItemSummary
import app.keeply.ui.components.IconTile
import app.keeply.ui.components.LogoTile
import app.keeply.ui.components.SiteIconTile
import app.keeply.ui.components.iconFor
import app.keeply.ui.components.tint

/**
 * An item's picture, the same in every list (Vault, Upcoming, Home, Activity, Notifications):
 * its own logo image if it has one, else its website's icon, else the coloured type icon
 * (for documents, the icon of their kind: passport, licence, national ID…).
 */
@Composable
fun ItemIcon(
    type: ItemType, logoId: String?, siteDomain: String?, size: Dp = 44.dp, iconSize: Dp = 22.dp,
    documentKind: DocumentKind? = null,
) {
    val typeIcon: @Composable () -> Unit = { IconTile(iconFor(type, documentKind), type.tint, size = size, iconSize = iconSize) }
    when {
        logoId != null -> LogoTile(logoId, size = size)
        siteDomain != null -> SiteIconTile(siteDomain, fallback = typeIcon, size = size)
        else -> typeIcon()
    }
}

@Composable
fun ItemIcon(item: VaultItemSummary, size: Dp = 44.dp, iconSize: Dp = 22.dp) =
    ItemIcon(item.type, item.logoId, item.site, size, iconSize, item.documentKind)

/** The website whose icon stands for this item: its page link, or for a saved link the link itself. */
val VaultItemSummary.site: String?
    get() = siteDomain ?: subtitle.takeIf { type == ItemType.LINK }

/** Domain of a page link ("https://www.netflix.com/account" -> "netflix.com"), for [ItemIcon]. */
fun siteOf(url: String?): String? = url?.let { UrlParser.parse(it)?.domain }
