package com.contextos.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width as layoutWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────────────

data class SkillSettingUiItem(
    val skillId: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val sensitivity: Float,  // 0f–3f
)

data class SettingsUiState(
    val skills: List<SkillSettingUiItem>,
    val autoApproveAll: Boolean,
    val emergencyContactName: String,
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            skills = listOf(
                SkillSettingUiItem(
                    skillId     = "dnd_setter",
                    name        = "DND Setter",
                    description = "Silences your phone before meetings automatically",
                    enabled     = true,
                    sensitivity = 2f,
                ),
                SkillSettingUiItem(
                    skillId     = "battery_warner",
                    name        = "Battery Warner",
                    description = "Alerts you when battery is critically low before a meeting",
                    enabled     = true,
                    sensitivity = 1f,
                ),
                SkillSettingUiItem(
                    skillId     = "navigation_launcher",
                    name        = "Navigation Launcher",
                    description = "Suggests when to leave for on-site meetings based on traffic",
                    enabled     = false,
                    sensitivity = 2f,
                ),
            ),
            autoApproveAll       = false,
            emergencyContactName = "Not set",
        )
    )

    val state: StateFlow<SettingsUiState> = _state

    fun toggleSkill(skillId: String, enabled: Boolean) {
        _state.update { s ->
            s.copy(skills = s.skills.map {
                if (it.skillId == skillId) it.copy(enabled = enabled) else it
            })
        }
    }

    fun updateSensitivity(skillId: String, sensitivity: Float) {
        _state.update { s ->
            s.copy(skills = s.skills.map {
                if (it.skillId == skillId) it.copy(sensitivity = sensitivity) else it
            })
        }
    }

    fun setAutoApproveAll(value: Boolean) {
        _state.update { it.copy(autoApproveAll = value) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack:    () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {

            // ── Skills ────────────────────────────────────────────────────────
            SectionHeader("Skills")

            state.skills.forEach { skill ->
                SkillToggleRow(
                    item             = skill,
                    onToggle         = { viewModel.toggleSkill(skill.skillId, it) },
                    onSensitivityChange = { viewModel.updateSensitivity(skill.skillId, it) },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Behavior ──────────────────────────────────────────────────────
            SectionHeader("Behavior")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    BehaviorRadioRow(
                        label    = "Auto-approve all actions",
                        subLabel = "ContextOS acts without asking",
                        selected = state.autoApproveAll,
                        onClick  = { viewModel.setAutoApproveAll(true) },
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    BehaviorRadioRow(
                        label    = "Confirm before acting",
                        subLabel = "ContextOS shows a notification first",
                        selected = !state.autoApproveAll,
                        onClick  = { viewModel.setAutoApproveAll(false) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Emergency Contact ─────────────────────────────────────────────
            SectionHeader("Emergency Contact")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text  = "Current contact",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            text       = state.emergencyContactName,
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    TextButton(onClick = { /* navigate to EmergencyContactScreen */ }) {
                        Text("Edit")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── About ─────────────────────────────────────────────────────────
            SectionHeader("About")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text  = "Version",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text  = "0.1.0-alpha",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(
                        onClick  = { /* open logs */ },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("View full action log")
                    }
                    TextButton(
                        onClick  = { /* sign out */ },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text  = "Sign out",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SkillToggleRow(
    item: SkillSettingUiItem,
    onToggle: (Boolean) -> Unit,
    onSensitivityChange: (Float) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = item.name,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text  = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                Switch(
                    checked         = item.enabled,
                    onCheckedChange = onToggle,
                )
            }

            if (item.enabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = "Sensitivity",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.width(80.dp),
                    )
                    Slider(
                        value         = item.sensitivity,
                        onValueChange = onSensitivityChange,
                        valueRange    = 0f..3f,
                        steps         = 2,
                        modifier      = Modifier.weight(1f),
                    )
                    Text(
                        text     = item.sensitivity.toInt().toString(),
                        style    = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BehaviorRadioRow(
    label:    String,
    subLabel: String,
    selected: Boolean,
    onClick:  () -> Unit,
) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick  = onClick,
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text       = label,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text  = subLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

// Alias to avoid importing an extra 80dp
@Composable
private fun Modifier.width(dp: androidx.compose.ui.unit.Dp): Modifier =
    this.then(layoutWidth(dp))
