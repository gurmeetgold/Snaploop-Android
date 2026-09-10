package com.snaploop.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedFacePoseEvaluatorTest {
    private fun observation(
        yaw: Float = 0f,
        pitch: Float = 0f,
        roll: Float = 0f,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f,
        width: Float = 0.42f,
        height: Float = 0.42f,
    ) = FacePoseObservation(yaw, pitch, roll, centerX, centerY, width, height)

    @Test fun front_requires_frontal_well_framed_face() {
        val evaluator = GuidedFacePoseEvaluator()
        assertTrue(evaluator.matches(GuidedFacePose.FRONT, observation()))
        assertFalse(evaluator.matches(GuidedFacePose.FRONT, observation(yaw = 18f)))
        assertFalse(evaluator.matches(GuidedFacePose.FRONT, observation(width = 0.18f, height = 0.18f)))
    }

    @Test fun left_and_right_match_the_mirrored_selfie_preview() {
        val evaluator = GuidedFacePoseEvaluator()
        assertTrue(evaluator.matches(GuidedFacePose.LEFT, observation(yaw = 22f)))
        assertFalse(evaluator.matches(GuidedFacePose.LEFT, observation(yaw = -22f)))
        assertTrue(evaluator.matches(GuidedFacePose.RIGHT, observation(yaw = -24f)))
        assertFalse(evaluator.matches(GuidedFacePose.RIGHT, observation(yaw = 24f)))
    }

    @Test fun tilt_down_uses_android_negative_pitch() {
        val evaluator = GuidedFacePoseEvaluator()
        assertTrue(evaluator.matches(GuidedFacePose.TILT_DOWN, observation(pitch = -16f)))
        assertFalse(evaluator.matches(GuidedFacePose.TILT_DOWN, observation(pitch = 16f)))
        assertFalse(evaluator.matches(GuidedFacePose.TILT_DOWN, observation(pitch = -16f, yaw = 25f)))
    }

    @Test fun finish_front_returns_to_center() {
        val evaluator = GuidedFacePoseEvaluator()
        assertTrue(evaluator.matches(GuidedFacePose.FINISH_FRONT, observation(yaw = 8f, pitch = -8f)))
        assertFalse(evaluator.matches(GuidedFacePose.FINISH_FRONT, observation(yaw = 15f)))
    }

    @Test fun framing_requires_centered_face() {
        val evaluator = GuidedFacePoseEvaluator()
        assertTrue(evaluator.framingStatus(observation()) == FaceFramingStatus.READY)
        assertTrue(evaluator.framingStatus(observation(centerX = 0.12f)) == FaceFramingStatus.NEEDS_ADJUSTMENT)
    }

    @Test fun framing_rejects_partially_cropped_face() {
        val evaluator = GuidedFacePoseEvaluator()
        val lowCropped = observation(centerY = 0.72f, height = 0.42f)
        assertTrue(evaluator.framingStatus(lowCropped) == FaceFramingStatus.NEEDS_ADJUSTMENT)
        assertFalse(evaluator.matches(GuidedFacePose.FRONT, lowCropped))

        val rightCropped = observation(centerX = 0.74f, width = 0.42f)
        assertTrue(evaluator.framingStatus(rightCropped) == FaceFramingStatus.NEEDS_ADJUSTMENT)
        assertFalse(evaluator.matches(GuidedFacePose.LEFT, rightCropped.copy(yawDegrees = 24f)))
    }

    @Test fun framing_rejects_narrow_partial_detection() {
        val evaluator = GuidedFacePoseEvaluator()
        val partial = observation(width = 0.22f, height = 0.55f)
        assertTrue(evaluator.framingStatus(partial) == FaceFramingStatus.NEEDS_ADJUSTMENT)
        assertFalse(evaluator.matches(GuidedFacePose.FRONT, partial))
    }
}
