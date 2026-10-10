package app.keeply.ui.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.keeply.AppContainer
import app.keeply.core.model.VaultItemSummary
import app.keeply.ui.app.keeplyViewModel
import app.keeply.ui.components.GroupedCard
import app.keeply.ui.components.RowDivider
import app.keeply.ui.theme.Radius
import app.keeply.ui.features.common.SummaryRow
import app.keeply.ui.theme.Keeply
import app.keeply.ui.theme.Space
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class SearchViewModel(container: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    val query: StateFlow<String> = handle.getStateFlow("q", "")

    /** Local FTS across every item type — works fully offline. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<VaultItemSummary>> = query
        .debounce(150)
        .flatMapLatest { container.vault.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { handle["q"] = q }
}

@Composable
fun SearchScreen(onBack: () -> Unit, onOpenItem: (String) -> Unit) {
    val vm = keeplyViewModel { c, h -> SearchViewModel(c, h) }
    val q by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val c = Keeply.colors
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.xs, end = Space.lg, top = Space.sm, bottom = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            TextField(
                value = q, onValueChange = vm::setQuery,
                modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Search" },
                placeholder = { Text("Search your vault", style = Keeply.type.bodyMd, color = c.textPlaceholder) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = c.textMuted) },
                trailingIcon = if (q.isNotEmpty()) {
                    { IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = c.textMuted) } }
                } else null,
                singleLine = true,
                textStyle = Keeply.type.bodyMd.copy(color = c.text),
                shape = Radius.pill,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = c.surfaceRaised, unfocusedContainerColor = c.surfaceRaised,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = c.accent,
                ),
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            when {
                q.isBlank() -> item {
                    SearchHint("Search everything", "Find receipts, warranties, subscriptions and links by name, shop, brand or tag. Works offline.")
                }
                results.isEmpty() -> item { SearchHint("No matches", "Nothing in your vault matches \"$q\". Try a shorter word.") }
                else -> {
                    item { Text("${results.size} ${if (results.size == 1) "result" else "results"}", style = Keeply.type.bodySmMedium, color = c.textMuted) }
                    item {
                        GroupedCard {
                            results.forEachIndexed { i, r ->
                                if (i > 0) RowDivider()
                                SummaryRow(r, onOpenItem)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHint(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(top = Space.xxl, start = Space.lg, end = Space.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(title, style = Keeply.type.heading4, color = Keeply.colors.text, textAlign = TextAlign.Center)
        Text(body, style = Keeply.type.bodyMd, color = Keeply.colors.textMuted, textAlign = TextAlign.Center)
    }
}
