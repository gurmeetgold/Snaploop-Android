package com.snaploop.app.ui

import android.content.Context
import com.snaploop.app.core.Embeddings
import com.snaploop.app.core.FaceModelPolicy
import com.snaploop.app.core.FaceTemplateMatchPolicy
import com.snaploop.app.data.FirebaseFaceProfileStore
import com.snaploop.app.face.AndroidFacePipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class FaceSetupTestResult(
    val accepted: Boolean,
    val bestSimilarity: Double,
    val message: String,
)

/**
 * Evaluates a fresh straight-on selfie against the authenticated user's saved
 * v5 Face Setup without persisting or uploading the test image.
 */
internal class FaceSetupParityActions(context: Context) {
    private val appContext = context.applicationContext
    private val profiles = FirebaseFaceProfileStore()

    suspend fun testSavedFace(userId: String, jpeg: ByteArray): FaceSetupTestResult {
        require(jpeg.isNotEmpty()) { "Test selfie is empty." }
        val profile = profiles.load(userId) ?: error("Set up your face before testing it.")

        val probe = withContext(Dispatchers.Default) {
            AndroidFacePipeline(appContext).use { it.embeddingForSelfie(jpeg) }
        }
        val normalizedProbe = Embeddings.normalize(probe)
            ?: error("SnapLoop could not read a usable face from that selfie.")
        val similarities = profile.effectiveEmbeddings().mapNotNull { saved ->
            Embeddings.cosine(normalizedProbe, saved)
        }
        val evaluation = FaceTemplateMatchPolicy.evaluate(
            similarities,
            FaceModelPolicy.EVALUATION_MATCH_THRESHOLD,
        ) ?: error("Your saved Face Setup could not be evaluated.")

        val best = evaluation.best.coerceIn(-1.0, 1.0)
        return FaceSetupTestResult(
            accepted = evaluation.accepted,
            bestSimilarity = best,
            message = if (evaluation.accepted) {
                "Face Setup is working. SnapLoop recognized this test selfie."
            } else {
                "SnapLoop did not confidently recognize this selfie. Try again in even lighting, or update Face Setup."
            },
        )
    }
}
