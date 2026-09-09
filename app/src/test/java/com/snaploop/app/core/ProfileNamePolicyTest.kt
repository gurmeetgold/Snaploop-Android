package com.snaploop.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileNamePolicyTest {
    @Test
    fun `contract is pinned to twenty characters and two visible characters`() {
        assertEquals(20, ProfileNamePolicy.MAXIMUM_CHARACTERS)
        assertEquals(2, ProfileNamePolicy.MINIMUM_CHARACTERS)
        assertEquals("abcdefghijklmnopqrst", ProfileNamePolicy.normalizeInput("abcdefghijklmnopqrstuv"))
        assertFalse(ProfileNamePolicy.isValid(" A "))
        assertTrue(ProfileNamePolicy.isValid(" GC "))
    }

    @Test
    fun `normalization truncates before trim like pinned iOS input model`() {
        assertEquals("Gurmeet", ProfileNamePolicy.normalizeForSave("  Gurmeet  "))
        assertEquals(20, ProfileNamePolicy.normalizeForSave("abcdefghijklmnopqrstuv").length)
        assertEquals("abcdefghijklmnopqrst", ProfileNamePolicy.normalizeOptionalForSave("abcdefghijklmnopqrstuv"))
        assertNull(ProfileNamePolicy.normalizeOptionalForSave("   "))
    }

    @Test
    fun `validation copy matches pinned iOS profile name screen`() {
        assertEquals("Enter at least 2 characters.", ProfileNamePolicy.validationMessage("A"))
        assertNull(ProfileNamePolicy.validationMessage("AB"))
    }
}
