package com.snaploop.app.ui

import kotlin.math.abs

/** Exact enrollment order from the pinned iOS GuidedFaceEnrollmentView. */
enum class GuidedFacePose {
    FRONT,
    LEFT,
    RIGHT,
    TILT_DOWN,
    FINISH_FRONT;

    companion object {}
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
    val leftFraction: Float get() = centerXFraction - widthFraction / 2f
    val rightFraction: Float get() = centerXFraction + widthFraction / 2f
    val topFraction: Float get() = centerYFraction - heightFraction / 2f
    val bottomFraction: Float get() = centerYFraction + heightFraction / 2f
}

/**
 * Enrollment framing gate shared by the live tracker and pure evaluator.
 *
 * The old gate checked only face center and total area. A half-visible face could therefore pass
 * whenever its detector box still had enough area and its center landed in the broad center range.
 * Require the detected face box itself to remain comfortably inside the capture region. The bounds
 * intentionally leave tolerance for side poses while rejecting cropped forehead/chin/cheeks.
 */
internal object GuidedFaceFramingPolicy {
    private const val MIN_AREA = 0.12f
    private const val MIN_WIDTH = 0.28f
    private const val MIN_HEIGHT = 0.30f
    private const val MIN_CENTER_X = 0.30f
    private const val MAX_CENTER_X = 0.70f
    private const val MIN_CENTER_Y = 0.30f
    private const val MAX_CENTER_Y = 0.68f
    private const val MIN_LEFT = 0.08f
    private const val MAX_RIGHT = 0.92f
    private const val MIN_TOP = 0.06f
    private const val MAX_BOTTOM = 0.90f

    fun isWellFramed(observation: FacePoseObservation): Boolean =
        observation.areaFraction >= MIN_AREA &&
            observation.widthFraction >= MIN_WIDTH &&
            observation.heightFraction >= MIN_HEIGHT &&
            observation.centerXFraction in MIN_CENTER_X..MAX_CENTER_X &&
            observation.centerYFraction in MIN_CENTER_Y..MAX_CENTER_Y &&
            observation.leftFraction >= MIN_LEFT &&
            observation.rightFraction <= MAX_RIGHT &&
            observation.topFraction >= MIN_TOP &&
            observation.bottomFraction <= MAX_BOTTOM

    fun detail(observation: FacePoseObservation): String = when {
        observation.areaFraction < MIN_AREA ||
            observation.widthFraction < MIN_WIDTH ||
            observation.heightFraction < MIN_HEIGHT -> "Move a little closer"
        else -> "Center your whole face inside the oval"
    }
}

/**
 * Pure pose gate used by tests and non-camera callers.
 *
 * CameraX/ML Kit analyze the unmirrored sensor frame while the selfie preview is mirrored. For
 * horizontal poses, positive ML Kit Euler-Y therefore corresponds to the user's LEFT in the UI and
 * negative Euler-Y corresponds to the user's RIGHT. Pitch follows Android/ML Kit convention: a
 * downward chin movement is negative.
 */
class GuidedFacePoseEvaluator {
    fun matches(pose: GuidedFacePose, observation: FacePoseObservation): Boolean {
        if (!GuidedFaceFramingPolicy.isWellFramed(observation) || abs(observation.rollDegrees) > 12f) return false

        return when (pose) {
            GuidedFacePose.FRONT ->
                abs(observation.yawDegrees) <= 8f && abs(observation.pitchDegrees) <= 10f

            GuidedFacePose.LEFT ->
                observation.yawDegrees in 18f..38f && abs(observation.pitchDegrees) <= 12f

            GuidedFacePose.RIGHT ->
                observation.yawDegrees in -38f..-18f && abs(observation.pitchDegrees) <= 12f

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
        !GuidedFaceFramingPolicy.isWellFramed(observation) -> GuidedFaceFramingPolicy.detail(observation)
        abs(observation.rollDegrees) > 12f -> "Keep your head level"
        pose == GuidedFacePose.FRONT || pose == GuidedFacePose.FINISH_FRONT -> when {
            observation.yawDegrees > 8f -> "Turn slightly RIGHT toward center"
            observation.yawDegrees < -8f -> "Turn slightly LEFT toward center"
            observation.pitchDegrees > 10f -> "Lower your chin slightly"
            observation.pitchDegrees < -10f -> "Raise your chin slightly"
            else -> "Hold still"
        }
        pose == GuidedFacePose.LEFT ->
            if (observation.yawDegrees < 18f) "Keep turning LEFT" else "Come slightly back toward center"
        pose == GuidedFacePose.RIGHT ->
            if (observation.yawDegrees > -18f) "Keep turning RIGHT" else "Come slightly back toward center"
        else -> when {
            observation.pitchDegrees > -12f -> "Lower your chin a little"
            observation.pitchDegrees < -30f -> "Raise your chin slightly"
            else -> "Hold still"
        }
    }

    fun framingStatus(observation: FacePoseObservation?): FaceFramingStatus = when {
        observation == null -> FaceFramingStatus.NOT_DETECTED
        !GuidedFaceFramingPolicy.isWellFramed(observation) -> FaceFramingStatus.NEEDS_ADJUSTMENT
        else -> FaceFramingStatus.READY
    }
}

enum class FaceFramingStatus {
    NOT_DETECTED,
    NEEDS_ADJUSTMENT,
    READY,
}