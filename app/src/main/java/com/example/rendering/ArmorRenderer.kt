package com.example.rendering

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.example.model.ArmorDefinition
import com.example.model.ArmorType
import com.example.model.LandmarkPoint
import com.example.model.PoseData
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * High-performance AR Armor Canvas Renderer that renders multi-segmented, articulated
 * 2D/3D procedural armor following body landmarks with real-time transforms and equip animations.
 */
class ArmorRenderer {

    // Glow paint for emissive lines
    private val glowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun render(
        drawScope: DrawScope,
        pose: PoseData,
        armor: ArmorDefinition,
        isEquipped: Boolean,
        equipProgress: Float, // 0f to 1f (0 = feet, 1 = head fully materialized)
        isEquipScanning: Boolean,
        pulseTime: Float, // animated seconds for glowing energy pulses
        showSkeletonDebug: Boolean = false
    ) {
        if (!pose.isDetected) return
        if (!isEquipped && !isEquipScanning) return

        val width = drawScope.size.width
        val height = drawScope.size.height

        // Calculate reference scales based on detected shoulders & torso
        val lShoulder = pose.leftShoulder.toOffset(width, height)
        val rShoulder = pose.rightShoulder.toOffset(width, height)
        val lHip = pose.leftHip.toOffset(width, height)
        val rHip = pose.rightHip.toOffset(width, height)

        val shoulderDist = (rShoulder - lShoulder).getDistance().coerceIn(width * 0.15f, width * 0.70f)
        val torsoLength = ((lHip + rHip) * 0.5f - (lShoulder + rShoulder) * 0.5f).getDistance()
            .coerceIn(height * 0.15f, height * 0.55f)

        val scaleRef = shoulderDist / (width * 0.35f)

        // The equip scanning vertical threshold on screen
        // Scan travels from feet (y ~ 0.95) up to head (y ~ 0.15)
        val scanY = if (isEquipScanning) {
            height * (0.95f - equipProgress * 0.82f)
        } else {
            -100f // everything is visible
        }

        // Helper to check if a body point is materialized yet
        fun isPartMaterialized(y: Float): Boolean {
            if (!isEquipScanning) return isEquipped
            return y >= scanY - 30f
        }

        fun isPartAtScanFront(y: Float): Boolean {
            if (!isEquipScanning) return false
            return kotlin.math.abs(y - scanY) < 55f
        }

        // -------------------------------------------------------------
        // LAYER 1: Background Elements (Cape / Back Thrusters)
        // -------------------------------------------------------------
        if (armor.hasCape && isPartMaterialized((lShoulder.y + rShoulder.y) * 0.5f)) {
            renderCape(drawScope, lShoulder, rShoulder, lHip, rHip, pulseTime, armor, isPartAtScanFront(lShoulder.y))
        }

        // -------------------------------------------------------------
        // LAYER 1.5: Anatomical Compression Undersuit / Arming Doublet
        // Hugs real body proportions between joints so it looks like authentic wearable armor
        // -------------------------------------------------------------
        renderAnatomicalUndersuit(
            drawScope = drawScope,
            pose = pose,
            lShoulder = lShoulder,
            rShoulder = rShoulder,
            lHip = lHip,
            rHip = rHip,
            shoulderDist = shoulderDist,
            torsoLength = torsoLength,
            scale = scaleRef,
            armor = armor,
            time = pulseTime,
            isPartMaterialized = ::isPartMaterialized
        )

        // -------------------------------------------------------------
        // LAYER 2: Lower Body (Legs: Thighs, Knees, Calves, Boots)
        // -------------------------------------------------------------
        if (pose.leftKnee.isValid() && pose.leftAnkle.isValid()) {
            val lKnee = pose.leftKnee.toOffset(width, height)
            val lAnkle = pose.leftAnkle.toOffset(width, height)

            if (isPartMaterialized(lHip.y)) {
                renderThighArmor(drawScope, lHip, lKnee, scaleRef, armor, pulseTime, isPartAtScanFront(lKnee.y))
            }
            if (isPartMaterialized(lKnee.y)) {
                renderKneeGuard(drawScope, lKnee, scaleRef, armor, pulseTime, isPartAtScanFront(lKnee.y))
            }
            if (isPartMaterialized(lAnkle.y)) {
                renderCalfBootArmor(drawScope, lKnee, lAnkle, scaleRef, armor, pulseTime, isPartAtScanFront(lAnkle.y))
            }
        }

        if (pose.rightKnee.isValid() && pose.rightAnkle.isValid()) {
            val rKnee = pose.rightKnee.toOffset(width, height)
            val rAnkle = pose.rightAnkle.toOffset(width, height)

            if (isPartMaterialized(rHip.y)) {
                renderThighArmor(drawScope, rHip, rKnee, scaleRef, armor, pulseTime, isPartAtScanFront(rKnee.y))
            }
            if (isPartMaterialized(rKnee.y)) {
                renderKneeGuard(drawScope, rKnee, scaleRef, armor, pulseTime, isPartAtScanFront(rKnee.y))
            }
            if (isPartMaterialized(rAnkle.y)) {
                renderCalfBootArmor(drawScope, rKnee, rAnkle, scaleRef, armor, pulseTime, isPartAtScanFront(rAnkle.y))
            }
        }

        // -------------------------------------------------------------
        // LAYER 3: Torso (Waist Faulds, Abdomen, Chestplate, Collar)
        // -------------------------------------------------------------
        val midShoulder = (lShoulder + rShoulder) * 0.5f
        val midHip = (lHip + rHip) * 0.5f

        if (isPartMaterialized(midHip.y)) {
            renderWaistFaulds(drawScope, lHip, rHip, shoulderDist, scaleRef, armor, pulseTime, isPartAtScanFront(midHip.y))
        }

        if (isPartMaterialized(midShoulder.y)) {
            renderChestArmor(
                drawScope,
                lShoulder,
                rShoulder,
                lHip,
                rHip,
                shoulderDist,
                torsoLength,
                scaleRef,
                armor,
                pulseTime,
                isPartAtScanFront(midShoulder.y)
            )
            // Neck Gorget connects chestplate firmly to the helmet
            val headPosForNeck = pose.headCenter.toOffset(width, height)
            renderGorgetNeckArmor(
                drawScope,
                midShoulder,
                headPosForNeck,
                shoulderDist,
                scaleRef,
                armor,
                pulseTime,
                isPartAtScanFront(midShoulder.y)
            )
        }

        // -------------------------------------------------------------
        // LAYER 4: Arms (Upper Arm, Elbow, Forearm, Gauntlets)
        // -------------------------------------------------------------
        if (pose.leftElbow.isValid()) {
            val lElbow = pose.leftElbow.toOffset(width, height)
            if (isPartMaterialized(lElbow.y)) {
                renderLimbSegment(drawScope, lShoulder, lElbow, scaleRef * 0.75f, armor, pulseTime, isPartAtScanFront(lElbow.y))
                renderCouterElbowGuard(drawScope, lElbow, scaleRef * 0.72f, armor, pulseTime, isPartAtScanFront(lElbow.y))
            }

            if (pose.leftWrist.isValid()) {
                val lWrist = pose.leftWrist.toOffset(width, height)
                if (isPartMaterialized(lWrist.y)) {
                    renderForearmVambrace(drawScope, lElbow, lWrist, scaleRef * 0.72f, armor, pulseTime, isPartAtScanFront(lWrist.y))
                    renderGauntlet(drawScope, lWrist, (lWrist - lElbow), scaleRef * 0.7f, armor, pulseTime, isPartAtScanFront(lWrist.y))
                }
            }
        }

        if (pose.rightElbow.isValid()) {
            val rElbow = pose.rightElbow.toOffset(width, height)
            if (isPartMaterialized(rElbow.y)) {
                renderLimbSegment(drawScope, rShoulder, rElbow, scaleRef * 0.75f, armor, pulseTime, isPartAtScanFront(rElbow.y))
                renderCouterElbowGuard(drawScope, rElbow, scaleRef * 0.72f, armor, pulseTime, isPartAtScanFront(rElbow.y))
            }

            if (pose.rightWrist.isValid()) {
                val rWrist = pose.rightWrist.toOffset(width, height)
                if (isPartMaterialized(rWrist.y)) {
                    renderForearmVambrace(drawScope, rElbow, rWrist, scaleRef * 0.72f, armor, pulseTime, isPartAtScanFront(rWrist.y))
                    renderGauntlet(drawScope, rWrist, (rWrist - rElbow), scaleRef * 0.7f, armor, pulseTime, isPartAtScanFront(rWrist.y))
                }
            }
        }

        // -------------------------------------------------------------
        // LAYER 5: Pauldrons (Shoulder Guards)
        // -------------------------------------------------------------
        if (isPartMaterialized(lShoulder.y)) {
            val lArmAngle = if (pose.leftElbow.isValid()) {
                val el = pose.leftElbow.toOffset(width, height)
                Math.toDegrees(atan2((el.y - lShoulder.y).toDouble(), (el.x - lShoulder.x).toDouble())).toFloat() - 90f
            } else 0f
            renderPauldrons(drawScope, lShoulder, lArmAngle, isLeft = true, scaleRef, armor, pulseTime, isPartAtScanFront(lShoulder.y))
        }

        if (isPartMaterialized(rShoulder.y)) {
            val rArmAngle = if (pose.rightElbow.isValid()) {
                val el = pose.rightElbow.toOffset(width, height)
                Math.toDegrees(atan2((el.y - rShoulder.y).toDouble(), (el.x - rShoulder.x).toDouble())).toFloat() - 90f
            } else 0f
            renderPauldrons(drawScope, rShoulder, rArmAngle, isLeft = false, scaleRef, armor, pulseTime, isPartAtScanFront(rShoulder.y))
        }

        // -------------------------------------------------------------
        // LAYER 6: Head & Helmet (Cowl / Visor)
        // -------------------------------------------------------------
        val headPos = pose.headCenter.toOffset(width, height)
        if (isPartMaterialized(headPos.y)) {
            val headAngle = pose.headAngle
            renderHelmet(
                drawScope,
                headPos,
                headAngle,
                shoulderDist * 0.45f,
                scaleRef,
                armor,
                pulseTime,
                isPartAtScanFront(headPos.y)
            )
        }

        // -------------------------------------------------------------
        // LAYER 7: Weapon / Accessories (Greatsword for Dark Knight)
        // -------------------------------------------------------------
        if (armor.hasWeapon && isPartMaterialized(rShoulder.y)) {
            val handPos = if (pose.rightWrist.isValid()) pose.rightWrist.toOffset(width, height) else rShoulder + Offset(40f, 120f)
            renderGreatsword(drawScope, handPos, rShoulder, scaleRef, armor, pulseTime, isPartAtScanFront(handPos.y))
        }

        // -------------------------------------------------------------
        // LAYER 8: Equip Scanning Plane & Laser Grid VFX
        // -------------------------------------------------------------
        if (isEquipScanning) {
            renderScanLineVfx(drawScope, scanY, width, armor, pulseTime)
        }

        // Optional debug skeleton
        if (showSkeletonDebug) {
            renderSkeletonDebug(drawScope, pose, width, height)
        }
    }

    // -----------------------------------------------------------------
    // ARMOR PART RENDERERS
    // -----------------------------------------------------------------

    private fun renderCape(
        drawScope: DrawScope,
        lShoulder: Offset,
        rShoulder: Offset,
        lHip: Offset,
        rHip: Offset,
        time: Float,
        armor: ArmorDefinition,
        isHologram: Boolean
    ) {
        val midShoulder = (lShoulder + rShoulder) * 0.5f
        val capeBottomY = (lHip.y + rHip.y) * 0.5f + 260f

        val sway1 = sin(time * 2.5f) * 18f
        val sway2 = cos(time * 2.0f) * 14f

        val capePath = Path().apply {
            moveTo(lShoulder.x - 20f, lShoulder.y - 10f)
            cubicTo(
                lShoulder.x - 70f, (lShoulder.y + capeBottomY) * 0.5f,
                lShoulder.x - 85f + sway1, capeBottomY - 50f,
                lShoulder.x - 70f + sway1, capeBottomY
            )
            quadTo(
                midShoulder.x + sway2, capeBottomY + 25f,
                rShoulder.x + 70f + sway2, capeBottomY
            )
            cubicTo(
                rShoulder.x + 85f + sway2, capeBottomY - 50f,
                rShoulder.x + 70f, (rShoulder.y + capeBottomY) * 0.5f,
                rShoulder.x + 20f, rShoulder.y - 10f
            )
            close()
        }

        val capeColor = if (isHologram) {
            armor.emissiveColor.copy(alpha = 0.65f)
        } else {
            Color(0xFF0D1117)
        }

        val capeHighlight = if (isHologram) {
            armor.emissiveColor.copy(alpha = 0.3f)
        } else {
            Color(0xFF1E2633)
        }

        drawScope.drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                // Convert android.graphics.Path or draw using nativeCanvas
            },
            color = capeColor
        )

        drawScope.drawContext.canvas.nativeCanvas.drawPath(
            capePath,
            Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = capeColor.toArgb()
            }
        )

        // Cape folds shadow lines
        drawScope.drawContext.canvas.nativeCanvas.drawPath(
            capePath,
            Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = capeHighlight.toArgb()
            }
        )
    }

    private fun renderChestArmor(
        drawScope: DrawScope,
        lShoulder: Offset,
        rShoulder: Offset,
        lHip: Offset,
        rHip: Offset,
        shoulderDist: Float,
        torsoLength: Float,
        scale: Float,
        armor: ArmorDefinition,
        time: Float,
        isHologram: Boolean
    ) {
        val midShoulder = (lShoulder + rShoulder) * 0.5f
        val midHip = (lHip + rHip) * 0.5f
        val chestCenter = midShoulder + (midHip - midShoulder) * 0.38f

        val dx = rShoulder.x - lShoulder.x
        val dy = rShoulder.y - lShoulder.y
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

        drawScope.rotate(degrees = angle, pivot = chestCenter) {
            val chestW = shoulderDist * 0.78f
            val chestH = torsoLength * 0.68f

            val basePaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = if (isHologram) armor.emissiveColor.copy(alpha = 0.5f).toArgb() else armor.primaryColor.toArgb()
            }

            val strokePaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 4f * scale
                color = if (isHologram) armor.emissiveColor.toArgb() else armor.accentColor.toArgb()
            }

            // Chestplate main body shape (tapered shield-like carapace)
            val chestPath = Path().apply {
                moveTo(chestCenter.x - chestW * 0.55f, chestCenter.y - chestH * 0.45f)
                lineTo(chestCenter.x + chestW * 0.55f, chestCenter.y - chestH * 0.45f)
                lineTo(chestCenter.x + chestW * 0.45f, chestCenter.y + chestH * 0.15f)
                lineTo(chestCenter.x + chestW * 0.25f, chestCenter.y + chestH * 0.52f)
                lineTo(chestCenter.x, chestCenter.y + chestH * 0.62f)
                lineTo(chestCenter.x - chestW * 0.25f, chestCenter.y + chestH * 0.52f)
                lineTo(chestCenter.x - chestW * 0.45f, chestCenter.y + chestH * 0.15f)
                close()
            }

            drawScope.drawContext.canvas.nativeCanvas.drawPath(chestPath, basePaint)
            drawScope.drawContext.canvas.nativeCanvas.drawPath(chestPath, strokePaint)

            if (armor.type == ArmorType.DARK_KNIGHT) {
                // Sculpted muscle & plate rib lines for medieval fantasy plate
                val darkDetailPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * scale
                    color = Color(0xFF1E293B).toArgb()
                }
                // Center ridge (prow of the cuirass)
                drawScope.drawLine(
                    color = Color(0xFF0F172A),
                    start = Offset(chestCenter.x, chestCenter.y - chestH * 0.42f),
                    end = Offset(chestCenter.x, chestCenter.y + chestH * 0.58f),
                    strokeWidth = 4f * scale
                )
                // Pectoral contours
                drawScope.drawArc(
                    color = Color(0xFF94A3B8),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(chestCenter.x - chestW * 0.45f, chestCenter.y - chestH * 0.35f),
                    size = Size(chestW * 0.42f, chestH * 0.38f),
                    style = Stroke(width = 3.5f * scale)
                )
                drawScope.drawArc(
                    color = Color(0xFF94A3B8),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(chestCenter.x + chestW * 0.03f, chestCenter.y - chestH * 0.35f),
                    size = Size(chestW * 0.42f, chestH * 0.38f),
                    style = Stroke(width = 3.5f * scale)
                )
                // Chiseled abdominal plates
                for (i in 0..2) {
                    val yOff = chestCenter.y + chestH * (0.12f + i * 0.13f)
                    val wOff = chestW * (0.34f - i * 0.05f)
                    drawScope.drawLine(
                        color = Color(0xFF334155),
                        start = Offset(chestCenter.x - wOff, yOff),
                        end = Offset(chestCenter.x + wOff, yOff),
                        strokeWidth = 3f * scale
                    )
                }
            } else {
                // CYBER KNIGHT: Sci-Fi glowing arc reactor & cyan circuit conduits
                val glowAlpha = (sin(time * 3.5f) * 0.25f + 0.75f).coerceIn(0.5f, 1.0f)
                val neonBlue = armor.emissiveColor.copy(alpha = glowAlpha)

                // High-tech angular chest panels
                val innerPanelPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.FILL
                    color = Color(0xFF0F172A).toArgb()
                }
                val cyberCorePath = Path().apply {
                    moveTo(chestCenter.x, chestCenter.y - chestH * 0.30f)
                    lineTo(chestCenter.x + chestW * 0.26f, chestCenter.y - chestH * 0.18f)
                    lineTo(chestCenter.x + chestW * 0.18f, chestCenter.y + chestH * 0.15f)
                    lineTo(chestCenter.x, chestCenter.y + chestH * 0.30f)
                    lineTo(chestCenter.x - chestW * 0.18f, chestCenter.y + chestH * 0.15f)
                    lineTo(chestCenter.x - chestW * 0.26f, chestCenter.y - chestH * 0.18f)
                    close()
                }
                drawScope.drawContext.canvas.nativeCanvas.drawPath(cyberCorePath, innerPanelPaint)

                // Glowing chest chevron / reactor arcs (reference visual matching)
                val arcPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 5f * scale
                    color = neonBlue.toArgb()
                    strokeCap = Paint.Cap.ROUND
                }
                val arcPath = Path().apply {
                    moveTo(chestCenter.x - chestW * 0.32f, chestCenter.y - chestH * 0.20f)
                    quadTo(chestCenter.x - chestW * 0.14f, chestCenter.y - chestH * 0.05f, chestCenter.x, chestCenter.y - chestH * 0.10f)
                    quadTo(chestCenter.x + chestW * 0.14f, chestCenter.y - chestH * 0.05f, chestCenter.x + chestW * 0.32f, chestCenter.y - chestH * 0.20f)
                }
                drawScope.drawContext.canvas.nativeCanvas.drawPath(arcPath, arcPaint)

                // Center energy core gem
                drawScope.drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, neonBlue, Color.Transparent),
                        center = Offset(chestCenter.x, chestCenter.y - chestH * 0.05f),
                        radius = 24f * scale
                    ),
                    radius = 20f * scale,
                    center = Offset(chestCenter.x, chestCenter.y - chestH * 0.05f)
                )

                // Lower abdominal cyber lines
                val lowerCyberPath = Path().apply {
                    moveTo(chestCenter.x - chestW * 0.20f, chestCenter.y + chestH * 0.18f)
                    lineTo(chestCenter.x - chestW * 0.08f, chestCenter.y + chestH * 0.35f)
                    lineTo(chestCenter.x, chestCenter.y + chestH * 0.40f)
                    lineTo(chestCenter.x + chestW * 0.08f, chestCenter.y + chestH * 0.35f)
                    lineTo(chestCenter.x + chestW * 0.20f, chestCenter.y + chestH * 0.18f)
                }
                drawScope.drawContext.canvas.nativeCanvas.drawPath(lowerCyberPath, arcPaint)
            }
        }
    }

    private fun renderPauldrons(
        drawScope: DrawScope,
        shoulderPos: Offset,
        armAngleDegrees: Float,
        isLeft: Boolean,
        scale: Float,
        armor: ArmorDefinition,
        time: Float,
        isHologram: Boolean
    ) {
        val sign = if (isLeft) -1f else 1f

        drawScope.rotate(degrees = armAngleDegrees * 0.5f, pivot = shoulderPos) {
            val pw = 65f * scale
            val ph = 70f * scale

            val pPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = if (isHologram) armor.emissiveColor.copy(alpha = 0.5f).toArgb() else armor.primaryColor.toArgb()
            }
            val strokePaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 3f * scale
                color = if (isHologram) armor.emissiveColor.toArgb() else armor.accentColor.toArgb()
            }

            if (armor.type == ArmorType.DARK_KNIGHT) {
                // Tiered Gothic Pointed Pauldron (large curved flared plates with points)
                for (tier in 0..2) {
                    val offsetY = tier * 18f * scale
                    val pauldronPath = Path().apply {
                        moveTo(shoulderPos.x - sign * 8f * scale, shoulderPos.y - 30f * scale + offsetY)
                        lineTo(shoulderPos.x + sign * pw * 1.15f, shoulderPos.y - 25f * scale + offsetY)
                        lineTo(shoulderPos.x + sign * (pw * 1.35f), shoulderPos.y + 10f * scale + offsetY) // outward spike
                        lineTo(shoulderPos.x + sign * (pw * 0.7f), shoulderPos.y + 35f * scale + offsetY)
                        lineTo(shoulderPos.x - sign * 15f * scale, shoulderPos.y + 20f * scale + offsetY)
                        close()
                    }
                    drawScope.drawContext.canvas.nativeCanvas.drawPath(pauldronPath, pPaint)
                    drawScope.drawContext.canvas.nativeCanvas.drawPath(pauldronPath, strokePaint)
                }
            } else {
                // CYBER KNIGHT: Flared Aerodynamic Wing Pauldron with glowing cyan edge
                val wingPath = Path().apply {
                    moveTo(shoulderPos.x - sign * 5f * scale, shoulderPos.y - 35f * scale)
                    lineTo(shoulderPos.x + sign * pw * 0.8f, shoulderPos.y - 45f * scale)
                    lineTo(shoulderPos.x + sign * pw * 1.45f, shoulderPos.y - 10f * scale) // aggressive wing flare
                    lineTo(shoulderPos.x + sign * pw * 0.95f, shoulderPos.y + 25f * scale)
                    lineTo(shoulderPos.x + sign * pw * 0.3f, shoulderPos.y + 35f * scale)
                    lineTo(shoulderPos.x - sign * 10f * scale, shoulderPos.y + 15f * scale)
                    close()
                }
                drawScope.drawContext.canvas.nativeCanvas.drawPath(wingPath, pPaint)
                drawScope.drawContext.canvas.nativeCanvas.drawPath(wingPath, strokePaint)

                // Glowing edge
                val glowPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 4f * scale
                    color = armor.emissiveColor.toArgb()
                    strokeCap = Paint.Cap.ROUND
                }
                val edgePath = Path().apply {
                    moveTo(shoulderPos.x + sign * pw * 0.3f, shoulderPos.y - 40f * scale)
                    lineTo(shoulderPos.x + sign * pw * 1.45f, shoulderPos.y - 10f * scale)
                    lineTo(shoulderPos.x + sign * pw * 0.95f, shoulderPos.y + 25f * scale)
                }
                drawScope.drawContext.canvas.nativeCanvas.drawPath(edgePath, glowPaint)
            }
        }
    }

    private fun renderLimbSegment(
        drawScope: DrawScope,
        p1: Offset,
        p2: Offset,
        widthScale: Float,
        armor: ArmorDefinition,
        time: Float,
        isHologram: Boolean
    ) {
        val dist = (p2 - p1).getDistance()
        if (dist < 10f) return

        val angle = Math.toDegrees(atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())).toFloat() - 90f
        val mid = (p1 + p2) * 0.5f

        drawScope.rotate(degrees = angle, pivot = mid) {
            val w = 36f * widthScale
            val h = dist * 0.82f

            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = if (isHologram) armor.emissiveColor.copy(alpha = 0.5f).toArgb() else armor.primaryColor.toArgb()
            }
            val stroke = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 3f * widthScale
                color = if (isHologram) armor.emissiveColor.toArgb() else armor.accentColor.toArgb()
            }

            val path = Path().apply {
                moveTo(mid.x - w * 0.45f, mid.y - h * 0.5f)
                lineTo(mid.x + w * 0.45f, mid.y - h * 0.5f)
                lineTo(mid.x + w * 0.35f, mid.y + h * 0.5f)
                lineTo(mid.x - w * 0.35f, mid.y + h * 0.5f)
                close()
            }

            drawScope.drawContext.canvas.nativeCanvas.drawPath(path, paint)
            drawScope.drawContext.canvas.nativeCanvas.drawPath(path, stroke)

            // Sci-fi circuit strip on Cyber Knight
            if (armor.type == ArmorType.CYBER_KNIGHT) {
                drawScope.drawLine(
                    color = armor.emissiveColor,
                    start = Offset(mid.x, mid.y - h * 0.4f),
                    end = Offset(mid.x, mid.y + h * 0.4f),
                    strokeWidth = 3f * widthScale
                )
            }
        }
    }

    private fun renderForearmVambrace(
        drawScope: DrawScope,
        p1: Offset,
        p2: Offset,
        widthScale: Float,
        armor: ArmorDefinition,
        time: Float,
        isHologram: Boolean
    ) {
        val dist = (p2 - p1).getDistance()
        if (dist < 10f) return

        val angle = Math.toDegrees(atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())).toFloat() - 90f
        val mid = (p1 + p2) * 0.5f

        drawScope.rotate(degrees = angle, pivot = mid) {
            val w = 38f * widthScale
            val h = dist * 0.85f

            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = if (isHologram) armor.emissiveColor.copy(alpha = 0.5f).toArgb() else armor.primaryColor.toArgb()
            }
            val stroke = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 3f * widthScale
                color = if (isHologram) armor.emissiveColor.toArgb() else armor.accentColor.toArgb()
            }

            val path = Path().apply {
                moveTo(mid.x - w * 0.5f, mid.y - h * 0.5f)
                lineTo(mid.x + w * 0.5f, mid.y - h * 0.5f)
                lineTo(mid.x + w * 0.62f, mid.y - h * 0.1f) // forearm armor flange
                lineTo(mid.x + w * 0.38f, mid.y + h * 0.5f)
                lineTo(mid.x - w * 0.38f, mid.y + h * 0.5f)
                lineTo(mid.x - w * 0.62f, mid.y - h * 0.1f)
                close()
            }

            drawScope.drawContext.canvas.nativeCanvas.drawPath(path, paint)
            drawScope.drawContext.canvas.nativeCanvas.drawPath(path, stroke)

            if (armor.type == ArmorType.CYBER_KNIGHT) {
                // Dual glowing energy strips along forearm
                val glowColor = armor.emissiveColor
                drawScope.drawLine(
                    color = glowColor,
                    start = Offset(mid.x - w * 0.25f, mid.y - h * 0.35f),
                    end = Offset(mid.x - w * 0.20f, mid.y + h * 0.35f),
                    strokeWidth = 3.5f * widthScale,
                    cap = StrokeCap.Round
                )
                drawScope.drawLine(
                    color = glowColor,
                    start = Offset(mid.x + w * 0.25f, mid.y - h * 0.35f),
                    end = Offset(mid.x + w * 0.20f, mid.y + h * 0.35f),
                    strokeWidth = 3.5f * widthScale,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    private fun renderGauntlet(
        drawScope: DrawScope,
        wristPos: Offset,
        dir: Offset,
        scale: Float,
        armor: ArmorDefinition,
        time: Float,
        isHologram: Boolean
    ) {
        val radius = 18f * scale
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = if (isHologram) armor.emissiveColor.copy(alpha = 0.5f).toArgb() else armor.secondaryColor.toArgb()
        }
        val stroke = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * scale
            color = if (isHologram) armor.emissiveColor.toArgb() else armor.primaryColor.toArgb()
        }

        drawScope.drawCircle(
            color = if (isHologram) armor.emissiveColor.copy(alpha = 0.5f) else armor.secondaryColor,
            radius = radius,
            center = wristPos
        )
        drawScope.drawCircle(
            color = if (isHologram) armor.emissiveColor else armor.primaryColor,
            radius = radius,
            center = wristPos,
            style = Stroke(width = 2.5f * scale)
        )
    }

    private fun renderWaistFaulds(
        drawScope: DrawScope,
        lHip: Offset,
        rHip: Offset,
        shoulderDist: Float,
        scale: Float,
        armor: ArmorDefinition,
        time: Float,
        isHologram: Boolean
    ) {
        val midHip = (lHip + rHip) * 0.5f
        val w = shoulderDist * 0.65f
        val h = 65f * scale

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = if (isHologram) armor.emissiveColor.copy(alpha = 0.5f).toArgb() else armor.secondaryColor.toArgb()
        }
        val stroke = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3f * scale
            color = if (isHologram) armor.emissiveColor.toArgb() else armor.accentColor.toArgb()
        }

        // Tassets / Groin protector
        val fauldsPath = Path().apply {
            moveTo(midHip.x - w * 0.5f, midHip.y - 10f * scale)
            lineTo(midHip.x + w * 0.5f, midHip.y - 10f * scale)
            lineTo(midHip.x + w * 0.45f, midHip.y + h * 0.6f)
            lineTo(midHip.x + w * 0.15f, midHip.y + h)
            lineTo(midHip.x, midHip.y + h * 0.75f)
            lineTo(midHip.x - w * 0.15f, midHip.y + h)
            lineTo(midHip.x - w * 0.45f, midHip.y + h * 0.6f)
            close()
        }

        drawScope.drawContext.canvas.nativeCanvas.drawPath(fauldsPath, paint)
        drawScope.drawContext.canvas.nativeCanvas.drawPath(fauldsPath, stroke)

        if (armor.type == ArmorType.CYBER_KNIGHT) {
            // Glowing hip nodes
            val glowColor = armor.emissiveColor
            drawScope.drawCircle(glowColor, radius = 6f * scale, center = Offset(midHip.x - w * 0.35f, midHip.y + 15f * scale))
            drawScope.drawCircle(glowColor, radius = 6f * scale, center = Offset(midHip.x + w * 0.35f, midHip.y + 15f * scale))
        }
    }

    private fun renderThighArmor(
        drawScope: DrawScope,
        hip: Offset,
        knee: Offset,
        scale: Float,
        armor: ArmorDefinition,
        time: Float,
        isHologram: Boolean
    ) {
        val dist = (knee - hip).getDistance()
        if (dist < 10f) return

        val angle = Math.toDegrees(atan2((knee.y - hip.y).toDouble(), (knee.x - hip.x).toDouble())).toFloat() - 90f
        val mid = (hip + knee) * 0.5f

        drawScope.rotate(degrees = angle, pivot = mid) {
            val w = 48f * scale
            val h = dist * 0.80f

            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = if (isHologram) armor.emissiveColor.copy(alpha = 0.5f).toArgb() else armor.primaryColor.toArgb()
            }
            val stroke = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 3f * scale
                color = if (isHologram) armor.emissiveColor.toArgb() else armor.accentColor.toArgb()
            }

            val path = Path().apply {
                moveTo(mid.x - w * 0.48f, mid.y - h * 0.48f)
                lineTo(mid.x + w * 0.48f, mid.y - h * 0.48f)
                lineTo(mid.x + w * 0.55f, mid.y)
                lineTo(mid.x + w * 0.38f, mid.y + h * 0.48f)
                lineTo(mid.x - w * 0.38f, mid.y + h * 0.48f)
                lineTo(mid.x - w * 0.55f, mid.y)
                close()
            }

            drawScope.drawContext.canvas.nativeCanvas.drawPath(path, paint)
            drawScope.drawContext.canvas.nativeCanvas.drawPath(path, stroke)

            if (armor.type == ArmorType.CYBER_KNIGHT) {
                // Cyber knee conduit
                drawScope.drawLine(
                    color = armor.emissiveColor,
                    start = Offset(mid.x, mid.y - h * 0.3f),
                    end = Offset(mid.x, mid.y + h * 0.3f),
                    strokeWidth = 3.5f * scale
                )
            }
        }
    }

    private fun renderKneeGuard(
        drawScope: DrawScope,
        knee: Offset,
        scale: Float,
        armor: ArmorDefinition,
        time: Float,
        isHologram: Boolean
    ) {
        val radius = 22f * scale
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = if (isHologram) armor.emissiveColor.copy(alpha = 0.5f).toArgb() else armor.primaryColor.toArgb()
        }
        val stroke = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3f * scale
            color = if (isHologram) armor.emissiveColor.toArgb() else armor.accentColor.toArgb()
        }

        val kneePath = Path().apply {
            moveTo(knee.x, knee.y - radius * 1.1f)
            lineTo(knee.x + radius, knee.y)
            lineTo(knee.x, knee.y + radius * 1.25f) // pointed gothic/cyber knee
            lineTo(knee.x - radius, knee.y)
            close()
        }

        drawScope.drawContext.canvas.nativeCanvas.drawPath(kneePath, paint)
        drawScope.drawContext.canvas.nativeCanvas.drawPath(kneePath, stroke)
    }

    private fun renderCalfBootArmor(
        drawScope: DrawScope,
        knee: Offset,
        ankle: Offset,
        scale: Float,
        armor: ArmorDefinition,
        time: Float,
        isHologram: Boolean
    ) {
        val dist = (ankle - knee).getDistance()
        if (dist < 10f) return

        val angle = Math.toDegrees(atan2((ankle.y - knee.y).toDouble(), (ankle.x - knee.x).toDouble())).toFloat() - 90f
        val mid = (knee + ankle) * 0.5f

        drawScope.rotate(degrees = angle, pivot = mid) {
            val w = 44f * scale
            val h = dist * 0.85f

            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = if (isHologram) armor.emissiveColor.copy(alpha = 0.5f).toArgb() else armor.primaryColor.toArgb()
            }
            val stroke = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 3f * scale
                color = if (isHologram) armor.emissiveColor.toArgb() else armor.accentColor.toArgb()
            }

            val path = Path().apply {
                moveTo(mid.x - w * 0.42f, mid.y - h * 0.48f)
                lineTo(mid.x + w * 0.42f, mid.y - h * 0.48f)
                lineTo(mid.x + w * 0.48f, mid.y - h * 0.1f)
                lineTo(mid.x + w * 0.35f, mid.y + h * 0.48f)
                lineTo(mid.x - w * 0.35f, mid.y + h * 0.48f)
                lineTo(mid.x - w * 0.48f, mid.y - h * 0.1f)
                close()
            }

            drawScope.drawContext.canvas.nativeCanvas.drawPath(path, paint)
            drawScope.drawContext.canvas.nativeCanvas.drawPath(path, stroke)

            if (armor.type == ArmorType.CYBER_KNIGHT) {
                // Neon energy fissure running down the calf
                val neonPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 3.5f * scale
                    color = armor.emissiveColor.toArgb()
                    strokeCap = Paint.Cap.ROUND
                }
                val fissurePath = Path().apply {
                    moveTo(mid.x - w * 0.15f, mid.y - h * 0.35f)
                    lineTo(mid.x, mid.y)
                    lineTo(mid.x - w * 0.1f, mid.y + h * 0.35f)
                }
                drawScope.drawContext.canvas.nativeCanvas.drawPath(fissurePath, neonPaint)
            }
        }
    }

    private fun renderHelmet(
        drawScope: DrawScope,
        headPos: Offset,
        headAngle: Float,
        headRadius: Float,
        scale: Float,
        armor: ArmorDefinition,
        time: Float,
        isHologram: Boolean
    ) {
        drawScope.rotate(degrees = headAngle, pivot = headPos) {
            val r = headRadius.coerceIn(45f * scale, 90f * scale)

            if (armor.type == ArmorType.DARK_KNIGHT) {
                // ---------------------------------------------------------
                // DARK KNIGHT HELMET & COWL HOOD (Matches reference image 1)
                // Pointed white/silver cowl hood draped over dark silver helmet
                // ---------------------------------------------------------
                val hoodPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.FILL
                    color = if (isHologram) armor.emissiveColor.copy(alpha = 0.5f).toArgb() else Color(0xFFF1F5F9).toArgb() // White hood
                }
                val hoodShadowPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.FILL
                    color = Color(0xFF0F172A).toArgb() // Deep hood void
                }
                val hoodStroke = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 3.5f * scale
                    color = if (isHologram) armor.emissiveColor.toArgb() else Color(0xFF64748B).toArgb()
                }

                // Pointed Cowl / Hood silhouette
                val hoodPath = Path().apply {
                    moveTo(headPos.x, headPos.y - r * 1.55f) // High pointed cowl peak
                    cubicTo(
                        headPos.x + r * 0.6f, headPos.y - r * 1.2f,
                        headPos.x + r * 1.25f, headPos.y - r * 0.3f,
                        headPos.x + r * 1.25f, headPos.y + r * 0.7f
                    )
                    lineTo(headPos.x + r * 0.7f, headPos.y + r * 1.25f)
                    lineTo(headPos.x - r * 0.7f, headPos.y + r * 1.25f)
                    cubicTo(
                        headPos.x - r * 1.25f, headPos.y + r * 0.7f,
                        headPos.x - r * 1.25f, headPos.y - r * 0.3f,
                        headPos.x - r * 0.6f, headPos.y - r * 1.2f
                    )
                    close()
                }

                drawScope.drawContext.canvas.nativeCanvas.drawPath(hoodPath, hoodPaint)
                drawScope.drawContext.canvas.nativeCanvas.drawPath(hoodPath, hoodStroke)

                // Deep dark interior shadow of the hood
                val innerFacePath = Path().apply {
                    moveTo(headPos.x, headPos.y - r * 0.75f)
                    lineTo(headPos.x + r * 0.75f, headPos.y + r * 0.1f)
                    lineTo(headPos.x, headPos.y + r * 0.85f)
                    lineTo(headPos.x - r * 0.75f, headPos.y + r * 0.1f)
                    close()
                }
                drawScope.drawContext.canvas.nativeCanvas.drawPath(innerFacePath, hoodShadowPaint)

                // Angular steel faceplate / visor inside the dark void
                val visorMetalPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.FILL
                    color = Color(0xFF94A3B8).toArgb()
                }
                val facePlatePath = Path().apply {
                    moveTo(headPos.x, headPos.y - r * 0.45f)
                    lineTo(headPos.x + r * 0.52f, headPos.y + r * 0.05f)
                    lineTo(headPos.x, headPos.y + r * 0.78f)
                    lineTo(headPos.x - r * 0.52f, headPos.y + r * 0.05f)
                    close()
                }
                drawScope.drawContext.canvas.nativeCanvas.drawPath(facePlatePath, visorMetalPaint)

                // Dark gothic eye slit
                drawScope.drawOval(
                    color = Color(0xFF020617),
                    topLeft = Offset(headPos.x - r * 0.35f, headPos.y - r * 0.08f),
                    size = Size(r * 0.70f, r * 0.18f)
                )

            } else {
                // ---------------------------------------------------------
                // CYBER KNIGHT FULL HELMET (Matches reference image 2)
                // Aerodynamic white helmet with glowing cyan chevron visor
                // ---------------------------------------------------------
                val helmPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.FILL
                    color = if (isHologram) armor.emissiveColor.copy(alpha = 0.5f).toArgb() else Color(0xFFF8FAFC).toArgb()
                }
                val helmStroke = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 3.5f * scale
                    color = if (isHologram) armor.emissiveColor.toArgb() else Color(0xFF475569).toArgb()
                }

                // Sleek aerodynamic helmet dome
                val helmPath = Path().apply {
                    moveTo(headPos.x, headPos.y - r * 1.35f)
                    cubicTo(
                        headPos.x + r * 0.75f, headPos.y - r * 1.35f,
                        headPos.x + r * 1.15f, headPos.y - r * 0.4f,
                        headPos.x + r * 0.95f, headPos.y + r * 0.5f
                    )
                    lineTo(headPos.x + r * 0.5f, headPos.y + r * 1.15f) // tapered chin
                    lineTo(headPos.x - r * 0.5f, headPos.y + r * 1.15f)
                    cubicTo(
                        headPos.x - r * 0.95f, headPos.y + r * 0.5f,
                        headPos.x - r * 1.15f, headPos.y - r * 0.4f,
                        headPos.x - r * 0.75f, headPos.y - r * 1.35f
                    )
                    close()
                }
                drawScope.drawContext.canvas.nativeCanvas.drawPath(helmPath, helmPaint)
                drawScope.drawContext.canvas.nativeCanvas.drawPath(helmPath, helmStroke)

                // High-tech black carbon cheek & ear plates
                val earPlatePaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.FILL
                    color = Color(0xFF0F172A).toArgb()
                }
                drawScope.drawOval(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(headPos.x - r * 0.78f, headPos.y - r * 0.2f),
                    size = Size(r * 1.56f, r * 0.85f)
                )

                // Neon cyan chevron V-slit visor with emissive glow
                val glowAlpha = (sin(time * 4f) * 0.2f + 0.8f).coerceIn(0.6f, 1f)
                val neonCyan = armor.emissiveColor.copy(alpha = glowAlpha)

                val visorPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 6.5f * scale
                    color = neonCyan.toArgb()
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }

                // Distinctive Chevron / V-Visor from reference 2
                val visorPath = Path().apply {
                    moveTo(headPos.x - r * 0.55f, headPos.y - r * 0.25f)
                    lineTo(headPos.x - r * 0.25f, headPos.y + r * 0.18f)
                    lineTo(headPos.x, headPos.y - r * 0.02f) // center notch
                    lineTo(headPos.x + r * 0.25f, headPos.y + r * 0.18f)
                    lineTo(headPos.x + r * 0.55f, headPos.y - r * 0.25f)
                }
                drawScope.drawContext.canvas.nativeCanvas.drawPath(visorPath, visorPaint)

                // Top head neon fin strip
                val finPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * scale
                    color = neonCyan.toArgb()
                }
                drawScope.drawLine(
                    color = neonCyan,
                    start = Offset(headPos.x, headPos.y - r * 1.25f),
                    end = Offset(headPos.x, headPos.y - r * 0.55f),
                    strokeWidth = 4f * scale
                )
            }
        }
    }

    private fun renderGreatsword(
        drawScope: DrawScope,
        handPos: Offset,
        shoulderPos: Offset,
        scale: Float,
        armor: ArmorDefinition,
        time: Float,
        isHologram: Boolean
    ) {
        // Massive Ornate Medieval Greatsword from reference image 1
        // Tilted diagonally across body or at side
        val angle = -45f + sin(time * 1.2f) * 5f

        drawScope.rotate(degrees = angle, pivot = handPos) {
            val bladeLength = 260f * scale
            val bladeWidth = 34f * scale
            val hiltLength = 75f * scale

            val steelPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = if (isHologram) armor.emissiveColor.copy(alpha = 0.6f).toArgb() else Color(0xFFE2E8F0).toArgb()
            }
            val edgePaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 2.5f * scale
                color = if (isHologram) armor.emissiveColor.toArgb() else Color(0xFF475569).toArgb()
            }

            // Central blade pointing upward
            val bladePath = Path().apply {
                moveTo(handPos.x - bladeWidth * 0.5f, handPos.y - 15f * scale)
                lineTo(handPos.x - bladeWidth * 0.55f, handPos.y - bladeLength * 0.85f)
                lineTo(handPos.x, handPos.y - bladeLength) // sharp tip
                lineTo(handPos.x + bladeWidth * 0.55f, handPos.y - bladeLength * 0.85f)
                lineTo(handPos.x + bladeWidth * 0.5f, handPos.y - 15f * scale)
                close()
            }
            drawScope.drawContext.canvas.nativeCanvas.drawPath(bladePath, steelPaint)
            drawScope.drawContext.canvas.nativeCanvas.drawPath(bladePath, edgePaint)

            // Center Fuller groove with gothic cutout
            drawScope.drawLine(
                color = Color(0xFF1E293B),
                start = Offset(handPos.x, handPos.y - 25f * scale),
                end = Offset(handPos.x, handPos.y - bladeLength * 0.78f),
                strokeWidth = 4f * scale
            )

            // Ornate Gothic Crossguard (quillons with downward points)
            val guardWidth = 85f * scale
            val guardPath = Path().apply {
                moveTo(handPos.x - guardWidth * 0.5f, handPos.y - 25f * scale)
                lineTo(handPos.x, handPos.y - 12f * scale)
                lineTo(handPos.x + guardWidth * 0.5f, handPos.y - 25f * scale)
                lineTo(handPos.x + guardWidth * 0.45f, handPos.y)
                lineTo(handPos.x, handPos.y - 5f * scale)
                lineTo(handPos.x - guardWidth * 0.45f, handPos.y)
                close()
            }
            drawScope.drawContext.canvas.nativeCanvas.drawPath(
                guardPath,
                Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.FILL
                    color = Color(0xFF334155).toArgb()
                }
            )

            // Hilt grip & Pommel
            drawScope.drawLine(
                color = Color(0xFF0F172A),
                start = Offset(handPos.x, handPos.y),
                end = Offset(handPos.x, handPos.y + hiltLength),
                strokeWidth = 9f * scale,
                cap = StrokeCap.Round
            )
            // Ornate pommel stone
            drawScope.drawCircle(
                color = Color(0xFFCBD5E1),
                radius = 12f * scale,
                center = Offset(handPos.x, handPos.y + hiltLength + 8f * scale)
            )
        }
    }

    private fun renderScanLineVfx(
        drawScope: DrawScope,
        scanY: Float,
        screenWidth: Float,
        armor: ArmorDefinition,
        time: Float
    ) {
        val glowColor = armor.emissiveColor

        // Main high-intensity laser scanline
        drawScope.drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    glowColor.copy(alpha = 0.5f),
                    Color.White,
                    glowColor.copy(alpha = 0.5f),
                    Color.Transparent
                )
            ),
            start = Offset(0f, scanY),
            end = Offset(screenWidth, scanY),
            strokeWidth = 5f
        )

        // Holographic grid / wave plane right behind the scanline
        for (i in -3..3) {
            val waveY = scanY + i * 14f
            val alpha = (1f - kotlin.math.abs(i) * 0.28f).coerceIn(0f, 0.6f)
            drawScope.drawLine(
                color = glowColor.copy(alpha = alpha),
                start = Offset(0f, waveY),
                end = Offset(screenWidth, waveY),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), time * 30f)
            )
        }

        // Particle sparks on the scan line
        for (p in 0..10) {
            val sparkX = (screenWidth * ((p * 0.1f + sin(time * 5f + p) * 0.05f).mod(1f)))
            val sparkY = scanY + sin(time * 8f + p) * 12f
            drawScope.drawCircle(
                color = Color.White,
                radius = 2.5f,
                center = Offset(sparkX, sparkY)
            )
        }
    }

    // -----------------------------------------------------------------
    // ANATOMICAL WEARABLE FIT HELPERS ("Kayak pake armor asli")
    // -----------------------------------------------------------------

    private fun renderAnatomicalUndersuit(
        drawScope: DrawScope,
        pose: PoseData,
        lShoulder: Offset,
        rShoulder: Offset,
        lHip: Offset,
        rHip: Offset,
        shoulderDist: Float,
        torsoLength: Float,
        scale: Float,
        armor: ArmorDefinition,
        time: Float,
        isPartMaterialized: (Float) -> Boolean
    ) {
        val width = drawScope.size.width
        val height = drawScope.size.height

        val undersuitColor = if (armor.type == ArmorType.CYBER_KNIGHT) {
            Color(0xFF0D1424) // High-tech nano-mesh under-chassis
        } else {
            Color(0xFF10141D) // Medieval arming doublet & chainmail weave
        }
        val seamColor = if (armor.type == ArmorType.CYBER_KNIGHT) {
            armor.emissiveColor.copy(alpha = 0.28f)
        } else {
            Color(0xFF2C3545)
        }

        val fillPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = undersuitColor.toArgb()
        }
        val seamPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * scale
            color = seamColor.toArgb()
        }

        // 1. Torso body-glove
        val midShoulder = (lShoulder + rShoulder) * 0.5f
        if (isPartMaterialized(midShoulder.y)) {
            val torsoPath = Path().apply {
                moveTo(lShoulder.x - 12f * scale, lShoulder.y)
                lineTo(rShoulder.x + 12f * scale, rShoulder.y)
                lineTo(rHip.x + 14f * scale, rHip.y)
                lineTo(lHip.x - 14f * scale, lHip.y)
                close()
            }
            drawScope.drawContext.canvas.nativeCanvas.drawPath(torsoPath, fillPaint)
            drawScope.drawContext.canvas.nativeCanvas.drawPath(torsoPath, seamPaint)
        }

        // 2. Arms undersuit sleeves
        if (pose.leftElbow.isValid() && isPartMaterialized(lShoulder.y)) {
            val lElbow = pose.leftElbow.toOffset(width, height)
            drawUndersuitLimb(drawScope, lShoulder, lElbow, 34f * scale, fillPaint, seamPaint)
            if (pose.leftWrist.isValid() && isPartMaterialized(lElbow.y)) {
                val lWrist = pose.leftWrist.toOffset(width, height)
                drawUndersuitLimb(drawScope, lElbow, lWrist, 26f * scale, fillPaint, seamPaint)
            }
        }
        if (pose.rightElbow.isValid() && isPartMaterialized(rShoulder.y)) {
            val rElbow = pose.rightElbow.toOffset(width, height)
            drawUndersuitLimb(drawScope, rShoulder, rElbow, 34f * scale, fillPaint, seamPaint)
            if (pose.rightWrist.isValid() && isPartMaterialized(rElbow.y)) {
                val rWrist = pose.rightWrist.toOffset(width, height)
                drawUndersuitLimb(drawScope, rElbow, rWrist, 26f * scale, fillPaint, seamPaint)
            }
        }

        // 3. Legs undersuit
        if (pose.leftKnee.isValid() && isPartMaterialized(lHip.y)) {
            val lKnee = pose.leftKnee.toOffset(width, height)
            drawUndersuitLimb(drawScope, lHip, lKnee, 40f * scale, fillPaint, seamPaint)
            if (pose.leftAnkle.isValid() && isPartMaterialized(lKnee.y)) {
                val lAnkle = pose.leftAnkle.toOffset(width, height)
                drawUndersuitLimb(drawScope, lKnee, lAnkle, 32f * scale, fillPaint, seamPaint)
            }
        }
        if (pose.rightKnee.isValid() && isPartMaterialized(rHip.y)) {
            val rKnee = pose.rightKnee.toOffset(width, height)
            drawUndersuitLimb(drawScope, rHip, rKnee, 40f * scale, fillPaint, seamPaint)
            if (pose.rightAnkle.isValid() && isPartMaterialized(rKnee.y)) {
                val rAnkle = pose.rightAnkle.toOffset(width, height)
                drawUndersuitLimb(drawScope, rKnee, rAnkle, 32f * scale, fillPaint, seamPaint)
            }
        }
    }

    private fun drawUndersuitLimb(
        drawScope: DrawScope,
        p1: Offset,
        p2: Offset,
        thickness: Float,
        fillPaint: Paint,
        strokePaint: Paint
    ) {
        val dist = (p2 - p1).getDistance()
        if (dist < 10f) return
        val angle = Math.toDegrees(atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())).toFloat() - 90f
        val mid = (p1 + p2) * 0.5f

        drawScope.rotate(degrees = angle, pivot = mid) {
            val halfW = thickness * 0.5f
            val halfH = dist * 0.52f
            val path = Path().apply {
                moveTo(mid.x - halfW, mid.y - halfH)
                lineTo(mid.x + halfW, mid.y - halfH)
                lineTo(mid.x + halfW * 0.85f, mid.y + halfH)
                lineTo(mid.x - halfW * 0.85f, mid.y + halfH)
                close()
            }
            drawScope.drawContext.canvas.nativeCanvas.drawPath(path, fillPaint)
            drawScope.drawContext.canvas.nativeCanvas.drawPath(path, strokePaint)
        }
    }

    private fun renderGorgetNeckArmor(
        drawScope: DrawScope,
        midShoulder: Offset,
        headPos: Offset,
        shoulderDist: Float,
        scale: Float,
        armor: ArmorDefinition,
        time: Float,
        isHologram: Boolean
    ) {
        val neckCenter = midShoulder + (headPos - midShoulder) * 0.45f
        val neckWidth = shoulderDist * 0.36f
        val neckHeight = (headPos.y - midShoulder.y).let { if (it < 0) -it * 0.65f else 35f * scale }.coerceIn(25f * scale, 60f * scale)

        val basePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = if (isHologram) armor.emissiveColor.copy(alpha = 0.5f).toArgb() else armor.primaryColor.toArgb()
        }
        val strokePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3f * scale
            color = if (isHologram) armor.emissiveColor.toArgb() else armor.accentColor.toArgb()
        }

        // Segmented neck collar / gorget plates
        val gorgetPath = Path().apply {
            moveTo(neckCenter.x - neckWidth * 0.5f, neckCenter.y + neckHeight * 0.5f)
            lineTo(neckCenter.x + neckWidth * 0.5f, neckCenter.y + neckHeight * 0.5f)
            lineTo(neckCenter.x + neckWidth * 0.38f, neckCenter.y - neckHeight * 0.5f)
            lineTo(neckCenter.x - neckWidth * 0.38f, neckCenter.y - neckHeight * 0.5f)
            close()
        }
        drawScope.drawContext.canvas.nativeCanvas.drawPath(gorgetPath, basePaint)
        drawScope.drawContext.canvas.nativeCanvas.drawPath(gorgetPath, strokePaint)

        // Metallic collar ridge line
        drawScope.drawLine(
            color = if (isHologram) armor.emissiveColor else Color(0xFF64748B),
            start = Offset(neckCenter.x - neckWidth * 0.42f, neckCenter.y),
            end = Offset(neckCenter.x + neckWidth * 0.42f, neckCenter.y),
            strokeWidth = 2.5f * scale
        )
    }

    private fun renderCouterElbowGuard(
        drawScope: DrawScope,
        elbow: Offset,
        scale: Float,
        armor: ArmorDefinition,
        time: Float,
        isHologram: Boolean
    ) {
        val radius = 18f * scale
        val basePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = if (isHologram) armor.emissiveColor.copy(alpha = 0.5f).toArgb() else armor.secondaryColor.toArgb()
        }
        val strokePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * scale
            color = if (isHologram) armor.emissiveColor.toArgb() else armor.accentColor.toArgb()
        }

        val couterPath = Path().apply {
            moveTo(elbow.x - radius, elbow.y)
            lineTo(elbow.x, elbow.y - radius * 1.15f)
            lineTo(elbow.x + radius, elbow.y)
            lineTo(elbow.x, elbow.y + radius * 1.2f)
            close()
        }
        drawScope.drawContext.canvas.nativeCanvas.drawPath(couterPath, basePaint)
        drawScope.drawContext.canvas.nativeCanvas.drawPath(couterPath, strokePaint)

        if (armor.type == ArmorType.CYBER_KNIGHT) {
            drawScope.drawCircle(
                color = armor.emissiveColor,
                radius = 4f * scale,
                center = elbow
            )
        }
    }

    private fun renderSkeletonDebug(
        drawScope: DrawScope,
        pose: PoseData,
        width: Float,
        height: Float
    ) {
        val jointColor = Color(0xFF00FF88)
        val boneColor = Color(0x8800FF88)

        val connections = listOf(
            Pair(pose.leftShoulder, pose.rightShoulder),
            Pair(pose.leftShoulder, pose.leftElbow),
            Pair(pose.leftElbow, pose.leftWrist),
            Pair(pose.rightShoulder, pose.rightElbow),
            Pair(pose.rightElbow, pose.rightWrist),
            Pair(pose.leftShoulder, pose.leftHip),
            Pair(pose.rightShoulder, pose.rightHip),
            Pair(pose.leftHip, pose.rightHip),
            Pair(pose.leftHip, pose.leftKnee),
            Pair(pose.leftKnee, pose.leftAnkle),
            Pair(pose.rightHip, pose.rightKnee),
            Pair(pose.rightKnee, pose.rightAnkle),
            Pair(pose.nose, pose.leftEye),
            Pair(pose.nose, pose.rightEye)
        )

        connections.forEach { (a, b) ->
            if (a.isValid(0.3f) && b.isValid(0.3f)) {
                drawScope.drawLine(
                    color = boneColor,
                    start = a.toOffset(width, height),
                    end = b.toOffset(width, height),
                    strokeWidth = 3f
                )
            }
        }

        listOf(
            pose.nose, pose.leftShoulder, pose.rightShoulder,
            pose.leftElbow, pose.rightElbow, pose.leftWrist, pose.rightWrist,
            pose.leftHip, pose.rightHip, pose.leftKnee, pose.rightKnee,
            pose.leftAnkle, pose.rightAnkle
        ).forEach { p ->
            if (p.isValid(0.3f)) {
                drawScope.drawCircle(
                    color = jointColor,
                    radius = 5f,
                    center = p.toOffset(width, height)
                )
            }
        }
    }
}
