package com.contextos.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextos.app.ui.theme.Background
import com.contextos.app.ui.theme.SurfaceBg
import com.contextos.app.ui.theme.DividerLine
import com.contextos.app.ui.theme.Accent
import com.contextos.app.ui.theme.OutlineStroke
import com.contextos.app.ui.theme.SurfaceCard
import com.contextos.app.ui.theme.SurfaceInput
import com.contextos.app.ui.theme.TextPrimary
import com.contextos.app.ui.theme.TextSecondary
import com.contextos.app.ui.theme.TextTertiary

data class PermissionItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String,
    val permission: String?,
)

private val REQUIRED_PERMISSIONS = listOf(
    PermissionItem(Icons.Default.LocationOn, "Location", "Detects home, office, commute",
        Manifest.permission.ACCESS_FINE_LOCATION),
    PermissionItem(Icons.Default.Event, "Calendar", "Reads upcoming meetings",
        Manifest.permission.READ_CALENDAR),
    PermissionItem(Icons.Default.Notifications, "Notifications", "Action confirm",
        Manifest.permission.POST_NOTIFICATIONS),
    PermissionItem(Icons.Default.BatteryAlert, "Battery Opt.", "Background running", null),
    PermissionItem(Icons.Default.Mic, "Microphone", "Meeting detection",
        Manifest.permission.RECORD_AUDIO),
)

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(Background, SurfaceBg))),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(64.dp))

                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .scale(pulseScale)
                        .background(Accent.copy(alpha = 0.1f), CircleShape)
                        .border(4.dp, Accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Accent,
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                Text(
                    text = "ContextOS",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Your proactive phone\norchestrator",
                    fontSize = 16.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "ContextOS watches your context 24/7 and quietly takes the right actions.",
                    fontSize = 13.sp,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(text = "Get Started", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

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

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp).verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Permissions", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                IconButton(onClick = onNext) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "ContextOS needs these to understand your context. All data stays on-device.",
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerLine))

            Spacer(modifier = Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                REQUIRED_PERMISSIONS.forEach { item ->
                    PermissionCard(item = item)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { launcher.launch(permissionsToRequest) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(text = "Grant Permissions", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Skip for now", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextTertiary)
            }

            Spacer(modifier = Modifier.height(24.dp))
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
    Row(
        modifier = Modifier.fillMaxWidth().background(SurfaceCard, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(Accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Column {
            Text(text = item.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = item.description, fontSize = 14.sp, color = TextSecondary)
        }
    }
}

@Composable
fun GoogleSignInScreen(
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    viewModel: GoogleSignInViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d("GoogleSignIn", "Result Code: ${result.resultCode}")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            android.util.Log.d("GoogleSignIn", "Sign in successful: ${account?.email}")
            if (viewModel.handleSignInSuccess()) {
                onSignIn()
            } else {
                Toast.makeText(context, "Google needs Calendar, Gmail, and Drive access to connect.", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            val statusName = GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)
            android.util.Log.e(
                "GoogleSignIn",
                "Sign-in failed: resultCode=${result.resultCode}, statusCode=${e.statusCode} ($statusName)",
                e,
            )
            if (viewModel.handleSignInSuccess()) {
                onSignIn()
            } else {
                val message = if (e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                    "Google sign-in was cancelled."
                } else {
                    "Google sign-in failed: $statusName"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("GoogleSignIn", "Sign-in failed.", e)
            Toast.makeText(context, "Google sign-in failed. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(120.dp))

            Box(
                modifier = Modifier.size(80.dp).border(2.dp, OutlineStroke, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.size(36.dp), tint = TextPrimary)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Connect your Google\naccount",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Syncs Calendar, Gmail, and Drive for proactive assistance. Full access, revokable.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(40.dp))

            Column(modifier = Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    "Calendar — manage meetings & DND",
                    "Gmail — read, label, and draft replies",
                    "Drive — read and organize docs before meetings",
                ).forEach { feature ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "•", fontSize = 14.sp, color = Accent, modifier = Modifier.padding(top = 2.dp))
                        Text(text = feature, fontSize = 14.sp, color = TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = { launcher.launch(viewModel.getSignInIntent()) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, Accent),
            ) {
                Text(text = "Sign in with Google", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Accent)
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Skip for now", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextTertiary)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private val RELATIONSHIP_OPTIONS = listOf("Family", "Friend", "Work", "Doctor")

data class EditableEmergencyContact(
    val name: String = "",
    val phone: String = "",
    val relationship: String = "",
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun EmergencyContactScreen(
    onNext: () -> Unit,
    onSaveContacts: (contacts: List<EditableEmergencyContact>) -> Unit = { _ -> },
) {
    val contacts = remember { mutableStateListOf(EditableEmergencyContact()) }

    val isValid = contacts.isNotEmpty() && contacts.all { contact ->
        contact.name.trim().isNotEmpty() &&
            contact.phone.trim().isNotEmpty() &&
            contact.relationship.isNotEmpty()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 56.dp, bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(text = "Emergency Contact", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Who should ContextOS contact if it detects you might need help?",
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                contacts.forEachIndexed { index, contact ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceCard, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = if (index == 0) "Primary contact" else "Contact ${index + 1}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = if (index == 0) "ContextOS uses this first if help is needed."
                                    else "Backup emergency contact.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                )
                            }
                            if (contacts.size > 1) {
                                TextButton(onClick = { contacts.removeAt(index) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove contact",
                                        tint = Accent,
                                    )
                                }
                            }
                        }

                        Column {
                            Text(
                                text = "Full name",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            BasicTextField(
                                value = contact.name,
                                onValueChange = { value -> contacts[index] = contact.copy(name = value) },
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                                    .background(SurfaceInput, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = TextPrimary),
                                singleLine = true,
                                cursorBrush = SolidColor(Accent),
                                decorationBox = { innerTextField ->
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                        if (contact.name.isEmpty()) {
                                            Text(text = "John Doe", fontSize = 16.sp, color = TextTertiary)
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        }

                        Column {
                            Text(
                                text = "Phone number",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            BasicTextField(
                                value = contact.phone,
                                onValueChange = { value -> contacts[index] = contact.copy(phone = value) },
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                                    .background(SurfaceInput, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = TextPrimary),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                cursorBrush = SolidColor(Accent),
                                decorationBox = { innerTextField ->
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                        if (contact.phone.isEmpty()) {
                                            Text(text = "+1 555 0123", fontSize = 16.sp, color = TextTertiary)
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        }

                        Column {
                            Text(
                                text = "Relationship",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                RELATIONSHIP_OPTIONS.forEach { rel ->
                                    val selected = contact.relationship == rel
                                    FilterChip(
                                        selected = selected,
                                        onClick = { contacts[index] = contact.copy(relationship = rel) },
                                        label = { Text(text = rel, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = if (selected) Accent else SurfaceInput,
                                            labelColor = if (selected) Color.White else TextSecondary,
                                            selectedContainerColor = Accent,
                                            selectedLabelColor = Color.White,
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { contacts.add(EditableEmergencyContact()) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(text = "Add another contact", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Accent)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    onSaveContacts(contacts.toList())
                    onNext()
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isValid) Accent else DividerLine,
                    contentColor = if (isValid) Color.White else TextTertiary,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(text = "Save & Continue", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Skip", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextTertiary)
            }
        }
    }
}
