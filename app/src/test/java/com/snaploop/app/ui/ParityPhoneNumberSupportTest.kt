package com.snaploop.app.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParityPhoneNumberSupportTest {
    @Test
    fun `supported countries match pinned iOS order`() {
        assertEquals(
            listOf("CA", "US", "IN", "GB", "AU", "NZ", "AE", "SG", "DE", "FR", "IT", "ES"),
            ParityPhoneNumberSupport.supportedCountries.map { it.regionCode },
        )
    }

    @Test
    fun `locale defaults to matching country or Canada`() {
        assertEquals("IN", ParityPhoneNumberSupport.localeDefault(Locale("en", "IN")).regionCode)
        assertEquals("CA", ParityPhoneNumberSupport.localeDefault(Locale("en", "JP")).regionCode)
    }

    @Test
    fun `explicit international number is preserved canonically`() {
        val canada = ParityPhoneNumberSupport.supportedCountries.first { it.regionCode == "CA" }
        assertEquals("+14165551234", ParityPhoneNumberSupport.e164("+1 (416) 555-1234", canada))
    }

    @Test
    fun `nanp leading one is not duplicated`() {
        val canada = ParityPhoneNumberSupport.supportedCountries.first { it.regionCode == "CA" }
        assertEquals("+14165551234", ParityPhoneNumberSupport.e164("1 416 555 1234", canada))
    }

    @Test
    fun `local trunk zero is removed before calling code`() {
        val uk = ParityPhoneNumberSupport.supportedCountries.first { it.regionCode == "GB" }
        assertEquals("+442071234567", ParityPhoneNumberSupport.e164("020 7123 4567", uk))
    }

    @Test
    fun `numbers outside e164 length bounds are rejected`() {
        val india = ParityPhoneNumberSupport.supportedCountries.first { it.regionCode == "IN" }
        assertNull(ParityPhoneNumberSupport.e164("123", india))
        assertNull(ParityPhoneNumberSupport.e164("+1234567890123456", india))
    }
}
