package com.snaploop.app.face

import org.junit.Assert.assertEquals
import org.junit.Test

class AuraFacePreprocessorTest {
    @Test fun usesBgrAndIosNormalizationContract() {
        val pixels = IntArray(112 * 112) { 0x00FF0000 }
        val chw = AuraFacePreprocessor.toBgrChw(pixels)
        val plane = 112 * 112
        assertEquals(-127.5f / 128f, chw[0], 1e-6f) // B
        assertEquals(-127.5f / 128f, chw[plane], 1e-6f) // G
        assertEquals(127.5f / 128f, chw[2 * plane], 1e-6f) // R
    }
}
