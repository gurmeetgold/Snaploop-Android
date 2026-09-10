package com.snaploop.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountInstallationIdentityStoreTest {
    @Test
    fun `stable identity is deterministic and backend compatible`() {
        val first = AccountInstallationIdentityStore.stableId("device-scoped-id", "user-1")
        val second = AccountInstallationIdentityStore.stableId("device-scoped-id", "user-1")

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `same device produces different pseudonyms for different accounts`() {
        val first = AccountInstallationIdentityStore.stableId("device-scoped-id", "user-1")
        val second = AccountInstallationIdentityStore.stableId("device-scoped-id", "user-2")

        assertNotEquals(first, second)
    }

    @Test
    fun `different devices produce different pseudonyms for same account`() {
        val first = AccountInstallationIdentityStore.stableId("device-a", "user-1")
        val second = AccountInstallationIdentityStore.stableId("device-b", "user-1")

        assertNotEquals(first, second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank platform identity is rejected`() {
        AccountInstallationIdentityStore.stableId("   ", "user-1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank account identity is rejected`() {
        AccountInstallationIdentityStore.stableId("device-a", "   ")
    }
}
