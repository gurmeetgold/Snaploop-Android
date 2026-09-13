package com.snaploop.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedFacePoseTransitionTest {
    private fun observation(yaw: Float = 0f, pitch: Float = 0f) = FacePoseObservation(
        yawDegrees = yaw,
        pitchDegrees = pitch,
        rollDegrees = 0f,
        centerXFraction = 0.5f,
        centerYFraction = 0.5f,
        widthFraction = 0.4f,
        heightFraction = 0.4f,
    )

    @Test
    fun capturedPoseRequiresNeutralTransitionBeforeNextPoseCanQualify() {
        val tracker = GuidedFacePoseTracker(calibrationSamplesRequired = 2, stableFramesRequired = 2)
        assertFalse(tracker.evaluate(GuidedFacePose.FRONT, observation()).readyToCapture)
        assertFalse(tracker.evaluate(GuidedFacePose.FRONT, observation()).readyToCapture)
        assertTrue(tracker.evaluate(GuidedFacePose.FRONT, observation()).readyToCapture)

        tracker.onCaptured()

        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 30f)).readyToCapture)
        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation()).readyToCapture)
        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 30f)).readyToCapture)
        assertTrue(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 30f)).readyToCapture)
    }
}
