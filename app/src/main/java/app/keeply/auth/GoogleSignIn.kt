package app.keeply.auth

import android.annotation.SuppressLint
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Shows the system "Sign in with Google" sheet and returns a Google ID token for Firebase. */
object GoogleSignIn {

    /** Null when the user dismissed the sheet; that's not an error worth showing. */
    suspend fun requestIdToken(activityContext: Context): String? {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId(activityContext)).build())
            .build()
        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (e: GetCredentialCancellationException) {
            return null
        } catch (e: NoCredentialException) {
            throw AuthError("No Google account found on this device. Add one in system Settings, or use email.")
        } catch (e: GetCredentialException) {
            throw AuthError("Google sign-in failed. Please try again.")
        }
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw AuthError("Google sign-in returned an unexpected response.")
    }

    /**
     * `default_web_client_id` is generated from google-services.json once Google sign-in is
     * enabled in the Firebase console. Looked up by name so a project without it still builds.
     */
    @SuppressLint("DiscouragedApi")
    private fun webClientId(context: Context): String {
        val res = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (res == 0) {
            throw AuthError(
                "Google sign-in isn't set up yet. In Firebase: enable Google under Authentication, add this app's SHA-1 " +
                    "fingerprint in Project settings, then download google-services.json again."
            )
        }
        return context.getString(res)
    }
}
