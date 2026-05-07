package com.contextos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SmartThings connection card shown in the Settings screen.
 *
 * Shows:
 *   - Connected / not connected status
 *   - "Connect SmartThings" action when not connected
 *   - Home location name when connected
 *
 * Phase 11.3 — SmartThings Home Arrival Integration
 */
@Composable
fun SmartThingsCard(
    isConnected: Boolean,
    locationName: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1A2332), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.Home else Icons.Default.LinkOff,
                contentDescription = null,
                tint = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF9800),
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = "SmartThings",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    text = if (isConnected) {
                        "Home location: ${locationName ?: "Connected"}"
                    } else {
                        "Not connected"
                    },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
        TextButton(onClick = if (isConnected) onDisconnect else onConnect) {
            Text(
                text = if (isConnected) "Disconnect" else "Connect",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isConnected) Color(0xFFCF6679) else Color(0xFF64B5F6),
            )
        }
    }
}
