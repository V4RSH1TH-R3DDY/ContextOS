package com.contextos.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contextos.core.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingCompleteViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    fun markOnboardingComplete() {
        viewModelScope.launch {
            preferencesManager.setOnboardingComplete(true)
        }
    }

    fun saveEmergencyContacts(contacts: List<PreferencesManager.EmergencyContact>) {
        viewModelScope.launch {
            preferencesManager.saveEmergencyContacts(contacts)
        }
    }
}
