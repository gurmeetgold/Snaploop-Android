package com.snaploop.app.core

/** Canonical display-name contract mirrored from pinned iOS ProfileNameModel. */
object ProfileNamePolicy {
    const val MAXIMUM_CHARACTERS = 20
    const val MINIMUM_CHARACTERS = 2

    fun normalizeInput(value: String): String = value.take(MAXIMUM_CHARACTERS)

    fun normalizeForSave(value: String): String = normalizeInput(value).trim()

    fun normalizeOptionalForSave(value: String?): String? = value
        ?.let(::normalizeForSave)
        ?.takeIf { it.isNotBlank() }

    fun isValid(value: String): Boolean = normalizeForSave(value).length >= MINIMUM_CHARACTERS

    fun validationMessage(value: String): String? =
        if (isValid(value)) null else "Enter at least 2 characters."
}
