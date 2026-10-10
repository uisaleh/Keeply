package app.keeply.ui.features.auth

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.keeply.ui.components.KeeplyCard
import app.keeply.ui.theme.Radius
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.keeply.AppContainer
import app.keeply.auth.AuthError
import app.keeply.auth.GoogleSignIn
import app.keeply.ui.app.keeplyViewModel
import app.keeply.ui.components.ButtonVariant
import app.keeply.ui.components.KeeplyButton
import app.keeply.ui.components.KeeplyTextField
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SIGN_UP, SIGN_IN }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_UP,
    val busy: Boolean = false,
    val error: String? = null,
    /** Field-level problems, shown under the field once the user has tried to submit. */
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
)

class AuthViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun setMode(mode: AuthMode) = _state.update { AuthUiState(mode = mode) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun submit(name: String, email: String, password: String) {
        val s = _state.value
        val emailError = if (!EMAIL.matches(email.trim())) "Enter a valid email address" else null
        val passwordError = when {
            password.isEmpty() -> "Enter your password"
            s.mode == AuthMode.SIGN_UP && password.length < MIN_PASSWORD -> "Use at least $MIN_PASSWORD characters"
            else -> null
        }
        val nameError = if (s.mode == AuthMode.SIGN_UP && name.isBlank()) "Enter your name" else null
        _state.update { it.copy(error = null, nameError = nameError, emailError = emailError, passwordError = passwordError) }
        if (emailError != null || passwordError != null || nameError != null) return
        run {
            if (s.mode == AuthMode.SIGN_UP) container.auth.signUp(name, email, password)
            else container.auth.signIn(email, password)
        }
    }

    fun google(activityContext: Context) = run {
        val token = GoogleSignIn.requestIdToken(activityContext) ?: return@run
        container.auth.signInWithGoogle(token)
    }

    /** Returns through [onSent] so the dialog can confirm; errors land on the main screen. */
    fun sendReset(email: String, onSent: () -> Unit) = run {
        if (!EMAIL.matches(email.trim())) throw AuthError("Enter a valid email address.")
        container.auth.sendPasswordReset(email)
        onSent()
    }

    // Success needs no handling here: the root switches to the vault when the account appears.
    private fun run(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (e: AuthError) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        /** Shared with adding or changing a password in Profile. */
        const val MIN_PASSWORD = 8
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

/** Shown after onboarding and whenever nobody is signed in. Every vault belongs to an account. */
@Composable
fun AuthScreen() {
    val vm = keeplyViewModel { c, _ -> AuthViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()
    val c = Keeply.colors
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val signUp = s.mode == AuthMode.SIGN_UP

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var resetOpen by rememberSaveable { mutableStateOf(false) }
    var resetSentTo by rememberSaveable { mutableStateOf<String?>(null) }

    val keyboard = LocalSoftwareKeyboardController.current
    // Hide the keyboard so the result (or error) is visible, and don't let focus jump to another field.
    fun submit() { keyboard?.hide(); focus.clearFocus(force = true); vm.submit(name, email, password) }

    Column(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg).padding(top = Space.xl, bottom = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Box(
                Modifier.size(56.dp).clip(Radius.lg).background(Brush.linearGradient(listOf(c.accent, c.accentDeep))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.height(Space.xs))
            Text(
                if (signUp) "Create your account" else "Welcome back",
                style = Keeply.type.displayLg, color = c.text, modifier = Modifier.semantics { heading() },
            )
            Text(
                if (signUp) "Your vault is saved to your account, so it's safe if you lose or change your phone."
                else "Sign in to open your vault.",
                style = Keeply.type.bodyMd, color = c.textMuted,
            )
        }

        KeeplyCard(spacing = Space.md, contentPadding = PaddingValues(Space.md)) {
            s.error?.let {
                Text(
                    it, style = Keeply.type.bodySmMedium, color = c.danger,
                    modifier = Modifier.fillMaxWidth().clip(Radius.sm).background(c.dangerSoft).padding(Space.sm)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            KeeplyButton(
                "Continue with Google", { vm.google(context) }, Modifier.fillMaxWidth(),
                variant = ButtonVariant.Outline, enabled = !s.busy, leadingIcon = Icons.Outlined.AccountCircle,
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                HorizontalDivider(Modifier.weight(1f), color = c.border)
                Text("or with email", style = Keeply.type.caption, color = c.textMuted)
                HorizontalDivider(Modifier.weight(1f), color = c.border)
            }

            if (signUp) {
                KeeplyTextField("Name", name, { name = it }, placeholder = "Your name", error = s.nameError)
            }
            KeeplyTextField(
                "Email", email, { email = it.trim() }, placeholder = "you@example.com",
                error = s.emailError, keyboardType = KeyboardType.Email,
            )
            KeeplyTextField(
                "Password", password, { password = it },
                error = s.passwordError,
                hint = if (signUp) "At least 8 characters" else null,
                keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { submit() }),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            tint = c.textMuted,
                        )
                    }
                },
            )
            if (!signUp) {
                TextButton(onClick = { resetOpen = true }, modifier = Modifier.align(Alignment.End)) {
                    Text("Forgot password?", style = Keeply.type.bodySmMedium, color = c.link)
                }
            }

            KeeplyButton(
                when {
                    s.busy -> "Please wait…"
                    signUp -> "Create account"
                    else -> "Sign in"
                },
                ::submit, Modifier.fillMaxWidth(), arrow = !s.busy, enabled = !s.busy,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(if (signUp) "Already have an account?" else "New to Keeply?", style = Keeply.type.bodySm, color = c.textMuted)
            TextButton(onClick = { vm.setMode(if (signUp) AuthMode.SIGN_IN else AuthMode.SIGN_UP); password = "" }) {
                Text(if (signUp) "Sign in" else "Create an account", style = Keeply.type.bodySmMedium, color = c.link)
            }
        }
    }

    if (resetOpen) {
        ResetPasswordDialog(
            initialEmail = email,
            sentTo = resetSentTo,
            busy = s.busy,
            error = s.error,
            onSend = { address -> vm.sendReset(address) { resetSentTo = address.trim() } },
            onDismiss = { resetOpen = false; resetSentTo = null; vm.clearError() },
        )
    }
}

@Composable
private fun ResetPasswordDialog(initialEmail: String, sentTo: String?, busy: Boolean, error: String?, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var address by rememberSaveable { mutableStateOf(initialEmail) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Keeply.colors.surfaceRaised,
        shape = Radius.xxl,
        title = { Text("Reset your password", style = Keeply.type.heading3) },
        text = {
            if (sentTo != null) {
                Text(
                    "If an account exists for $sentTo, we've sent a link to reset the password. Check your inbox and spam folder.",
                    style = Keeply.type.bodyMd, color = Keeply.colors.textMuted,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    Text("We'll email you a link to choose a new password.", style = Keeply.type.bodyMd, color = Keeply.colors.textMuted)
                    KeeplyTextField("Email", address, { address = it.trim() }, error = error, keyboardType = KeyboardType.Email, imeAction = ImeAction.Done)
                }
            }
        },
        confirmButton = {
            if (sentTo != null) {
                TextButton(onClick = onDismiss) { Text("Done", style = Keeply.type.button, color = Keeply.colors.link) }
            } else {
                TextButton(onClick = { onSend(address) }, enabled = !busy) { Text("Send link", style = Keeply.type.button, color = Keeply.colors.link) }
            }
        },
        dismissButton = {
            if (sentTo == null) TextButton(onClick = onDismiss) { Text("Cancel", style = Keeply.type.button, color = Keeply.colors.textMuted) }
        },
    )
}
