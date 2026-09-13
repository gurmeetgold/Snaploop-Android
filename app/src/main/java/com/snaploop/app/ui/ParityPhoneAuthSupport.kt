package com.snaploop.app.ui

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

internal data class ParityPhoneCountry(
    val regionCode: String,
    val name: String,
    val callingCode: String,
)

internal object ParityPhoneNumberSupport {
    val supportedCountries = listOf(
        ParityPhoneCountry("CA", "Canada", "+1"),
        ParityPhoneCountry("US", "United States", "+1"),
        ParityPhoneCountry("IN", "India", "+91"),
        ParityPhoneCountry("GB", "United Kingdom", "+44"),
        ParityPhoneCountry("AU", "Australia", "+61"),
        ParityPhoneCountry("NZ", "New Zealand", "+64"),
        ParityPhoneCountry("AE", "United Arab Emirates", "+971"),
        ParityPhoneCountry("SG", "Singapore", "+65"),
        ParityPhoneCountry("DE", "Germany", "+49"),
        ParityPhoneCountry("FR", "France", "+33"),
        ParityPhoneCountry("IT", "Italy", "+39"),
        ParityPhoneCountry("ES", "Spain", "+34"),
    )

    fun localeDefault(locale: Locale = Locale.getDefault()): ParityPhoneCountry {
        val region = locale.country.uppercase(Locale.US)
        return supportedCountries.firstOrNull { it.regionCode == region }
            ?: supportedCountries.first { it.regionCode == "CA" }
    }

    /**
     * Prefer the SIM country, then the currently registered mobile network,
     * then the device locale. This avoids treating the UI language/locale as
     * the user's phone-number country, which is unreliable for travellers and
     * devices that were originally configured in another country.
     */
    fun preferredRegionCode(
        simCountryIso: String?,
        networkCountryIso: String?,
        locale: Locale = Locale.getDefault(),
    ): String {
        return sequenceOf(simCountryIso, networkCountryIso, locale.country)
            .mapNotNull { it?.trim()?.uppercase(Locale.US)?.takeIf { code -> code.length == 2 } }
            .firstOrNull()
            ?: "CA"
    }

    fun deviceRegionCode(context: Context): String {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val sim = runCatching { telephony?.simCountryIso }.getOrNull()
        val network = runCatching { telephony?.networkCountryIso }.getOrNull()
        return preferredRegionCode(sim, network, Locale.getDefault())
    }

    fun deviceDefault(context: Context): ParityPhoneCountry {
        val region = deviceRegionCode(context)
        return supportedCountries.firstOrNull { it.regionCode == region }
            ?: localeDefault()
    }

    /** Conservative normalizer mirrored from pinned iOS PhoneNumberSupport.swift. */
    fun e164(localInput: String, country: ParityPhoneCountry): String? {
        val allowed = localInput.trim().filter { it.isDigit() || it == '+' }
        if (allowed.startsWith('+')) {
            val digits = allowed.drop(1).filter(Char::isDigit)
            return if (digits.length in 8..15) "+$digits" else null
        }

        var digits = allowed.filter(Char::isDigit)
        while (digits.startsWith('0')) digits = digits.drop(1)
        if ((country.regionCode == "CA" || country.regionCode == "US") &&
            digits.length == 11 && digits.startsWith('1')
        ) {
            digits = digits.drop(1)
        }

        val callingDigits = country.callingCode.filter(Char::isDigit)
        val combined = callingDigits + digits
        return if (combined.length in 8..15) "+$combined" else null
    }

    const val INVALID_PHONE_MESSAGE =
        "That phone number doesn't look right. Please check and try again."
}
