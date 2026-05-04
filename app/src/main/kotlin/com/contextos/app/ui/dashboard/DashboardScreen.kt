package com.contextos.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.contextos.app.ui.theme.BgDark
import com.contextos.app.ui.theme.DividerLine
import com.contextos.app.ui.theme.IndigoBase
import com.contextos.app.ui.theme.IndigoVariant
import com.contextos.app.ui.theme.OutlineStroke
import com.contextos.app.ui.theme.SurfaceCard
import com.contextos.app.ui.theme.SurfaceElevated
import com.contextos.app.ui.theme.SurfaceInput
import com.contextos.app.ui.theme.SuccessGreen
import com.contextos.app.ui.theme.TextPrimary
import com.contextos.app.ui.theme.TextSecondary
import com.contextos.app.ui.theme.TextTertiary
import com.contextos.core.data.preferences.PreferencesManager
import com.contextos.core.service.ContextOSServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    val _messages = mutableStateOf(
        listOf(
            ChatMessage(
                id = "1",
                text = "Hello! I'm ContextOS. I can help manage your phone proactively based on your context.",
                sender = "assistant",
                timestamp = "10:00",
            ),
        )
    )

    val features = mutableStateOf(
        listOf(
            FeatureItem("1", "DND Setter", "Silences phone before meetings", true, 2),
            FeatureItem("2", "Battery Warner", "Alerts on low battery", true, 1),
            FeatureItem("3", "Navigation", "Suggests when to leave", false, 2),
            FeatureItem("4", "Meeting Alert", "Smart meeting reminders", true, 2),
            FeatureItem("5", "System Monitor", "Monitors system health", false, 1),
        )
    )

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val now = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val userMsg = ChatMessage(
            id = (System.currentTimeMillis() + 1).toString(),
            text = text,
            sender = "user",
            timestamp = now,
        )
        _messages.value = _messages.value + userMsg
        viewModelScope.launch {
            delay(1000)
            val response = ChatMessage(
                id = (System.currentTimeMillis() + 2).toString(),
                text = "I understand. Let me help you with that.",
                sender = "assistant",
                timestamp = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date()),
            )
            _messages.value = _messages.value + response
        }
    }

    fun updateFeature(id: String, enabled: Boolean, sensitivity: Int) {
        features.value = features.value.map {
            if (it.id == id) it.copy(enabled = enabled, sensitivity = sensitivity) else it
        }
    }

    fun markOnboardingComplete() {
        viewModelScope.launch {
            preferencesManager.setOnboardingComplete(true)
        }
    }

    private fun formatTime(ms: Long): String {
        return java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(ms))
    }
}

@Composable
fun DashboardScreen(
    onSettingsClick: () -> Unit,
    onActionClick: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val messages by viewModel._messages
    val features by viewModel.features
    val input = remember { mutableStateOf("") }
    var sidebarOpen by remember { mutableStateOf(false) }
    var showPermissions by remember { mutableStateOf(false) }
    var selectedFeature by remember { mutableStateOf<FeatureItem?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        delay(1000)
        showPermissions = true
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { sidebarOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = TextPrimary,
                    )
                }
                Text(
                    text = "ContextOS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextPrimary,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DividerLine)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
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
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicTextField(
                    value = input.value,
                    onValueChange = { input.value = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(SurfaceInput, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = TextPrimary,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(IndigoBase),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (input.value.isEmpty()) {
                                Text(
                                    text = "Message ContextOS...",
                                    fontSize = 14.sp,
                                    color = TextTertiary,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(IndigoBase, RoundedCornerShape(16.dp))
                        .clickable {
                            if (input.value.isNotBlank()) {
                                viewModel.sendMessage(input.value)
                                input.value = ""
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
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
                onClose = { sidebarOpen = false },
            )
        }
    }

    if (sidebarOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { sidebarOpen = false },
        )
    }

    if (showPermissions) {
        PermissionsModal(
            isOpen = true,
            onClose = { showPermissions = false },
            onGrant = {
                viewModel.markOnboardingComplete()
                ContextOSServiceManager.startService(context)
            },
        )
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
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == "user"
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .background(
                    color = if (isUser) IndigoBase else SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(12.dp),
        ) {
            Text(
                text = message.text,
                fontSize = 14.sp,
                color = if (isUser) Color.White else TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.timestamp,
                fontSize = 12.sp,
                color = if (isUser) Color.White.copy(alpha = 0.7f) else TextTertiary,
            )
        }
    }
}

@Composable
private fun Sidebar(
    features: List<FeatureItem>,
    onFeatureClick: (FeatureItem) -> Unit,
    onClose: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .background(SurfaceCard)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Features",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = IndigoBase,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                features.forEach { feature ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceElevated, RoundedCornerShape(16.dp))
                            .clickable { onFeatureClick(feature) }
                            .padding(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(
                                        if (feature.enabled) SuccessGreen else TextTertiary,
                                        CircleShape,
                                    )
                            )
                            Text(
                                text = feature.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = feature.description,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 30.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.padding(start = 30.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (feature.enabled) SuccessGreen else TextTertiary,
                                        CircleShape,
                                    )
                            )
                            Text(
                                text = if (feature.enabled) "Active" else "Inactive",
                                fontSize = 12.sp,
                                color = TextTertiary,
                            )
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { onClose() },
        )
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
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(SurfaceElevated, RoundedCornerShape(24.dp))
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Permissions",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
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
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "ContextOS needs these to understand your context. All data stays on-device.",
                fontSize = 14.sp,
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                permissions.forEach { (icon, title, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceCard, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(IndigoBase, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = desc, fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(
                onClick = {
                    onGrant()
                    onClose()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = IndigoBase,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(text = "Grant Permissions", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Skip for now", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextTertiary)
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
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceElevated, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = feature.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = feature.description,
                fontSize = 14.sp,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Enable Feature", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(28.dp)
                        .background(
                            if (enabled) IndigoBase else OutlineStroke,
                            RoundedCornerShape(14.dp),
                        )
                        .clickable { enabled = !enabled },
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.White, CircleShape)
                            .align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart)
                            .padding(if (enabled) 4.dp else 4.dp),
                    )
                }
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(24.dp))
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
                        thumbColor = IndigoBase,
                        activeTrackColor = IndigoBase,
                        inactiveTrackColor = OutlineStroke,
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

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineStroke),
                ) {
                    Text(text = "Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                }
                androidx.compose.material3.Button(
                    onClick = {
                        onUpdate(enabled, sensitivity)
                        onClose()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = IndigoBase,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(text = "Save", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
