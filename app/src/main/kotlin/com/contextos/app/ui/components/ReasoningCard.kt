package com.contextos.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextos.core.data.model.ReasoningPayload

/**
 * Structured reasoning card that replaces the plain-text "Why?" expansion.
 *
 * Phase 9.2 — "Why I Acted" UI Panel
 *
 * Elements:
 *   - Context label in a SuggestionChip styled pill (Material 3)
 *   - Confidence score as LinearProgressIndicator with numeric label
 *   - Bullet list of reasoning points with data-source icons
 *   - Anomaly flags in amber-tinted callout box
 *   - Collapsed-by-default "Why other actions were not taken" section
 *   - AnimatedVisibility with smooth height transition
 *   - Pending approval cards show expanded by default
 */
@Composable
fun ReasoningCard(
    reasoning: ReasoningPayload,
    expandedByDefault: Boolean = false,
    showPulseAnimation: Boolean = false,
    dismissedSkillReasons: List<DismissedSkillInfo> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(expandedByDefault) }
    var showDismissed by remember { mutableStateOf(false) }

    // Phase 9.3 — subtle animated pulse for demo mode
    val pulseAlpha = remember { Animatable(1f) }
    LaunchedEffect(showPulseAnimation) {
        if (showPulseAnimation) {
            pulseAlpha.animateTo(
                targetValue = 0.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Context label chip
            if (reasoning.contextLabel.isNotEmpty()) {
                SuggestionChip(
                    onClick = { expanded = !expanded },
                    label = { Text(reasoning.contextLabel, fontSize = 12.sp) },
                )
            }

            // Confidence score
            if (reasoning.confidenceScore > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Confidence: ${(reasoning.confidenceScore * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    LinearProgressIndicator(
                        progress = { reasoning.confidenceScore },
                        modifier = Modifier.weight(2f),
                        color = getConfidenceColor(reasoning.confidenceScore),
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                }
            }

            // Expandable reasoning points toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.padding(0.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .then(
                                if (showPulseAnimation) Modifier.alpha(pulseAlpha.value)
                                else Modifier
                            ),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (expanded) "Hide reasoning" else "Why I acted",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Why I acted:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    reasoning.reasoningPoints.forEach { point ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // Icon based on the content of the reasoning point
                            Icon(
                                imageVector = getReasoningPointIcon(point),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = point,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Anomaly flags in amber-tinted callout
                    if (reasoning.anomalyFlags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color(0xFFFFF3E0),
                                    RoundedCornerShape(8.dp),
                                )
                                .border(
                                    1.dp,
                                    Color(0xFFFFCC80),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(8.dp),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "⚠ Anomaly detected",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF6F00),
                                )
                                reasoning.anomalyFlags.forEach { flag ->
                                    Text(
                                        text = "• $flag",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFF6F00),
                                    )
                                }
                            }
                        }
                    }

                    // Data sources with icons
                    if (reasoning.dataSourcesUsed.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Sources:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            reasoning.dataSourcesUsed.forEach { source ->
                                val (icon, _) = getSourceIcon(source)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = source,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = source,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (source != reasoning.dataSourcesUsed.last()) {
                                        Text(
                                            text = "·",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Phase 9.2 — Collapsed "Why other actions were not taken" section
                    if (dismissedSkillReasons.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        androidx.compose.material3.TextButton(
                            onClick = { showDismissed = !showDismissed },
                        ) {
                            Text(
                                text = if (showDismissed) "Hide other actions" else "Why other actions were not taken",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }

                        AnimatedVisibility(
                            visible = showDismissed,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(8.dp),
                            ) {
                                dismissedSkillReasons.take(2).forEach { dismissed ->
                                    Column {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = dismissed.skillName,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                text = "${(dismissed.confidence * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            )
                                        }
                                        Text(
                                            text = dismissed.reason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Information about a skill that was evaluated but did not trigger.
 * Phase 9.2 — shown in collapsed state by default in the reasoning card.
 */
data class DismissedSkillInfo(
    val skillName: String,
    val confidence: Float,
    val reason: String,
)

/**
 * Returns an icon for a reasoning point based on keyword matching.
 */
@Composable
private fun getReasoningPointIcon(point: String): ImageVector {
    val lower = point.lowercase()
    return when {
        lower.contains("meeting") || lower.contains("calendar") || lower.contains("event") -> Icons.Default.Schedule
        lower.contains("location") || lower.contains("km away") || lower.contains("office") || lower.contains("home") -> Icons.Default.LocationOn
        lower.contains("battery") -> Icons.Default.BatteryAlert
        lower.contains("audio") || lower.contains("microphone") -> Icons.Default.Mic
        lower.contains("watch") || lower.contains("walking") || lower.contains("running") || lower.contains("steps") -> Icons.Default.Watch
        lower.contains("buds") || lower.contains("noise cancellation") || lower.contains("anc") -> Icons.Default.Headphones
        lower.contains("wifi") || lower.contains("connected to") -> Icons.Default.Wifi
        lower.contains("history") || lower.contains("pattern") || lower.contains("routine") || lower.contains("usually") -> Icons.Default.History
        lower.contains("traffic") || lower.contains("commut") -> Icons.Default.DirectionsWalk
        lower.contains("call") || lower.contains("phone") -> Icons.Default.Call
        else -> Icons.Default.Schedule
    }
}

/**
 * Returns the appropriate confidence color based on score.
 */
@Composable
private fun getConfidenceColor(score: Float): Color {
    return when {
        score >= 0.85f -> Color(0xFF4CAF50)  // Green — high confidence
        score >= 0.65f -> MaterialTheme.colorScheme.primary
        score >= 0.45f -> Color(0xFFFF9800)  // Orange — moderate
        else -> Color(0xFFF44336)             // Red — low
    }
}

@Composable
private fun getSourceIcon(source: String): Pair<ImageVector, String> {
    return when (source.lowercase()) {
        "calendar" -> Icons.Default.Schedule to "Calendar"
        "gps", "location" -> Icons.Default.LocationOn to "Location"
        "call", "phone" -> Icons.Default.Call to "Call"
        "battery" -> Icons.Default.BatteryAlert to "Battery"
        "microphone" -> Icons.Default.Mic to "Microphone"
        "your history" -> Icons.Default.History to "History"
        "galaxy watch" -> Icons.Default.Watch to "Galaxy Watch"
        "galaxy buds" -> Icons.Default.Headphones to "Galaxy Buds"
        "wifi" -> Icons.Default.Wifi to "WiFi"
        "smartthings" -> Icons.Default.Bluetooth to "SmartThings"
        else -> Icons.Default.Schedule to source
    }
}
