package com.example.tracking

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.model.LandmarkPoint
import com.example.model.PoseData
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

/**
 * Manages ML Kit Pose Detection stream and converts camera frames to normalized PoseData.
 */
class PoseDetectorManager(
    private val onPoseDetected: (PoseData) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .setPreferredHardwareConfigs(PoseDetectorOptions.CPU_GPU)
        .build()

    private val detector: PoseDetector = PoseDetection.getClient(options)
    private val smoother = PoseSmoother(alpha = 0.55f)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        // For 90 or 270 rotation degrees, width & height are swapped in the inputImage
        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val imageWidth = if (isRotated) imageProxy.height else imageProxy.width
        val imageHeight = if (isRotated) imageProxy.width else imageProxy.height

        detector.process(inputImage)
            .addOnSuccessListener { pose ->
                val rawPose = parsePose(pose, imageWidth, imageHeight, isFrontCamera = true)
                val smoothed = smoother.smooth(rawPose)
                onPoseDetected(smoothed)
            }
            .addOnFailureListener {
                // Keep processing
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun parsePose(
        pose: Pose,
        imageWidth: Int,
        imageHeight: Int,
        isFrontCamera: Boolean
    ): PoseData {
        val allLandmarks = pose.allPoseLandmarks
        if (allLandmarks.isEmpty()) {
            return PoseData.EMPTY
        }

        fun getPoint(landmarkType: Int): LandmarkPoint {
            val landmark = pose.getPoseLandmark(landmarkType) ?: return LandmarkPoint()
            val position = landmark.position
            // Normalize coordinates (0.0 to 1.0)
            var normX = position.x / imageWidth.toFloat()
            val normY = position.y / imageHeight.toFloat()

            // Mirror for front camera natural selfie view
            if (isFrontCamera) {
                normX = 1f - normX
            }

            return LandmarkPoint(
                x = normX.coerceIn(0f, 1f),
                y = normY.coerceIn(0f, 1f),
                z = landmark.position3D.z,
                confidence = landmark.inFrameLikelihood
            )
        }

        // When front camera is mirrored, anatomical left/right swap visual screen sides
        // Let's ensure left/right are mapped to screen-left and screen-right for natural overlay:
        val screenLeftShoulder = if (isFrontCamera) getPoint(PoseLandmark.RIGHT_SHOULDER) else getPoint(PoseLandmark.LEFT_SHOULDER)
        val screenRightShoulder = if (isFrontCamera) getPoint(PoseLandmark.LEFT_SHOULDER) else getPoint(PoseLandmark.RIGHT_SHOULDER)

        val screenLeftElbow = if (isFrontCamera) getPoint(PoseLandmark.RIGHT_ELBOW) else getPoint(PoseLandmark.LEFT_ELBOW)
        val screenRightElbow = if (isFrontCamera) getPoint(PoseLandmark.LEFT_ELBOW) else getPoint(PoseLandmark.RIGHT_ELBOW)

        val screenLeftWrist = if (isFrontCamera) getPoint(PoseLandmark.RIGHT_WRIST) else getPoint(PoseLandmark.LEFT_WRIST)
        val screenRightWrist = if (isFrontCamera) getPoint(PoseLandmark.LEFT_WRIST) else getPoint(PoseLandmark.RIGHT_WRIST)

        val screenLeftHip = if (isFrontCamera) getPoint(PoseLandmark.RIGHT_HIP) else getPoint(PoseLandmark.LEFT_HIP)
        val screenRightHip = if (isFrontCamera) getPoint(PoseLandmark.LEFT_HIP) else getPoint(PoseLandmark.RIGHT_HIP)

        val screenLeftKnee = if (isFrontCamera) getPoint(PoseLandmark.RIGHT_KNEE) else getPoint(PoseLandmark.LEFT_KNEE)
        val screenRightKnee = if (isFrontCamera) getPoint(PoseLandmark.LEFT_KNEE) else getPoint(PoseLandmark.RIGHT_KNEE)

        val screenLeftAnkle = if (isFrontCamera) getPoint(PoseLandmark.RIGHT_ANKLE) else getPoint(PoseLandmark.LEFT_ANKLE)
        val screenRightAnkle = if (isFrontCamera) getPoint(PoseLandmark.LEFT_ANKLE) else getPoint(PoseLandmark.RIGHT_ANKLE)

        val screenLeftEye = if (isFrontCamera) getPoint(PoseLandmark.RIGHT_EYE) else getPoint(PoseLandmark.LEFT_EYE)
        val screenRightEye = if (isFrontCamera) getPoint(PoseLandmark.LEFT_EYE) else getPoint(PoseLandmark.RIGHT_EYE)

        val screenLeftEar = if (isFrontCamera) getPoint(PoseLandmark.RIGHT_EAR) else getPoint(PoseLandmark.LEFT_EAR)
        val screenRightEar = if (isFrontCamera) getPoint(PoseLandmark.LEFT_EAR) else getPoint(PoseLandmark.RIGHT_EAR)

        val avgConfidence = listOf(
            screenLeftShoulder.confidence,
            screenRightShoulder.confidence,
            screenLeftHip.confidence,
            screenRightHip.confidence
        ).average().toFloat()

        return PoseData(
            isDetected = avgConfidence >= 0.45f,
            confidence = avgConfidence,
            nose = getPoint(PoseLandmark.NOSE),
            leftEye = screenLeftEye,
            rightEye = screenRightEye,
            leftEar = screenLeftEar,
            rightEar = screenRightEar,
            leftShoulder = screenLeftShoulder,
            rightShoulder = screenRightShoulder,
            leftElbow = screenLeftElbow,
            rightElbow = screenRightElbow,
            leftWrist = screenLeftWrist,
            rightWrist = screenRightWrist,
            leftHip = screenLeftHip,
            rightHip = screenRightHip,
            leftKnee = screenLeftKnee,
            rightKnee = screenRightKnee,
            leftAnkle = screenLeftAnkle,
            rightAnkle = screenRightAnkle
        )
    }

    fun close() {
        detector.close()
    }
}
