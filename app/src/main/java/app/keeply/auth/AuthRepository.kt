package app.keeply.auth

import android.net.Uri
import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.io.File

/** The signed-in person, as the UI needs them. */
data class Account(
    val uid: String,
    val email: String?,
    val displayName: String?,
    /** Download URL of the profile photo (uploaded here, or the Google account picture). */
    val photoUrl: String?,
    /** "password" or "google.com"; decides how to re-confirm identity before deleting the account. */
    val provider: String,
    /** Can sign in with email and password (set at sign-up, or added later in Profile). */
    val hasPassword: Boolean = provider == "password",
    val hasGoogle: Boolean = provider == "google.com",
)

/** A failure with a message that can be shown to the user as-is. */
class AuthError(message: String) : Exception(message)

/** Deleting the account needs a fresh sign-in; ask for the password or run Google sign-in again. */
class ReauthRequired : Exception("Please confirm it's you to continue.")

class AuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    private val _account = MutableStateFlow(auth.currentUser?.toAccount())
    /** Known synchronously at launch (Firebase restores the session from disk), so there is no loading state. */
    val account: StateFlow<Account?> = _account.asStateFlow()

    init {
        auth.addAuthStateListener { _account.value = it.currentUser?.toAccount() }
    }

    suspend fun signUp(name: String, email: String, password: String) = guard {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: throw AuthError("Couldn't create your account. Please try again.")
        if (name.isNotBlank()) {
            user.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
            _account.value = user.toAccount() // the auth listener doesn't fire for profile changes
        }
    }

    suspend fun signIn(email: String, password: String) = guard {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signInWithGoogle(idToken: String) = guard {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    suspend fun sendPasswordReset(email: String) = guard {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() = auth.signOut()

    /** Uploads [jpeg] as the profile photo (or removes it when null) and updates the account. */
    suspend fun setProfilePhoto(jpeg: File?) = guard {
        val user = auth.currentUser ?: throw AuthError("You're signed out.")
        val ref = FirebaseStorage.getInstance().reference.child("users/${user.uid}/profile.jpg")
        val url: Uri? = if (jpeg != null) {
            ref.putFile(Uri.fromFile(jpeg), storageMetadata { contentType = "image/jpeg" }).await()
            ref.downloadUrl.await()
        } else {
            runCatching { ref.delete().await() } // fine if there was none
            null
        }
        user.updateProfile(userProfileChangeRequest { photoUri = url }).await()
        _account.value = user.toAccount()
    }

    /**
     * Adds email-and-password sign-in to this account (e.g. one made with Google), so either
     * works and both open the same vault. Throws [ReauthRequired] if Firebase wants a fresh sign-in.
     */
    suspend fun addPassword(password: String) = guard {
        val user = auth.currentUser ?: throw AuthError("You're signed out.")
        val email = user.email ?: throw AuthError("Your account has no email address to sign in with.")
        user.linkWithCredential(EmailAuthProvider.getCredential(email, password)).await()
        _account.value = user.toAccount() // the auth listener doesn't fire for a new sign-in method
    }

    /** Checks [current] first, so someone holding an unlocked phone can't change it. */
    suspend fun changePassword(current: String, new: String) = guard {
        val user = auth.currentUser ?: throw AuthError("You're signed out.")
        val email = user.email ?: throw AuthError("Your account has no email address.")
        try {
            user.reauthenticate(EmailAuthProvider.getCredential(email, current)).await()
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw AuthError("Your current password is incorrect.")
        }
        user.updatePassword(new).await()
    }

    fun passwordCredential(password: String): AuthCredential =
        EmailAuthProvider.getCredential(auth.currentUser?.email.orEmpty(), password)

    fun googleCredential(idToken: String): AuthCredential = GoogleAuthProvider.getCredential(idToken, null)

    /**
     * Confirms the user can delete the account. Firebase only deletes accounts that signed in
     * within the last few minutes, so check that before any data is removed.
     */
    suspend fun ensureRecentLogin(credential: AuthCredential?) = guard {
        val user = auth.currentUser ?: throw AuthError("You're signed out.")
        if (credential != null) {
            user.reauthenticate(credential).await()
        } else {
            val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0L
            if (System.currentTimeMillis() - lastSignIn > RECENT_LOGIN_MS) throw ReauthRequired()
        }
    }

    suspend fun deleteAccount() = guard {
        val user = auth.currentUser ?: throw AuthError("You're signed out.")
        try {
            user.delete().await()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            throw ReauthRequired()
        }
    }

    /** Runs a Firebase call and turns its exceptions into messages people can act on. */
    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: ReauthRequired) {
        throw e
    } catch (e: AuthError) {
        throw e
    } catch (e: FirebaseAuthRecentLoginRequiredException) {
        throw ReauthRequired()
    } catch (e: FirebaseNetworkException) {
        throw AuthError("No internet connection. Check your connection and try again.")
    } catch (e: FirebaseTooManyRequestsException) {
        throw AuthError("Too many attempts. Please wait a few minutes and try again.")
    } catch (e: FirebaseAuthException) {
        throw AuthError(messageFor(e.errorCode))
    } catch (e: StorageException) {
        Log.w("KeeplyAuth", "Storage call failed", e)
        throw AuthError(
            when (e.errorCode) {
                StorageException.ERROR_BUCKET_NOT_FOUND, StorageException.ERROR_PROJECT_NOT_FOUND ->
                    "Photo storage isn't set up yet. In Firebase, open Storage and click Get started."
                StorageException.ERROR_NOT_AUTHORIZED -> "Photo upload was refused. Check the Storage rules in Firebase."
                StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> "No internet connection. Check your connection and try again."
                else -> "Couldn't upload the photo. Please try again."
            }
        )
    } catch (e: FirebaseException) {
        // Anything else (misconfigured project, server error) must not crash the app.
        Log.w("KeeplyAuth", "Firebase call failed", e)
        val msg = e.message.orEmpty()
        throw AuthError(
            when {
                // Authentication was never switched on for this Firebase project.
                "CONFIGURATION_NOT_FOUND" in msg -> "Sign-in isn't enabled for this app yet. In Firebase, open Authentication, click Get started and enable Email/Password."
                "API key not valid" in msg -> "This build has an invalid Firebase key. Replace app/google-services.json with the one from your Firebase project."
                else -> "Couldn't reach Keeply's servers. Please try again in a moment."
            }
        )
    }

    private fun messageFor(code: String) = when (code) {
        "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> "Email or password is incorrect."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Sign in instead."
        "ERROR_WEAK_PASSWORD" -> "Choose a stronger password (at least 8 characters)."
        "ERROR_USER_DISABLED" -> "This account has been disabled."
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> "This email is already registered with a different sign-in method."
        "ERROR_OPERATION_NOT_ALLOWED" -> "This sign-in method isn't enabled yet."
        "ERROR_PROVIDER_ALREADY_LINKED" -> "Your account already has a password."
        "ERROR_CREDENTIAL_ALREADY_IN_USE" -> "This email and password already belong to another account."
        else -> "Something went wrong ($code). Please try again."
    }

    private companion object {
        const val RECENT_LOGIN_MS = 4 * 60 * 1000L
    }
}

private fun com.google.firebase.auth.FirebaseUser.toAccount() = Account(
    uid = uid,
    email = email,
    displayName = displayName?.takeIf { it.isNotBlank() },
    photoUrl = photoUrl?.toString(),
    provider = providerData.map { it.providerId }.firstOrNull { it != "firebase" } ?: "password",
    hasPassword = providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID },
    hasGoogle = providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID },
)
