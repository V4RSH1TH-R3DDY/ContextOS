package com.contextos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * One-time consent prompt for cloud-based message drafting.
 *
 * "Better message drafts require sending anonymised context to a cloud model.
 *  No personal names or contacts are included."
 *
 * Phase 12.1 — On-Device Privacy Architecture
 */
@Composable
fun CloudConsentDialog(
    onConsent: () -> Unit,
    onDecline: () -> Unit,
) {
    Dialog(onDismissRequest = onDecline) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A2332), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF2A3A4A), RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Icon
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = Color(0xFF64B5F6),
                    modifier = Modifier.size(28.dp),
                )
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(28.dp),
                )
            }

            // Title
            Text(
                text = "Better Message Drafts",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            // Body
            Text(
                text = "Better message drafts require sending anonymised context to a cloud model. " +
                        "No personal names or contacts are included.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            // Privacy guarantees
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PrivacyBullet("Contact names replaced with [CONTACT]")
                PrivacyBullet("Addresses replaced with [LOCATION]")
                PrivacyBullet("Meeting titles replaced with categories")
                PrivacyBullet("All reasoning stays on-device")
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White.copy(alpha = 0.7f),
                    ),
                ) {
                    Text("Keep on-device", fontSize = 13.sp)
                }
                Button(
                    onClick = onConsent,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF64B5F6),
                    ),
                ) {
                    Text("Enable", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun PrivacyBullet(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "✓",
            fontSize = 12.sp,
            color = Color(0xFF4CAF50),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}
