package app.keeply.ui.features.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import app.keeply.ui.theme.Radius
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.keeply.ui.components.ButtonVariant
import app.keeply.ui.components.ConfirmDialog
import app.keeply.ui.components.KeeplyButton
import app.keeply.ui.components.FormErrorScroller
import app.keeply.ui.components.KeeplyCard
import app.keeply.ui.components.LocalFormErrorScroller
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space

const val SAVE_ERROR = "Something went wrong while saving this item. Your information hasn't been lost."

/**
 * Shared shell for every create/edit form: back, bold title, fields on a white card,
 * a Save button pinned above the keyboard. Edit mode adds Delete behind a confirmation.
 */
@Composable
fun FormScaffold(
    eyebrow: String,
    title: String,
    isEditing: Boolean,
    saving: Boolean,
    errorMessage: String?,
    hasUnsavedChanges: Boolean = false,
    confirmSave: Boolean = false,
    onConfirmSave: () -> Unit = {},
    onDismissSave: () -> Unit = {},
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    deleteWhat: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val errorScroller = remember { FormErrorScroller() }
    val c = Keeply.colors
    // Leaving with edits asks first, so a stray back gesture never throws work away.
    val leave = { if (hasUnsavedChanges && !saving) confirmDiscard = true else onBack() }
    BackHandler(onBack = leave)

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Surface(shape = Radius.pill, color = c.surfaceRaised, onClick = leave, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = c.text) }
            }
            Text(eyebrow, style = Keeply.type.bodyMdMedium, color = c.textMuted)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.lg).padding(bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(title, style = Keeply.type.displayLg, color = c.text, modifier = Modifier.padding(top = Space.xxs, bottom = Space.xxs).semantics { heading() })
            CompositionLocalProvider(LocalFormErrorScroller provides errorScroller) {
                KeeplyCard(spacing = Space.md, contentPadding = PaddingValues(Space.md)) {
                    content()
                }
            }
            if (isEditing && onDelete != null) {
                KeeplyButton("Delete $deleteWhat", { confirmDelete = true }, Modifier.fillMaxWidth(), variant = ButtonVariant.Danger, leadingIcon = Icons.Outlined.Delete)
            }
        }
        Column(
            Modifier.fillMaxWidth().background(c.surfaceRaised).padding(horizontal = Space.lg, vertical = Space.md).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            if (errorMessage != null) {
                Text(
                    errorMessage, style = Keeply.type.bodySm, color = c.danger,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            KeeplyButton(
                when {
                    saving -> "Saving…"
                    isEditing -> "Save changes"
                    else -> "Save $deleteWhat"
                },
                { errorScroller.onSaveAttempt(); onSave() }, Modifier.fillMaxWidth(), enabled = !saving,
            )
        }
    }

    if (confirmSave) {
        ConfirmDialog(
            title = "Save changes?",
            body = "Your edits will replace the current details of \"$title\", on this phone and in your account.",
            confirmLabel = "Save changes",
            destructive = false,
            onConfirm = onConfirmSave,
            onDismiss = onDismissSave,
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            body = "You have changes that haven't been saved. If you leave now, they'll be lost.",
            confirmLabel = "Discard",
            onConfirm = { confirmDiscard = false; onBack() },
            onDismiss = { confirmDiscard = false },
        )
    }

    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            title = "Delete this $deleteWhat?",
            body = "It will be removed from your vault along with its reminders. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun FieldGroupLabel(text: String) {
    Text(text, style = Keeply.type.captionBold, color = Keeply.colors.textMuted)
}
