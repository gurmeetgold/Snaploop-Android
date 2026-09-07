package com.snaploop.app.face

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.snaploop.app.core.FaceModelPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class AuraFaceRuntimeInstrumentedTest {
    @Test
    fun verifiedModelLoadsAndProducesNormalizedEmbedding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val model = ModelAssetVerifier.verifiedModelFile(context)
        assertTrue("AuraFace model asset is empty", model.isFile && model.length() > 0L)
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().buffered(64 * 1024).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
        assertEquals(FaceModelPolicy.SOURCE_MODEL_SHA256.lowercase(), actualSha)

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
