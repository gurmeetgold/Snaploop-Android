package com.snaploop.app.invite

import java.util.Locale

data class PhoneCountry(
    val regionCode: String,
    val name: String,
    val callingCode: String,
) {
    companion object {
        val supported = listOf(
            PhoneCountry("CA", "Canada", "+1"),
            PhoneCountry("US", "United States", "+1"),
            PhoneCountry("IN", "India", "+91"),
            PhoneCountry("GB", "United Kingdom", "+44"),
            PhoneCountry("AU", "Australia", "+61"),
            PhoneCountry("NZ", "New Zealand", "+64"),
            PhoneCountry("AE", "United Arab Emirates", "+971"),
            PhoneCountry("SG", "Singapore", "+65"),
            PhoneCountry("DE", "Germany", "+49"),
            PhoneCountry("FR", "France", "+33"),
            PhoneCountry("IT", "Italy", "+39"),
            PhoneCountry("ES", "Spain", "+34"),
        )

        fun localeDefault(locale: Locale = Locale.getDefault()): PhoneCountry {
            val region = locale.country.uppercase(Locale.ROOT)
            return supported.firstOrNull { it.regionCode == region }
                ?: supported.first { it.regionCode == "CA" }
        }
    }
}

/** Exact conservative E.164 normalization policy used by the pinned iOS app. */
object PhoneNumberNormalizer {
    fun e164(localInput: String, country: PhoneCountry): String? {
        val trimmed = localInput.trim()
        val allowed = trimmed.filter { it.isDigit() || it == '+' }

        if (allowed.startsWith('+')) {
            val digits = allowed.drop(1).filter(Char::isDigit)
            if (digits.length !in 8..15) return null
            return "+$digits"
        }

        var digits = allowed.filter(Char::isDigit).trimStart('0')
        val callingDigits = country.callingCode.filter(Char::isDigit)
        if ((country.regionCode == "CA" || country.regionCode == "US") &&
            digits.length == 11 && digits.firstOrNull() == '1'
        ) {
            digits = digits.drop(1)
        }

        val combined = callingDigits + digits
        if (combined.length !in 8..15) return null
        return "+$combined"
    }

    fun localDisplayNumber(raw: String, country: PhoneCountry): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith('+')) return raw
        val digits = trimmed.filter(Char::isDigit)
        val callingDigits = country.callingCode.filter(Char::isDigit)
        if (callingDigits.isEmpty() || !digits.startsWith(callingDigits)) return raw
        return digits.drop(callingDigits.length)
    }
}
