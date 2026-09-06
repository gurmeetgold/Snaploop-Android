package com.snaploop.app.core

import org.junit.Assert.*
import org.junit.Test

class FaceMatcherTest {
    private fun unit(index: Int): FloatArray = FloatArray(512).also { it[index] = 1f }
    @Test fun normalizationRejectsWrongDimension() { assertNull(Embeddings.normalize(FloatArray(511))) }
    @Test fun cosineIdentityIsOne() { assertEquals(1.0, Embeddings.cosine(unit(0), unit(0))!!, 1e-9) }
    @Test fun corroborationMatchesIosPolicy() {
        val e = FaceTemplateMatchPolicy.evaluate(listOf(.49,.47,.2), .52)!!
        assertTrue(e.corroborated); assertTrue(e.accepted); assertEquals(.488, e.decisionScore, 1e-9)
    }
    @Test fun weakSingleRejected() { assertFalse(FaceTemplateMatchPolicy.evaluate(listOf(.60),.52)!!.accepted) }
    @Test fun strongSingleAccepted() { assertTrue(FaceTemplateMatchPolicy.evaluate(listOf(.63),.52)!!.strongSingle) }
    @Test fun ambiguityRejectsWinner() {
        val p1=FaceParticipant("a","m1","id1","r1",5,listOf(unit(0),unit(0)))
        val p2=FaceParticipant("b","m2","id2","r2",5,listOf(unit(0),unit(0)))
        val matcher=FaceMatcher(MatchConfig(minFaceSizeFraction=0.01))
        assertTrue(matcher.appearances(listOf(DetectedEmbedding(unit(0),.2)),listOf(p1,p2)).isEmpty())
    }
}
