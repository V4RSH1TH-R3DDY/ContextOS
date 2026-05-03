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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.contextos.core.service.ContextOSServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────────────

data class ActionLogUiItem(
    val id: Long,
    val skillName: String,
    val description: String,
    val timestampMs: Long,
    val wasAutoApproved: Boolean,
    val outcome: String,  // SUCCESS | FAILURE | PENDING_USER_CONFIRMATION | SKIPPED
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class ActionLogViewModel @Inject constructor() : ViewModel() {

    // Stub list — Phase 5.2 will replace this with a real DB-backed Flow
    private val _logs = MutableStateFlow(
        listOf(
            ActionLogUiItem(
                id              = 1L,
                skillName       = "DND Setter",
                description     = "Enabled Do Not Disturb — meeting starts in 5 minutes",
                timestampMs     = System.currentTimeMillis() - 3 * 60_000,
                wasAutoApproved = true,
                outcome         = "SUCCESS",
            ),
            ActionLogUiItem(
                id              = 2L,
                skillName       = "Battery Warner",
                description     = "Battery at 18% — charger not detected. Sent low-battery alert.",
                timestampMs     = System.currentTimeMillis() - 22 * 60_000,
                wasAutoApproved = false,
                outcome         = "PENDING_USER_CONFIRMATION",
            ),
            ActionLogUiItem(
                id              = 3L,
                skillName       = "Navigation Launcher",
                description     = "Meeting at Bangalore Tech Park in 40 min — traffic suggests leaving now",
                timestampMs     = System.currentTimeMillis() - 45 * 60_000,
                wasAutoApproved = true,
                outcome         = "SUCCESS",
            ),
            ActionLogUiItem(
                id              = 4L,
                skillName       = "System Heartbeat",
                description     = "Service uptime: 3600s | Cycles: 4",
                timestampMs     = System.currentTimeMillis() - 60 * 60_000,
                wasAutoApproved = true,
                outcome         = "SUCCESS",
            ),
        )
    )

    val logs: StateFlow<List<ActionLogUiItem>> = _logs
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionLogScreen(
    onSettingsClick: () -> Unit,
    onLogItemClick:  (Long) -> Unit,
    viewModel: ActionLogViewModel = viewModel(),
) {
    val logs   by viewModel.logs.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "ContextOS",
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Text(text = "⚙", fontSize = 20.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { startService(context) },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text  = "▶",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { paddingValues ->
        if (logs.isEmpty()) {
            EmptyState(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                contentPadding  = paddingValues,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(logs, key = { it.id }) { item ->
                    ActionLogCard(item = item, onClick = onLogItemClick)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Log card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ActionLogCard(
    item:    ActionLogUiItem,
    onClick: (Long) -> Unit,
) {
    val borderColor = when (item.outcome) {
        "SUCCESS"                  -> Color(0xFF4CAF50)
        "FAILURE"                  -> Color(0xFFF44336)
        "PENDING_USER_CONFIRMATION"-> Color(0xFFFFC107)
        else                       -> Color(0xFF9E9E9E)  // SKIPPED
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(item.id) },
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Colored left border strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(80.dp)
                    .background(borderColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Top,
                ) {
                    Text(
                        text       = item.skillName,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.weight(1f),
                    )
                    Text(
                        text  = relativeTime(item.timestampMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text  = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                )
                if (item.wasAutoApproved) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AssistChip(
                        onClick = {},
                        label   = { Text("Auto-approved", fontSize = 10.sp) },
                        colors  = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor     = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.height(22.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text(text = "🤖", fontSize = 72.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text      = "No actions yet — ContextOS is watching",
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun relativeTime(timestampMs: Long): String {
    val diff = System.currentTimeMillis() - timestampMs
    return when {
        diff < 60_000       -> "Just now"
        diff < 3_600_000    -> "${diff / 60_000} min ago"
        diff < 86_400_000   -> "${diff / 3_600_000}h ago"
        else                -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMs))
    }
}

private fun startService(context: Context) {
    ContextOSServiceManager.startService(context)
}
