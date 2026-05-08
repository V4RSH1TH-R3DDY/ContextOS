package com.contextos.app.ui.onboarding

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.contextos.core.network.CalendarSyncWorker
import com.contextos.core.network.GoogleAuthManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class GoogleSignInViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val googleAuthManager: GoogleAuthManager,
    private val preferencesManager: com.contextos.core.data.preferences.PreferencesManager
) : ViewModel() {

    fun getSignInIntent(): Intent {
        return googleAuthManager.signInClient.signInIntent
    }

    fun hasRequiredScopes(): Boolean {
        return googleAuthManager.hasRequiredScopes()
    }

    fun handleSignInSuccess(): Boolean {
        val email = googleAuthManager.getConnectedAccountEmail()
        viewModelScope.launch {
            preferencesManager.setGoogleAccountEmail(email)
            if (email != null) {
                preferencesManager.setGoogleReauthRequired(false)
                CalendarSyncWorker.schedule(appContext)
            }
        }
        return email != null
    }
}
