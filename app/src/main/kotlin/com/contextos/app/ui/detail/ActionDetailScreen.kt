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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.contextos.app.ui.dashboard.ActionLogUiItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class ActionDetailViewModel @Inject constructor() : ViewModel() {

    // Stub — Phase 5 will fetch the real entry from ActionLogRepository
    private val _item = MutableStateFlow(
        ActionLogUiItem(
            id              = 1L,
            skillName       = "DND Setter",
            description     = "Enabled Do Not Disturb — meeting starts in 5 minutes",
            timestampMs     = System.currentTimeMillis() - 3 * 60_000,
            wasAutoApproved = true,
            outcome         = "SUCCESS",
        )
    )

    val item: StateFlow<ActionLogUiItem> = _item

    /** Situation snapshot bullet points — Phase 3 will derive these from the real SituationModel. */
    val situationBullets = listOf(
        "🔋  Battery: 62%, not charging",
        "📍  Location: Inferred as 'Office' (WiFi: CorpNet-5G)",
        "🕑  Time: 9:55 AM, Tuesday",
        "📅  Next meeting: Stand-up @ 10:00 AM (Google Meet)",
        "🎙️  Ambient audio: Quiet (office background noise)",
    )

    fun approve() {
        _item.value = _item.value.copy(outcome = "SUCCESS", wasAutoApproved = true)
    }

    fun dismiss() {
        _item.value = _item.value.copy(outcome = "SKIPPED")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionDetailScreen(
    logId:     Long,
    onBack:    () -> Unit,
    viewModel: ActionDetailViewModel = viewModel(),
) {
    val item             by viewModel.item.collectAsState()
    val situationBullets = viewModel.situationBullets
    var snapshotExpanded by remember { mutableStateOf(false) }

    val outcomeBgColor = when (item.outcome) {
        "SUCCESS"                   -> Color(0xFF4CAF50)
        "FAILURE"                   -> Color(0xFFF44336)
        "PENDING_USER_CONFIRMATION" -> Color(0xFFFFC107)
        else                        -> Color(0xFF9E9E9E)
    }
    val outcomeLabel = when (item.outcome) {
        "SUCCESS"                   -> "✓ Success"
        "FAILURE"                   -> "✗ Failed"
        "PENDING_USER_CONFIRMATION" -> "⏳ Awaiting confirmation"
        else                        -> "— Skipped"
    }

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

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text       = item.skillName,
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
                text  = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )

            // ── Why ContextOS acted ───────────────────────────────────────────
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

            // ── Timeline ──────────────────────────────────────────────────────
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
                        value = formatTime(item.timestampMs),
                    )
                    TimelineStep(
                        icon  = if (item.wasAutoApproved) "✅" else "⏳",
                        label = if (item.wasAutoApproved) "Auto-approved" else "Awaiting confirmation",
                        value = if (item.wasAutoApproved) formatTime(item.timestampMs + 100) else "—",
                    )
                    if (item.outcome == "SUCCESS") {
                        TimelineStep(
                            icon  = "🎯",
                            label = "Completed",
                            value = formatTime(item.timestampMs + 250),
                        )
                    }
                }
            }

            // ── Actions (only shown when pending) ─────────────────────────────
            if (item.outcome == "PENDING_USER_CONFIRMATION") {
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

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

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
