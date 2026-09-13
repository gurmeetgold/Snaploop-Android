package com.snaploop.app.model

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricJurisdictionCatalogTest {
    @Test
    fun `global country catalog does not expose rollout list`() {
        val countries = BiometricJurisdictionCatalog.countries(Locale.ENGLISH)
        val codes = countries.map { it.code }.toSet()

        assertTrue(codes.contains("CA"))
        assertTrue(codes.contains("IN"))
        assertTrue(codes.contains("US"))
        assertTrue(codes.size > 100)
    }

    @Test
    fun `device region becomes initial country when valid`() {
        val india = Locale.Builder().setLanguage("en").setRegion("IN").build()
        val canada = Locale.Builder().setLanguage("en").setRegion("CA").build()

        assertEquals("IN", BiometricJurisdictionCatalog.defaultCountryCode(india))
        assertEquals("CA", BiometricJurisdictionCatalog.defaultCountryCode(canada))
        assertEquals("ON", BiometricJurisdictionCatalog.defaultSubdivision("CA"))
    }

    @Test
    fun `quebec is displayed but remains unavailable for face match`() {
        assertTrue(BiometricJurisdictionCatalog.canadianSubdivisions.any { it.code == "QC" })
        assertFalse(BiometricJurisdictionCatalog.faceMatchCanadianSubdivisionCodes.contains("QC"))
        assertFalse(BiometricJurisdiction("CA", "QC").isFaceMatchAvailable)
    }

    @Test
    fun `supported launch jurisdictions keep face match policy`() {
        assertTrue(BiometricJurisdiction("IN").isFaceMatchAvailable)
        assertTrue(BiometricJurisdiction("CA", "ON").isFaceMatchAvailable)
        assertFalse(BiometricJurisdiction("US").isFaceMatchAvailable)
    }
}
