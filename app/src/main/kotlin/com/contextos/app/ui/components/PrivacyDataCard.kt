package com.contextos.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Privacy data card shown in the Settings screen.
 *
 * Displays a real-time summary of all data stored by ContextOS,
 * with controls to export anonymised history or wipe everything.
 *
 * Phase 12.2 — On-Device Storage Hardening
 *
 * ```
 * ┌─────────────────────────────────────┐
 * │  Your data — stored only here       │
 * │  Action history: 847 entries        │
 * │  Location patterns: 12 places       │
 * │  Routines learned: 6                │
 * │  Storage used: 2.1 MB              │
 * │                                     │
 * │  [Export as CSV]  [Delete all]      │
 * └─────────────────────────────────────┘
 * ```
 */
@Composable
fun PrivacyDataCard(
    actionLogCount: Int,
    locationMemoryCount: Int,
    routineMemoryCount: Int,
    storageUsed: String,
    onExportCsv: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color(0xFF1A2332),
                RoundedCornerShape(16.dp),
            )
            .border(
                1.dp,
                Color(0xFF2A3A4A),
                RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header with shield icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Your data — stored only here",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "On-device only",
                tint = Color(0xFF4CAF50).copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp),
            )
        }

        // Data counts
        DataRow("Action history", "$actionLogCount entries")
        DataRow("Location patterns", "$locationMemoryCount places")
        DataRow("Routines learned", "$routineMemoryCount")
        DataRow("Storage used", storageUsed)

        Spacer(modifier = Modifier.height(4.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onExportCsv,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF81C784),
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text("Export CSV", fontSize = 12.sp)
            }

            Button(
                onClick = onDeleteAll,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFCF6679).copy(alpha = 0.2f),
                    contentColor = Color(0xFFCF6679),
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text("Delete all", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}
