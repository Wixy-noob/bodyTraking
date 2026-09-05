package com.example.tracking

import com.example.model.LandmarkPoint
import com.example.model.PoseData

/**
 * Exponential Moving Average filter for landmark coordinates to prevent jitter
 * and produce buttery-smooth AR tracking motions.
 */
class PoseSmoother(
    private val alpha: Float = 0.55f // Weight of new values vs previous values
) {
    private var previousPose: PoseData? = null

    fun smooth(raw: PoseData): PoseData {
        if (!raw.isDetected) {
            previousPose = null
            return raw
        }

        val prev = previousPose
        if (prev == null || !prev.isDetected) {
            previousPose = raw
            return raw
        }

        val smoothed = PoseData(
            isDetected = true,
            confidence = smoothValue(raw.confidence, prev.confidence),
            nose = smoothPoint(raw.nose, prev.nose),
            leftEye = smoothPoint(raw.leftEye, prev.leftEye),
            rightEye = smoothPoint(raw.rightEye, prev.rightEye),
            leftEar = smoothPoint(raw.leftEar, prev.leftEar),
            rightEar = smoothPoint(raw.rightEar, prev.rightEar),
            leftShoulder = smoothPoint(raw.leftShoulder, prev.leftShoulder),
            rightShoulder = smoothPoint(raw.rightShoulder, prev.rightShoulder),
            leftElbow = smoothPoint(raw.leftElbow, prev.leftElbow),
            rightElbow = smoothPoint(raw.rightElbow, prev.rightElbow),
            leftWrist = smoothPoint(raw.leftWrist, prev.leftWrist),
            rightWrist = smoothPoint(raw.rightWrist, prev.rightWrist),
            leftHip = smoothPoint(raw.leftHip, prev.leftHip),
            rightHip = smoothPoint(raw.rightHip, prev.rightHip),
            leftKnee = smoothPoint(raw.leftKnee, prev.leftKnee),
            rightKnee = smoothPoint(raw.rightKnee, prev.rightKnee),
            leftAnkle = smoothPoint(raw.leftAnkle, prev.leftAnkle),
            rightAnkle = smoothPoint(raw.rightAnkle, prev.rightAnkle),
            leftIndex = smoothPoint(raw.leftIndex, prev.leftIndex),
            rightIndex = smoothPoint(raw.rightIndex, prev.rightIndex),
            leftHeel = smoothPoint(raw.leftHeel, prev.leftHeel),
            rightHeel = smoothPoint(raw.rightHeel, prev.rightHeel)
        )

        previousPose = smoothed
        return smoothed
    }

    private fun smoothPoint(curr: LandmarkPoint, prev: LandmarkPoint): LandmarkPoint {
        if (!curr.isValid(0.2f)) return curr
        if (!prev.isValid(0.2f)) return curr

        // Adaptive alpha: if movement is large, respond faster; if small, smooth out jitter
        val dx = curr.x - prev.x
        val dy = curr.y - prev.y
        val distSq = dx * dx + dy * dy
        val dynamicAlpha = if (distSq > 0.005f) {
            (alpha + 0.35f).coerceAtMost(0.9f)
        } else {
            alpha
        }

        return LandmarkPoint(
            x = prev.x + dynamicAlpha * (curr.x - prev.x),
            y = prev.y + dynamicAlpha * (curr.y - prev.y),
            z = prev.z + dynamicAlpha * (curr.z - prev.z),
            confidence = curr.confidence
        )
    }

    private fun smoothValue(curr: Float, prev: Float): Float {
        return prev + alpha * (curr - prev)
    }

    fun reset() {
        previousPose = null
    }
}
