package com.snaploop.app.face

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.snaploop.app.core.FaceModelPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class AuraFaceRuntimeInstrumentedTest {
    @Test
    fun verifiedModelLoadsAndProducesNormalizedEmbedding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val bytes = ModelAssetVerifier.readVerified(context)
        assertTrue("AuraFace model asset is empty", bytes.isNotEmpty())

        AuraFaceEngine(context).use { engine ->
            val neutralRgb = IntArray(112 * 112) { 0xFF7F7F7F.toInt() }
            val embedding = engine.embedCanonical112(neutralRgb)
            assertEquals(FaceModelPolicy.EMBEDDING_DIMENSION, embedding.size)
            assertTrue(embedding.all(Float::isFinite))
            val norm = sqrt(embedding.fold(0.0) { sum, value -> sum + value * value })
            assertTrue("AuraFace embedding must be L2 normalized", norm in 0.999..1.001)
        }
    }
}
