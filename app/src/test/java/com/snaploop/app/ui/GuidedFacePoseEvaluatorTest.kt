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
    ) = FacePoseObservation(yaw, pitch, roll, centerX, centerY, width)

    @Test fun center_requires_frontal_well_framed_face() {
        val evaluator = GuidedFacePoseEvaluator()
        assertTrue(evaluator.matches(GuidedFacePose.CENTER, observation()))
        assertFalse(evaluator.matches(GuidedFacePose.CENTER, observation(yaw = 18f)))
        assertFalse(evaluator.matches(GuidedFacePose.CENTER, observation(width = 0.12f)))
    }

    @Test fun opposite_side_requires_opposite_yaw_sign() {
        val evaluator = GuidedFacePoseEvaluator()
        assertTrue(evaluator.matches(GuidedFacePose.LEFT, observation(yaw = -22f)))
        assertFalse(evaluator.matches(GuidedFacePose.RIGHT, observation(yaw = -24f)))
        assertTrue(evaluator.matches(GuidedFacePose.RIGHT, observation(yaw = 24f)))
    }

    @Test fun first_side_is_mirror_agnostic() {
        val evaluator = GuidedFacePoseEvaluator()
        assertTrue(evaluator.matches(GuidedFacePose.LEFT, observation(yaw = 21f)))
        assertTrue(evaluator.matches(GuidedFacePose.RIGHT, observation(yaw = -21f)))
    }

    @Test fun up_and_down_use_pitch_direction() {
        val evaluator = GuidedFacePoseEvaluator()
        assertTrue(evaluator.matches(GuidedFacePose.UP, observation(pitch = 16f)))
        assertFalse(evaluator.matches(GuidedFacePose.UP, observation(pitch = -16f)))
        assertTrue(evaluator.matches(GuidedFacePose.DOWN, observation(pitch = -16f)))
    }

    @Test fun excessive_roll_is_rejected() {
        val evaluator = GuidedFacePoseEvaluator()
        assertFalse(evaluator.matches(GuidedFacePose.CENTER, observation(roll = 25f)))
    }
}
