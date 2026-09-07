package com.snaploop.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedFacePoseTrackerTest {
    private fun observation(
        yaw: Float = 0f,
        pitch: Float = 0f,
        roll: Float = 0f,
    ) = FacePoseObservation(
        yawDegrees = yaw,
        pitchDegrees = pitch,
        rollDegrees = roll,
        centerXFraction = 0.5f,
        centerYFraction = 0.5f,
        widthFraction = 0.42f,
        heightFraction = 0.42f,
    )

    @Test
    fun neutralEulerBiasDoesNotBecomeLeftRightOrTilt() {
        val tracker = GuidedFacePoseTracker(calibrationSamplesRequired = 3, stableFramesRequired = 2)

        // Simulate a Redmi/OEM camera whose straight-ahead estimate is biased.
        repeat(3) { tracker.evaluate(GuidedFacePose.FRONT, observation(yaw = 19f, pitch = -10f)) }
        val front = tracker.evaluate(GuidedFacePose.FRONT, observation(yaw = 19f, pitch = -10f))
        assertTrue(front.readyToCapture)
        tracker.onCaptured()

        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 19f, pitch = -10f)).readyToCapture)
        assertFalse(tracker.evaluate(GuidedFacePose.RIGHT, observation(yaw = 19f, pitch = -10f)).readyToCapture)
        assertFalse(tracker.evaluate(GuidedFacePose.TILT_DOWN, observation(yaw = 19f, pitch = -10f)).readyToCapture)
    }

    @Test
    fun poseRequiresConsecutiveStableFrames() {
        val tracker = GuidedFacePoseTracker(calibrationSamplesRequired = 2, stableFramesRequired = 3)
        repeat(2) { tracker.evaluate(GuidedFacePose.FRONT, observation()) }
        tracker.evaluate(GuidedFacePose.FRONT, observation())
        assertTrue(tracker.evaluate(GuidedFacePose.FRONT, observation()).readyToCapture)
        tracker.onCaptured()

        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = -23f)).readyToCapture)
        // A large jump resets the stability sequence.
        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = -36f)).readyToCapture)
        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = -24f)).readyToCapture)
        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = -23f)).readyToCapture)
        assertTrue(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = -22f)).readyToCapture)
    }

    @Test
    fun relativeTiltUsesCalibratedPitchInsteadOfRawPitch() {
        val tracker = GuidedFacePoseTracker(calibrationSamplesRequired = 2, stableFramesRequired = 2)
        repeat(2) { tracker.evaluate(GuidedFacePose.FRONT, observation(pitch = -11f)) }
        tracker.evaluate(GuidedFacePose.FRONT, observation(pitch = -11f))
        assertTrue(tracker.evaluate(GuidedFacePose.FRONT, observation(pitch = -11f)).readyToCapture)
        tracker.onCaptured()

        // Raw -11 is neutral, not a tilt after calibration.
        assertFalse(tracker.evaluate(GuidedFacePose.TILT_DOWN, observation(pitch = -11f)).readyToCapture)
        assertFalse(tracker.evaluate(GuidedFacePose.TILT_DOWN, observation(pitch = -27f)).readyToCapture)
        assertTrue(tracker.evaluate(GuidedFacePose.TILT_DOWN, observation(pitch = -27f)).readyToCapture)
    }
}
