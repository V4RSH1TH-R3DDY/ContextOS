package com.contextos.app.ui.dashboard

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import com.contextos.app.ui.background.RippleGridBackground
import com.contextos.app.ui.theme.Background
import com.contextos.app.ui.theme.Border
import com.contextos.app.ui.theme.BorderLight
import com.contextos.app.ui.theme.DividerLine
import com.contextos.app.ui.theme.GlassCard
import com.contextos.app.ui.theme.GlassOverlay
import com.contextos.app.ui.theme.NeonBright
import com.contextos.app.ui.theme.NeonDim
import com.contextos.app.ui.theme.NeonGlow
import com.contextos.app.ui.theme.NeonRing
import com.contextos.app.ui.theme.NeonOrange
import com.contextos.app.ui.theme.OutlineStroke
import com.contextos.app.ui.theme.SurfaceBg
import com.contextos.app.ui.theme.SurfaceCard
import com.contextos.app.ui.theme.SurfaceElevated
import com.contextos.app.ui.theme.SurfaceHover
import com.contextos.app.ui.theme.SurfaceInput
import com.contextos.app.ui.theme.SuccessGreen
import com.contextos.app.ui.theme.TextPrimary
import com.contextos.app.ui.theme.TextSecondary
import com.contextos.app.ui.theme.TextTertiary
import com.contextos.app.ui.theme.UserBubble
import com.contextos.app.BuildConfig
import com.contextos.core.data.preferences.PreferencesManager
import com.contextos.core.data.model.CalendarEventSummary
import com.contextos.core.network.CalendarSyncWorker
import com.contextos.core.network.GoogleAuthManager
import com.contextos.core.network.CalendarApiClient
import com.contextos.core.service.ContextOSServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException

data class ChatMessage(
    val id: String,
    val text: String,
    val sender: String,
    val timestamp: String,
)

data class FeatureItem(
    val id: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val sensitivity: Int,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val preferencesManager: PreferencesManager,
    private val googleAuthManager: GoogleAuthManager,
    private val calendarApiClient: CalendarApiClient,
    private val openClawAgent: com.contextos.core.service.agent.openclaw.OpenClawAgent,
    private val skillRegistry: com.contextos.core.skills.SkillRegistry,
    private val userPreferenceRepository: com.contextos.core.data.repository.UserPreferenceRepository,
) : ViewModel() {

    val _messages = mutableStateOf(
        listOf(
            ChatMessage(
                id = "1",
                text = "Hello! I'm ContextOS. I can help manage your phone proactively based on your context.",
                sender = "assistant",
                timestamp = "10:00 AM",
            ),
        )
    )

    val isTyping = mutableStateOf(false)

    /** Conversation history for multi-turn context (system + user + model turns). */
    private val chatHistory = mutableListOf(
        com.contextos.core.service.agent.openclaw.ChatTurn(
            role = "system",
            content = buildSystemPrompt(),
        )
    )

    val features: StateFlow<List<FeatureItem>> = userPreferenceRepository.getAll()
        .map { dbPrefs ->
            skillRegistry.skills.map { skill ->
                val dbPref = dbPrefs.find { it.skillId == skill.id }
                FeatureItem(
                    id = skill.id,
                    name = skill.name,
                    description = skill.description,
                    enabled = dbPref?.autoApprove != false, // Default true
                    sensitivity = dbPref?.sensitivityLevel ?: 1,
                )
            }.sortedBy { it.name }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val googleReauthRequired: StateFlow<Boolean> = preferencesManager.googleReauthRequired
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    private val _debugEventsMessage = MutableStateFlow<String?>(null)
    val debugEventsMessage: StateFlow<String?> = _debugEventsMessage

    fun getSignInIntent(): Intent = googleAuthManager.signInClient.signInIntent

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

    fun fetchEventsForMay8() {
        viewModelScope.launch {
            val year = LocalDate.now().year
            val date = LocalDate.of(year, 5, 8)
            val events = calendarApiClient.getEventsForDay(date, ZoneId.systemDefault())
            _debugEventsMessage.value = buildEventsMessage(date, events)
        }
    }

    fun consumeDebugMessage() {
        _debugEventsMessage.value = null
    }

    private fun buildEventsMessage(
        date: LocalDate,
        events: List<CalendarEventSummary>,
    ): String {
        if (events.isEmpty()) {
            return "No events found for ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}."
        }
        val titles = events.joinToString("; ") { event ->
            event.title.ifBlank { "(untitled event)" }
        }
        return "${events.size} events on ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}: $titles"
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val now = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val userMsg = ChatMessage(
            id = (System.currentTimeMillis() + 1).toString(),
            text = text,
            sender = "user",
            timestamp = now,
        )
        _messages.value = _messages.value + userMsg
        chatHistory.add(com.contextos.core.service.agent.openclaw.ChatTurn(role = "user", content = text))

        isTyping.value = true
        viewModelScope.launch {
            try {
                val responseText = openClawAgent.chat(chatHistory.toList())
                chatHistory.add(com.contextos.core.service.agent.openclaw.ChatTurn(role = "model", content = responseText))
                val response = ChatMessage(
                    id = (System.currentTimeMillis() + 2).toString(),
                    text = responseText,
                    sender = "assistant",
                    timestamp = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date()),
                )
                _messages.value = _messages.value + response
            } catch (e: Exception) {
                val errorMsg = ChatMessage(
                    id = (System.currentTimeMillis() + 2).toString(),
                    text = "I'm having trouble connecting to my intelligence backend right now.",
                    sender = "assistant",
                    timestamp = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date()),
                )
                _messages.value = _messages.value + errorMsg
            } finally {
                isTyping.value = false
            }
        }
    }

    fun updateFeature(id: String, enabled: Boolean, sensitivity: Int) {
        viewModelScope.launch {
            userPreferenceRepository.upsert(
                skillId = id,
                autoApprove = enabled,
                sensitivityLevel = sensitivity,
            )
        }
    }

    fun markOnboardingComplete() {
        viewModelScope.launch {
            preferencesManager.setOnboardingComplete(true)
        }
    }

    private fun buildSystemPrompt(): String {
        val skillNames = skillRegistry.skills.joinToString(", ") { it.name }
        return """
            |You are ContextOS, an intelligent on-device phone assistant built for proactive context-aware automation.
            |
            |Your personality:
            |• Helpful, concise, and privacy-focused
            |• You speak in short, clear sentences
            |• You are transparent when cloud intelligence or connected Google services are being used
            |• You're knowledgeable about the user's registered skills and can explain how they work
            |
            |Available skills: $skillNames
            |
            |Capabilities:
            |• You analyze the user's situation (location, calendar, battery, audio context) every 15 minutes
            |• When connected, you can use Google Calendar, Gmail, and Drive with full access
            |• You can trigger skills like DND before meetings, warn about low battery, launch navigation, draft messages
            |• You learn from user patterns to make better recommendations over time
            |• Users can configure skill sensitivity and auto-approval in settings
            |
            |Rules:
            |• Keep responses under 200 words unless the user asks for detail
            |• If asked about something outside your scope, politely redirect
            |• Never fabricate sensor data or actions you haven't taken
            |• Always be transparent about what you can and cannot do
        """.trimMargin()
    }
}

@Composable
fun DashboardScreen(
    onSettingsClick: () -> Unit,
    onActionLogClick: () -> Unit = {},
    onActionClick: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val messages by viewModel._messages
    val isTyping by viewModel.isTyping
    val features by viewModel.features.collectAsState()
    val input = remember { mutableStateOf("") }
    var sidebarOpen by remember { mutableStateOf(false) }
    var selectedFeature by remember { mutableStateOf<FeatureItem?>(null) }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val needsGoogleReauth by viewModel.googleReauthRequired.collectAsState()
    val debugMessage by viewModel.debugEventsMessage.collectAsState()

    LaunchedEffect(Unit) {
        ContextOSServiceManager.startService(context)
    }

    val reauthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            task.getResult(ApiException::class.java)
            if (!viewModel.handleSignInSuccess()) {
                Toast.makeText(context, "Google needs updated permissions to reconnect.", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            val statusName = GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)
            android.util.Log.e(
                "GoogleSignIn",
                "Dashboard sign-in failed: resultCode=${result.resultCode}, statusCode=${e.statusCode} ($statusName)",
                e,
            )
            val message = if (e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                "Google sign-in was cancelled."
            } else {
                "Google sign-in failed: $statusName"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.util.Log.e("GoogleSignIn", "Dashboard sign-in failed.", e)
            Toast.makeText(context, "Google sign-in failed. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(debugMessage) {
        val message = debugMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.consumeDebugMessage()
    }




    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            val targetIndex = if (isTyping) messages.size else messages.size - 1
            listState.animateScrollToItem(targetIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        RippleGridBackground(
            gridColorHex = "#FF5F1F",
            rippleIntensity = 0.01f,
            gridSize = 16f,
            gridThickness = 16f,
            fadeDistance = 2f,
            vignetteStrength = 1f,
            glowIntensity = 0.04f,
            opacity = 0.6f,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { sidebarOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = TextSecondary,
                    )
                }
                Text(
                    text = "ContextOS",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary,
                    )
                }
            }

            AnimatedVisibility(visible = needsGoogleReauth) {
                GoogleReauthBanner(
                    onReconnect = { reauthLauncher.launch(viewModel.getSignInIntent()) },
                )
            }

            if (BuildConfig.DEBUG) {
                TextButton(
                    onClick = { viewModel.fetchEventsForMay8() },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .align(Alignment.End),
                ) {
                    Text(
                        text = "Debug: Load May 8 events",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextTertiary,
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    if (message.id == "1" && messages.size <= 2) {
                        AiOrbGreeting(message = message)
                    } else {
                        MessageBubble(message = message)
                    }
                }
                if (isTyping) {
                    item(key = "typing_indicator") {
                        TypingIndicator()
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                SurfaceInput.copy(alpha = 0.6f),
                                SurfaceCard.copy(alpha = 0.4f),
                                SurfaceInput.copy(alpha = 0.6f),
                            )
                        ),
                        RoundedCornerShape(24.dp),
                    )
                    .border(1.dp, BorderLight.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicTextField(
                    value = input.value,
                    onValueChange = { input.value = it },
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Normal,
                    ),
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (input.value.isNotBlank()) {
                                viewModel.sendMessage(input.value)
                                input.value = ""
                                keyboardController?.hide()
                            }
                        },
                    ),
                    cursorBrush = SolidColor(NeonOrange),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (input.value.isEmpty()) {
                                Text(
                                    text = "Message ContextOS...",
                                    fontSize = 15.sp,
                                    color = TextTertiary,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                PulsingSendButton(
                    enabled = input.value.isNotBlank(),
                    onClick = {
                        if (input.value.isNotBlank()) {
                            viewModel.sendMessage(input.value)
                            input.value = ""
                            keyboardController?.hide()
                        }
                    },
                )
            }
        }

        if (sidebarOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { sidebarOpen = false },
            )
        }

        AnimatedVisibility(
            visible = sidebarOpen,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(200),
            ) + fadeIn(animationSpec = tween(200)),
        ) {
            Sidebar(
                features = features,
                onFeatureClick = { feature ->
                    selectedFeature = feature
                    sidebarOpen = false
                },
                onActionLogClick = {
                    sidebarOpen = false
                    onActionLogClick()
                },
                onClose = { sidebarOpen = false },
            )
        }
    }

    if (selectedFeature != null) {
        FeatureSettingsSheet(
            feature = selectedFeature!!,
            onClose = { selectedFeature = null },
            onUpdate = { enabled, sensitivity ->
                viewModel.updateFeature(selectedFeature!!.id, enabled, sensitivity)
            },
        )
    }
}

@Composable
private fun GoogleReauthBanner(
    onReconnect: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .border(1.dp, BorderLight.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        color = SurfaceCard.copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Reconnect Google",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "We need your consent to access Calendar, Gmail, and Drive.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
            TextButton(onClick = onReconnect) {
                Text(
                    text = "Reconnect",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonOrange,
                )
            }
        }
    }
}

@Composable
private fun AiOrbGreeting(message: ChatMessage) {
    val pulseScale = remember { Animatable(1f) }
    val pulseAlpha = remember { Animatable(0.6f) }
    val glowScale = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            pulseScale.animateTo(1.08f, tween(1500, easing = EaseInOutCubic))
            pulseScale.animateTo(1f, tween(1500, easing = EaseInOutCubic))
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            pulseAlpha.animateTo(0.9f, tween(2000))
            pulseAlpha.animateTo(0.4f, tween(2000))
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            glowScale.animateTo(1.3f, tween(1800, easing = EaseInOutCubic))
            glowScale.animateTo(1f, tween(1800, easing = EaseInOutCubic))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(96.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(pulseScale.value)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                NeonOrange.copy(alpha = 0.3f),
                                NeonDim.copy(alpha = 0.1f),
                                Color.Transparent,
                            ),
                        ),
                        CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(pulseScale.value)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = pulseAlpha.value * 0.9f),
                                NeonBright.copy(alpha = pulseAlpha.value * 0.4f),
                                NeonOrange.copy(alpha = 0.15f),
                            ),
                        ),
                        CircleShape,
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Hello!",
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message.text,
            fontSize = 15.sp,
            lineHeight = 24.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message.timestamp,
            fontSize = 12.sp,
            color = TextTertiary,
        )
    }
}

@Composable
private fun PulsingSendButton(enabled: Boolean, onClick: () -> Unit) {
    val glowScale = remember { Animatable(1f) }

    LaunchedEffect(enabled) {
        if (enabled) {
            while (true) {
                glowScale.animateTo(1.2f, tween(1200, easing = EaseInOutCubic))
                glowScale.animateTo(1f, tween(1200, easing = EaseInOutCubic))
            }
        }
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                if (enabled) NeonOrange else Border,
                CircleShape,
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send",
            tint = if (enabled) Background else TextTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.sender == "user"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            if (!isUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(SurfaceHover, CircleShape)
                            .border(0.5.dp, Border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Text(
                        text = "ContextOS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Box(
                modifier = Modifier
                    .then(
                        if (isUser) {
                            Modifier
                                .background(UserBubble, RoundedCornerShape(18.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                        } else {
                            Modifier
                                .background(GlassCard, RoundedCornerShape(18.dp))
                                .border(0.5.dp, BorderLight.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                        }
                    )
                    .padding(
                        horizontal = 14.dp,
                        vertical = 10.dp,
                    ),
            ) {
                Text(
                    text = message.text,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = TextPrimary,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = message.timestamp,
                fontSize = 11.sp,
                color = TextTertiary,
                modifier = Modifier.padding(start = if (!isUser) 26.dp else 0.dp),
            )
        }
    }
}

@Composable
private fun Sidebar(
    features: List<FeatureItem>,
    onFeatureClick: (FeatureItem) -> Unit,
    onActionLogClick: () -> Unit = {},
    onClose: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(SurfaceBg, SurfaceCard),
                    )
                )
                .padding(horizontal = 16.dp)
                .padding(top = 40.dp, bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Features",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp,
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                features.forEach { feature ->
                    FeatureCard(feature = feature, onClick = { onFeatureClick(feature) })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DividerLine)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassCard, RoundedCornerShape(12.dp))
                    .border(0.5.dp, Border.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .clickable { onActionLogClick() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(SurfaceHover, CircleShape)
                        .border(0.5.dp, Border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = NeonOrange,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Column {
                    Text(
                        text = "Action History",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                    Text(
                        text = "View past agent decisions",
                        fontSize = 12.sp,
                        color = TextTertiary,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable { onClose() },
        )
    }
}

@Composable
private fun FeatureCard(feature: FeatureItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (feature.enabled) SurfaceHover else GlassCard,
                RoundedCornerShape(12.dp),
            )
            .border(
                width = if (feature.enabled) 1.5.dp else 0.5.dp,
                color = if (feature.enabled) Color.White.copy(alpha = 0.5f) else Border.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .then(
                    if (feature.enabled) {
                        Modifier
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(SuccessGreen.copy(alpha = 0.4f), Color.Transparent),
                                ),
                                CircleShape,
                            )
                    } else Modifier
                )
                .background(
                    if (feature.enabled) SuccessGreen else TextTertiary,
                    CircleShape,
                ),
        )
        Column {
            Text(
                text = feature.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
            )
            Text(
                text = feature.description,
                fontSize = 12.sp,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun PermissionsModal(
    isOpen: Boolean,
    onClose: () -> Unit,
    onGrant: () -> Unit,
) {
    if (!isOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(SurfaceElevated, SurfaceCard),
                    ),
                    RoundedCornerShape(16.dp),
                )
                .border(1.dp, BorderLight.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Permissions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "ContextOS needs these to understand your context. All data stays on-device.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 20.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            val permissions = listOf(
                Triple(Icons.Default.LocationOn, "Location", "Detects home, office, commute"),
                Triple(Icons.Default.Event, "Calendar", "Reads upcoming meetings"),
                Triple(Icons.Default.Notifications, "Notifications", "Action confirm"),
                Triple(Icons.Default.BatteryAlert, "Battery Opt.", "Background running"),
                Triple(Icons.Default.Mic, "Microphone", "Meeting detection"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                permissions.forEach { (icon, title, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GlassCard, RoundedCornerShape(12.dp))
                            .border(0.5.dp, Border.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(SurfaceHover, CircleShape)
                                .border(0.5.dp, Border, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(imageVector = icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(text = desc, fontSize = 12.sp, color = TextTertiary)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    onGrant()
                    onClose()
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonOrange,
                    contentColor = Background,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(text = "Grant Permissions", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Skip for now", fontSize = 13.sp, color = TextTertiary)
            }
        }
    }
}

@Composable
private fun FeatureSettingsSheet(
    feature: FeatureItem,
    onClose: () -> Unit,
    onUpdate: (Boolean, Int) -> Unit,
) {
    var enabled by remember { mutableStateOf(feature.enabled) }
    var sensitivity by remember { mutableStateOf(feature.sensitivity) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(SurfaceElevated, SurfaceCard),
                    ),
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                )
                .border(1.dp, BorderLight.copy(alpha = 0.15f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = feature.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = feature.description,
                fontSize = 13.sp,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Enable", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(24.dp)
                        .background(
                            if (enabled) NeonOrange else Border,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { enabled = !enabled },
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color.White, CircleShape)
                            .align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart)
                            .padding(2.dp),
                    )
                }
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Sensitivity", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text(text = sensitivity.toString(), fontSize = 14.sp, color = TextSecondary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = sensitivity.toFloat(),
                    onValueChange = { sensitivity = it.toInt() },
                    valueRange = 0f..3f,
                    steps = 2,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = NeonOrange,
                        activeTrackColor = NeonOrange,
                        inactiveTrackColor = Border,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "Low", fontSize = 12.sp, color = TextTertiary)
                    Text(text = "High", fontSize = 12.sp, color = TextTertiary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                ) {
                    Text(text = "Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                }
                Button(
                    onClick = {
                        onUpdate(enabled, sensitivity)
                        onClose()
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonOrange,
                        contentColor = Background,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(text = "Save", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val dot1Alpha = remember { Animatable(0.3f) }
    val dot2Alpha = remember { Animatable(0.3f) }
    val dot3Alpha = remember { Animatable(0.3f) }

    LaunchedEffect(Unit) {
        while (true) {
            dot1Alpha.animateTo(1f, tween(400))
            dot1Alpha.animateTo(0.3f, tween(400))
        }
    }
    LaunchedEffect(Unit) {
        delay(150)
        while (true) {
            dot2Alpha.animateTo(1f, tween(400))
            dot2Alpha.animateTo(0.3f, tween(400))
        }
    }
    LaunchedEffect(Unit) {
        delay(300)
        while (true) {
            dot3Alpha.animateTo(1f, tween(400))
            dot3Alpha.animateTo(0.3f, tween(400))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(SurfaceHover, CircleShape)
                        .border(0.5.dp, Border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    text = "ContextOS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .background(GlassCard, RoundedCornerShape(18.dp))
                    .border(0.5.dp, BorderLight.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(dot1Alpha.value)
                            .background(NeonOrange, CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(dot2Alpha.value)
                            .background(NeonOrange, CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(dot3Alpha.value)
                            .background(NeonOrange, CircleShape),
                    )
                }
            }
        }
    }
}
