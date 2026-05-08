package com.contextos.app.ui.settings

import android.content.Context
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.contextos.core.network.CalendarSyncWorker
import com.contextos.core.network.GoogleAuthManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
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
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val emergencyContacts: List<PreferencesManager.EmergencyContact>,
    val googleAccountEmail: String?,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val preferenceRepository: UserPreferenceRepository,
    private val preferencesManager: PreferencesManager,
    private val googleAuthManager: GoogleAuthManager,
    private val skillRegistry: com.contextos.core.skills.SkillRegistry,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        preferenceRepository.getAll(),
        preferencesManager.emergencyContacts,
        preferencesManager.googleAccountEmail,
    ) { dbPrefs, contacts, email ->
        val skills = skillRegistry.skills.map { skill ->
            val dbPref = dbPrefs.find { it.skillId == skill.id }
            SkillSettingItem(
                skillId = skill.id,
                name = skill.name,
                description = skill.description,
                enabled = dbPref?.autoApprove != false,
                sensitivity = dbPref?.sensitivityLevel ?: 1,
            )
        }.sortedBy { it.name }
        SettingsUiState(
            skills = skills,
            behavior = if (dbPrefs.all { it.autoApprove }) "auto" else "confirm",
            emergencyContacts = contacts,
            googleAccountEmail = email,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(
            skills = emptyList(),
            behavior = "confirm",
            emergencyContacts = emptyList(),
            googleAccountEmail = null,
        ),
    )

    init {
        syncGoogleConnectionState()
    }

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
            val autoApprove = behavior == "auto"
            skillRegistry.skills.forEach { skill ->
                val current = preferenceRepository.getBySkillId(skill.id)
                preferenceRepository.upsert(
                    skillId = skill.id,
                    autoApprove = autoApprove,
                    sensitivityLevel = current?.sensitivityLevel ?: 1,
                )
            }
        }
    }

    fun updateEmergencyContact(name: String, phone: String) {
        viewModelScope.launch {
            preferencesManager.saveEmergencyContact(
                PreferencesManager.EmergencyContact(
                    name = name,
                    phone = phone,
                    relationship = "Emergency Contact",
                )
            )
        }
    }

    fun getSignInIntent() = googleAuthManager.signInClient.signInIntent

    fun syncGoogleConnectionState() {
        val email = googleAuthManager.getConnectedAccountEmail()
        viewModelScope.launch {
            preferencesManager.setGoogleAccountEmail(email)
        }
    }

    fun handleSignInSuccess(): Boolean {
        val email = googleAuthManager.getConnectedAccountEmail()
        viewModelScope.launch {
            preferencesManager.setGoogleAccountEmail(email)
            if (email != null) {
                preferencesManager.setGoogleReauthRequired(false)
                CalendarSyncWorker.schedule(appContext)
            }
        }
        return email != null
    }

    fun deleteAccount() {
        viewModelScope.launch {
            googleAuthManager.signOut()
            googleAuthManager.revokeAccess()
            preferencesManager.clearEmergencyContact()
            preferencesManager.setGoogleAccountEmail(null)
            preferencesManager.setGoogleReauthRequired(false)
            preferencesManager.setOnboardingComplete(false)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            googleAuthManager.signOut()
            preferencesManager.setGoogleAccountEmail(null)
            preferencesManager.setGoogleReauthRequired(false)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            googleAuthManager.signOut()
            preferencesManager.clearEmergencyContact()
            preferencesManager.setGoogleAccountEmail(null)
            preferencesManager.setGoogleReauthRequired(false)
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsState()
    var behavior by remember { mutableStateOf(state.behavior) }
    var showEditEmergencyContact by remember { mutableStateOf(false) }
    var emergencyContactName by remember { mutableStateOf("") }
    var emergencyContactPhone by remember { mutableStateOf("") }

    // Initialize emergency contact fields from state
    if (state.emergencyContacts.isNotEmpty()) {
        val contact = state.emergencyContacts.first()
        if (emergencyContactName.isEmpty()) {
            emergencyContactName = contact.name
            emergencyContactPhone = contact.phone
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            task.getResult(ApiException::class.java)
            if (!viewModel.handleSignInSuccess()) {
                Toast.makeText(context, "Google needs Calendar, Gmail, and Drive access to connect.", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            val statusName = GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)
            android.util.Log.e(
                "GoogleSignIn",
                "Settings sign-in failed: resultCode=${result.resultCode}, statusCode=${e.statusCode} ($statusName)",
                e,
            )
            viewModel.syncGoogleConnectionState()
            val message = if (e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                "Google sign-in was cancelled."
            } else {
                "Google sign-in failed: $statusName"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            viewModel.syncGoogleConnectionState()
            Toast.makeText(context, "Google sign-in failed. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 40.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                            text = "Saved contacts",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                        val contacts = state.emergencyContacts
                        if (contacts.isEmpty()) {
                            Text(
                                text = "No emergency contacts saved",
                                fontSize = 14.sp,
                                color = TextSecondary,
                            )
                        } else {
                            contacts.forEach { contact ->
                                Text(
                                    text = "${contact.name} · ${contact.phone}",
                                    fontSize = 14.sp,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { showEditEmergencyContact = true }) {
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
                TextButton(onClick = {
                    if (state.googleAccountEmail != null) {
                        viewModel.signOut()
                    } else {
                        launcher.launch(viewModel.getSignInIntent())
                    }
                }) {
                    Text(
                        text = if (state.googleAccountEmail != null) "Sign Out" else "Sign In",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Accent
                    )
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

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { 
                        viewModel.deleteAccount()
                        // Since this deletes the account and resets onboarding, navigate to welcome screen
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Delete Account",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFCF6679),
                    )
                }
            }
        }
    }

    // Emergency Contact Edit Dialog
    if (showEditEmergencyContact) {
        EmergencyContactEditDialog(
            name = emergencyContactName,
            phone = emergencyContactPhone,
            onConfirm = { name, phone ->
                emergencyContactName = name
                emergencyContactPhone = phone
                viewModel.updateEmergencyContact(name, phone)
                showEditEmergencyContact = false
            },
            onDismiss = { showEditEmergencyContact = false },
        )
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

@Composable
private fun EmergencyContactEditDialog(
    name: String,
    phone: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var contactName by remember { mutableStateOf(name) }
    var contactPhone by remember { mutableStateOf(phone) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Edit Emergency Contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.TextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("Contact Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.TextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(contactName, contactPhone) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
