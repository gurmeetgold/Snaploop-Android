package com.snaploop.app.ui

import kotlin.math.abs

/**
 * Stateful pose qualification for the live CameraX enrollment stream.
 *
 * ML Kit Euler angles can have a repeatable neutral offset on particular front-camera/OEM
 * combinations. Treating absolute yaw/pitch as truth caused a straight face on some Xiaomi/Redmi
 * phones to qualify as Left/Right/Tilt. The tracker first learns a stable straight-ahead baseline,
 * then evaluates every remaining pose relative to that baseline and requires several consecutive
 * qualifying frames before a capture is allowed.
 */
internal class GuidedFacePoseTracker(
    private val calibrationSamplesRequired: Int = 6,
    private val stableFramesRequired: Int = 4,
) {
    data class Decision(
        val readyToCapture: Boolean,
        val instruction: String,
        val detail: String,
        val calibrated: Boolean,
    )

    private val calibrationSamples = ArrayDeque<FacePoseObservation>()
    private var neutralYaw: Float? = null
    private var neutralPitch: Float? = null
    private var activePose: GuidedFacePose? = null
    private var stableFrameCount = 0
    private var lastQualified: FacePoseObservation? = null

    fun reset() {
        calibrationSamples.clear()
        neutralYaw = null
        neutralPitch = null
        activePose = null
        stableFrameCount = 0
        lastQualified = null
    }

    fun onCaptured() {
        activePose = null
        stableFrameCount = 0
        lastQualified = null
    }

    fun evaluate(pose: GuidedFacePose, observation: FacePoseObservation): Decision {
        if (pose != activePose) {
            activePose = pose
            stableFrameCount = 0
            lastQualified = null
        }

        if (observation.areaFraction < 0.12f) {
            resetStability()
            return decision(false, pose, "Move a little closer")
        }
        if (observation.centerXFraction !in 0.24f..0.76f || observation.centerYFraction !in 0.20f..0.80f) {
            resetStability()
            return decision(false, pose, "Center your face inside the oval")
        }
        if (abs(observation.rollDegrees) > 12f) {
            resetStability()
            return decision(false, pose, "Keep your head level")
        }

        if (neutralYaw == null || neutralPitch == null) {
            return calibrate(pose, observation)
        }

        val yaw = observation.yawDegrees - neutralYaw!!
        val pitch = observation.pitchDegrees - neutralPitch!!
        val qualifies = when (pose) {
            GuidedFacePose.FRONT -> abs(yaw) <= 7f && abs(pitch) <= 8f
            GuidedFacePose.LEFT -> yaw in -42f..-18f && abs(pitch) <= 11f
            GuidedFacePose.RIGHT -> yaw in 18f..42f && abs(pitch) <= 11f
            GuidedFacePose.TILT_DOWN -> pitch in -32f..-12f && abs(yaw) <= 11f
            GuidedFacePose.FINISH_FRONT -> abs(yaw) <= 8f && abs(pitch) <= 10f
        }

        if (!qualifies) {
            resetStability()
            return Decision(
                readyToCapture = false,
                instruction = instruction(pose),
                detail = directionHint(pose, yaw, pitch),
                calibrated = true,
            )
        }

        val previous = lastQualified
        val stableWithPrevious = previous == null || (
            abs(observation.yawDegrees - previous.yawDegrees) <= 4f &&
                abs(observation.pitchDegrees - previous.pitchDegrees) <= 4f &&
                abs(observation.rollDegrees - previous.rollDegrees) <= 5f
            )
        stableFrameCount = if (stableWithPrevious) stableFrameCount + 1 else 1
        lastQualified = observation

        val ready = stableFrameCount >= stableFramesRequired
        return Decision(
            readyToCapture = ready,
            instruction = if (ready) "Hold still" else instruction(pose),
            detail = if (ready) {
                "Angle confirmed"
            } else {
                "Hold this position briefly"
            },
            calibrated = true,
        )
    }

    private fun calibrate(pose: GuidedFacePose, observation: FacePoseObservation): Decision {
        if (pose != GuidedFacePose.FRONT) {
            return Decision(false, instruction(pose), "Restart Face Setup and begin looking straight", false)
        }

        // Wide enough to absorb an OEM-specific neutral Euler bias, but not wide enough to learn a
        // deliberately turned/tilted head as the baseline.
        if (abs(observation.yawDegrees) > 25f || abs(observation.pitchDegrees) > 22f) {
            calibrationSamples.clear()
            return Decision(false, instruction(pose), "Face the camera naturally and keep your head level", false)
        }

        calibrationSamples.addLast(observation)
        while (calibrationSamples.size > calibrationSamplesRequired) calibrationSamples.removeFirst()

        if (calibrationSamples.size < calibrationSamplesRequired) {
            return Decision(false, instruction(pose), "Hold straight for a moment", false)
        }

        val yawValues = calibrationSamples.map { it.yawDegrees }
        val pitchValues = calibrationSamples.map { it.pitchDegrees }
        val yawSpread = (yawValues.maxOrNull() ?: 0f) - (yawValues.minOrNull() ?: 0f)
        val pitchSpread = (pitchValues.maxOrNull() ?: 0f) - (pitchValues.minOrNull() ?: 0f)
        if (yawSpread > 6f || pitchSpread > 6f) {
            // Keep a rolling window until the user's neutral pose is actually stable.
            return Decision(false, instruction(pose), "Keep your face still while SnapLoop calibrates", false)
        }

        neutralYaw = yawValues.average().toFloat()
        neutralPitch = pitchValues.average().toFloat()
        stableFrameCount = 1
        lastQualified = observation
        return Decision(
            readyToCapture = stableFramesRequired <= 1,
            instruction = instruction(pose),
            detail = "Great — hold straight briefly",
            calibrated = true,
        )
    }

    private fun decision(ready: Boolean, pose: GuidedFacePose, detail: String) = Decision(
        readyToCapture = ready,
        instruction = instruction(pose),
        detail = detail,
        calibrated = neutralYaw != null && neutralPitch != null,
    )

    private fun resetStability() {
        stableFrameCount = 0
        lastQualified = null
    }

    private fun instruction(pose: GuidedFacePose): String = when (pose) {
        GuidedFacePose.FRONT -> "Look straight at the camera"
        GuidedFacePose.LEFT -> "Turn your face LEFT"
        GuidedFacePose.RIGHT -> "Turn your face RIGHT"
        GuidedFacePose.TILT_DOWN -> "Tilt slightly DOWN"
        GuidedFacePose.FINISH_FRONT -> "Look straight again"
    }

    private fun directionHint(pose: GuidedFacePose, yaw: Float, pitch: Float): String = when (pose) {
        GuidedFacePose.FRONT, GuidedFacePose.FINISH_FRONT -> when {
            yaw < -8f -> "Turn slightly RIGHT toward center"
            yaw > 8f -> "Turn slightly LEFT toward center"
            pitch < -10f -> "Raise your chin slightly"
            pitch > 10f -> "Lower your chin slightly"
            else -> "Hold still"
        }
        GuidedFacePose.LEFT -> when {
            yaw > -18f -> "Keep turning LEFT"
            yaw < -42f -> "Come slightly back toward center"
            abs(pitch) > 11f -> "Keep your chin level"
            else -> "Hold still"
        }
        GuidedFacePose.RIGHT -> when {
            yaw < 18f -> "Keep turning RIGHT"
            yaw > 42f -> "Come slightly back toward center"
            abs(pitch) > 11f -> "Keep your chin level"
            else -> "Hold still"
        }
        GuidedFacePose.TILT_DOWN -> when {
            abs(yaw) > 11f -> "Face forward while lowering your chin"
            pitch > -12f -> "Lower your chin a little"
            pitch < -32f -> "Raise your chin slightly"
            else -> "Hold still"
        }
    }
}
