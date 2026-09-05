package com.example.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2

/**
 * Normalized 2D/3D landmark point from body tracking (0.0 to 1.0 on screen coordinates)
 */
data class LandmarkPoint(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val confidence: Float = 0f
) {
    fun toOffset(width: Float, height: Float): Offset = Offset(x * width, y * height)

    fun isValid(minConfidence: Float = 0.4f): Boolean = confidence >= minConfidence
}

/**
 * Clean data structure holding all key body tracking landmarks and calculated segments
 */
data class PoseData(
    val isDetected: Boolean = false,
    val confidence: Float = 0f,

    // Head
    val nose: LandmarkPoint = LandmarkPoint(),
    val leftEye: LandmarkPoint = LandmarkPoint(),
    val rightEye: LandmarkPoint = LandmarkPoint(),
    val leftEar: LandmarkPoint = LandmarkPoint(),
    val rightEar: LandmarkPoint = LandmarkPoint(),

    // Upper body
    val leftShoulder: LandmarkPoint = LandmarkPoint(),
    val rightShoulder: LandmarkPoint = LandmarkPoint(),
    val leftElbow: LandmarkPoint = LandmarkPoint(),
    val rightElbow: LandmarkPoint = LandmarkPoint(),
    val leftWrist: LandmarkPoint = LandmarkPoint(),
    val rightWrist: LandmarkPoint = LandmarkPoint(),

    // Lower body
    val leftHip: LandmarkPoint = LandmarkPoint(),
    val rightHip: LandmarkPoint = LandmarkPoint(),
    val leftKnee: LandmarkPoint = LandmarkPoint(),
    val rightKnee: LandmarkPoint = LandmarkPoint(),
    val leftAnkle: LandmarkPoint = LandmarkPoint(),
    val rightAnkle: LandmarkPoint = LandmarkPoint(),

    // Extra landmarks
    val leftIndex: LandmarkPoint = LandmarkPoint(),
    val rightIndex: LandmarkPoint = LandmarkPoint(),
    val leftHeel: LandmarkPoint = LandmarkPoint(),
    val rightHeel: LandmarkPoint = LandmarkPoint()
) {
    // Computed midpoints & body vectors
    val chestCenter: LandmarkPoint
        get() {
            val midShoulderX = (leftShoulder.x + rightShoulder.x) / 2f
            val midShoulderY = (leftShoulder.y + rightShoulder.y) / 2f
            val midHipX = (leftHip.x + rightHip.x) / 2f
            val midHipY = (leftHip.y + rightHip.y) / 2f
            // Chest is roughly 35% down from shoulders towards hips
            return LandmarkPoint(
                x = midShoulderX * 0.65f + midHipX * 0.35f,
                y = midShoulderY * 0.65f + midHipY * 0.35f,
                confidence = (leftShoulder.confidence + rightShoulder.confidence) / 2f
            )
        }

    val pelvisCenter: LandmarkPoint
        get() = LandmarkPoint(
            x = (leftHip.x + rightHip.x) / 2f,
            y = (leftHip.y + rightHip.y) / 2f,
            confidence = (leftHip.confidence + rightHip.confidence) / 2f
        )

    val headCenter: LandmarkPoint
        get() {
            return if (nose.isValid()) {
                LandmarkPoint(nose.x, nose.y, nose.z, nose.confidence)
            } else {
                LandmarkPoint(
                    x = (leftShoulder.x + rightShoulder.x) / 2f,
                    y = (leftShoulder.y + rightShoulder.y) / 2f - 0.12f,
                    confidence = 0.5f
                )
            }
        }

    // Angles (in degrees)
    val torsoAngle: Float
        get() {
            val dx = (rightShoulder.x - leftShoulder.x)
            val dy = (rightShoulder.y - leftShoulder.y)
            return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        }

    val headAngle: Float
        get() {
            val dx = (rightEar.x - leftEar.x)
            val dy = (rightEar.y - leftEar.y)
            return if (leftEar.isValid() && rightEar.isValid()) {
                Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            } else {
                torsoAngle
            }
        }

    companion object {
        val EMPTY = PoseData()
    }
}
