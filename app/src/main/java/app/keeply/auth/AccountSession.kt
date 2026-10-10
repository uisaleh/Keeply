package app.keeply.auth

import app.keeply.data.repo.RoomVaultRepository
import app.keeply.sync.CloudSync
import com.google.firebase.auth.AuthCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Ties the signed-in account to the local vault: whoever is signed in is the only person
 * whose data is on this device. Sign-in starts sync; sign-out uploads what it can, then
 * clears the device so the next account starts empty.
 */
class AccountSession(
    private val auth: AuthRepository,
    private val vault: RoomVaultRepository,
    private val sync: CloudSync,
    private val scope: CoroutineScope,
) {
    val account get() = auth.account

    fun start() {
        scope.launch {
            var previous: Account? = auth.account.value
            previous?.let { onSignedIn(it, justSignedIn = false) }
            auth.account.collect { current ->
                if (current != null && current.uid != previous?.uid) onSignedIn(current, justSignedIn = true)
                if (current == null) sync.stop()
                previous = current
            }
        }
    }

    private suspend fun onSignedIn(account: Account, justSignedIn: Boolean) {
        // Data saved before this device had an account belongs to whoever signs in first.
        if (justSignedIn) sync.adoptLocalItems(account.uid)
        sync.start(account.uid)
    }

    /**
     * Returns false without signing out if some changes haven't reached the cloud yet
     * (usually because the phone is offline); pass `force = true` to sign out anyway.
     */
    suspend fun signOut(force: Boolean = false): Boolean {
        val uid = auth.account.value?.uid ?: return true
        if (!force && !sync.flush(uid)) return false
        sync.stop()
        auth.signOut()
        clearDevice(uid)
        return true
    }

    /**
     * Permanently deletes the account and its cloud data. Throws [ReauthRequired] when the
     * user must confirm their identity first; call again with the resulting credential.
     */
    suspend fun deleteAccount(credential: AuthCredential?) {
        val uid = auth.account.value?.uid ?: return
        auth.ensureRecentLogin(credential)
        // Stop listening first, or the cloud deletions would be mirrored into the local vault mid-way.
        sync.stop()
        try {
            sync.deleteCloudData(uid)
            auth.deleteAccount()
        } catch (e: Exception) {
            // The account still exists; put back whatever we managed to delete from the cloud.
            sync.adoptLocalItems(uid)
            sync.start(uid)
            throw e as? AuthError ?: e as? ReauthRequired ?: AuthError("Couldn't delete your account. Check your connection and try again.")
        }
        clearDevice(uid)
    }

    private suspend fun clearDevice(uid: String) {
        sync.forget(uid)
        vault.clearLocal()
        sync.clearCache()
    }
}
