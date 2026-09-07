package com.snaploop.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small Android Keystore AES/GCM primitive for device-only sensitive files. */
class KeystoreAes(private val alias: String) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized private fun key(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // With randomized encryption required, Android Keystore must generate the nonce/IV.
        // Supplying our own IV on ENCRYPT_MODE is rejected by KeyMint/Keymaster and would also
        // weaken the platform's nonce-reuse protection. Persist the generated IV with ciphertext.
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain)
        val iv = requireNotNull(cipher.iv) { "Android Keystore did not return an AES-GCM IV" }
        require(iv.size in 12..16) { "Unexpected AES-GCM IV length" }
        return byteArrayOf(iv.size.toByte()) + iv + encrypted
    }

    fun decrypt(payload: ByteArray): ByteArray {
        require(payload.isNotEmpty())
        val ivLength = payload[0].toInt() and 0xff
        require(ivLength in 12..16 && payload.size > 1 + ivLength)
        val iv = payload.copyOfRange(1, 1 + ivLength)
        val encrypted = payload.copyOfRange(1 + ivLength, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }
}
