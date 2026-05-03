package com.contextos.app.ui.detail

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.contextos.app.ui.dashboard.ActionLogUiItem
import com.contextos.core.data.repository.ActionLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ActionDetailViewModel @Inject constructor(
    private val repository: ActionLogRepository,
) : ViewModel() {

    val item: StateFlow<ActionLogUiItem?> = repository.getAll()
        .map { entities ->
            entities.find { it.id > 0 }?.let { entity ->
                ActionLogUiItem(
                    id              = entity.id,
                    skillName       = entity.skillName,
                    description     = entity.description,
                    timestampMs     = entity.timestampMs,
                    wasAutoApproved = entity.wasAutoApproved,
                    outcome         = entity.outcome,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun approve() {
        // TODO: Update action log entry outcome via repository
    }

    fun dismiss() {
        // TODO: Update action log entry outcome via repository
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionDetailScreen(
    logId:     Long,
    onBack:    () -> Unit,
    viewModel: ActionDetailViewModel = viewModel(),
) {
    val item             by viewModel.item.collectAsState()
    var snapshotExpanded by remember { mutableStateOf(false) }

    if (item == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Action Detail", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Text(text = "←", fontSize = 20.sp)
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "Action not found")
            }
        }
        return
    }

    val currentItem = item!!

    val outcomeBgColor = when (currentItem.outcome) {
        "SUCCESS"                   -> Color(0xFF4CAF50)
        "FAILURE"                   -> Color(0xFFF44336)
        "PENDING_USER_CONFIRMATION" -> Color(0xFFFFC107)
        else                        -> Color(0xFF9E9E9E)
    }
    val outcomeLabel = when (currentItem.outcome) {
        "SUCCESS"                   -> "✓ Success"
        "FAILURE"                   -> "✗ Failed"
        "PENDING_USER_CONFIRMATION" -> "⏳ Awaiting confirmation"
        else                        -> "— Skipped"
    }

    val situationBullets = listOf(
        "🔋  Battery: 62%, not charging",
        "📍  Location: Inferred as 'Office' (WiFi: CorpNet-5G)",
        "🕑  Time: 9:55 AM, Tuesday",
        "📅  Next meeting: Stand-up @ 10:00 AM (Google Meet)",
        "🎙️  Ambient audio: Quiet (office background noise)",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Action Detail", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(text = "←", fontSize = 20.sp)
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text       = currentItem.skillName,
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {},
                    label   = { Text(outcomeLabel, fontSize = 11.sp) },
                    colors  = AssistChipDefaults.assistChipColors(
                        containerColor = outcomeBgColor.copy(alpha = 0.2f),
                        labelColor     = outcomeBgColor,
                    ),
                )
            }

            Text(
                text  = currentItem.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { snapshotExpanded = !snapshotExpanded },
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            text       = "Why ContextOS acted",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (snapshotExpanded) "▴" else "▾",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AnimatedVisibility(visible = snapshotExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            situationBullets.forEach { bullet ->
                                Text(
                                    text  = bullet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text       = "Timeline",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    TimelineStep(
                        icon  = "🔍",
                        label = "Triggered",
                        value = formatTime(currentItem.timestampMs),
                    )
                    TimelineStep(
                        icon  = if (currentItem.wasAutoApproved) "✅" else "⏳",
                        label = if (currentItem.wasAutoApproved) "Auto-approved" else "Awaiting confirmation",
                        value = if (currentItem.wasAutoApproved) formatTime(currentItem.timestampMs + 100) else "—",
                    )
                    if (currentItem.outcome == "SUCCESS") {
                        TimelineStep(
                            icon  = "🎯",
                            label = "Completed",
                            value = formatTime(currentItem.timestampMs + 250),
                        )
                    }
                }
            }

            if (currentItem.outcome == "PENDING_USER_CONFIRMATION") {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick  = { viewModel.approve() },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            contentColor   = Color.White,
                        ),
                    ) {
                        Text(text = "✓", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Approve")
                    }
                    OutlinedButton(
                        onClick  = { viewModel.dismiss() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Dismiss")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TimelineStep(icon: String, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = icon, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text  = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Text(
                text       = value,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(ms))
