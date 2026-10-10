package app.keeply.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Radius
import app.keeply.ui.theme.Space
import java.util.Currency
import java.util.Locale

private data class CurrencyOption(val code: String, val name: String)

/** Shown first: the most used currencies, plus the one for the phone's own region. */
private val POPULAR = listOf("USD", "EUR", "GBP", "BDT", "INR", "PKR", "AED", "SAR", "CAD", "AUD", "JPY", "CNY", "SGD", "MYR")

private val ALL: List<CurrencyOption> by lazy {
    Currency.getAvailableCurrencies()
        .map { CurrencyOption(it.currencyCode, it.getDisplayName(Locale.getDefault())) }
        .sortedBy { it.code }
}

/**
 * Currency chooser that looks like a text field. Opens a searchable list (by code or name),
 * so nobody has to know ISO codes by heart and an invalid code can't be entered.
 */
@Composable
fun CurrencyField(label: String, code: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, hint: String? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    val c = Keeply.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = Keeply.type.captionBold, color = c.textMuted)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).background(c.fieldFill, Radius.md)
                .clickable(onClickLabel = "Choose $label", role = Role.Button) { open = true }
                .padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(code.ifBlank { "Choose" }, style = Keeply.type.bodyMd, color = if (code.isBlank()) c.textPlaceholder else c.text, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = c.textMuted)
        }
        if (hint != null) Text(hint, style = Keeply.type.caption, color = c.textMuted)
    }
    if (open) CurrencySheet(code, onDismiss = { open = false }, onPick = { onSelect(it); open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencySheet(selected: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = Keeply.colors
    var query by rememberSaveable { mutableStateOf("") }
    val regional = remember { runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() }
    val popularCodes = remember(regional) { (listOfNotNull(regional) + POPULAR).distinct() }
    val results = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            popularCodes.mapNotNull { code -> ALL.firstOrNull { it.code == code } } + ALL.filterNot { it.code in popularCodes }
        } else {
            ALL.filter { it.code.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true) }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceRaised,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxHeight(0.85f).imePadding()) {
            Text("Choose currency", style = Keeply.type.heading3, color = c.text, modifier = Modifier.padding(horizontal = Space.lg))
            TextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
                placeholder = { Text("Search, e.g. taka or BDT", style = Keeply.type.bodyMd, color = c.textPlaceholder) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = c.textMuted) },
                singleLine = true, shape = Radius.pill, textStyle = Keeply.type.bodyMd.copy(color = c.text),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = c.fieldFill, unfocusedContainerColor = c.fieldFill,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = c.accent,
                ),
            )
            LazyColumn(Modifier.fillMaxWidth()) {
                items(results, key = { it.code }) { option ->
                    val isSelected = option.code == selected
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(option.code) }
                            .padding(horizontal = Space.lg, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        Box(Modifier.width(52.dp)) { Text(option.code, style = Keeply.type.bodyMdMedium, color = c.text) }
                        Text(option.name, style = Keeply.type.bodyMd, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (isSelected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = c.accent)
                    }
                }
            }
        }
    }
}
