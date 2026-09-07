package com.snaploop.app.ui

import kotlin.math.abs

/** Exact enrollment order from the pinned iOS GuidedFaceEnrollmentView. */
enum class GuidedFacePose {
    FRONT,
    LEFT,
    RIGHT,
    TILT_DOWN,
    FINISH_FRONT,
}

data class FacePoseObservation(
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val rollDegrees: Float,
    val centerXFraction: Float,
    val centerYFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
) {
    val areaFraction: Float get() = widthFraction * heightFraction
}

/**
 * Pure pose gate used by tests and non-camera callers.
 *
 * The live CameraX enrollment additionally calibrates yaw/pitch from the accepted front pose and
 * requires several consecutive qualifying frames. These absolute limits deliberately stay strict:
 * a side pose must also remain level, and a tilt must remain close to the user's forward yaw. This
 * prevents a noisy straight-ahead ML Kit estimate from satisfying several enrollment steps.
 */
class GuidedFacePoseEvaluator {
    fun matches(pose: GuidedFacePose, observation: FacePoseObservation): Boolean {
        if (!isWellFramed(observation) || abs(observation.rollDegrees) > 12f) return false

        return when (pose) {
            GuidedFacePose.FRONT ->
                abs(observation.yawDegrees) <= 8f && abs(observation.pitchDegrees) <= 10f

            GuidedFacePose.LEFT ->
                observation.yawDegrees in -38f..-18f && abs(observation.pitchDegrees) <= 12f

            GuidedFacePose.RIGHT ->
                observation.yawDegrees in 18f..38f && abs(observation.pitchDegrees) <= 12f

            GuidedFacePose.TILT_DOWN ->
                observation.pitchDegrees in -30f..-12f && abs(observation.yawDegrees) <= 12f

            GuidedFacePose.FINISH_FRONT ->
                abs(observation.yawDegrees) <= 10f && abs(observation.pitchDegrees) <= 12f
        }
    }

    fun instruction(pose: GuidedFacePose): String = when (pose) {
        GuidedFacePose.FRONT -> "Look straight at the camera"
        GuidedFacePose.LEFT -> "Turn your face LEFT"
        GuidedFacePose.RIGHT -> "Turn your face RIGHT"
        GuidedFacePose.TILT_DOWN -> "Tilt slightly DOWN"
        GuidedFacePose.FINISH_FRONT -> "Look straight again"
    }

    fun detail(pose: GuidedFacePose, observation: FacePoseObservation?): String = when {
        observation == null -> "Keep the phone steady"
        observation.areaFraction < 0.12f -> "Move a little closer"
        !isCentered(observation) -> "Center your face inside the oval"
        abs(observation.rollDegrees) > 12f -> "Keep your head level"
        pose == GuidedFacePose.FRONT || pose == GuidedFacePose.FINISH_FRONT -> when {
            observation.yawDegrees < -8f -> "Turn slightly RIGHT toward center"
            observation.yawDegrees > 8f -> "Turn slightly LEFT toward center"
            observation.pitchDegrees > 10f -> "Lower your chin slightly"
            observation.pitchDegrees < -10f -> "Raise your chin slightly"
            else -> "Hold still"
        }
        pose == GuidedFacePose.LEFT ->
            if (observation.yawDegrees > -18f) "Keep turning LEFT" else "Come slightly back toward center"
        pose == GuidedFacePose.RIGHT ->
            if (observation.yawDegrees < 18f) "Keep turning RIGHT" else "Come slightly back toward center"
        else -> when {
            observation.pitchDegrees > -12f -> "Lower your chin a little"
            observation.pitchDegrees < -30f -> "Raise your chin slightly"
            else -> "Hold still"
        }
    }

    fun framingStatus(observation: FacePoseObservation?): FaceFramingStatus = when {
        observation == null -> FaceFramingStatus.NOT_DETECTED
        observation.areaFraction < 0.12f || !isCentered(observation) -> FaceFramingStatus.NEEDS_ADJUSTMENT
        else -> FaceFramingStatus.READY
    }

    private fun isWellFramed(observation: FacePoseObservation): Boolean =
        observation.areaFraction >= 0.12f && isCentered(observation)

    private fun isCentered(observation: FacePoseObservation): Boolean =
        observation.centerXFraction in 0.24f..0.76f &&
            observation.centerYFraction in 0.20f..0.80f
}

enum class FaceFramingStatus {
    NOT_DETECTED,
    NEEDS_ADJUSTMENT,
    READY,
}
