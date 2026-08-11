package com.hanifedma.waterline.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.hanifedma.waterline.BuildConfig
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * Whether this build can talk to Firebase at all.
 *
 * Two separate questions, and both have to be yes: was a google-services.json
 * present at build time (so the plugin generated the config resources), and did
 * FirebaseApp actually initialise from them at runtime.
 */
object FirebaseGate {

    fun available(context: Context): Boolean =
        BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty()

    /**
     * The OAuth *web* client id, which is what Google sign-in on Android
     * exchanges for an ID token. Looked up by name rather than as
     * R.string.default_web_client_id, because that resource does not exist in
     * a checkout without google-services.json and a static reference would
     * fail to compile there. res/raw/keep.xml stops the shrinker dropping it.
     */
    fun webClientId(context: Context): String {
        val id = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName,
        )
        return if (id != 0) context.getString(id) else ""
    }
}

/**
 * Google sign-in through Credential Manager — the current API; the old
 * GoogleSignInClient/startActivityForResult flow is deprecated.
 *
 * Every Google account on the device is offered, always. Filtering to
 * already-authorised accounts would make the common case one tap, but it also
 * hides every other account behind no visible affordance — and because the web
 * app shares this OAuth client, an account used there is already "authorised",
 * so the filtered list is usually a list of one with no way past it.
 */
class AuthManager(private val auth: FirebaseAuth, private val webClientId: String) {

    /** @return null on success, otherwise a Strings key describing what went wrong. */
    suspend fun signIn(context: Context): String? {
        if (webClientId.isBlank()) return "err.auth.generic"
        val manager = CredentialManager.create(context)

        val token = try {
            requestToken(manager, context)
        } catch (e: GetCredentialCancellationException) {
            return "err.auth.cancelled"
        } catch (e: NoCredentialException) {
            return "err.auth.noAccount"
        } catch (e: IOException) {
            return "err.auth.network"
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager failed", e)
            return "err.auth.generic"
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed", e)
            return "err.auth.generic"
        } ?: return "err.auth.noAccount"

        return try {
            auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Firebase sign-in failed", e)
            // Firebase reports a dropped connection as a generic exception, so
            // sniff the message rather than showing "check your fingerprint"
            // to someone who simply has no signal.
            if (e.message?.contains("network", ignoreCase = true) == true) "err.auth.network"
            else "err.auth.generic"
        }
    }

    private suspend fun requestToken(manager: CredentialManager, context: Context): String? {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            // false = offer every Google account on the device, not only the
            // ones that have signed into this app before.
            .setFilterByAuthorizedAccounts(false)
            // Skip "one tap" auto-select so the chooser is always shown and
            // switching accounts stays possible.
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val cred = manager.getCredential(context, request).credential
        return if (cred is CustomCredential &&
            cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenCredential.createFrom(cred.data).idToken
        } else {
            null
        }
    }

    suspend fun signOut(context: Context) {
        auth.signOut()
        try {
            // Also clear the credential state, so the next sign-in shows the
            // account chooser instead of silently reusing the same account.
            CredentialManager.create(context).clearCredentialState(
                ClearCredentialStateRequest(ClearCredentialStateRequest.TYPE_CLEAR_CREDENTIAL_STATE),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't clear credential state", e)
        }
    }

    private companion object { const val TAG = "AuthManager" }
}
