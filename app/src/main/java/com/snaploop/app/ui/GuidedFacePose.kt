package com.snaploop.app.ui

import kotlin.math.abs

enum class GuidedFacePose {
    CENTER,
    LEFT,
    RIGHT,
    UP,
    DOWN,
}

data class FacePoseObservation(
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val rollDegrees: Float,
    val centerXFraction: Float,
    val centerYFraction: Float,
    val widthFraction: Float,
)

/**
 * Pure pose gate used by the live CameraX analyzer.
 *
 * LEFT intentionally accepts either yaw sign for the first side pose because front-camera sensor
 * coordinates differ from the mirrored preview users see. Once LEFT is observed, RIGHT must use
 * the opposite sign. This makes the UX deterministic without hard-coding a device-specific mirror.
 */
class GuidedFacePoseEvaluator {
    private var firstSideSign: Float? = null

    fun resetAll() {
        firstSideSign = null
    }

    fun matches(pose: GuidedFacePose, observation: FacePoseObservation): Boolean {
        if (!isWellFramed(observation)) return false
        if (abs(observation.rollDegrees) > 16f) return false

        return when (pose) {
            GuidedFacePose.CENTER ->
                abs(observation.yawDegrees) <= 9f && abs(observation.pitchDegrees) <= 9f

            GuidedFacePose.LEFT -> {
                val side = abs(observation.yawDegrees) in 14f..42f && abs(observation.pitchDegrees) <= 18f
                if (side && firstSideSign == null) {
                    firstSideSign = if (observation.yawDegrees >= 0f) 1f else -1f
                }
                side
            }

            GuidedFacePose.RIGHT -> {
                val sign = firstSideSign ?: return false
                abs(observation.yawDegrees) in 14f..42f &&
                    observation.yawDegrees * sign < 0f &&
                    abs(observation.pitchDegrees) <= 18f
            }

            GuidedFacePose.UP ->
                observation.pitchDegrees in 10f..30f && abs(observation.yawDegrees) <= 20f

            GuidedFacePose.DOWN ->
                observation.pitchDegrees in -30f..-10f && abs(observation.yawDegrees) <= 20f
        }
    }

    fun guidance(pose: GuidedFacePose, observation: FacePoseObservation?): String = when {
        observation == null -> "Position your face inside the frame"
        observation.widthFraction < 0.22f -> "Move a little closer"
        observation.widthFraction > 0.72f -> "Move a little farther away"
        !isCentered(observation) -> "Center your face"
        abs(observation.rollDegrees) > 16f -> "Keep your head upright"
        pose == GuidedFacePose.CENTER -> "Look straight at the camera"
        pose == GuidedFacePose.LEFT -> "Turn slightly left"
        pose == GuidedFacePose.RIGHT -> "Turn slightly right"
        pose == GuidedFacePose.UP -> "Look slightly up"
        else -> "Look slightly down"
    }

    private fun isWellFramed(observation: FacePoseObservation): Boolean =
        observation.widthFraction in 0.22f..0.72f && isCentered(observation)

    private fun isCentered(observation: FacePoseObservation): Boolean =
        observation.centerXFraction in 0.30f..0.70f &&
            observation.centerYFraction in 0.25f..0.75f
}
