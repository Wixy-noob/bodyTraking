package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ArmorDefinition
import com.example.model.ArmorType

/**
 * Futuristic Semi-Transparent Dark Glass Armor Selection HUD Panel
 */
@Composable
fun ArmorSelectorPanel(
    isVisible: Boolean,
    onClose: () -> Unit,
    allArmors: List<ArmorDefinition>,
    selectedArmorIndex: Int,
    onSelectArmorIndex: (Int) -> Unit,
    equippedArmor: ArmorDefinition?,
    isEquipped: Boolean,
    isScanning: Boolean,
    onEquipPressed: (ArmorDefinition) -> Unit,
    onUnequipPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(350, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(280, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(250)),
        modifier = modifier
    ) {
        val currentArmor = allArmors.getOrElse(selectedArmorIndex) { allArmors.first() }
        val isCurrentEquipped = isEquipped && equippedArmor?.type == currentArmor.type

        val themeColor = currentArmor.emissiveColor

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xEE090E18),
                            Color(0xFA020617)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            themeColor.copy(alpha = 0.8f),
                            HudGlassBorder,
                            themeColor.copy(alpha = 0.3f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .drawBehind {
                    // Futuristic corner HUD bracket accents
                    val stroke = 3.dp.toPx()
                    val bracketLen = 22.dp.toPx()
                    val bracketColor = themeColor.copy(alpha = 0.7f)

                    // Top-Left corner
                    drawLine(bracketColor, Offset(0f, bracketLen), Offset(0f, 0f), stroke)
                    drawLine(bracketColor, Offset(0f, 0f), Offset(bracketLen, 0f), stroke)
                    // Top-Right corner
                    drawLine(bracketColor, Offset(size.width - bracketLen, 0f), Offset(size.width, 0f), stroke)
                    drawLine(bracketColor, Offset(size.width, 0f), Offset(size.width, bracketLen), stroke)
                    // Bottom-Left corner
                    drawLine(bracketColor, Offset(0f, size.height - bracketLen), Offset(0f, size.height), stroke)
                    drawLine(bracketColor, Offset(0f, size.height), Offset(bracketLen, size.height), stroke)
                    // Bottom-Right corner
                    drawLine(bracketColor, Offset(size.width - bracketLen, size.height), Offset(size.width, size.height), stroke)
                    drawLine(bracketColor, Offset(size.width, size.height - bracketLen), Offset(size.width, size.height), stroke)
                }
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Bar: [ ARMOR ] + Close Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(themeColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "[ ARMOR ]",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 3.sp
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("close_armor_panel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector Carousel Header: ◀  DARK KNIGHT  ▶
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = {
                            val next = (selectedArmorIndex - 1 + allArmors.size) % allArmors.size
                            onSelectArmorIndex(next)
                        },
                        modifier = Modifier.testTag("prev_armor_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Armor",
                            tint = themeColor
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = currentArmor.name,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = currentArmor.category,
                            color = themeColor.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            val next = (selectedArmorIndex + 1) % allArmors.size
                            onSelectArmorIndex(next)
                        },
                        modifier = Modifier.testTag("next_armor_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Armor",
                            tint = themeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // [ PREVIEW ] Card with artwork & stats
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0D1322))
                        .border(1.dp, themeColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                ) {
                    // Armor Preview Image from generated reference art
                    Image(
                        painter = painterResource(id = currentArmor.previewDrawable),
                        contentDescription = "${currentArmor.name} Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradient overlay to keep text crisp
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0x88020617),
                                        Color(0xF5020617)
                                    ),
                                    startY = 180f
                                )
                            )
                    )

                    // Preview badge at top left
                    Box(
                        modifier = Modifier
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC000000))
                            .border(1.dp, themeColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "[ PREVIEW ]",
                            color = themeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Equipped badge if active
                    if (isCurrentEquipped) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HudNeonGreen.copy(alpha = 0.25f))
                                .border(1.dp, HudNeonGreen, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                tint = HudNeonGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ACTIVE",
                                color = HudNeonGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Bottom info & stats inside preview
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = currentArmor.subtitle,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentArmor.description,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // Mini stats row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatPill(label = "DEF", value = "${currentArmor.statsDefense}", color = themeColor)
                            StatPill(label = "AGI", value = "${currentArmor.statsAgility}", color = themeColor)
                            StatPill(label = "PWR", value = "${currentArmor.statsPower}", color = themeColor)
                            if (currentArmor.weaponName != null) {
                                StatPill(label = "WEAPON", value = "GREATSWORD", color = Color(0xFFFFB703))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // [ EQUIP ] / [ EQUIPPED ] / [ UNEQUIP ] Action Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCurrentEquipped) {
                        // Already Equipped state: Allow Unequip
                        Button(
                            onClick = { onEquipPressed(currentArmor) },
                            enabled = !isScanning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = themeColor.copy(alpha = 0.3f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("re_equip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Re-scan",
                                tint = themeColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isScanning) "SCANNING..." else "RE-EQUIP SCAN",
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = onUnequipPressed,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4D4D)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF4D4D).copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(0.7f)
                                .height(48.dp)
                                .testTag("unequip_button")
                        ) {
                            Text(
                                text = "UNEQUIP",
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        // Main [ EQUIP ] button
                        Button(
                            onClick = { onEquipPressed(currentArmor) },
                            enabled = !isScanning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = themeColor,
                                contentColor = Color(0xFF020617)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = themeColor, spotColor = themeColor)
                                .testTag("equip_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Equip",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isScanning) "SCANNING BODY..." else "[ EQUIP ${currentArmor.name} ]",
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "$label ",
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
