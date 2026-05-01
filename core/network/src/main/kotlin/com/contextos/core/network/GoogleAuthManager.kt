package com.contextos.core.network

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google Sign-In, OAuth token acquisition, refresh, and secure storage.
 *
 * Scopes requested:
 *  - calendar.readonly  — read upcoming events
 *  - gmail.readonly     — read recent messages
 *  - drive.readonly     — list recent files
 *
 * Full implementation: Phase 0.2
 */
@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(
            Scope("https://www.googleapis.com/auth/calendar.readonly"),
            Scope("https://www.googleapis.com/auth/gmail.readonly"),
            Scope("https://www.googleapis.com/auth/drive.readonly"),
        )
        .build()

    val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    /** Returns the currently signed-in account, or null if not signed in. */
    fun getCurrentAccount() = GoogleSignIn.getLastSignedInAccount(context)

    /** Returns true if the user is currently signed in with the required scopes. */
    fun isSignedIn(): Boolean = getCurrentAccount() != null

    /**
     * Retrieves a fresh OAuth access token.
     * TODO: Phase 0.2 — implement token refresh via Android Keystore.
     */
    suspend fun getAccessToken(): String? {
        TODO("Phase 0.2 — GoogleAuthManager.getAccessToken()")
    }

    /** Signs out the current user and clears cached credentials. */
    suspend fun signOut() {
        TODO("Phase 0.2 — GoogleAuthManager.signOut()")
    }
}
