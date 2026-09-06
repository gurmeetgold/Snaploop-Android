package com.snaploop.app.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FaceGeometryTest {
    @Test fun recoversKnownSimilarityTransform() {
        val angle = 0.22
        val scale = 1.37
        val c = cos(angle) * scale
        val s = sin(angle) * scale
        val known = SimilarityTransform(c, s, 14.5, -7.25)
        val source = CanonicalFaceGeometry.canonical112
        val target = source.map(known::map)
        val estimated = CanonicalFaceGeometry.estimateSimilarity(source, target)
        assertNotNull(estimated)
        estimated!!
        assertEquals(known.c, estimated.c, 1e-9)
        assertEquals(known.s, estimated.s, 1e-9)
        assertEquals(known.tx, estimated.tx, 1e-8)
        assertEquals(known.ty, estimated.ty, 1e-8)
    }
}
