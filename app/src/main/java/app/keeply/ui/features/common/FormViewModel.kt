package app.keeply.ui.features.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Save/delete plumbing shared by forms. Errors never leak technical detail to the user. */
abstract class FormViewModel : ViewModel() {
    var saving by mutableStateOf(false)
        protected set
    var errorMessage by mutableStateOf<String?>(null)
        protected set
    var finished by mutableStateOf(false)
        protected set

    /** The form's values right after loading, to tell whether leaving would lose edits. */
    private var baseline by mutableStateOf<String?>(null)

    /** Every user-editable value, joined; compared against [baseline]. */
    protected abstract fun snapshot(): String

    /** Call once the form has finished filling in its starting values. */
    protected fun markBaseline() {
        baseline = snapshot()
    }

    val hasUnsavedChanges: Boolean get() = baseline.let { it != null && it != snapshot() }

    /** Set while an edit is waiting for the user to confirm "Save changes?". */
    var confirmSave by mutableStateOf(false)
        private set
    private var pendingSave: (suspend () -> Unit)? = null

    /**
     * Saves straight away for a new item; for an existing one, asks first so a change is
     * always deliberate (the form shows the dialog while [confirmSave] is true).
     */
    protected fun commit(editing: Boolean, block: suspend () -> Unit) {
        if (!editing) return runSave(block)
        pendingSave = block
        confirmSave = true
    }

    fun confirmPendingSave() {
        val block = pendingSave ?: return
        pendingSave = null
        confirmSave = false
        runSave(block)
    }

    fun dismissPendingSave() {
        pendingSave = null
        confirmSave = false
    }

    protected fun runSave(block: suspend () -> Unit) {
        if (saving) return
        saving = true
        errorMessage = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                // Do not log the exception message: it may contain user content.
                errorMessage = SAVE_ERROR
            } finally {
                saving = false
            }
        }
    }
}
