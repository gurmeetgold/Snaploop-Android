package com.snaploop.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedFacePoseTrackerTest {
    private fun observation(
        yaw: Float = 0f,
        pitch: Float = 0f,
        roll: Float = 0f,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f,
        width: Float = 0.42f,
        height: Float = 0.42f,
    ) = FacePoseObservation(
        yawDegrees = yaw,
        pitchDegrees = pitch,
        rollDegrees = roll,
        centerXFraction = centerX,
        centerYFraction = centerY,
        widthFraction = width,
        heightFraction = height,
    )

    @Test
    fun neutralEulerBiasDoesNotBecomeLeftRightOrTilt() {
        val tracker = GuidedFacePoseTracker(calibrationSamplesRequired = 3, stableFramesRequired = 2)

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

        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 23f)).readyToCapture)
        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 36f)).readyToCapture)
        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 24f)).readyToCapture)
        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 23f)).readyToCapture)
        assertTrue(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 22f)).readyToCapture)
    }

    @Test
    fun mirroredPreviewMapsPositiveYawToUserLeftAndNegativeToRight() {
        val tracker = GuidedFacePoseTracker(calibrationSamplesRequired = 2, stableFramesRequired = 2)
        repeat(2) { tracker.evaluate(GuidedFacePose.FRONT, observation()) }
        tracker.evaluate(GuidedFacePose.FRONT, observation())
        assertTrue(tracker.evaluate(GuidedFacePose.FRONT, observation()).readyToCapture)
        tracker.onCaptured()

        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = -24f)).readyToCapture)
        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 24f)).readyToCapture)
        assertTrue(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 24f)).readyToCapture)
        tracker.onCaptured()

        assertFalse(tracker.evaluate(GuidedFacePose.RIGHT, observation(yaw = 24f)).readyToCapture)
        assertFalse(tracker.evaluate(GuidedFacePose.RIGHT, observation(yaw = -24f)).readyToCapture)
        assertTrue(tracker.evaluate(GuidedFacePose.RIGHT, observation(yaw = -24f)).readyToCapture)
    }

    @Test
    fun relativeTiltUsesCalibratedPitchInsteadOfRawPitch() {
        val tracker = GuidedFacePoseTracker(calibrationSamplesRequired = 2, stableFramesRequired = 2)
        repeat(2) { tracker.evaluate(GuidedFacePose.FRONT, observation(pitch = -11f)) }
        tracker.evaluate(GuidedFacePose.FRONT, observation(pitch = -11f))
        assertTrue(tracker.evaluate(GuidedFacePose.FRONT, observation(pitch = -11f)).readyToCapture)
        tracker.onCaptured()

        assertFalse(tracker.evaluate(GuidedFacePose.TILT_DOWN, observation(pitch = -11f)).readyToCapture)
        assertFalse(tracker.evaluate(GuidedFacePose.TILT_DOWN, observation(pitch = -27f)).readyToCapture)
        assertTrue(tracker.evaluate(GuidedFacePose.TILT_DOWN, observation(pitch = -27f)).readyToCapture)
    }

    @Test
    fun croppedFaceResetsPoseStability() {
        val tracker = GuidedFacePoseTracker(calibrationSamplesRequired = 2, stableFramesRequired = 3)
        repeat(2) { tracker.evaluate(GuidedFacePose.FRONT, observation()) }
        tracker.evaluate(GuidedFacePose.FRONT, observation())
        assertTrue(tracker.evaluate(GuidedFacePose.FRONT, observation()).readyToCapture)
        tracker.onCaptured()

        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 24f)).readyToCapture)
        assertFalse(
            tracker.evaluate(
                GuidedFacePose.LEFT,
                observation(yaw = 24f, centerY = 0.72f),
            ).readyToCapture,
        )
        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 24f)).readyToCapture)
        assertFalse(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 24f)).readyToCapture)
        assertTrue(tracker.evaluate(GuidedFacePose.LEFT, observation(yaw = 24f)).readyToCapture)
    }
}
