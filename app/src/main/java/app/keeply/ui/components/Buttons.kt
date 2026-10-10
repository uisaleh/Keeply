package app.keeply.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Motion
import app.keeply.ui.theme.Radius

enum class ButtonVariant { Primary, Dark, Outline, Ghost, Danger, Soft }
enum class ButtonSize(val height: Int, val hPad: Int) { Sm(36, 14), Md(44, 18), Lg(54, 22) }

/**
 * 14dp rounded button. One Primary (orange) per view; Dark or Soft for the rest.
 * `arrow` adds a → that nudges 3dp right while pressed.
 */
@Composable
fun KeeplyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Lg,
    arrow: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val c = Keeply.colors
    val (container, content) = when (variant) {
        ButtonVariant.Primary -> c.accent to c.onAccent
        ButtonVariant.Dark -> c.btnDark to c.onBtnDark
        ButtonVariant.Outline -> Color.Transparent to c.text
        ButtonVariant.Ghost -> Color.Transparent to c.text
        ButtonVariant.Danger -> c.dangerSoft to c.danger
        ButtonVariant.Soft -> c.selected to c.link
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val nudge by animateDpAsState(if (pressed) 3.dp else 0.dp, tween(Motion.BASE), label = "arrow")

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = size.height.dp),
        shape = Radius.md,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = if (container == Color.Transparent) Color.Transparent else c.border,
            disabledContentColor = c.textPlaceholder,
        ),
        border = when (variant) {
            ButtonVariant.Outline -> BorderStroke(1.dp, c.borderStrong)
            else -> null
        },
        contentPadding = PaddingValues(horizontal = size.hPad.dp, vertical = 0.dp),
        elevation = null,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = Keeply.type.button)
            if (arrow) Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp).offset(x = nudge))
        }
    }
}
