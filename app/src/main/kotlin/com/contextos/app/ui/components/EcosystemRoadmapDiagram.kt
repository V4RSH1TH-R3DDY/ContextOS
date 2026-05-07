package com.contextos.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Ecosystem roadmap diagram for the demo.
 *
 * Visualises the Samsung ecosystem integration:
 * ```
 * Galaxy Watch  ──→  Activity context (commuting, heart rate)
 * Galaxy Buds   ──→  Audio context (calls, noise environment)
 * SmartThings   ──→  Presence detection (home arrival, night mode)
 *                        │
 *                        ▼
 *                ContextOS Intelligence Layer
 *                        │
 *                        ▼
 *                Samsung One UI Actions
 * ```
 *
 * Phase 11.4 — Ecosystem Roadmap Slide (Demo Asset)
 */
@Composable
fun EcosystemRoadmapDiagram(
    modifier: Modifier = Modifier,
) {
    val fadeIn1 = remember { Animatable(0f) }
    val fadeIn2 = remember { Animatable(0f) }
    val fadeIn3 = remember { Animatable(0f) }
    val fadeInCenter = remember { Animatable(0f) }
    val fadeInBottom = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(200)
        fadeIn1.animateTo(1f, tween(600, easing = EaseInOutCubic))
        delay(100)
        fadeIn2.animateTo(1f, tween(600, easing = EaseInOutCubic))
        delay(100)
        fadeIn3.animateTo(1f, tween(600, easing = EaseInOutCubic))
        delay(200)
        fadeInCenter.animateTo(1f, tween(800, easing = EaseInOutCubic))
        delay(200)
        fadeInBottom.animateTo(1f, tween(600, easing = EaseInOutCubic))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color(0xFF0D1117),
                RoundedCornerShape(20.dp),
            )
            .border(1.dp, Color(0xFF2A3A4A), RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Title
        Text(
            text = "Future Roadmap",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = "What you've seen runs today. Here's where it goes.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Source devices
        EcosystemNode(
            icon = Icons.Default.Watch,
            label = "Galaxy Watch",
            description = "Activity context (commuting, heart rate)",
            color = Color(0xFF4FC3F7),
            alpha = fadeIn1.value,
        )
        EcosystemNode(
            icon = Icons.Default.Headphones,
            label = "Galaxy Buds",
            description = "Audio context (calls, noise environment)",
            color = Color(0xFF81C784),
            alpha = fadeIn2.value,
        )
        EcosystemNode(
            icon = Icons.Default.Home,
            label = "SmartThings",
            description = "Presence detection (home arrival, night mode)",
            color = Color(0xFFFFB74D),
            alpha = fadeIn3.value,
        )

        // Arrow down
        Box(
            modifier = Modifier
                .alpha(fadeInCenter.value)
                .height(32.dp)
                .width(2.dp)
                .background(Color(0xFFFF5F1F).copy(alpha = 0.6f)),
        )

        // Central intelligence layer
        Box(
            modifier = Modifier
                .alpha(fadeInCenter.value)
                .fillMaxWidth()
                .background(
                    Color(0xFFFF5F1F).copy(alpha = 0.1f),
                    RoundedCornerShape(12.dp),
                )
                .border(1.dp, Color(0xFFFF5F1F).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.DeviceHub,
                    contentDescription = null,
                    tint = Color(0xFFFF5F1F),
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "ContextOS Intelligence Layer",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF5F1F),
                )
            }
        }

        // Arrow down
        Box(
            modifier = Modifier
                .alpha(fadeInBottom.value)
                .height(32.dp)
                .width(2.dp)
                .background(Color(0xFFFF5F1F).copy(alpha = 0.6f)),
        )

        // Output actions
        Box(
            modifier = Modifier
                .alpha(fadeInBottom.value)
                .fillMaxWidth()
                .background(
                    Color(0xFF1A2332),
                    RoundedCornerShape(12.dp),
                )
                .border(1.dp, Color(0xFF2A3A4A), RoundedCornerShape(12.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Samsung One UI Actions",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun EcosystemNode(
    icon: ImageVector,
    label: String,
    description: String,
    color: Color,
    alpha: Float,
) {
    Row(
        modifier = Modifier
            .alpha(alpha)
            .fillMaxWidth()
            .background(
                color.copy(alpha = 0.05f),
                RoundedCornerShape(12.dp),
            )
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
        Column {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "──→",
            fontSize = 12.sp,
            color = color.copy(alpha = 0.6f),
        )
    }
}
