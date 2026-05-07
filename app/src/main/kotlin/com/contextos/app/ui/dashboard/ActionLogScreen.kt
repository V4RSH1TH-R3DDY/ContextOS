package com.contextos.app.ui.dashboard

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.launch
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

    /**
     * Pre-populates demo reasoning entries for Phase 9.3 demo mode.
     */
    fun loadDemoData() {
        viewModelScope.launch {
            repository.prePopulateDemoReasoningEntries()
        }
    }

    private fun parseReasoning(jsonString: String): ReasoningPayload? {
        return try {
            json.decodeFromString(ReasoningPayload.serializer(), jsonString)
        } catch (e: Exception) {
            null
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Screen Composable — Phase 9.2 integration
// ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionLogScreen(
    onBack: () -> Unit,
    viewModel: ActionLogViewModel = hiltViewModel(),
) {
    val logs by viewModel.logs.collectAsState()
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Action History",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D1117),
                ),
            )
        },
        containerColor = Color(0xFF0D1117),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.loadDemoData() },
                containerColor = Color(0xFFFF5F1F),
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.AutoAwesome, "Load demo data")
            }
        },
    ) { paddingValues ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "No actions yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                    Text(
                        text = "Tap ✨ to load demo data",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(logs, key = { it.id }) { item ->
                    ActionLogCard(
                        item = item,
                        dateFormat = dateFormat,
                        isPending = item.outcome == "PENDING_USER_CONFIRMATION",
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionLogCard(
    item: ActionLogUiItem,
    dateFormat: SimpleDateFormat,
    isPending: Boolean,
) {
    var expanded by remember { mutableStateOf(isPending) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A2332),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header row: skill name + outcome chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutcomeIcon(item.outcome)
                    Text(
                        text = item.skillName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
                OutcomeChip(item.outcome)
            }

            // Description
            Text(
                text = item.description,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
            )

            // Timestamp + auto-approve badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dateFormat.format(Date(item.timestampMs)),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.4f),
                )
                if (item.wasAutoApproved) {
                    Text(
                        text = "Auto-approved",
                        fontSize = 11.sp,
                        color = Color(0xFF4CAF50).copy(alpha = 0.7f),
                    )
                }
            }

            // Phase 9.2 — ReasoningCard integration
            item.reasoningPayload?.let { reasoning ->
                AnimatedVisibility(
                    visible = true,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    ReasoningCard(
                        reasoning = reasoning,
                        expandedByDefault = isPending,
                        showPulseAnimation = isPending,
                    )
                }
            }
        }
    }
}

@Composable
private fun OutcomeIcon(outcome: String) {
    val (icon, color) = getOutcomeIconAndColor(outcome)
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(color.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun OutcomeChip(outcome: String) {
    val (_, color) = getOutcomeIconAndColor(outcome)
    val label = when (outcome) {
        "SUCCESS" -> "Success"
        "FAILURE" -> "Failed"
        "PENDING_USER_CONFIRMATION" -> "Pending"
        "SKIPPED" -> "Skipped"
        else -> outcome
    }
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.1f),
            labelColor = color,
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = color.copy(alpha = 0.3f),
        ),
    )
}

private fun getOutcomeIconAndColor(outcome: String): Pair<ImageVector, Color> {
    return when (outcome) {
        "SUCCESS" -> Icons.Default.Check to Color(0xFF4CAF50)
        "FAILURE" -> Icons.Default.Close to Color(0xFFF44336)
        "PENDING_USER_CONFIRMATION" -> Icons.Default.Pending to Color(0xFFFF9800)
        "SKIPPED" -> Icons.Default.PlayArrow to Color(0xFF9E9E9E)
        else -> Icons.Default.Pending to Color(0xFF9E9E9E)
    }
}
