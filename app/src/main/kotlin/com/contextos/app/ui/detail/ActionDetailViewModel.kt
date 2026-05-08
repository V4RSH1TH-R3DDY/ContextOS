package com.contextos.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contextos.core.data.model.UserOverride
import com.contextos.core.data.repository.ActionLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActionDetailViewModel @Inject constructor(
    private val actionLogRepository: ActionLogRepository,
) : ViewModel() {

    fun approveAction(logId: Long) {
        viewModelScope.launch {
            try {
                actionLogRepository.updateWithUserOverride(logId, UserOverride.APPROVED.name)
            } catch (e: Exception) {
                // Log error, but don't propagate (UI handles via state if needed)
                e.printStackTrace()
            }
        }
    }

    fun dismissAction(logId: Long) {
        viewModelScope.launch {
            try {
                actionLogRepository.updateWithUserOverride(logId, UserOverride.DISMISSED.name)
            } catch (e: Exception) {
                // Log error, but don't propagate (UI handles via state if needed)
                e.printStackTrace()
            }
        }
    }
}
