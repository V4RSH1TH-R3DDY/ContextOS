package com.contextos.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Learning section shown on the dashboard during the first 14 days.
 *
 * Displays:
 *   - Progress bar: "ContextOS has learned [N] of your routines"
 *   - Lists confirmed routines as discovered
 *   - Each routine has a toggle: active/paused
 *   - After 14 days, collapses into a one-line "Routines learned: 6 · Manage" link
 *
 * Phase 10.3 — Accelerated Learning UI
 */
@Composable
fun LearningSection(
    routinesLearned: Int,
    learnedRoutines: List<LearnedRoutineInfo>,
    daysSinceInstall: Int,
    onToggleRoutine: (Long, Boolean) -> Unit,
    onManageRoutines: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFirstTwoWeeks = daysSinceInstall <= 14
    val maxRoutinesTarget = 10 // Target number to show in progress bar

    if (isFirstTwoWeeks) {
        // Full learning section visible in first 14 days
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A2636),
                            Color(0xFF1E2D3D),
                        ),
                    ),
                    RoundedCornerShape(16.dp),
                )
                .border(1.dp, Color(0xFF2A4A5A), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Color(0xFF64B5F6),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Learning",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Day $daysSinceInstall of 14",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }

            // Progress bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "ContextOS has learned $routinesLearned of your routines",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f),
                )
                LinearProgressIndicator(
                    progress = { (routinesLearned.toFloat() / maxRoutinesTarget).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = Color(0xFF64B5F6),
                    trackColor = Color(0xFF2A3A4A),
                )
            }

            // Learned routines list
            if (learnedRoutines.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    learnedRoutines.forEach { routine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color(0xFF1A2332).copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(14.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Learnt: ${routine.description}",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                            }
                            Switch(
                                checked = routine.isActive,
                                onCheckedChange = { onToggleRoutine(routine.id, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF64B5F6),
                                    checkedTrackColor = Color(0xFF64B5F6).copy(alpha = 0.3f),
                                ),
                            )
                        }
                    }
                }
            }
        }
    } else {
        // After 14 days — collapsed one-liner
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable { onManageRoutines() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                tint = Color(0xFF64B5F6),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Routines learned: $routinesLearned",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = "· Manage",
                fontSize = 13.sp,
                color = Color(0xFF64B5F6),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

data class LearnedRoutineInfo(
    val id: Long,
    val description: String,
    val isActive: Boolean,
)
