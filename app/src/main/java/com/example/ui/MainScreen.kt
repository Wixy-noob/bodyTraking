package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.ArmorDefinition
import com.example.model.ArmorType
import com.example.model.PoseData
import com.example.rendering.ArmorRenderer
import com.example.tracking.DemoPoseGenerator
import com.example.tracking.PoseDetectorManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    // Web vs Native AR mode toggle
    var isWebMode by remember { mutableStateOf(false) }

    if (isWebMode) {
        WebARScreen(onSwitchToNative = { isWebMode = false })
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Camera Permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Pose Tracking States
    var cameraPose by remember { mutableStateOf(PoseData.EMPTY) }
    var isDemoMode by remember { mutableStateOf(!hasCameraPermission) }
    var demoPoseIndex by remember { mutableIntStateOf(0) }
    val demoStates = remember { DemoPoseGenerator.AnimationState.values() }
    val demoGenerator = remember { DemoPoseGenerator() }

    // If camera permission granted, default to camera mode, but user can toggle anytime
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            isDemoMode = false
        }
    }

    // Demo Pose Animation Loop (for emulators or demo pose preview)
    var demoPose by remember { mutableStateOf(PoseData.EMPTY) }
    LaunchedEffect(isDemoMode, demoPoseIndex) {
        if (isDemoMode) {
            val currentState = demoStates[demoPoseIndex % demoStates.size]
            var lastTime = System.currentTimeMillis()
            while (true) {
                val now = System.currentTimeMillis()
                val deltaSec = (now - lastTime).coerceIn(1, 100) / 1000f
                lastTime = now
                demoPose = demoGenerator.generate(deltaSec, currentState)
                delay(16) // ~60 FPS
            }
        }
    }

    val activePose = if (isDemoMode) demoPose else cameraPose

    // ML Kit Camera Analyzer
    val poseDetectorManager = remember {
        PoseDetectorManager { detected ->
            cameraPose = detected
        }
    }

    // Armor States
    val allArmors = remember { ArmorDefinition.ALL_ARMORS }
    var selectedArmorIndex by remember { mutableIntStateOf(0) }
    var equippedArmor by remember { mutableStateOf<ArmorDefinition?>(allArmors.first()) }
    var isEquipped by remember { mutableStateOf(true) }
    var isArmorPanelOpen by remember { mutableStateOf(false) }
    var showSkeletonDebug by remember { mutableStateOf(false) }

    // Equip Scanning Animation State
    var isEquipScanning by remember { mutableStateOf(false) }
    val scanAnimatable = remember { Animatable(0f) }
    var scanNotificationText by remember { mutableStateOf<String?>(null) }

    // Pulse timer for glowing emissive shaders
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_time")
    val pulseTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_float"
    )

    // Renderer instance
    val armorRenderer = remember { ArmorRenderer() }

    // Trigger Equip Animation
    fun startEquipScan(armor: ArmorDefinition) {
        equippedArmor = armor
        isArmorPanelOpen = false
        coroutineScope.launch {
            isEquipScanning = true
            scanAnimatable.snapTo(0f)
            scanNotificationText = "SCANNING BODY: ${armor.name}"

            // 1. Scanning effect moves from feet to head
            scanAnimatable.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing)
            )

            // 2. Lock armor solid
            isEquipScanning = false
            isEquipped = true
            scanNotificationText = "ARMOR EQUIPPED: ${armor.name}"
            delay(2200)
            scanNotificationText = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
    ) {
        // -------------------------------------------------------------
        // BACKGROUND LAYER: Live Camera Preview or Sci-Fi Grid Canvas
        // -------------------------------------------------------------
        if (hasCameraPermission && !isDemoMode) {
            CameraPreviewView(
                poseDetectorManager = poseDetectorManager,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    // If camera hardware fails, fallback to demo mode seamlessly
                    isDemoMode = true
                }
            )
        } else {
            // Simulated Sci-Fi Holo-Deck backdrop for demo mode
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Subtle cyberpunk grid lines
                        val gridSize = 40.dp.toPx()
                        val lineColor = Color(0x1800F0FF)
                        var x = 0f
                        while (x <= size.width) {
                            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1f)
                            x += gridSize
                        }
                        var y = 0f
                        while (y <= size.height) {
                            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1f)
                            y += gridSize
                        }
                        // Vignette
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color(0xCC030712)),
                                center = center,
                                radius = size.maxDimension * 0.7f
                            )
                        )
                    }
            )
        }

        // -------------------------------------------------------------
        // AR OVERLAY LAYER: Real-time Multi-Segment Armor Canvas
        // -------------------------------------------------------------
        if (equippedArmor != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("ar_armor_canvas")
            ) {
                armorRenderer.render(
                    drawScope = this,
                    pose = activePose,
                    armor = equippedArmor!!,
                    isEquipped = isEquipped,
                    equipProgress = scanAnimatable.value,
                    isEquipScanning = isEquipScanning,
                    pulseTime = pulseTime,
                    showSkeletonDebug = showSkeletonDebug
                )
            }
        }

        // -------------------------------------------------------------
        // TOP HUD LAYER: Status indicator & [ ARMOR ] toggle button
        // -------------------------------------------------------------
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Left: Tracking Status Badge
            TrackingStatusHud(
                isDetected = activePose.isDetected,
                confidence = activePose.confidence,
                isDemoMode = isDemoMode
            )

            // Right: Web Mode toggle & Futuristic [ ARMOR ] Menu Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button to toggle Website / Web AR Mode
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xCC090D16),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { isWebMode = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "🌐 WEB",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                ArmorHudButton(
                    onClick = { isArmorPanelOpen = !isArmorPanelOpen },
                    isSelected = isArmorPanelOpen,
                    currentArmor = if (isEquipped) equippedArmor else null
                )
            }
        }

        // -------------------------------------------------------------
        // HUD NOTIFICATION BANNER (When scanning or equipped)
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = scanNotificationText != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 70.dp)
        ) {
            scanNotificationText?.let { text ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xEE090D16),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HudNeonCyan),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isEquipScanning) Icons.Default.FlashOn else Icons.Default.Check,
                            contentDescription = null,
                            tint = HudNeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = text,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // BOTTOM HUD LAYER: Controls Toolbar
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp)
        ) {
            HudControlsBar(
                isDemoMode = isDemoMode,
                onToggleDemoMode = {
                    if (!hasCameraPermission) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    isDemoMode = !isDemoMode
                },
                showSkeleton = showSkeletonDebug,
                onToggleSkeleton = { showSkeletonDebug = !showSkeletonDebug },
                demoStateName = demoStates[demoPoseIndex % demoStates.size].name.replace("_", " "),
                onNextDemoPose = { demoPoseIndex++ }
            )
        }

        // -------------------------------------------------------------
        // ARMOR SELECTOR PANEL (HUD Drawer / Glass Popup)
        // -------------------------------------------------------------
        ArmorSelectorPanel(
            isVisible = isArmorPanelOpen,
            onClose = { isArmorPanelOpen = false },
            allArmors = allArmors,
            selectedArmorIndex = selectedArmorIndex,
            onSelectArmorIndex = { nextIndex ->
                selectedArmorIndex = nextIndex
                // Live preview swap on body when navigating
                val selectedArmor = allArmors[nextIndex]
                if (isEquipped) {
                    equippedArmor = selectedArmor
                }
            },
            equippedArmor = equippedArmor,
            isEquipped = isEquipped,
            isScanning = isEquipScanning,
            onEquipPressed = { armorToEquip ->
                startEquipScan(armorToEquip)
            },
            onUnequipPressed = {
                isEquipped = false
                scanNotificationText = "ARMOR UNEQUIPPED"
                coroutineScope.launch {
                    delay(1500)
                    scanNotificationText = null
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
