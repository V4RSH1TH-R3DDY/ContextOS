package com.contextos.app.ui.onboarding

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.contextos.core.network.GoogleAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class GoogleSignInViewModel @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val preferencesManager: com.contextos.core.data.preferences.PreferencesManager
) : ViewModel() {

    fun getSignInIntent(): Intent {
        return googleAuthManager.signInClient.signInIntent
    }

    fun hasRequiredScopes(): Boolean {
        return googleAuthManager.hasRequiredScopes()
    }

    fun handleSignInSuccess(fallbackEmail: String? = null) {
        viewModelScope.launch {
            val account = googleAuthManager.getCurrentAccount()
            if (account != null) {
                preferencesManager.setGoogleAccountEmail(account.email)
            } else if (fallbackEmail != null) {
                preferencesManager.setGoogleAccountEmail(fallbackEmail)
            } else {
                preferencesManager.setGoogleAccountEmail("user@gmail.com")
            }
        }
    }
}
