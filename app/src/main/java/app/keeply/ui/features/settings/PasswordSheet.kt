package app.keeply.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.keeply.ui.components.KeeplyButton
import app.keeply.ui.components.KeeplyTextField
import app.keeply.ui.features.auth.AuthViewModel
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space

/**
 * Add a password to an account that signs in with Google (so email and password work too), or
 * change an existing one. [change] asks for the current password first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordSheet(
    change: Boolean, email: String?, busy: Boolean, serverError: String?,
    onSubmit: (current: String, new: String) -> Unit, onDismiss: () -> Unit,
) {
    val c = Keeply.colors
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val min = AuthViewModel.MIN_PASSWORD

    val currentError = if (tried && change && current.isEmpty()) "Enter your current password" else null
    val newError = when {
        !tried -> null
        new.length < min -> "Use at least $min characters"
        change && new == current -> "Choose a password different from your current one"
        else -> null
    }
    val confirmError = if (tried && confirm != new) "Passwords don't match" else null
    fun submit() {
        tried = true
        if ((change && current.isEmpty()) || new.length < min || (change && new == current) || confirm != new) return
        onSubmit(current, new)
    }
    val visual = if (show) VisualTransformation.None else PasswordVisualTransformation()
    val eye: @Composable () -> Unit = {
        IconButton(onClick = { show = !show }) {
            Icon(
                if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = if (show) "Hide passwords" else "Show passwords", tint = c.textMuted,
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceRaised,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.lg).padding(bottom = Space.lg)
                .navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(
                    if (change) "Change password" else "Add a password", style = Keeply.type.heading3, color = c.text,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    if (change) "You'll use the new password next time you sign in with your email."
                    else "Then you can sign in with ${email ?: "your email"} and this password, as well as with Google. Both open the same vault.",
                    style = Keeply.type.bodySm, color = c.textMuted,
                )
            }
            if (change) {
                KeeplyTextField(
                    "Current password", current, { current = it }, error = currentError,
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Next, visualTransformation = visual, trailing = eye,
                )
            }
            KeeplyTextField(
                "New password", new, { new = it }, error = newError, hint = if (newError == null) "At least $min characters" else null,
                keyboardType = KeyboardType.Password, imeAction = ImeAction.Next, visualTransformation = visual,
                trailing = if (change) null else eye,
            )
            KeeplyTextField(
                "Confirm new password", confirm, { confirm = it }, error = confirmError,
                keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, visualTransformation = visual,
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            serverError?.let {
                Text(it, style = Keeply.type.bodySmMedium, color = c.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            KeeplyButton(
                when {
                    busy -> "Please wait…"
                    change -> "Change password"
                    else -> "Add password"
                },
                { if (!busy) submit() }, Modifier.fillMaxWidth(),
            )
        }
    }
}
