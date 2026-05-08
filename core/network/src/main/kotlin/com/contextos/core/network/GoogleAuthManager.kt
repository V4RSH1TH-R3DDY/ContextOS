package com.contextos.core.network

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import android.annotation.SuppressLint
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Central authority for Google OAuth on this device.
 *
 * Responsibilities:
 *  - Exposes the [GoogleSignInClient] so the UI can launch the sign-in flow.
 *  - Builds [GoogleAccountCredential] objects consumed by the Google API service
 *    builders (Calendar, Gmail, Drive).
 *  - Provides a raw access token via [getAccessToken] for cases where a plain
 *    Bearer header is needed.
 *  - Handles token invalidation so the next call forces a network refresh.
 *
 * Token storage: Google Play Services caches tokens internally and backs them
 * via the Android Keystore — no additional EncryptedSharedPreferences layer is
 * required. [GoogleAuthUtil.invalidateToken] busts the cache when a 401 is seen.
 */
@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ─── Sign-in config ───────────────────────────────────────────────────────

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(
            Scope(SCOPE_CALENDAR),
            Scope(SCOPE_GMAIL),
            Scope(SCOPE_DRIVE),
        )
        .build()

    /** Use this client to launch the sign-in Intent from any Activity/Fragment. */
    val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    // ─── Account state ────────────────────────────────────────────────────────

    /** Returns the last successfully signed-in account, or null. */
    fun getCurrentAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    /** True if a Google account is signed in (scopes not yet verified). */
    fun isSignedIn(): Boolean = getCurrentAccount() != null

    /**
     * True if the signed-in account has granted all three required scopes.
     * Call this before building API service objects to avoid silent failures.
     */
    fun hasRequiredScopes(): Boolean {
        val account = getCurrentAccount() ?: return false
        return GoogleSignIn.hasPermissions(
            account,
            Scope(SCOPE_CALENDAR),
            Scope(SCOPE_GMAIL),
            Scope(SCOPE_DRIVE),
        )
    }

    /** Returns the signed-in account email only when all required scopes exist. */
    fun getConnectedAccountEmail(): String? {
        val account = getCurrentAccount() ?: return null
        if (!hasRequiredScopes()) return null
        return account.email ?: account.account?.name
    }

    // ─── Credential / token ───────────────────────────────────────────────────

    /**
     * Builds a [GoogleAccountCredential] scoped to [scopes] for the signed-in
     * account. Pass this directly to Calendar/Gmail/Drive service Builders.
     *
     * Returns null if no account is signed in.
     */
    fun getCredential(scopes: List<String> = ALL_SCOPES): GoogleAccountCredential? {
        val androidAccount = getCurrentAccount()?.account ?: return null
        return GoogleAccountCredential
            .usingOAuth2(context, scopes)
            .apply { selectedAccount = androidAccount }
    }

    /**
     * Returns a raw OAuth 2.0 access token for all three scopes.
     *
     * The call blocks the coroutine on [Dispatchers.IO]; never call from Main.
     * Returns null on any error — caller should surface a re-auth prompt when
     * this returns null after the user is confirmed signed-in.
     */
    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val androidAccount = getCurrentAccount()?.account
            ?: return@withContext null.also {
                Log.w(TAG, "getAccessToken: no signed-in account")
            }

        val scopeString = "oauth2:${ALL_SCOPES.joinToString(" ")}"
        try {
            GoogleAuthUtil.getToken(context, androidAccount, scopeString)
        } catch (e: UserRecoverableAuthException) {
            // The user needs to re-grant permissions — propagate the intent
            // to the UI layer (e.g., via a LiveData/StateFlow event).
            Log.w(TAG, "User needs to re-authorize — intent available", e)
            null
        } catch (e: GoogleAuthException) {
            Log.e(TAG, "Non-recoverable auth error", e)
            null
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching token", e)
            null
        }
    }

    /**
     * Invalidates [token] in the Play Services cache so the next [getAccessToken]
     * call forces a network refresh. Call this whenever an API returns HTTP 401.
     */
    @SuppressLint("MissingPermission")
    suspend fun invalidateToken(token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
            Log.d(TAG, "Token invalidated — next call will refresh")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to invalidate token (non-fatal)", e)
        }
    }

    // ─── Sign-out ─────────────────────────────────────────────────────────────

    /**
     * Signs out the current user. Clears the cached account from Play Services.
     * Safe to call even if no user is signed in.
     */
    suspend fun signOut(): Unit = suspendCancellableCoroutine { cont ->
        signInClient.signOut()
            .addOnSuccessListener {
                Log.i(TAG, "Sign-out successful")
                cont.resume(Unit)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Sign-out failed", e)
                cont.resumeWithException(e)
            }
    }

    /**
     * Revokes all granted OAuth scopes and signs out.
     * Use this on account deletion or full data wipe.
     */
    suspend fun revokeAccess(): Unit = suspendCancellableCoroutine { cont ->
        signInClient.revokeAccess()
            .addOnSuccessListener {
                Log.i(TAG, "Access revoked")
                cont.resume(Unit)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Revoke access failed", e)
                cont.resumeWithException(e)
            }
    }

    // ─── Constants ────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "GoogleAuthManager"

        const val SCOPE_CALENDAR = "https://www.googleapis.com/auth/calendar"
        const val SCOPE_GMAIL    = "https://www.googleapis.com/auth/gmail.modify"
        const val SCOPE_DRIVE    = "https://www.googleapis.com/auth/drive"

        val ALL_SCOPES = listOf(SCOPE_CALENDAR, SCOPE_GMAIL, SCOPE_DRIVE)
    }
}
