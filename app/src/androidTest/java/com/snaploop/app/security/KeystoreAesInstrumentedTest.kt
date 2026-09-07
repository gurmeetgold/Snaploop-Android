package com.snaploop.app.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KeystoreAesInstrumentedTest {
    @Test
    fun encryptRoundTripsWithKeystoreGeneratedNonce() {
        val alias = "snaploop.test.keystore.${UUID.randomUUID()}"
        try {
            val crypto = KeystoreAes(alias)
            val plaintext = "SnapLoop local face reference test".toByteArray(Charsets.UTF_8)

            val first = crypto.encrypt(plaintext)
            val second = crypto.encrypt(plaintext)

            assertArrayEquals(plaintext, crypto.decrypt(first))
            assertArrayEquals(plaintext, crypto.decrypt(second))
            assertFalse("AES-GCM encryption must use a fresh IV", first.contentEquals(second))
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                if (containsAlias(alias)) deleteEntry(alias)
            }
        }
    }
}
