package com.snaploop.app.ui

import android.content.Context
import com.snaploop.app.core.Embeddings
import com.snaploop.app.core.FaceModelPolicy
import com.snaploop.app.core.FaceTemplateEvaluation
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
 * Tests an ordinary photo against the authenticated user's saved Face Setup.
 * The photo may contain one or many people; each usable detected face is
 * evaluated independently and the strongest Face Setup match wins. The test
 * image is never persisted or uploaded by this action.
 */
internal class FaceSetupParityActions(context: Context) {
    private val appContext = context.applicationContext
    private val profiles = FirebaseFaceProfileStore()

    suspend fun testSavedFace(userId: String, jpeg: ByteArray): FaceSetupTestResult {
        require(jpeg.isNotEmpty()) { "Test photo is empty." }
        val profile = profiles.load(userId) ?: error("Set up your face before testing it.")
        val savedEmbeddings = profile.effectiveEmbeddings()
        if (savedEmbeddings.isEmpty()) error("Your saved Face Setup could not be evaluated.")

        val probes = withContext(Dispatchers.Default) {
            AndroidFacePipeline(appContext).use { pipeline ->
                pipeline.detectFaces(jpeg).mapNotNull { detected ->
                    Embeddings.normalize(detected.embedding)
                }
            }
        }
        if (probes.isEmpty()) error("SnapLoop could not read a usable face from that photo.")

        val evaluations = probes.mapNotNull { probe ->
            val similarities = savedEmbeddings.mapNotNull { saved -> Embeddings.cosine(probe, saved) }
            FaceTemplateMatchPolicy.evaluate(similarities, FaceModelPolicy.EVALUATION_MATCH_THRESHOLD)
        }
        val evaluation = strongestEvaluation(evaluations)
            ?: error("Your saved Face Setup could not be evaluated.")

        val best = evaluation.best.coerceIn(-1.0, 1.0)
        return FaceSetupTestResult(
            accepted = evaluation.accepted,
            bestSimilarity = best,
            message = if (evaluation.accepted) {
                "Face Setup is working. SnapLoop recognized you in this photo."
            } else {
                "SnapLoop did not confidently recognize you in this photo. Try another clear photo, or update Face Setup."
            },
        )
    }

    private fun strongestEvaluation(evaluations: List<FaceTemplateEvaluation>): FaceTemplateEvaluation? =
        evaluations.maxWithOrNull(
            compareBy<FaceTemplateEvaluation> { it.accepted }
                .thenBy { it.decisionScore }
                .thenBy { it.best },
        )
}
