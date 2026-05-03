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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.contextos.core.data.preferences.PreferencesManager
import com.contextos.core.data.repository.UserPreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────────────

data class SkillSettingUiItem(
    val skillId: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val sensitivity: Float,
)

data class SettingsUiState(
    val skills: List<SkillSettingUiItem>,
    val autoApproveAll: Boolean,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val googleAccountEmail: String?,
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceRepository: UserPreferenceRepository,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    private val skillMetadata = mapOf(
        "dnd_setter" to ("DND Setter" to "Silences your phone before meetings automatically"),
        "battery_warner" to ("Battery Warner" to "Alerts you when battery is critically low before a meeting"),
        "navigation_launcher" to ("Navigation Launcher" to "Suggests when to leave for on-site meetings based on traffic"),
        "message_drafter" to ("Message Drafter" to "Drafts messages when you're running late or unreachable"),
        "document_fetcher" to ("Document Fetcher" to "Finds relevant files before your meetings"),
        "location_intelligence" to ("Location Intelligence" to "Adapts notification behavior based on where you are"),
    )

    val state: StateFlow<SettingsUiState> = combine(
        preferenceRepository.getAll(),
        preferencesManager.emergencyContact,
        preferencesManager.googleAccountEmail,
    ) { dbPrefs, contact, email ->
        val skills = skillMetadata.map { (skillId, pair) ->
            val dbPref = dbPrefs.find { it.skillId == skillId }
            SkillSettingUiItem(
                skillId     = skillId,
                name        = pair.first,
                description = pair.second,
                enabled     = dbPref?.autoApprove != false,
                sensitivity = (dbPref?.sensitivityLevel ?: 1).toFloat(),
            )
        }

        SettingsUiState(
            skills                 = skills,
            autoApproveAll         = dbPrefs.all { it.autoApprove },
            emergencyContactName   = contact?.name ?: "Not set",
            emergencyContactPhone  = contact?.phone ?: "",
            googleAccountEmail     = email,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(
            skills = emptyList(),
            autoApproveAll = false,
            emergencyContactName = "Not set",
            emergencyContactPhone = "",
            googleAccountEmail = null,
        ),
    )

    fun toggleSkill(skillId: String, enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.upsert(skillId, enabled, sensitivityLevel = 1)
        }
    }

    fun updateSensitivity(skillId: String, sensitivity: Float) {
        viewModelScope.launch {
            val current = preferenceRepository.getBySkillId(skillId)
            preferenceRepository.upsert(
                skillId = skillId,
                autoApprove = current?.autoApprove ?: true,
                sensitivityLevel = sensitivity.toInt(),
            )
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            preferencesManager.clearEmergencyContact()
            preferencesManager.setGoogleAccountEmail(null)
        }
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

            SectionHeader("Skills")

            state.skills.forEach { skill ->
                SkillToggleRow(
                    item             = skill,
                    onToggle         = { viewModel.toggleSkill(skill.skillId, it) },
                    onSensitivityChange = { viewModel.updateSensitivity(skill.skillId, it) },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                        onClick  = { /* TODO: bulk update */ },
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    BehaviorRadioRow(
                        label    = "Confirm before acting",
                        subLabel = "ContextOS shows a notification first",
                        selected = !state.autoApproveAll,
                        onClick  = { /* TODO: bulk update */ },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                        if (state.emergencyContactPhone.isNotBlank()) {
                            Text(
                                text  = state.emergencyContactPhone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                    TextButton(onClick = { /* navigate to EmergencyContactScreen */ }) {
                        Text("Edit")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader("Google Account")

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
                            text  = "Connected account",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            text       = state.googleAccountEmail ?: "Not connected",
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    TextButton(onClick = { /* TODO: sign out */ }) {
                        Text(if (state.googleAccountEmail != null) "Sign out" else "Connect")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                        onClick  = { viewModel.clearAllData() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text  = "Clear all data",
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

@Composable
private fun Modifier.width(dp: androidx.compose.ui.unit.Dp): Modifier =
    this.then(layoutWidth(dp))
