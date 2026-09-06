package com.snaploop.app.core

import kotlin.math.sqrt

data class FaceTemplateEvaluation(val best: Double, val second: Double?, val decisionScore: Double, val corroborated: Boolean, val strongSingle: Boolean) { val accepted get() = corroborated || strongSingle }

data class FaceParticipant(val userId: String, val membershipId: String?, val faceIdentityId: String, val faceProfileRevision: String, val faceProfileVersion: Int, val embeddings: List<FloatArray>)
data class DetectedEmbedding(val embedding: FloatArray, val sizeFraction: Double)
data class FaceAppearance(val participantUserId: String, val recipientMembershipId: String?, val confidence: Double, val faceIdentityId: String, val faceProfileRevision: String)

data class MatchConfig(val threshold: Double = FaceModelPolicy.EVALUATION_MATCH_THRESHOLD, val ambiguityMargin: Double = FaceModelPolicy.EVALUATION_AMBIGUITY_MARGIN, val minFaceSizeFraction: Double)

object Embeddings {
    fun normalize(input: FloatArray): FloatArray? {
        if (input.size != FaceModelPolicy.EMBEDDING_DIMENSION || input.any { !it.isFinite() }) return null
        val norm = sqrt(input.fold(0.0) { a, v -> a + v * v })
        if (norm <= 1e-12) return null
        return FloatArray(input.size) { (input[it] / norm).toFloat() }
    }
    fun cosine(a: FloatArray, b: FloatArray): Double? {
        if (a.size != FaceModelPolicy.EMBEDDING_DIMENSION || b.size != a.size) return null
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { val x=a[i].toDouble(); val y=b[i].toDouble(); if(!x.isFinite()||!y.isFinite()) return null; dot += x*y; na += x*x; nb += y*y }
        if (na <= 1e-12 || nb <= 1e-12) return null
        return dot / sqrt(na * nb)
    }
}

object FaceTemplateMatchPolicy {
    fun evaluate(similarities: List<Double>, threshold: Double): FaceTemplateEvaluation? {
        val scores = similarities.sortedDescending(); val best = scores.firstOrNull() ?: return null; val second = scores.getOrNull(1)
        val corroborated = best >= threshold - FaceModelPolicy.CORROBORATED_BEST_TEMPLATE_SLACK && second?.let { it >= threshold - FaceModelPolicy.SUPPORTING_TEMPLATE_SLACK } == true
        val strongSingle = best >= threshold + FaceModelPolicy.STRONG_SINGLE_TEMPLATE_BONUS
        val decision = if (second != null && corroborated) best * 0.90 + second * 0.10 else best
        return FaceTemplateEvaluation(best, second, decision, corroborated, strongSingle)
    }
}

class FaceMatcher(private val config: MatchConfig) {
    fun appearances(faces: List<DetectedEmbedding>, participants: List<FaceParticipant>): List<FaceAppearance> {
        val best = mutableMapOf<String, FaceAppearance>()
        for (face in faces) {
            if (face.sizeFraction < config.minFaceSizeFraction) continue
            val ranked = participants.mapNotNull { p ->
                if (p.faceProfileVersion != FaceModelPolicy.CURRENT_VERSION || p.faceIdentityId.isBlank() || p.faceProfileRevision.isBlank()) null
                else FaceTemplateMatchPolicy.evaluate(p.embeddings.mapNotNull { Embeddings.cosine(face.embedding, it) }, config.threshold)?.let { p to it }
            }.sortedByDescending { it.second.decisionScore }
            val winner = ranked.firstOrNull() ?: continue
            if (!winner.second.accepted) continue
            if (ranked.size > 1 && winner.second.decisionScore - ranked[1].second.decisionScore < config.ambiguityMargin) continue
            val p=winner.first; val e=winner.second
            val appearance = FaceAppearance(p.userId,p.membershipId,e.decisionScore,p.faceIdentityId,p.faceProfileRevision)
            if ((best[p.userId]?.confidence ?: Double.NEGATIVE_INFINITY) < appearance.confidence) best[p.userId]=appearance
        }
        return best.values.sortedByDescending { it.confidence }
    }
}
