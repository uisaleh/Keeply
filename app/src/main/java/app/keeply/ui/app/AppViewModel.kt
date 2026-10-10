package app.keeply.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.keeply.AppContainer
import app.keeply.core.model.ItemType
import app.keeply.data.settings.AppSettings
import app.keeply.premium.AddDecision
import app.keeply.premium.EntitlementPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RootState {
    data object Loading : RootState
    data class Ready(val settings: AppSettings) : RootState
}

/** App-wide concerns: settings/theme, the add gate (free limits) and opening items by id. */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<RootState> = container.settings.settings
        .map<AppSettings, RootState> { RootState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootState.Loading)

    /** Null while nobody is signed in; the root then shows the sign-in screen instead of the vault. */
    val account = container.session.account

    val locked = container.appLock.locked
    fun unlocked() = container.appLock.unlocked()

    private val _limit = MutableStateFlow<AddDecision?>(null)
    /** Non-null while the contextual upgrade sheet should be visible. */
    val limit: StateFlow<AddDecision?> = _limit.asStateFlow()

    fun requestAdd(type: ItemType, onAllowed: () -> Unit) {
        viewModelScope.launch {
            when (val d = EntitlementPolicy.canAdd(container.entitlements.tier.value, type, container.vault.counts())) {
                AddDecision.Allowed -> onAllowed()
                else -> _limit.value = d
            }
        }
    }

    fun dismissLimit() { _limit.value = null }

    fun completeOnboarding() {
        viewModelScope.launch { container.settings.completeOnboarding() }
    }
}
