package app.keeply.ui.features.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import app.keeply.core.domain.UrlParser
import app.keeply.ui.components.IconTile
import app.keeply.ui.components.KeeplyTextField
import app.keeply.ui.components.SiteIconTile
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** "Not a web address" when something was typed that can't be opened; empty is fine (it's optional). */
fun pageUrlError(value: String): String? =
    if (value.isNotBlank() && UrlParser.parse(value) == null) "That doesn't look like a web address" else null

/** The cleaned-up link to save, or null when the field is empty or invalid. */
fun pageUrlToSave(value: String): String? = UrlParser.parse(value)?.url

/**
 * Optional web page for any item. Shows the site's icon as soon as it's recognised, so people
 * see what the item will look like in their lists.
 */
@Composable
fun PageLinkField(value: String, onChange: (String) -> Unit, hint: String) {
    val domain = UrlParser.parse(value)?.domain
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Column(Modifier.weight(1f)) {
            KeeplyTextField(
                "Website or link", value, { onChange(it.trim()) },
                placeholder = "Optional", keyboardType = KeyboardType.Uri,
                error = pageUrlError(value), hint = hint,
            )
        }
        if (domain != null) {
            SiteIconTile(domain, fallback = { IconTile(Icons.Outlined.Link, Keeply.colors.tintBlue) }, size = 56.dp)
        }
    }
}
