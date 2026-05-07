package com.contextos.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contextos.core.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StartDestinationViewModel @Inject constructor(
    preferencesManager: PreferencesManager,
) : ViewModel() {

    val startDestination: StateFlow<String?> = preferencesManager.isOnboardingComplete
        .map { complete ->
            if (complete) Screen.Dashboard.route else Screen.Onboarding.Welcome.route
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )
}
