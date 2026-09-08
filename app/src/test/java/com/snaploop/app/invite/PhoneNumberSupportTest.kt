package com.snaploop.app.invite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberSupportTest {
    private val canada = PhoneCountry.supported.first { it.regionCode == "CA" }
    private val india = PhoneCountry.supported.first { it.regionCode == "IN" }
    private val uk = PhoneCountry.supported.first { it.regionCode == "GB" }

    @Test
    fun `canonical plus number is preserved after punctuation removal`() {
        assertEquals("+14165551234", PhoneNumberNormalizer.e164("+1 (416) 555-1234", canada))
    }

    @Test
    fun `canadian leading one is not duplicated`() {
        assertEquals("+14165551234", PhoneNumberNormalizer.e164("1 416 555 1234", canada))
    }

    @Test
    fun `local leading zeroes are stripped before country code`() {
        assertEquals("+447911123456", PhoneNumberNormalizer.e164("07911 123456", uk))
    }

    @Test
    fun `india local number uses selected country calling code`() {
        assertEquals("+919876543210", PhoneNumberNormalizer.e164("09876 543210", india))
    }

    @Test
    fun `numbers outside e164 length bounds are rejected`() {
        assertNull(PhoneNumberNormalizer.e164("123", canada))
        assertNull(PhoneNumberNormalizer.e164("+1234567890123456", canada))
    }

    @Test
    fun `local display strips selected calling code only`() {
        assertEquals("4165551234", PhoneNumberNormalizer.localDisplayNumber("+14165551234", canada))
        assertEquals("+919876543210", PhoneNumberNormalizer.localDisplayNumber("+919876543210", canada))
    }
}
