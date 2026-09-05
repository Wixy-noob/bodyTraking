package com.example.tracking

import com.example.model.LandmarkPoint
import com.example.model.PoseData
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates natural animated body posture landmarks for demonstration, testing,
 * and emulator environments where a physical human in front of the camera may not be present.
 */
class DemoPoseGenerator {

    enum class AnimationState {
        IDLE_BREATHING,
        ARM_RAISE_MOTION,
        COMBAT_READY,
        WALK_CYCLE
    }

    private var timeSeconds: Float = 0f

    fun generate(deltaTime: Float, animState: AnimationState): PoseData {
        timeSeconds += deltaTime

        val breath = sin(timeSeconds * 2.0f) * 0.015f
        val swayX = sin(timeSeconds * 1.2f) * 0.012f

        // Base spine & center coordinates
        val centerX = 0.5f + swayX
        val headY = 0.22f + breath
        val shoulderY = 0.33f + breath
        val shoulderHalfW = 0.13f
        val hipY = 0.58f
        val hipHalfW = 0.085f

        val leftShoulderX = centerX - shoulderHalfW
        val rightShoulderX = centerX + shoulderHalfW
        val leftHipX = centerX - hipHalfW
        val rightHipX = centerX + hipHalfW

        val headTurnAngle = when (animState) {
            AnimationState.ARM_RAISE_MOTION -> sin(timeSeconds * 1.5f) * 0.025f
            AnimationState.COMBAT_READY -> -0.015f
            else -> sin(timeSeconds * 0.8f) * 0.01f
        }

        // Arm positions depending on animation
        val (leftElbow, leftWrist, rightElbow, rightWrist) = when (animState) {
            AnimationState.ARM_RAISE_MOTION -> {
                // Right arm raises up high and lowers smoothly to test arm armor rotation
                val armAngle = (sin(timeSeconds * 1.8f) * 0.5f + 0.5f) // 0 to 1
                val rElbowX = rightShoulderX + 0.12f + armAngle * 0.08f
                val rElbowY = shoulderY + 0.14f - armAngle * 0.26f // moves above shoulder
                val rWristX = rElbowX + 0.04f + armAngle * 0.08f
                val rWristY = rElbowY + 0.12f - armAngle * 0.22f

                // Left arm slight flex
                val lElbowX = leftShoulderX - 0.11f
                val lElbowY = shoulderY + 0.15f
                val lWristX = lElbowX - 0.04f
                val lWristY = lElbowY + 0.14f

                Quadruple(
                    LandmarkPoint(lElbowX, lElbowY, 0f, 0.95f),
                    LandmarkPoint(lWristX, lWristY, 0f, 0.95f),
                    LandmarkPoint(rElbowX, rElbowY, 0f, 0.95f),
                    LandmarkPoint(rWristX, rWristY, 0f, 0.95f)
                )
            }
            AnimationState.COMBAT_READY -> {
                // Guard posture: left arm out forward, right arm holding weapon position
                val lElbowX = leftShoulderX - 0.14f
                val lElbowY = shoulderY + 0.08f
                val lWristX = leftShoulderX - 0.06f
                val lWristY = shoulderY - 0.02f

                val rElbowX = rightShoulderX + 0.14f
                val rElbowY = shoulderY + 0.12f
                val rWristX = rightShoulderX + 0.08f
                val rWristY = shoulderY + 0.02f

                Quadruple(
                    LandmarkPoint(lElbowX, lElbowY, 0f, 0.95f),
                    LandmarkPoint(lWristX, lWristY, 0f, 0.95f),
                    LandmarkPoint(rElbowX, rElbowY, 0f, 0.95f),
                    LandmarkPoint(rWristX, rWristY, 0f, 0.95f)
                )
            }
            AnimationState.WALK_CYCLE -> {
                val armSwing = sin(timeSeconds * 3.0f) * 0.08f
                val lElbowX = leftShoulderX - 0.08f
                val lElbowY = shoulderY + 0.16f - armSwing
                val lWristX = lElbowX - 0.02f
                val lWristY = lElbowY + 0.15f - armSwing

                val rElbowX = rightShoulderX + 0.08f
                val rElbowY = shoulderY + 0.16f + armSwing
                val rWristX = rElbowX + 0.02f
                val rWristY = rElbowY + 0.15f + armSwing

                Quadruple(
                    LandmarkPoint(lElbowX, lElbowY, 0f, 0.95f),
                    LandmarkPoint(lWristX, lWristY, 0f, 0.95f),
                    LandmarkPoint(rElbowX, rElbowY, 0f, 0.95f),
                    LandmarkPoint(rWristX, rWristY, 0f, 0.95f)
                )
            }
            AnimationState.IDLE_BREATHING -> {
                val lElbowX = leftShoulderX - 0.09f
                val lElbowY = shoulderY + 0.16f
                val lWristX = leftShoulderX - 0.07f
                val lWristY = shoulderY + 0.30f

                val rElbowX = rightShoulderX + 0.09f
                val rElbowY = shoulderY + 0.16f
                val rWristX = rightShoulderX + 0.07f
                val rWristY = shoulderY + 0.30f

                Quadruple(
                    LandmarkPoint(lElbowX, lElbowY, 0f, 0.95f),
                    LandmarkPoint(lWristX, lWristY, 0f, 0.95f),
                    LandmarkPoint(rElbowX, rElbowY, 0f, 0.95f),
                    LandmarkPoint(rWristX, rWristY, 0f, 0.95f)
                )
            }
        }

        // Leg positions depending on animation
        val (leftKnee, leftAnkle, rightKnee, rightAnkle) = when (animState) {
            AnimationState.WALK_CYCLE -> {
                val legCycle = sin(timeSeconds * 3.0f)
                val lKneeY = 0.74f + (if (legCycle > 0) -legCycle * 0.06f else 0f)
                val lAnkleY = 0.90f + (if (legCycle > 0) -legCycle * 0.07f else 0f)
                val rKneeY = 0.74f + (if (legCycle < 0) legCycle * 0.06f else 0f)
                val rAnkleY = 0.90f + (if (legCycle < 0) legCycle * 0.07f else 0f)

                Quadruple(
                    LandmarkPoint(leftHipX - 0.02f, lKneeY, 0f, 0.95f),
                    LandmarkPoint(leftHipX - 0.02f, lAnkleY, 0f, 0.95f),
                    LandmarkPoint(rightHipX + 0.02f, rKneeY, 0f, 0.95f),
                    LandmarkPoint(rightHipX + 0.02f, rAnkleY, 0f, 0.95f)
                )
            }
            AnimationState.COMBAT_READY -> {
                Quadruple(
                    LandmarkPoint(leftHipX - 0.05f, 0.72f, 0f, 0.95f),
                    LandmarkPoint(leftHipX - 0.08f, 0.88f, 0f, 0.95f),
                    LandmarkPoint(rightHipX + 0.06f, 0.73f, 0f, 0.95f),
                    LandmarkPoint(rightHipX + 0.09f, 0.89f, 0f, 0.95f)
                )
            }
            else -> {
                Quadruple(
                    LandmarkPoint(leftHipX - 0.015f, 0.74f, 0f, 0.95f),
                    LandmarkPoint(leftHipX - 0.02f, 0.90f, 0f, 0.95f),
                    LandmarkPoint(rightHipX + 0.015f, 0.74f, 0f, 0.95f),
                    LandmarkPoint(rightHipX + 0.02f, 0.90f, 0f, 0.95f)
                )
            }
        }

        return PoseData(
            isDetected = true,
            confidence = 0.98f,
            nose = LandmarkPoint(centerX + headTurnAngle, headY, 0f, 0.98f),
            leftEye = LandmarkPoint(centerX - 0.025f + headTurnAngle, headY - 0.02f, 0f, 0.95f),
            rightEye = LandmarkPoint(centerX + 0.025f + headTurnAngle, headY - 0.02f, 0f, 0.95f),
            leftEar = LandmarkPoint(centerX - 0.06f + headTurnAngle, headY, 0f, 0.95f),
            rightEar = LandmarkPoint(centerX + 0.06f + headTurnAngle, headY, 0f, 0.95f),
            leftShoulder = LandmarkPoint(leftShoulderX, shoulderY, 0f, 0.98f),
            rightShoulder = LandmarkPoint(rightShoulderX, shoulderY, 0f, 0.98f),
            leftElbow = leftElbow,
            rightElbow = rightElbow,
            leftWrist = leftWrist,
            rightWrist = rightWrist,
            leftHip = LandmarkPoint(leftHipX, hipY, 0f, 0.98f),
            rightHip = LandmarkPoint(rightHipX, hipY, 0f, 0.98f),
            leftKnee = leftKnee,
            rightKnee = rightKnee,
            leftAnkle = leftAnkle,
            rightAnkle = rightAnkle
        )
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
