package com.snaploop.app.domain

import com.snaploop.app.core.FaceModelPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceProfileReplacementPolicyTest {
    private fun vector(index: Int): FloatArray = FloatArray(FaceModelPolicy.EMBEDDING_DIMENSION) { if (it == index) 1f else 0f }
    private fun profile(offset: Int = 0): FaceProfile {
        val templates = listOf(FaceTemplatePose.CENTER, FaceTemplatePose.SIDE_A, FaceTemplatePose.SIDE_B).mapIndexed { i, pose ->
            FaceTemplateRecord("t$offset-$i", vector(offset), pose, 1.0, 1000L + i)
        }
        return FaceProfile("u", "identity", vector(offset), templates, FaceModelPolicy.CURRENT_VERSION, 2000)
    }

    @Test fun sameIdentityReplacementAccepted() { assertTrue(FaceProfileReplacementPolicy.isSameIdentity(profile(0), profile(0))) }
    @Test fun differentIdentityReplacementRejected() { assertFalse(FaceProfileReplacementPolicy.isSameIdentity(profile(1), profile(0))) }
}
