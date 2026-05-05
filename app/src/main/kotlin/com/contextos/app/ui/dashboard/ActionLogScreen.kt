package com.contextos.app.ui.dashboard

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.contextos.app.ui.components.ReasoningCard
import com.contextos.core.data.model.ReasoningPayload
import com.contextos.core.data.repository.ActionLogRepository
import com.contextos.core.service.ContextOSServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────

data class ActionLogUiItem(
    val id: Long,
    val skillName: String,
    val description: String,
    val timestampMs: Long,
    val wasAutoApproved: Boolean,
    val outcome: String,
    val reasoningPayload: ReasoningPayload? = null,
)

// ─────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────

@HiltViewModel
class ActionLogViewModel @Inject constructor(
    private val repository: ActionLogRepository,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val logs: StateFlow<List<ActionLogUiItem>> = repository.getAll()
        .map { entities ->
            entities.map { entity ->
                ActionLogUiItem(
                    id              = entity.id,
                    skillName       = entity.skillName,
                    description     = entity.description,
                    timestampMs     = entity.timestampMs,
                    wasAutoApproved = entity.wasAutoApproved,
                    outcome         = entity.outcome,
                    reasoningPayload = parseReasoning(entity.reasoningPayload),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private fun parseReasoning(jsonString: String): ReasoningPayload? {
        return try {
            json.decodeFromString(ReasoningPayload.serializer(), jsonString)
        } catch (e: Exception) {
            null
        }
    }
}
