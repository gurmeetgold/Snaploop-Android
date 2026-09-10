package com.snaploop.app.ui

import kotlin.math.abs

/**
 * Stateful pose qualification for the live CameraX enrollment stream.
 *
 * ML Kit Euler angles can have a repeatable neutral offset on particular front-camera/OEM
 * combinations. Treating absolute yaw/pitch as truth caused a straight face on some Xiaomi/Redmi
 * phones to qualify as Left/Right/Tilt. The tracker first learns a stable straight-ahead baseline,
 * then evaluates every remaining pose relative to that baseline and requires a sustained sequence
 * of qualifying frames before a capture is allowed.
 *
 * CameraX analyzes the unmirrored sensor image while the user sees a mirrored selfie preview.
 * ML Kit therefore reports the opposite horizontal direction from the direction presented to the
 * user. Positive relative Euler-Y is the user's LEFT in the mirrored preview; negative is RIGHT.
 */
internal class GuidedFacePoseTracker(
    private val calibrationSamplesRequired: Int = 8,
    private val stableFramesRequired: Int = 10,
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

        if (!GuidedFaceFramingPolicy.isWellFramed(observation)) {
            resetStability()
            return decision(false, pose, GuidedFaceFramingPolicy.detail(observation))
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
            // ML Kit observes the unmirrored sensor frame. These signs intentionally mirror the
            // displayed selfie preview so LEFT/RIGHT mean the direction the user is instructed to turn.
            GuidedFacePose.LEFT -> yaw in 20f..42f && abs(pitch) <= 11f
            GuidedFacePose.RIGHT -> yaw in -42f..-20f && abs(pitch) <= 11f
            GuidedFacePose.TILT_DOWN -> pitch in -32f..-14f && abs(yaw) <= 11f
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
            abs(observation.yawDegrees - previous.yawDegrees) <= 3f &&
                abs(observation.pitchDegrees - previous.pitchDegrees) <= 3f &&
                abs(observation.rollDegrees - previous.rollDegrees) <= 4f &&
                abs(observation.centerXFraction - previous.centerXFraction) <= 0.035f &&
                abs(observation.centerYFraction - previous.centerYFraction) <= 0.035f
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
        if (abs(observation.yawDegrees) > 22f || abs(observation.pitchDegrees) > 20f) {
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
        val xValues = calibrationSamples.map { it.centerXFraction }
        val yValues = calibrationSamples.map { it.centerYFraction }
        val yawSpread = (yawValues.maxOrNull() ?: 0f) - (yawValues.minOrNull() ?: 0f)
        val pitchSpread = (pitchValues.maxOrNull() ?: 0f) - (pitchValues.minOrNull() ?: 0f)
        val xSpread = (xValues.maxOrNull() ?: 0f) - (xValues.minOrNull() ?: 0f)
        val ySpread = (yValues.maxOrNull() ?: 0f) - (yValues.minOrNull() ?: 0f)
        if (yawSpread > 5f || pitchSpread > 5f || xSpread > 0.05f || ySpread > 0.05f) {
            // Keep a rolling window until both pose and framing are actually stable.
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
            yaw > 8f -> "Turn slightly RIGHT toward center"
            yaw < -8f -> "Turn slightly LEFT toward center"
            pitch < -10f -> "Raise your chin slightly"
            pitch > 10f -> "Lower your chin slightly"
            else -> "Hold still"
        }
        GuidedFacePose.LEFT -> when {
            yaw < 20f -> "Keep turning LEFT"
            yaw > 42f -> "Come slightly back toward center"
            abs(pitch) > 11f -> "Keep your chin level"
            else -> "Hold still"
        }
        GuidedFacePose.RIGHT -> when {
            yaw > -20f -> "Keep turning RIGHT"
            yaw < -42f -> "Come slightly back toward center"
            abs(pitch) > 11f -> "Keep your chin level"
            else -> "Hold still"
        }
        GuidedFacePose.TILT_DOWN -> when {
            abs(yaw) > 11f -> "Face forward while lowering your chin"
            pitch > -14f -> "Lower your chin a little"
            pitch < -32f -> "Raise your chin slightly"
            else -> "Hold still"
        }
    }
}