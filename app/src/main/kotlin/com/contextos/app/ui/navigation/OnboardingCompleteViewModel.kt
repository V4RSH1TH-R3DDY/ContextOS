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

    fun saveEmergencyContact(name: String, phone: String, relationship: String) {
        viewModelScope.launch {
            preferencesManager.saveEmergencyContact(
                PreferencesManager.EmergencyContact(name, phone, relationship)
            )
        }
    }
}
