package com.contextos.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.contextos.app.ui.theme.Background
import com.contextos.app.ui.theme.DividerLine
import com.contextos.app.ui.theme.Accent
import com.contextos.app.ui.theme.OutlineStroke
import com.contextos.app.ui.theme.SurfaceCard
import com.contextos.app.ui.theme.SurfaceHover
import com.contextos.app.ui.theme.TextPrimary
import com.contextos.app.ui.theme.TextSecondary
import com.contextos.app.ui.theme.TextTertiary
import com.contextos.core.data.preferences.PreferencesManager
import com.contextos.core.data.repository.UserPreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SkillSettingItem(
    val skillId: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val sensitivity: Int,
)

data class SettingsUiState(
    val skills: List<SkillSettingItem>,
    val behavior: String,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val googleAccountEmail: String?,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceRepository: UserPreferenceRepository,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    private val skillMetadata = mapOf(
        "dnd_setter" to ("DND Setter" to "Silences phone before meetings"),
        "battery_warner" to ("Battery Warner" to "Alerts on low battery"),
        "navigation_launcher" to ("Navigation" to "Suggests when to leave"),
    )

    val state: StateFlow<SettingsUiState> = combine(
        preferenceRepository.getAll(),
        preferencesManager.emergencyContact,
        preferencesManager.googleAccountEmail,
    ) { dbPrefs, contact, email ->
        val skills = skillMetadata.map { (skillId, pair) ->
            val dbPref = dbPrefs.find { it.skillId == skillId }
            SkillSettingItem(
                skillId = skillId,
                name = pair.first,
                description = pair.second,
                enabled = dbPref?.autoApprove != false,
                sensitivity = dbPref?.sensitivityLevel ?: 1,
            )
        }
        SettingsUiState(
            skills = skills,
            behavior = if (dbPrefs.all { it.autoApprove }) "auto" else "confirm",
            emergencyContactName = contact?.name ?: "John Doe",
            emergencyContactPhone = contact?.phone ?: "",
            googleAccountEmail = email,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(
            skills = listOf(
                SkillSettingItem("dnd_setter", "DND Setter", "Silences phone before meetings", true, 2),
                SkillSettingItem("battery_warner", "Battery Warner", "Alerts on low battery", true, 1),
                SkillSettingItem("navigation_launcher", "Navigation", "Suggests when to leave", false, 2),
            ),
            behavior = "confirm",
            emergencyContactName = "John Doe",
            emergencyContactPhone = "",
            googleAccountEmail = "john@gmail.com",
        ),
    )

    fun toggleSkill(skillId: String, enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.upsert(skillId, enabled, sensitivityLevel = 1)
        }
    }

    fun updateSensitivity(skillId: String, sensitivity: Int) {
        viewModelScope.launch {
            val current = preferenceRepository.getBySkillId(skillId)
            preferenceRepository.upsert(
                skillId = skillId,
                autoApprove = current?.autoApprove ?: true,
                sensitivityLevel = sensitivity,
            )
        }
    }

    fun setBehavior(behavior: String) {
        viewModelScope.launch {
            preferenceRepository.getAll()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            preferencesManager.clearEmergencyContact()
            preferencesManager.setGoogleAccountEmail(null)
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var behavior by remember { mutableStateOf(state.behavior) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                    )
                }
                Text(
                    text = "Settings",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("Skills")

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.skills.forEach { skill ->
                    var enabled by remember { mutableStateOf(skill.enabled) }
                    var sensitivity by remember { mutableStateOf(skill.sensitivity) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (enabled) SurfaceHover else SurfaceCard,
                                RoundedCornerShape(16.dp),
                            )
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = skill.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                            )
                            CustomToggleSwitch(
                                enabled = enabled,
                                onToggle = {
                                    enabled = !enabled
                                    viewModel.toggleSkill(skill.skillId, it)
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = skill.description,
                            fontSize = 14.sp,
                            color = TextSecondary,
                        )
                        if (enabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "Sensitivity",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                )
                                Slider(
                                    value = sensitivity.toFloat(),
                                    onValueChange = {
                                        sensitivity = it.toInt()
                                        viewModel.updateSensitivity(skill.skillId, it.toInt())
                                    },
                                    valueRange = 0f..3f,
                                    steps = 2,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Accent,
                                        activeTrackColor = Accent,
                                        inactiveTrackColor = OutlineStroke,
                                    ),
                                )
                                Text(
                                    text = sensitivity.toString(),
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    modifier = Modifier.width(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("Behavior")

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            behavior = "auto"
                            viewModel.setBehavior("auto")
                        },
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CustomRadio(selected = behavior == "auto")
                    Column {
                        Text(
                            text = "Auto-approve all",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                        Text(
                            text = "Acts without asking",
                            fontSize = 12.sp,
                            color = TextSecondary,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DividerLine)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            behavior = "confirm"
                            viewModel.setBehavior("confirm")
                        },
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CustomRadio(selected = behavior == "confirm")
                    Column {
                        Text(
                            text = "Confirm before",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                        Text(
                            text = "Shows notification",
                            fontSize = 12.sp,
                            color = TextSecondary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("Emergency Contact")

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "Current contact",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                        Text(
                            text = state.emergencyContactName,
                            fontSize = 14.sp,
                            color = TextSecondary,
                        )
                    }
                }
                TextButton(onClick = { /* TODO */ }) {
                    Text(text = "Edit", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Accent)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("Google Account")

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "Connected account",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                        Text(
                            text = state.googleAccountEmail ?: "Not connected",
                            fontSize = 14.sp,
                            color = TextSecondary,
                        )
                    }
                }
                TextButton(onClick = { /* TODO */ }) {
                    Text(text = "Sign", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Accent)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("About")

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Version", fontSize = 14.sp, color = TextSecondary)
                    Text(text = "0.1.0-alpha", fontSize = 14.sp, color = TextPrimary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DividerLine)
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { viewModel.clearAllData() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Clear all data",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFCF6679),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Accent,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun CustomToggleSwitch(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(24.dp)
            .background(
                if (enabled) Accent else OutlineStroke,
                RoundedCornerShape(12.dp),
            )
            .clickable { onToggle(!enabled) },
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(Color.White, CircleShape)
                .align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(4.dp),
        )
    }
}

@Composable
private fun CustomRadio(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(
                width = 2.dp,
                color = if (selected) Accent else OutlineStroke,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Accent, CircleShape),
            )
        }
    }
}
