package app.keeply.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Radius
import app.keeply.ui.theme.Space

/** Every list gets a useful empty state with one clear action. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inventory2,
    tint: Pair<Color, Color> = Keeply.colors.tintOrange,
) {
    KeeplyCard(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = Space.lg, horizontal = Space.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Box(Modifier.size(72.dp).clip(Radius.pill).background(tint.first), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint.second, modifier = Modifier.size(32.dp))
            }
            VSpace(Space.xxs)
            Text(title, style = Keeply.type.heading3, color = Keeply.colors.text, textAlign = TextAlign.Center)
            Text(body, style = Keeply.type.bodyMd, color = Keeply.colors.textMuted, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                VSpace(Space.xs)
                KeeplyButton(actionLabel, onAction, variant = ButtonVariant.Primary, size = ButtonSize.Md)
            }
        }
    }
}

/** Destructive actions always go through an explicit confirmation. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = Keeply.type.heading3) },
        text = { Text(body, style = Keeply.type.bodyMd, color = Keeply.colors.textMuted) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) Keeply.colors.danger else Keeply.colors.link, style = Keeply.type.button)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Keeply.colors.textMuted, style = Keeply.type.button) } },
        containerColor = Keeply.colors.surfaceRaised,
        shape = Radius.xxl,
    )
}
