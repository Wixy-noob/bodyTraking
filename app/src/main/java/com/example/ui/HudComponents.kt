package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ArmorDefinition

val HudNeonCyan = Color(0xFF00F0FF)
val HudNeonGreen = Color(0xFF00FF66)
val HudDarkBg = Color(0xCC090D16)
val HudGlassBorder = Color(0x4400F0FF)

/**
 * Top-Right "ARMOR" Button with futuristic HUD styling and subtle pulsing glow.
 */
@Composable
fun ArmorHudButton(
    onClick: () -> Unit,
    isSelected: Boolean,
    currentArmor: ArmorDefinition?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hud_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val activeColor = currentArmor?.emissiveColor ?: HudNeonCyan

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = HudDarkBg,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = if (isSelected) activeColor.copy(alpha = glowAlpha) else activeColor.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .testTag("armor_menu_button")
            .drawBehind {
                if (isSelected) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(activeColor.copy(alpha = 0.2f), Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.8f
                        )
                    )
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Armor Menu",
                tint = activeColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "ARMOR",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                if (currentArmor != null) {
                    Text(
                        text = currentArmor.name,
                        color = activeColor.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Top-Left Tracking Status HUD badge
 */
@Composable
fun TrackingStatusHud(
    isDetected: Boolean,
    confidence: Float,
    isDemoMode: Boolean,
    modifier: Modifier = Modifier
) {
    val statusColor = if (isDetected) HudNeonGreen else Color(0xFFFFB703)
    val infiniteTransition = rememberInfiniteTransition(label = "radar_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(HudDarkBg)
            .border(1.dp, HudGlassBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = if (isDetected) 1f else pulseAlpha))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (isDemoMode) "TEST POSE SIMULATOR" else if (isDetected) "BODY LOCK: ACTIVE" else "TRACKING: SCANNING...",
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (isDemoMode) "Front Camera Mode Available" else "Confidence: ${(confidence * 100).toInt()}%",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * HUD Bottom Control Toolbar:
 * - Toggle Front Camera vs Demo Pose
 * - Toggle Skeleton Debug Lines
 */
@Composable
fun HudControlsBar(
    isDemoMode: Boolean,
    onToggleDemoMode: () -> Unit,
    showSkeleton: Boolean,
    onToggleSkeleton: () -> Unit,
    demoStateName: String,
    onNextDemoPose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(HudDarkBg)
            .border(1.dp, HudGlassBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Mode Switcher button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onToggleDemoMode)
                .background(if (isDemoMode) HudNeonCyan.copy(alpha = 0.2f) else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("mode_toggle_button")
        ) {
            Icon(
                imageVector = if (isDemoMode) Icons.Default.PlayArrow else Icons.Default.CameraFront,
                contentDescription = "Toggle Mode",
                tint = if (isDemoMode) HudNeonCyan else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isDemoMode) "DEMO: $demoStateName" else "FRONT CAM",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        if (isDemoMode) {
            // Next Demo Pose trigger
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onNextDemoPose)
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("next_pose_button")
            ) {
                Text(
                    text = "NEXT POSE ▶",
                    color = HudNeonCyan,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Skeleton view toggle
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onToggleSkeleton)
                .background(if (showSkeleton) HudNeonGreen.copy(alpha = 0.25f) else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("skeleton_toggle_button")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (showSkeleton) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Toggle Skeleton",
                    tint = if (showSkeleton) HudNeonGreen else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "BONES",
                    color = if (showSkeleton) HudNeonGreen else Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
