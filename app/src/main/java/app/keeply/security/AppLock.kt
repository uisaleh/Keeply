package app.keeply.security

import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the vault is behind the lock right now. Starts locked, so a cold start always asks;
 * after that it locks again when Keeply has been in the background longer than the timeout.
 * Uses fingerprint/face where enrolled, otherwise the phone's own PIN, pattern or password.
 */
class AppLock {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundedAt: Long? = null

    fun onBackground() { backgroundedAt = SystemClock.elapsedRealtime() }

    fun onForeground(lockAfterSeconds: Int) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (SystemClock.elapsedRealtime() - since >= lockAfterSeconds * 1000L) _locked.value = true
    }

    fun unlocked() { _locked.value = false }

    companion object {
        private val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

        /** False when the phone has no screen lock at all, so there's nothing to check against. */
        fun canUse(activity: FragmentActivity): Boolean =
            if (Build.VERSION.SDK_INT >= 30) BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
            else activity.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure

        /** Shows the system unlock sheet; [onResult] gets true only on success. */
        fun authenticate(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Use your fingerprint, face or screen lock")
                .apply {
                    if (Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(AUTHENTICATORS)
                    else @Suppress("DEPRECATION") setDeviceCredentialAllowed(true)
                }
                .build()
            prompt.authenticate(info)
        }
    }
}
