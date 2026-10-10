package app.keeply.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.keeply.AppContainer
import app.keeply.KeeplyApp

/** Creates a ViewModel scoped to the current nav entry, with the app container and saved state. */
@Composable
inline fun <reified VM : ViewModel> keeplyViewModel(crossinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val app = LocalContext.current.applicationContext as KeeplyApp
    return viewModel(factory = viewModelFactory { initializer { create(app.container, createSavedStateHandle()) } })
}
