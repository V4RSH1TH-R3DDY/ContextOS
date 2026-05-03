package com.contextos.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextos.app.ui.theme.IndigoBase

import com.contextos.app.ui.theme.IndigoDark
import com.contextos.app.ui.theme.IndigoLight
import com.contextos.app.ui.theme.TealAccent

// ─────────────────────────────────────────────────────────────────────────────
// Step 1 of 4 — Welcome
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(IndigoDark, IndigoBase, IndigoLight),
                    )
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
                verticalArrangement   = Arrangement.SpaceBetween,
                horizontalAlignment   = Alignment.CenterHorizontally,
            ) {

                Spacer(modifier = Modifier.height(32.dp))

                // Animated logo
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(140.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(pulseScale)
                            .background(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = CircleShape,
                            )
                    )
                    CircularProgressIndicator(
                        modifier  = Modifier.size(120.dp).scale(pulseScale),
                        color     = TealAccent.copy(alpha = 0.7f),
                        strokeWidth = 3.dp,
                    )
                    Text(
                        text     = "⚡",
                        fontSize = 48.sp,
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Titles
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text      = "ContextOS",
                        style     = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color     = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text      = "Your proactive phone orchestrator",
                        style     = MaterialTheme.typography.titleMedium,
                        color     = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text      = "ContextOS watches your context 24/7 and quietly takes\n" +
                                    "the right actions — before you even think to ask.",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = Color.White.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // CTA
                Button(
                    onClick  = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor   = IndigoDark,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text      = "Get Started",
                        style     = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2 of 4 — Permissions
// ─────────────────────────────────────────────────────────────────────────────

private data class PermissionItem(
    val emoji: String,
    val title: String,
    val reason: String,
    val permission: String?,
)

private val REQUIRED_PERMISSIONS = listOf(
    PermissionItem("📍", "Location", "Detects home, office, and commute context",
        Manifest.permission.ACCESS_FINE_LOCATION),
    PermissionItem("📅", "Calendar", "Reads upcoming meetings to trigger smart reminders",
        Manifest.permission.READ_CALENDAR),
    PermissionItem("🎙️", "Microphone", "Detects if you're in a meeting (audio level only)",
        Manifest.permission.RECORD_AUDIO),
    PermissionItem("🔔", "Notifications", "Shows action confirmations and status updates",
        Manifest.permission.POST_NOTIFICATIONS),
    PermissionItem("🔋", "Battery Optimization", "Keeps ContextOS running in the background",
        null /* handled via Settings intent */),
)

@Composable
fun PermissionsScreen(onNext: () -> Unit) {
    val context = LocalContext.current
    val permissionsToRequest = REQUIRED_PERMISSIONS.mapNotNull { it.permission }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        requestBatteryOptimizationExemption(context)
        onNext()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text      = "Permissions",
                style     = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color     = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text  = "ContextOS needs these to understand your context. " +
                        "All data stays on-device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(8.dp))

            REQUIRED_PERMISSIONS.forEach { item ->
                PermissionCard(item = item)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick  = { launcher.launch(permissionsToRequest) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text      = "Grant Permissions",
                    style     = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            TextButton(
                onClick  = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Skip for now", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun requestBatteryOptimizationExemption(context: Context) {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) return

    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun PermissionCard(item: PermissionItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = item.emoji, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text      = item.title,
                    style     = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text  = item.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 3 of 4 — Google Sign-In
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GoogleSignInScreen(
    onSignIn: () -> Unit,
    onSkip:   () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement   = Arrangement.Center,
            horizontalAlignment   = Alignment.CenterHorizontally,
        ) {
            // Google "G" badge
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = "G",
                    fontSize   = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF4285F4),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text       = "Connect your Google account",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text      = "Syncs Calendar, Gmail, and Drive for proactive assistance — " +
                            "read-only access, revokable at any time.",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Feature bullets
            listOf(
                "📅  Calendar — smart meeting reminders & DND",
                "📧  Gmail — draft replies when you're unreachable",
                "📁  Drive — surface docs before your next meeting",
            ).forEach { feature ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(IndigoBase, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text  = feature,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedButton(
                onClick  = onSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape  = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    2.dp, MaterialTheme.colorScheme.primary
                ),
            ) {
                Text(
                    text      = "Sign in with Google",
                    style     = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onSkip) {
                Text(
                    text  = "Skip for now",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 4 of 4 — Emergency Contact
// ─────────────────────────────────────────────────────────────────────────────

private val RELATIONSHIP_OPTIONS = listOf("Family", "Friend", "Colleague", "Doctor")

@Composable
fun EmergencyContactScreen(onNext: () -> Unit) {
    var name         by remember { mutableStateOf("") }
    var phone        by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("Family") }
    var phoneError   by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text      = "Emergency Contact",
                style     = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text  = "Who should ContextOS contact if it detects you might need help?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Full name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value         = phone,
                onValueChange = {
                    phone = it
                    phoneError = it.isNotEmpty() && !it.matches(Regex("[0-9+\\-() ]{7,15}"))
                },
                label         = { Text("Phone number") },
                isError       = phoneError,
                supportingText = if (phoneError) {
                    { Text("Enter a valid phone number") }
                } else null,
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier      = Modifier.fillMaxWidth(),
            )

            // Relationship chips
            Text(
                text  = "Relationship",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RELATIONSHIP_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = relationship == option,
                        onClick  = { relationship = option },
                        label    = { Text(option) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick  = {
                    if (!phoneError) onNext()
                },
                enabled  = name.isNotBlank() && phone.isNotBlank() && !phoneError,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text      = "Save & Continue",
                    style     = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            TextButton(
                onClick  = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text  = "Skip",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }
    }
}
